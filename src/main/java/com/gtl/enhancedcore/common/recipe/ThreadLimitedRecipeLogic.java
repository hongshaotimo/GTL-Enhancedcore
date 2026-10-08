package com.gtl.enhancedcore.common.recipe;

import com.gregtechceu.gtceu.api.capability.recipe.FluidRecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.RecipeCapability;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.RecipeHelper;
import com.gregtechceu.gtceu.api.recipe.content.Content;
import com.gregtechceu.gtceu.data.recipe.builder.GTRecipeBuilder;
import com.lowdragmc.lowdraglib.syncdata.annotation.DescSynced;
import com.lowdragmc.lowdraglib.syncdata.field.ManagedFieldHolder;
import com.gtl.enhancedcore.common.util.FairRecipeSelector;
import com.gtladd.gtladditions.api.machine.logic.GTLAddMultipleRecipesLogic;
import com.gtladd.gtladditions.api.machine.multiblock.GTLAddWorkableElectricMultipleRecipesMachine;
import com.gtladd.gtladditions.api.machine.trait.IWirelessNetworkEnergyHandler;
import com.gtladd.gtladditions.api.recipe.WirelessGTRecipe;
import com.gtladd.gtladditions.common.data.ParallelData;
import com.gtladd.gtladditions.utils.RecipeCalculationHelper;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.IntSupplier;
import net.minecraft.network.chat.Component;
import org.gtlcore.gtlcore.api.recipe.IGTRecipe;
import org.gtlcore.gtlcore.api.recipe.IAdditionalRecipeIterator;
import org.gtlcore.gtlcore.api.recipe.IParallelLogic;
import org.gtlcore.gtlcore.api.recipe.RecipeResult;
import org.gtlcore.gtlcore.api.recipe.RecipeRunnerHelper;

/** Shares GTLAdditions processing while enforcing the advertised concurrent recipe count. */
public final class ThreadLimitedRecipeLogic extends GTLAddMultipleRecipesLogic {
    private static final ManagedFieldHolder FIELDS = new ManagedFieldHolder(ThreadLimitedRecipeLogic.class, MANAGED_FIELD_HOLDER);
    @DescSynced private String diagnosticJson = "";
    private final IntSupplier threads;
    private final FairRecipeSelector<GTRecipe> selector = new FairRecipeSelector<>();
    private RecipeResult searchFailure;
    private String searchDiagnostic = "";
    private GTRecipe pendingLock;
    private record Admission(GTRecipe origin, long parallel, GTRecipe scaled) {}

    public ThreadLimitedRecipeLogic(GTLAddWorkableElectricMultipleRecipesMachine machine, IntSupplier threads) {
        super(machine);
        this.threads = threads;
    }

    @Override
    public ManagedFieldHolder getFieldHolder() {
        return FIELDS;
    }

    public Component getDiagnosticReason() {
        return MachineDiagnostics.decode(diagnosticJson);
    }

    @Override
    public int getMultipleThreads() {
        return Math.max(1, threads.getAsInt());
    }

    @Override
    public void serverTick() {
        if (!getMachine().isRecipeLogicAvailable()) return;
        if (!RecipeRetryPolicy.shouldTick(getMachine().getOffsetTimer(), isIdle(), lastRecipe != null, recipeDirty)) return;
        if (isIdle() && lastRecipe == null) {
            lastFailedMatches = null;
            markLastRecipeDirty();
        }
        super.serverTick();
    }

    @Override
    public void findAndHandleRecipe() {
        setWorkingStatus(null);
        super.findAndHandleRecipe();
        if (isWorking()) committed();
        else if (lastRecipe == null && (getRecipeStatus() == null || getRecipeStatus().isSuccess()))
            setRecipeStatus(searchFailure == null ? RecipeResult.FAIL_FIND : searchFailure);
    }

