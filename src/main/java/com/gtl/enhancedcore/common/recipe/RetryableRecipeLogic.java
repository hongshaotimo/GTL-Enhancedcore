package com.gtl.enhancedcore.common.recipe;

import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.RecipeHelper;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.lowdragmc.lowdraglib.syncdata.annotation.DescSynced;
import com.lowdragmc.lowdraglib.syncdata.field.ManagedFieldHolder;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import net.minecraft.network.chat.Component;
import org.gtlcore.gtlcore.api.machine.trait.ILockRecipe;
import org.gtlcore.gtlcore.api.machine.trait.IRecipeStatus;
import org.gtlcore.gtlcore.api.recipe.IAdditionalRecipeIterator;
import org.gtlcore.gtlcore.api.recipe.RecipeResult;
import org.gtlcore.gtlcore.api.recipe.RecipeRunnerHelper;

/** Shared single-recipe admission, retry and rejection snapshots. */
public class RetryableRecipeLogic extends RecipeLogic {
    private static final ManagedFieldHolder FACTORY_FIELDS = new ManagedFieldHolder(RetryableRecipeLogic.class, MANAGED_FIELD_HOLDER);
    @DescSynced private String diagnosticJson = "";
    private RecipeResult searchFailure;
    private String searchDiagnostic = "";

    public RetryableRecipeLogic(WorkableElectricMultiblockMachine machine) {
        super(machine);
    }

    @Override
    public ManagedFieldHolder getFieldHolder() {
        return FACTORY_FIELDS;
    }

    public Component getDiagnosticReason() {
        return MachineDiagnostics.decode(diagnosticJson);
    }

    @Override
    public void serverTick() {
        if (!((WorkableElectricMultiblockMachine) getMachine()).isRecipeLogicAvailable()) return;
        if (!RecipeRetryPolicy.shouldTick(getMachine().getOffsetTimer(), isIdle(), lastRecipe != null, recipeDirty)) return;
        if (isIdle() && lastRecipe == null) markLastRecipeDirty();
        super.serverTick();
    }

    @Override
    public void onRecipeFinish() {
        if (lastRecipe != null && !RecipeRunnerHelper.matchRecipeOutput(machine, lastRecipe)) {
            diagnosticJson = Component.Serializer.toJson(MachineDiagnostics.text("output"));
            ((IRecipeStatus) (Object) this).setWorkingStatus(RecipeResult.FAIL_OUTPUT);
            setWaiting(MachineDiagnostics.text("output"));
            return;
        }
        markLastRecipeDirty();
        super.onRecipeFinish();
        if (isWorking()) diagnosticJson = "";
        if (isIdle()) findAndHandleRecipe();
    }

    @Override
    public void findAndHandleRecipe() {
        GTRecipe previous = lastOriginRecipe;
        lastRecipe = null;
        lastOriginRecipe = null;
        lastFailedMatches = null;
        var state = (IRecipeStatus) (Object) this;
        state.setRecipeStatus(null);
        state.setWorkingStatus(null);
        searchFailure = null;
        searchDiagnostic = "";
        diagnosticJson = "";
        var lock = (ILockRecipe) (Object) this;
        try {
            if (!machine.hasProxies()) return;
            if (lock.isLock() && lock.getLockRecipe() != null) {
                tryCandidate(lock.getLockRecipe());
                return;
            }
            Set<GTRecipe> tried = Collections.newSetFromMap(new IdentityHashMap<>());
            if (!recipeDirty && previous != null && previous.recipeType == machine.getRecipeType()) {
                tried.add(previous);
                if (tryCandidate(previous)) return;
            }
            var iterator = machine.getRecipeType().getLookup().getRecipeIterator(machine, recipe -> true);
            ((IAdditionalRecipeIterator) iterator).setUseDiveIngredientTreeFind(true);
            while (iterator.hasNext()) {
                GTRecipe candidate = iterator.next();
                if (candidate != null && tried.add(candidate) && tryCandidate(candidate)) return;
            }
            for (var runner : machine.getRecipeType().getCustomRecipeLogicRunners()) {
                GTRecipe candidate = runner.createCustomRecipe(machine);
                if (candidate != null && tryCandidate(candidate)) return;
            }
        } finally {
            recipeDirty = false;
            if (lastRecipe == null) {
                state.setRecipeStatus(searchFailure == null ? RecipeResult.FAIL_FIND : searchFailure);
                diagnosticJson = searchDiagnostic;
            }
        }
    }

    private boolean tryCandidate(GTRecipe candidate) {
        diagnosticJson = "";
        if (checkMatchedRecipeAvailable(candidate)) return true;
        RecipeResult reason = ((IRecipeStatus) (Object) this).getRecipeStatus();
        if (reason != null && !reason.isSuccess()
                && (searchFailure == null || failurePriority(reason) > failurePriority(searchFailure))) {
            searchFailure = reason;
            searchDiagnostic = diagnosticJson;
        }
        return false;
    }

    private static int failurePriority(RecipeResult reason) {
        if (reason.equals(RecipeResult.FAIL_NO_ENOUGH_EU_IN)) return 4;
        if (reason.equals(RecipeResult.FAIL_OUTPUT)) return 3;
        if (reason.equals(RecipeResult.FAIL_FIND) || reason.equals(RecipeResult.FAIL_INPUT)) return 0;
        return 1;
    }