    @Override
    protected GTRecipe getGTRecipe() {
        searchFailure = null;
        searchDiagnostic = "";
        diagnosticJson = "";
        pendingLock = null;
        GTRecipe recipe = super.getGTRecipe();
        if (recipe == null && searchFailure != null) {
            var current = getRecipeStatus();
            if (current == null || current.isSuccess() || priority(searchFailure) >= priority(current)) {
                setRecipeStatus(searchFailure);
                diagnosticJson = searchDiagnostic;
            }
        }
        return recipe;
    }

    @Override
    protected GTRecipe buildFinalNormalRecipe(ParallelData data) {
        return prepareRecipe(data, null);
    }

    @Override
    protected WirelessGTRecipe buildFinalWirelessRecipe(ParallelData data, IWirelessNetworkEnergyHandler wireless) {
        return (WirelessGTRecipe) prepareRecipe(data, wireless);
    }

    private GTRecipe prepareRecipe(ParallelData data, IWirelessNetworkEnergyHandler wireless) {
        if (wireless != null && !wireless.isOnline()) {
            record(RecipeResult.FAIL_NO_ENOUGH_EU_IN, MachineDiagnostics.text("iv_native_wireless_offline"));
            return null;
        }
        var admitted = new ArrayList<Admission>();
        for (int recipeIndex = 0; recipeIndex < data.getOriginRecipeList().size(); recipeIndex++) {
            GTRecipe origin = data.getOriginRecipeList().get(recipeIndex);
            long accepted = BoundedRecipeAdmission.maximum(data.getParallels()[recipeIndex],
                    parallel -> fits(admitted, origin, parallel, wireless));
            if (accepted <= 0) continue;
            GTRecipe scaled = RecipeCalculationHelper.INSTANCE.multipleRecipe(origin.copy(), accepted);
            admitted.add(new Admission(origin, accepted, scaled));
            if (wireless == null && totalEnergy(admitted) / getMachine().getOverclockVoltage() > 10000) break;
        }
        if (admitted.isEmpty()) return null;
        GTRecipe batch = buildBatch(admitted, wireless, false);
        if (!batch.matchTickRecipe(machine).isSuccess()) {
            record(RecipeResult.FAIL_NO_ENOUGH_EU_IN, wireless == null
                    ? MachineDiagnostics.power(getMachine(), RecipeHelper.getInputEUt(batch))
                    : MachineDiagnostics.text("wireless_power"));
            return null;
        }
        batch = buildBatch(admitted, wireless, true);
        if (!RecipeRunnerHelper.matchRecipeInputNocache(machine, batch)) {
            record(RecipeResult.FAIL_INPUT, MachineDiagnostics.text("input"));
            return null;
        }
        if (!RecipeRunnerHelper.matchRecipeOutput(machine, batch)) {
            record(RecipeResult.FAIL_OUTPUT, MachineDiagnostics.text("output"));
            return null;
        }
        return batch;
    }

    private boolean fits(List<Admission> admitted, GTRecipe origin, long parallel,
                         IWirelessNetworkEnergyHandler wireless) {
        GTRecipe scaled;
        try {
            scaled = RecipeCalculationHelper.INSTANCE.multipleRecipe(origin.copy(), parallel);
        } catch (ArithmeticException overflow) {
            record(RecipeResult.fail(MachineDiagnostics.text("amount_overflow")), MachineDiagnostics.text("amount_overflow"));
            return false;
        }
        if (!FactoryRecipeSafety.valid(scaled)) {
            record(RecipeResult.fail(MachineDiagnostics.text("amount_overflow")), MachineDiagnostics.text("amount_overflow"));
            return false;
        }
        var proposed = new ArrayList<>(admitted);
        proposed.add(new Admission(origin, parallel, scaled));
        double energy = totalEnergy(proposed);
        if (wireless == null && (!Double.isFinite(energy) || energy < 0
                || energy / getMachine().getOverclockVoltage() > Integer.MAX_VALUE)) {
            record(RecipeResult.fail(MachineDiagnostics.text("amount_overflow")), MachineDiagnostics.text("amount_overflow"));
            return false;
        }
        if (wireless != null && isEnergyConsumer() && wirelessEnergy(proposed).compareTo(wireless.getMaxAvailableEnergy()) > 0) {
            record(RecipeResult.FAIL_NO_ENOUGH_EU_IN, Component.translatable("gtl_enhancedcore.diagnostic.iv_native_wireless_energy",
                    wirelessEnergy(proposed).toString(), wireless.getMaxAvailableEnergy().toString()));
            return false;
        }
        GTRecipe batch = buildBatch(proposed, wireless, false);
        if (!RecipeRunnerHelper.matchRecipeInputNocache(machine, batch)) {
            record(RecipeResult.FAIL_INPUT, MachineDiagnostics.text("input"));
            return false;
        }
        if (!RecipeRunnerHelper.matchRecipeOutput(machine, batch)) {
            record(RecipeResult.FAIL_OUTPUT, MachineDiagnostics.text("output"));
            return false;
        }
        return true;
    }