    @Override
    public boolean checkMatchedRecipeAvailable(GTRecipe match) {
        if (match.recipeType != machine.getRecipeType()) return false;
        if (!RecipeRunnerHelper.matchRecipeInput(machine, match)) {
            ((IRecipeStatus) (Object) this).setRecipeStatus(RecipeResult.FAIL_INPUT);
            return false;
        }
        ((IRecipeStatus) (Object) this).setRecipeStatus(null);
        GTRecipe modified;
        ocResult.reset();
        try {
            modified = machine.fullModifyRecipe(match.copy(), ocParams, ocResult);
        } catch (ArithmeticException overflow) {
            fail("gtl_enhancedcore.diagnostic.amount_overflow");
            return false;
        } finally {
            ocResult.reset();
        }
        if (modified == null) {
            var state = (IRecipeStatus) (Object) this;
            if (state.getRecipeStatus() == null || state.getRecipeStatus().isSuccess()) {
                if (!RecipeRunnerHelper.matchRecipeOutput(machine, match)) state.setRecipeStatus(RecipeResult.FAIL_OUTPUT);
                else {
                    Component reason = rejectedModifierReason(match);
                    state.setRecipeStatus(RecipeResult.fail(reason));
                    diagnosticJson = Component.Serializer.toJson(reason);
                }
            }
            if (RecipeResult.FAIL_VOLTAGE_TIER.equals(state.getRecipeStatus()))
                diagnosticJson = Component.Serializer.toJson(MachineDiagnostics.voltage((WorkableElectricMultiblockMachine) getMachine(), match));
            return false;
        }
        if (!FactoryRecipeSafety.valid(modified)) {
            fail("gtl_enhancedcore.diagnostic.amount_overflow");
            return false;
        }
        if (!RecipeRunnerHelper.matchRecipeInput(machine, modified)) {
            ((IRecipeStatus) (Object) this).setRecipeStatus(RecipeResult.FAIL_INPUT);
            return false;
        }
        if (!RecipeRunnerHelper.matchRecipeOutput(machine, modified)) {
            ((IRecipeStatus) (Object) this).setRecipeStatus(RecipeResult.FAIL_OUTPUT);
            return false;
        }
        var energy = ((WorkableElectricMultiblockMachine) getMachine()).getEnergyContainer();
        if (requiresStoredEnergy() && RecipeHelper.getInputEUt(modified) > (energy == null ? 0 : energy.getEnergyStored())) {
            ((IRecipeStatus) (Object) this).setRecipeStatus(RecipeResult.FAIL_NO_ENOUGH_EU_IN);
            diagnosticJson = Component.Serializer.toJson(MachineDiagnostics.power((WorkableElectricMultiblockMachine) getMachine(), RecipeHelper.getInputEUt(modified)));
            return false;
        }
        if (!accept(modified.checkConditions(this), modified) || !accept(modified.matchTickRecipe(machine))) return false;
        lastOriginRecipe = match;
        setupRecipe(modified);
        if (lastRecipe == null || !isWorking()) {
            lastOriginRecipe = null;
            Component rejected = rejectedStartReason(modified);
            ((IRecipeStatus) (Object) this).setRecipeStatus(RecipeResult.fail(rejected));
            diagnosticJson = Component.Serializer.toJson(rejected);
            return false;
        }
        if (((ILockRecipe) (Object) this).isLock()) ((ILockRecipe) (Object) this).setLockRecipe(match);
        ((IRecipeStatus) (Object) this).setRecipeStatus(RecipeResult.SUCCESS);
        diagnosticJson = "";
        getMachine().markDirty();
        return true;
    }

    protected boolean requiresStoredEnergy() {
        return true;
    }

    protected Component rejectedModifierReason(GTRecipe recipe) {
        return MachineDiagnostics.text("modifier_rejected");
    }

    protected Component rejectedStartReason(GTRecipe recipe) {
        return MachineDiagnostics.text("start_rejected");
    }

    private boolean accept(GTRecipe.ActionResult result) {
        return accept(result, null);
    }

    private boolean accept(GTRecipe.ActionResult result, GTRecipe recipe) {
        if (result.isSuccess()) return true;
        Component reason = result.reason() == null ? null : result.reason().get();
        ((IRecipeStatus) (Object) this).setRecipeStatus(RecipeResult.fail(reason == null
                ? Component.translatable("gtl_enhancedcore.diagnostic.conditions") : reason));
        diagnosticJson = Component.Serializer.toJson(recipe != null ? MachineDiagnostics.conditionDetail(recipe, reason)
                : reason == null ? MachineDiagnostics.text("conditions")
                : Component.translatable("gtl_enhancedcore.diagnostic.condition_detail", reason));
        return false;
    }

    private void fail(String key) {
        ((IRecipeStatus) (Object) this).setRecipeStatus(RecipeResult.fail(Component.translatable(key)));
        diagnosticJson = Component.Serializer.toJson(Component.translatable(key));
    }
}