    private GTRecipe buildBatch(List<Admission> admitted, IWirelessNetworkEnergyHandler wireless, boolean settleOutputs) {
        GTRecipe combined = GTRecipeBuilder.ofRaw().buildRawRecipe();
        var inputs = new RecipeInputAdmission();
        for (var admission : admitted) {
            GTRecipe scaled = admission.scaled();
            append(combined.outputs, (settleOutputs ? IParallelLogic.getRecipeOutputChance(machine, scaled) : scaled).outputs);
            inputs.include(machine, scaled, settleOutputs);
        }
        inputs.writeTo(combined.inputs);
        var itemOutputs = combined.outputs.getOrDefault(ItemRecipeCapability.CAP, List.of());
        var fluidOutputs = combined.outputs.getOrDefault(FluidRecipeCapability.CAP, List.of());
        int minimumDuration = Math.max(1, getLimited().getLimitedDuration());
        GTRecipe batch = wireless == null
                ? RecipeCalculationHelper.INSTANCE.buildNormalRecipe(itemOutputs, fluidOutputs, totalEnergy(admitted),
                        getMachine().getOverclockVoltage(), minimumDuration)
                : RecipeCalculationHelper.INSTANCE.buildWirelessRecipe(itemOutputs, fluidOutputs, minimumDuration,
                        isEnergyConsumer() ? wirelessEnergy(admitted) : wirelessEnergy(admitted).negate(), combined.recipeType);
        append(batch.inputs, combined.inputs);
        return batch;
    }

    private double totalEnergy(List<Admission> admitted) {
        double total = 0;
        for (var admission : admitted) total += getTotalEuOfRecipe(admission.origin()) * admission.parallel() * getEuMultiplier();
        return total;
    }

    private BigInteger wirelessEnergy(List<Admission> admitted) {
        BigInteger total = BigInteger.ZERO;
        for (var admission : admitted) {
            BigInteger parallelEUt = BigInteger.valueOf(getWirelessRecipeEut(admission.origin()))
                    .multiply(BigInteger.valueOf(admission.parallel()));
            total = total.add(BigDecimal.valueOf(admission.origin().duration * getEuMultiplier())
                    .multiply(new BigDecimal(parallelEUt)).toBigInteger());
        }
        return total;
    }

    private static void append(Map<RecipeCapability<?>, List<Content>> target,
                               Map<RecipeCapability<?>, List<Content>> source) {
        source.forEach((capability, contents) -> target.computeIfAbsent(capability, ignored -> new ArrayList<>()).addAll(contents));
    }

    @Override
    protected boolean handleRecipeIO(GTRecipe recipe, IO io) {
        return io == IO.IN && machine.hasProxies() && RecipeRunnerHelper.handleRecipeInputNocache(machine, recipe);
    }

    @Override
    protected boolean checkBeforeWorking() {
        if (super.checkBeforeWorking()) return true;
        record(RecipeResult.fail(MachineDiagnostics.text(!getMachine().isRecipeLogicAvailable()
                ? "parts_unloaded" : getMachine().getOverclockVoltage() <= 0 ? "power" : "start_rejected")),
                !getMachine().isRecipeLogicAvailable() ? MachineDiagnostics.text("parts_unloaded")
                        : getMachine().getOverclockVoltage() <= 0 ? MachineDiagnostics.power(getMachine(), 0)
                        : MachineDiagnostics.text("start_rejected"));
        return false;
    }

    @Override
    protected boolean checkRecipe(GTRecipe recipe) {
        if (!RecipeRunnerHelper.matchRecipeInput(machine, recipe)) {
            record(RecipeResult.FAIL_INPUT, MachineDiagnostics.text("input"));
            return false;
        }
        if (!RecipeRunnerHelper.matchRecipeOutput(machine, recipe)) {
            record(RecipeResult.FAIL_OUTPUT, MachineDiagnostics.text("output"));
            return false;
        }
        if (IGTRecipe.of(recipe).getEuTier() > getMachine().getTier()) {
            record(RecipeResult.FAIL_VOLTAGE_TIER, MachineDiagnostics.voltage(getMachine(), recipe));
            return false;
        }
        var condition = recipe.checkConditions(machine.getRecipeLogic());
        if (!condition.isSuccess()) {
            Component reason = condition.reason() == null ? MachineDiagnostics.text("conditions") : condition.reason().get();
            if (reason == null) reason = MachineDiagnostics.text("conditions");
            record(RecipeResult.fail(reason), MachineDiagnostics.conditionDetail(recipe, reason));
            return false;
        }
        if (getRecipeCheck() != null && !getRecipeCheck().test(recipe, machine)) {
            record(RecipeResult.fail(MachineDiagnostics.text("start_rejected")), MachineDiagnostics.text("start_rejected"));
            return false;
        }
        return true;
    }

    @Override
    public void onRecipeFinish() {
        if (lastRecipe != null && !RecipeRunnerHelper.matchRecipeOutput(machine, lastRecipe)) {
            setWorkingStatus(RecipeResult.FAIL_OUTPUT);
            diagnosticJson = Component.Serializer.toJson(MachineDiagnostics.text("output"));
            setWaiting(MachineDiagnostics.text("output"));
            return;
        }
        setWorkingStatus(null);
        super.onRecipeFinish();
        if (isWorking()) committed();
    }

    private void committed() {
        diagnosticJson = "";
        if (isLock() && getLockRecipe() == null && pendingLock != null) setLockRecipe(pendingLock);
        pendingLock = null;
    }

    private void record(RecipeResult failure, Component detail) {
        setRecipeStatus(failure);
        if (searchFailure == null || priority(failure) > priority(searchFailure)) {
            searchFailure = failure;
            searchDiagnostic = Component.Serializer.toJson(detail);
        }
    }

    private static int priority(RecipeResult failure) {
        if (failure.equals(RecipeResult.FAIL_NO_ENOUGH_EU_IN)) return 4;
        if (failure.equals(RecipeResult.FAIL_OUTPUT)) return 3;
        if (failure.equals(RecipeResult.FAIL_FIND) || failure.equals(RecipeResult.FAIL_INPUT)) return 0;
        return 1;
    }

    @Override
    protected Set<GTRecipe> lookupRecipeIterator() {
        if (isLock() && getLockRecipe() != null)
            return checkRecipe(getLockRecipe()) ? Set.of(getLockRecipe()) : Set.of();
        var iterator = machine.getRecipeType().getLookup().getRecipeIterator(machine, recipe -> true);
        ((IAdditionalRecipeIterator) iterator).setUseDiveIngredientTreeFind(true);
        Set<GTRecipe> candidates = new LinkedHashSet<>();
        while (iterator.hasNext()) {
            GTRecipe candidate = iterator.next();
            if (candidate != null && checkRecipe(candidate)) candidates.add(candidate);
        }
        var selected = selector.select(candidates, isLock() ? 1 : getMultipleThreads(), recipe -> recipe.id.toString());
        if (isLock() && !selected.isEmpty()) pendingLock = selected.iterator().next();
        return selected;
    }
}
