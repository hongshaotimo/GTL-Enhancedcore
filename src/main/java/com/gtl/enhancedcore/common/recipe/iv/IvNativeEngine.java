package com.gtl.enhancedcore.common.recipe.iv;

import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.*;
import appeng.api.storage.StorageHelper;
import com.gregtechceu.gtceu.api.capability.IEnergyContainer;
import com.gregtechceu.gtceu.api.capability.IDataAccessHatch;
import com.gregtechceu.gtceu.api.capability.recipe.*;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.CoilWorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gregtechceu.gtceu.api.recipe.*;
import com.gregtechceu.gtceu.api.recipe.content.Content;
import com.gregtechceu.gtceu.api.recipe.ingredient.FluidIngredient;
import com.gregtechceu.gtceu.api.recipe.logic.*;
import com.gregtechceu.gtceu.common.recipe.condition.ResearchCondition;
import com.gtl.enhancedcore.common.util.FairRecipeSelector;
import com.gtladd.gtladditions.api.machine.IWirelessElectricMultiblockMachine;
import com.gtladd.gtladditions.api.machine.trait.IWirelessNetworkEnergyHandler;
import org.gtlcore.gtlcore.common.machine.multiblock.part.ae.MEPatternBufferPartMachine;
import com.gtladd.gtladditions.utils.RecipeCalculationHelper;
import com.lowdragmc.lowdraglib.side.fluid.FluidStack;
import java.math.*;
import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import org.gtlcore.gtlcore.api.machine.trait.IRecipeCapabilityMachine;
import org.gtlcore.gtlcore.api.recipe.*;
import org.gtlcore.gtlcore.api.recipe.ingredient.LongIngredient;
import org.gtlcore.gtlcore.common.machine.trait.MultipleRecipesLogic;

/**
 * Orders own all material state. The original machine/trait still owns coils, energy, research,
 * computation, maintenance and modules. Only explicitly opted-in registrations enter this engine.
 */
public final class IvNativeEngine {
    private final WorkableElectricMultiblockMachine machine;
    private final RecipeLogic logic;
    private final IvNativeAccess access;
    private final FairRecipeSelector<Ref> selector = new FairRecipeSelector<>();
    private final FairRecipeSelector<Ref> runningSelector = new FairRecipeSelector<>();
    private final FairRecipeSelector<Ref> deliverySelector = new FairRecipeSelector<>();
    private record Ref(MEPatternBufferPartMachine buffer, IvJob job) {}
    private long currentEUt;
    private String message = "";
    private boolean advanced;
    private boolean fault;
    private int powerRotation;
    private final Map<UUID, Component> rejectionDetails = new HashMap<>();
    private final List<Component> machineDetails = new ArrayList<>();

    public IvNativeEngine(WorkableElectricMultiblockMachine machine, RecipeLogic logic, IvNativeAccess access) {
        this.machine = machine; this.logic = logic; this.access = access;
        if (!IvMachineScope.nativeTarget(machine)) throw new IllegalArgumentException("Not an isolated native machine");
    }
    public void tick() {
        if (machine.isRemote()) return;
        if (!machine.isRecipeLogicAvailable() || !machine.hasProxies()) {
            invalidateRuntime();
            var summary = access.iv$summary();
            summary.putLong("eut", 0);
            summary.putBoolean("available", false);
            storeReasons(summary, List.of(nativeDetail("loading")));
            access.iv$publish(summary, "gtl_enhancedcore.diagnostic.iv_native_loading", logic.getLastRecipe(),
                    logic.getProgress(), logic.getDuration(), logic.isWorkingEnabled()
                            ? RecipeLogic.Status.WAITING : RecipeLogic.Status.SUSPEND);
            return;
        }
        try { tickOrders(); }
        catch (RuntimeException failure) {
            fault = true; logic.setWorkingEnabled(false);
            var summary = access.iv$summary();
            storeReasons(summary, List.of(nativeDetail("fault", failure.getClass().getSimpleName())));
            access.iv$publish(summary, "gtl_enhancedcore.diagnostic.iv_failure", null, 0, 0, RecipeLogic.Status.SUSPEND);
            machine.markDirty(); IvTaskLog.error(null, null, "NATIVE_MACHINE_TICK", failure);
        }
    }
    public void invalidateRuntime() {
        message = ""; advanced = false; currentEUt = 0;
        rejectionDetails.clear(); machineDetails.clear();
    }
    private static Component nativeDetail(String key, Object... arguments) {
        return Component.translatable("gtl_enhancedcore.diagnostic.iv_native_" + key, arguments);
    }
    private static Component resultDetail(GTRecipe.ActionResult result, String fallback) {
        try {
            if (result.reason() != null) {
                var reason = result.reason().get();
                if (reason != null) return reason;
            }
        } catch (RuntimeException invalid) { return Component.translatable(fallback); }
        return Component.translatable(fallback);
    }
    private Component dataDetail(IvJob job) {
        if (machine.getDefinition().getId().toString().equals("gtceu:suprachronal_assembly_line")
                && machine instanceof IRecipeCapabilityMachine capabilities) {
            var researchIds = new ArrayList<String>();
            boolean research = false;
            for (var condition : job.recipe.conditions) if (condition instanceof ResearchCondition required) {
                research = true;
                for (var entry : required.data) if (researchIds.size() < 8) researchIds.add(entry.getResearchId());
            }
            if (research) {
                String required = researchIds.isEmpty() ? job.recipe.id.toString() : String.join(", ", researchIds);
                if (capabilities.getDataAccessHatch() == null) {
                    if (machine.getParts().stream().anyMatch(IDataAccessHatch.class::isInstance)) return nativeDetail("loading");
                    return nativeDetail("research_hatch", required);
                }
                return nativeDetail("research_missing", required);
            }
        }
        return nativeDetail("data", job.recipe.id.toString());
    }
    private Component modifierDetail(GTRecipe recipe) {
        if (machine.getDefinition().getId().toString().equals("gtceu:advanced_vacuum_drying_furnace")
                && recipe.data.contains("ebf_temp") && machine instanceof CoilWorkableElectricMultiblockMachine coil) {
            int required = recipe.data.getInt("ebf_temp");
            long available = (long)coil.getCoilType().getCoilTemperature() + 100L * Math.max(0, machine.getTier() - 2);
            if (required > available) return nativeDetail("temperature", required, available);
        }
        return nativeDetail("modifier", recipe.id.toString());
    }
    private static void validateNativeJob(IvJob job) {
        job.validateAccounting();
        if (job.active() && (job.nativeRecipe == null || job.duration < 1 || job.elapsed < 0 || job.elapsed >= job.duration
                || job.nativeCapacity < 0 || job.energyLeft.signum() < 0 || job.energyLeft.compareTo(job.energyTotal) > 0))
            throw new IllegalStateException("Invalid native committed batch snapshot");
        if (job.done() && job.pendingOperations == 0
                && Math.addExact(job.deliveredOperations, job.cancelledOperations) != job.totalOperations)
            throw new IllegalStateException("Undelivered native order cannot retire");
    }
    private static void storeReasons(CompoundTag summary, List<Component> reasons) {
        var encoded = new ListTag();
        var unique = new LinkedHashSet<String>();
        for (var reason : reasons) {
            if (encoded.size() >= 8) break;
            String json = Component.Serializer.toJson(reason);
            if (unique.add(json)) encoded.add(StringTag.valueOf(json));
        }
        summary.put("reasons", encoded);
    }
    private long inputPower() {
        var inputs = machine.getCapabilitiesProxy().get(IO.IN, EURecipeCapability.CAP);
        if (inputs == null) return 0;
        var sources = new ArrayList<IvNativeRecoveryState.PowerInput>();
        for (var input : inputs) if (input instanceof IEnergyContainer energy)
            sources.add(new IvNativeRecoveryState.PowerInput(Math.max(0, energy.getInputVoltage()),
                    Math.max(0, energy.getInputAmperage())));
        return IvNativeRecoveryState.inputPower(sources);
    }
    private boolean powerReady(Ref ref, BigInteger payment, IWirelessNetworkEnergyHandler wireless, long availablePower) {
        if (wireless != null) {
            if (!wireless.isOnline()) {
                waiting(ref, "gtl_enhancedcore.diagnostic.power", nativeDetail("wireless_offline"));
                return false;
            }
            var available = wireless.getMaxAvailableEnergy();
            if (available.compareTo(payment) < 0) {
                waiting(ref, "gtl_enhancedcore.diagnostic.power", nativeDetail("wireless_energy", payment.toString(), available.toString()));
                return false;
            }
        } else {
            var energy = machine.getEnergyContainer();
            long stored = energy == null ? 0 : Math.max(0, energy.getEnergyStored());
            if (!IvNativeRecoveryState.canPay(payment, availablePower, stored)) {
                waiting(ref, "gtl_enhancedcore.diagnostic.power", nativeDetail("power", payment.toString(), availablePower, stored));
                return false;
            }
        }
        return true;
    }
    private void tickOrders() {
        if (fault && !logic.isWorkingEnabled()) return;
        fault = false; invalidateRuntime();
        // Do not adopt a legacy, already-paid recipe as a new order.
        if (!access.iv$managed() && logic.getLastRecipe() != null && logic.getDuration() > 0) {
            logic.setWorkingEnabled(false);
            var summary = new CompoundTag();
            storeReasons(summary, List.of(Component.translatable("gtl_enhancedcore.diagnostic.iv_legacy")));
            access.iv$publish(summary, "gtl_enhancedcore.diagnostic.iv_legacy",
                    logic.getLastRecipe(), logic.getProgress(), logic.getDuration(), RecipeLogic.Status.SUSPEND);
            return;
        }
        if (!access.iv$managed()) { access.iv$managed(true); machine.markDirty(); }
        List<Ref> all = new ArrayList<>();
        int buffers = 0;
        for (var buffer : IvBuffers.collect(machine)) {
            buffers++;
            if (!IvBuffers.bind(buffer, machine)) {
                var state = IvBuffers.state(buffer);
                String reason = state == null || state.message.isEmpty() ? "gtl_enhancedcore.diagnostic.iv_exclusive" : state.message;
                if (message.isEmpty()) message = reason;
                machineDetails.add(Component.translatable(reason));
                continue;
            }
            for (var job : IvBuffers.state(buffer).jobs) {
                var ref = new Ref(buffer, job);
                try { validateNativeJob(job); }
                catch (RuntimeException failure) {
                    boolean newlyHalted = !job.halted || !job.error.equals("gtl_enhancedcore.diagnostic.iv_failure");
                    job.halted = true;
                    waiting(ref, "gtl_enhancedcore.diagnostic.iv_failure", nativeDetail("fault", failure.getClass().getSimpleName()));
                    if (newlyHalted) {
                        buffer.markDirty(); IvTaskLog.error(buffer, job, "NATIVE_ACCOUNTING", failure);
                    }
                }
                all.add(ref);
            }
        }
        if (buffers == 0) {
            message = "gtl_enhancedcore.diagnostic.iv_native_buffer";
            machineDetails.add(nativeDetail("buffer"));
        }
        for (var ref : all) if (!ref.job.halted && ref.job.pending.isEmpty()) acknowledgeDelivery(ref);
        var pending = all.stream().filter(ref -> !ref.job.halted && !ref.job.pending.isEmpty()).toList();
        for (var ref : IvWorkBudget.deliveries(pending, deliverySelector, ref -> ref.job.id.toString())) flush(ref);
        all.removeIf(ref -> {
            if (ref.job.halted || !ref.job.done()) return false;
            IvBuffers.state(ref.buffer).jobs.remove(ref.job); ref.buffer.markDirty();
            IvTaskLog.event(ref.buffer, ref.job, "RETIRE", "FULLY_DELIVERED",
                    "totalOperations", ref.job.totalOperations, "deliveredOperations", ref.job.deliveredOperations,
                    "cancelledOperations", ref.job.cancelledOperations);
            return true;
        });
        if (logic.isWorkingEnabled()) { start(all); advance(all); }
        publish(all);
    }
    private IWirelessNetworkEnergyHandler wireless() {
        return IvMachineScope.batching(machine) && machine instanceof IWirelessElectricMultiblockMachine wireless
                ? wireless.getWirelessNetworkEnergyHandler() : null;
    }
    private boolean lockAllows(Ref ref) {
        var job = ref.job;
        if (logic instanceof org.gtlcore.gtlcore.api.machine.trait.ILockRecipe lock && lock.isLock() && lock.getLockRecipe() != null
                && (!lock.getLockRecipe().id.equals(job.recipe.id) || lock.getLockRecipe().recipeType != job.recipe.recipeType)) {
            waiting(ref, "gtl_enhancedcore.diagnostic.iv_lock"); return false;
        }
        return true;
    }
    private boolean allowed(Ref ref) {
        var job = ref.job;
        if (!lockAllows(ref)) return false;
        if (IGTRecipe.of(job.recipe).getEuTier() > machine.getTier()) {
            waiting(ref, "gtl_enhancedcore.diagnostic.voltage", nativeDetail("voltage", IGTRecipe.of(job.recipe).getEuTier(), machine.getTier()));
            return false;
        }
        var conditions = job.recipe.checkConditions(logic);
        if (!conditions.isSuccess()) {
            waiting(ref, "gtl_enhancedcore.diagnostic.conditions", resultDetail(conditions, "gtl_enhancedcore.diagnostic.conditions"));
            return false;
        }
        if (logic instanceof MultipleRecipesLogic multiple && multiple.getDataCheck() != null
                && !multiple.getDataCheck().test(job.recipe.data, machine)) {
            Component detail = nativeDetail("data", job.recipe.id.toString());
            if (job.recipe.data.contains("ebf_temp") && machine instanceof CoilWorkableElectricMultiblockMachine coil) {
                long available = (long)coil.getCoilType().getCoilTemperature() + 100L * Math.max(0, machine.getTier() - 2);
                if (job.recipe.data.getInt("ebf_temp") > available)
                    detail = nativeDetail("temperature", job.recipe.data.getInt("ebf_temp"), available);
            }
            waiting(ref, "gtl_enhancedcore.diagnostic.conditions", detail); return false;
        }
        if (logic instanceof com.gtl.enhancedcore.mixin.gtceu.IvNativeMutableAccessor mutable
                && mutable.iv$recipeCheck() != null && !mutable.iv$recipeCheck().test(job.recipe, machine)) {
            waiting(ref, "gtl_enhancedcore.diagnostic.conditions", dataDetail(job)); return false;
        }
        return true;
    }
    private void start(List<Ref> all) {
        long budget = IvMachineScope.budget(machine);
        int slots = IvMachineScope.activeThreads(machine);
        for (var ref : all) if (ref.job.active() && !ref.job.halted) { budget = Math.max(0, budget - ref.job.nativeCapacity); slots--; }
        if (slots <= 0) {
            for (var ref : all) if (!ref.job.halted && !ref.job.active() && ref.job.remaining > 0 && ref.job.pending.isEmpty())
                waiting(ref, "gtl_enhancedcore.diagnostic.iv_parallel", nativeDetail("threads", IvMachineScope.threads(machine) - slots, IvMachineScope.threads(machine)));
            return;
        }
        var ready = new ArrayList<Ref>();
        for (var ref : all) {
            var job = ref.job;
            if (!job.halted && !job.active() && job.pending.isEmpty() && job.remaining > 0 && allowed(ref)) {
                job.error = "";
                ready.add(ref);
            }
        }
        var chosen = new ArrayList<>(selector.select(ready, slots, ref -> ref.job.id.toString()));
        var selected = new HashSet<>(chosen);
        int scheduledSlots = IvMachineScope.threads(machine) - slots + chosen.size();
        for (var ref : ready) if (!selected.contains(ref))
            waiting(ref, "gtl_enhancedcore.diagnostic.iv_parallel", nativeDetail("threads", scheduledSlots, IvMachineScope.threads(machine)));
        long[] wants = new long[chosen.size()];
        long[] freeParallel = new long[chosen.size()];
        for (int i = 0; i < wants.length; i++) {
            var ref = chosen.get(i);
            var virtual = IvBuffers.availableVirtual(ref.buffer, ref.job);
            if (IvMachineScope.unmeteredBatch(machine, ref.job.recipe)) {
                freeParallel[i] = IvRecipeInputs.maxParallel(ref.job.recipe, ref.job.inventory, virtual, ref.job.remaining);
            } else {
                wants[i] = IvRecipeInputs.maxParallel(ref.job.recipe, ref.job.inventory,
                        virtual, Math.min(budget, ref.job.remaining));
            }
            if (wants[i] == 0 && freeParallel[i] == 0)
                waiting(ref, budget == 0 ? "gtl_enhancedcore.diagnostic.iv_parallel" : "gtl_enhancedcore.diagnostic.iv_catalyst");
        }
        long[] grants = FairBudget.divide(budget, wants, 0);
        String cycle = UUID.randomUUID().toString();
        for (int i = 0; i < grants.length; i++) {
            var ref = chosen.get(i);
            if (!lockAllows(ref)) continue;
            if (freeParallel[i] > 0) prepare(ref, freeParallel[i], 0, cycle);
            else if (grants[i] > 0) prepare(ref, grants[i], grants[i], cycle);
            if (ref.job.active() && !ref.job.halted
                    && logic instanceof org.gtlcore.gtlcore.api.machine.trait.ILockRecipe lock
                    && lock.isLock() && lock.getLockRecipe() == null) {
                lock.setLockRecipe(ref.job.recipe); machine.markDirty();
            }
        }
    }
    private double multiplier(MultipleRecipesLogic multiple) {
        return ((com.gtl.enhancedcore.mixin.gtlcore.IvNativeMultipleAccessor)multiple).iv$euMultiplier();
    }
    private double multiplier() {
        return logic instanceof MultipleRecipesLogic multiple ? multiplier(multiple)
                : ((com.gtl.enhancedcore.mixin.gtceu.IvNativeMutableAccessor)logic).iv$euMultiplier();
    }
    private long batchEUt(GTRecipe recipe) {
        return logic instanceof com.gtl.enhancedcore.mixin.gtceu.IvNativeMutableAccessor mutable
                ? mutable.iv$recipeEUt(recipe) : RecipeHelper.getInputEUt(recipe);
    }
    private void prepare(Ref ref, long granted, long capacity, String cycle) {
        var job = ref.job;
        try {
            var virtual = IvBuffers.availableVirtual(ref.buffer, job);
            GTRecipe scaled;
            GTRecipe timing;
            var wireless = wireless();
            BigInteger wirelessEUt = BigInteger.ZERO;
            long parallel;
            if (IvMachineScope.batching(machine)) {
                parallel = granted;
                scaled = RecipeCalculationHelper.INSTANCE.multipleRecipe(job.recipe, parallel);
                if (wireless != null) {
                    BigInteger cost = BigDecimal.valueOf(job.recipe.duration * multiplier())
                            .multiply(BigDecimal.valueOf(batchEUt(job.recipe)))
                            .multiply(BigDecimal.valueOf(parallel)).toBigInteger();
                    if (!powerReady(ref, cost, wireless, 0)) return;
                    // GTLAdd's native wireless helper pays floor(total / 20) on each of 20 ticks.
                    wirelessEUt = cost.divide(BigInteger.valueOf(20));
                    timing = scaled.copy(); timing.duration = 20; timing.tickInputs.remove(EURecipeCapability.CAP);
                } else {
                    long power = machine.getOverclockVoltage();
                    if (power <= 0) {
                        waiting(ref, "gtl_enhancedcore.diagnostic.power", nativeDetail("power", batchEUt(job.recipe), inputPower(), 0));
                        return;
                    }
                    double baseCost = logic instanceof MultipleRecipesLogic
                            ? ((com.gtl.enhancedcore.mixin.gtlcore.IvNativeMultipleAccessor)logic).iv$totalEu(job.recipe)
                            : batchEUt(job.recipe) * (double)job.recipe.duration;
                    double cost = baseCost * parallel * multiplier();
                    if (!Double.isFinite(cost) || cost < 0 || cost / power > Integer.MAX_VALUE) throw new ArithmeticException("Native batch is too large");
                    timing = RecipeCalculationHelper.INSTANCE.buildNormalRecipe(List.of(), List.of(), cost, power, 20);
                }
            } else {
                var context = new IvNativeMatchContext(machine, job.inventory, virtual, job.remaining, granted);
                scaled = context.simulate(() -> machine.doModifyRecipe(job.recipe.copy(), new OCParams(), new OCResult()));
                if (scaled == null) {
                    waiting(ref, "gtl_enhancedcore.diagnostic.conditions", modifierDetail(job.recipe));
                    return;
                }
                parallel = Math.max(1, IGTRecipe.of(scaled).getRealParallels());
                capacity = context.reserved();
                if (capacity > granted || parallel > job.remaining) throw new IllegalStateException("Native modifier exceeded reserved capacity");
                timing = scaled;
            }
            if (IvRecipeInputs.plan(scaled, job.inventory, virtual, 1) == null) {
                waiting(ref, "gtl_enhancedcore.diagnostic.iv_catalyst"); return;
            }
            var conditions = scaled.checkConditions(logic);
            if (!conditions.isSuccess()) {
                waiting(ref, "gtl_enhancedcore.diagnostic.conditions", resultDetail(conditions, "gtl_enhancedcore.diagnostic.conditions"));
                return;
            }
            long eut = wireless == null ? RecipeHelper.getInputEUt(timing) : 0;
            if (eut < 0 || timing.duration < 1) throw new IllegalStateException("Invalid native timing");
            // Persist only extra tick capabilities: EU is charged by the per-order energy ledger.
            GTRecipe tick = scaled.copy();
            tick.inputs.clear(); tick.outputs.clear(); tick.tickInputs.remove(EURecipeCapability.CAP);
            tick.duration = timing.duration; IGTRecipe.of(tick).setHasTick(!tick.tickInputs.isEmpty());
            var tickMatch = tick.matchTickRecipe(machine);
            if (!tickMatch.isSuccess()) {
                waiting(ref, "gtl_enhancedcore.diagnostic.conditions", resultDetail(tickMatch, "gtl_enhancedcore.diagnostic.conditions"));
                return;
            }
            BigInteger firstPayment = wireless == null ? BigInteger.valueOf(eut) : wirelessEUt;
            if (!powerReady(ref, firstPayment, wireless, inputPower())) return;
            GTRecipeSerializer.CODEC.encodeStart(NbtOps.INSTANCE, tick).getOrThrow(false, ignored -> {});
            if (!machine.beforeWorking(scaled)) {
                waiting(ref, "gtl_enhancedcore.diagnostic.conditions", nativeDetail("startup", job.recipe.id.toString()));
                return;
            }
            GTRecipe outputs = IParallelLogic.getRecipeOutputChance(machine, scaled);
            GTRecipe inputRoll = scaled.copy(); inputRoll.outputs.clear();
            scaled.inputs.forEach((cap, contents) -> {
                var consumed = contents.stream().filter(content -> content.chance > 0).toList();
                if (!consumed.isEmpty()) inputRoll.outputs.put(cap, consumed);
            });
            var consumed = IvRecipeInputs.plan(IParallelLogic.getRecipeOutputChance(machine, inputRoll).outputs, job.inventory, Map.of(), 1);
            if (consumed == null) throw new IllegalStateException("Native input commit changed");
            var produced = IvRecipeInputs.outputs(outputs.outputs);
            BigInteger totalEnergy = firstPayment.multiply(BigInteger.valueOf(timing.duration));
            IvRecipeInputs.subtract(job.inventory, consumed);
            job.completion.putAll(produced); job.remaining -= parallel; job.parallel = parallel;
            job.nativeRecipe = tick; job.nativeWireless = wireless != null; job.nativeWirelessEUt = wirelessEUt;
            job.nativeCapacity = capacity;
            job.eut = eut; job.duration = timing.duration; job.elapsed = 0;
            job.energyTotal = totalEnergy;
            job.energyLeft = job.energyTotal; job.supplyPower = Math.max(0, machine.getOverclockVoltage());
            job.cycle = cycle; job.error = ""; job.validateAccounting(); ref.buffer.markDirty();
            IvTaskLog.event(ref.buffer, job, "NATIVE_START", "COMMITTED",
                    "parallel", parallel, "capacity", capacity, "remaining", job.remaining, "eut", eut, "duration", job.duration,
                    "wireless", job.nativeWireless, "energy", job.energyTotal.toString(),
                    "consumed", IvTaskLog.stock(consumed), "completion", IvTaskLog.stock(produced));
        } catch (RuntimeException failure) {
            job.halted = true; job.error = "gtl_enhancedcore.diagnostic.iv_failure"; ref.buffer.markDirty();
            rejectionDetails.put(job.id, nativeDetail("fault", failure.getClass().getSimpleName()));
            IvTaskLog.error(ref.buffer, job, "NATIVE_PREPARE", failure);
        }
    }
    private void advance(List<Ref> all) {
        long parallelBudget = IvMachineScope.budget(machine);
        long powerBudget = inputPower();
        var running = new ArrayList<>(runningSelector.select(all.stream().filter(ref -> ref.job.active() && !ref.job.halted).toList(),
                IvMachineScope.activeThreads(machine), ref -> ref.job.id.toString()));
        if (!running.isEmpty()) Collections.rotate(running, Math.floorMod(powerRotation++, running.size()));
        boolean callbacks = false;
        for (var ref : running) {
            var job = ref.job;
            if (job.nativeCapacity > parallelBudget) { waiting(ref, "gtl_enhancedcore.diagnostic.iv_parallel"); continue; }
            parallelBudget -= job.nativeCapacity;
            if (!allowed(ref)) continue;
            if (job.nativeRecipe == null) throw new IllegalStateException("Missing native batch tick snapshot");
            var conditions = job.nativeRecipe.checkConditions(logic);
            if (!conditions.isSuccess()) {
                waiting(ref, "gtl_enhancedcore.diagnostic.conditions", resultDetail(conditions, "gtl_enhancedcore.diagnostic.conditions"));
                continue;
            }
            var tickMatch = job.nativeRecipe.matchTickRecipe(machine);
            if (!tickMatch.isSuccess()) {
                waiting(ref, "gtl_enhancedcore.diagnostic.conditions", resultDetail(tickMatch, "gtl_enhancedcore.diagnostic.conditions"));
                continue;
            }
            BigInteger payment = (job.nativeWireless ? job.nativeWirelessEUt : BigInteger.valueOf(job.eut)).min(job.energyLeft);
            var wireless = wireless();
            if (job.nativeWireless) {
                if (wireless == null) {
                    waiting(ref, "gtl_enhancedcore.diagnostic.power", nativeDetail("wireless_offline"));
                    continue;
                }
            }
            if (!powerReady(ref, payment, job.nativeWireless ? wireless : null, powerBudget)) continue;
            if (!callbacks) {
                if (!machine.onWorking() || !logic.isWorkingEnabled()) {
                    for (var waiting : running)
                        waiting(waiting, "gtl_enhancedcore.diagnostic.conditions", nativeDetail("working", waiting.job.recipe.id.toString()));
                    return;
                }
                callbacks = true;
            }
            var nextTick = IvNativeRecoveryState.paid(job.energyLeft, payment, job.elapsed, job.duration);
            // Every tick requirement is simulated above. No progress is credited unless both CWU and EU are paid.
            var tickResult = logic.handleTickRecipe(job.nativeRecipe);
            if (!tickResult.isSuccess()) {
                waiting(ref, "gtl_enhancedcore.diagnostic.conditions", resultDetail(tickResult, "gtl_enhancedcore.diagnostic.conditions"));
                continue;
            }
            if (job.nativeWireless) {
                if (payment.signum() > 0 && !wireless.consumeEnergy(payment.negate())) {
                    waiting(ref, "gtl_enhancedcore.diagnostic.power", nativeDetail("wireless_energy", payment.toString(), wireless.getMaxAvailableEnergy().toString()));
                    continue;
                }
            } else {
                long paid = payment.longValueExact();
                if (paid > 0 && machine.getEnergyContainer().removeEnergy(paid) != paid)
                    throw new IllegalStateException("Native energy commit was incomplete");
                powerBudget -= paid; currentEUt = Math.addExact(currentEUt, paid);
            }
            job.energyLeft = nextTick.energyLeft(); job.elapsed = nextTick.elapsed(); job.error = "";
            rejectionDetails.remove(job.id); advanced = true;
            if (IvTaskLog.TRACE_TICKS) IvTaskLog.event(ref.buffer, job, "NATIVE_ADVANCE", "PAID",
                    "paidEU", payment.toString(), "energyLeft", job.energyLeft.toString(), "elapsed", job.elapsed, "duration", job.duration);
            if (nextTick.complete()) {
                job.completeBatch();
                job.nativeRecipe = null; job.nativeWireless = false; job.nativeWirelessEUt = BigInteger.ZERO;
                job.nativeCapacity = 0;
                job.validateAccounting();
                ref.buffer.markDirty();
                if (all.stream().noneMatch(other -> other.job.active() && other.job.cycle.equals(job.cycle))) {
                    try { machine.afterWorking(); } catch (RuntimeException failure) { IvTaskLog.error(ref.buffer, job, "FINISH_CALLBACK", failure); }
                }
                IvTaskLog.event(ref.buffer, job, "BATCH_COMPLETE", "OK",
                        "remaining", job.remaining, "completedOperations", job.completedOperations, "pending", IvTaskLog.stock(job.pending));
            }
            ref.buffer.markDirty();
        }
    }
    private void flush(Ref ref) {
        var job = ref.job;
        boolean hadPending = !job.pending.isEmpty();
        if (!job.pending.isEmpty()) {
            var grid = ref.buffer.getGrid();
            if (grid == null || !ref.buffer.getMainNode().isActive()) { waiting(ref, "gtl_enhancedcore.diagnostic.iv_ae"); return; }
            int budget = IvWorkBudget.TRANSFERS_PER_JOB;
            var iterator = job.pending.entrySet().iterator();
            while (iterator.hasNext() && budget-- > 0) {
                var entry = iterator.next(); long offered = entry.getValue();
                long inserted = StorageHelper.poweredInsert(grid.getEnergyService(), grid.getStorageService().getInventory(),
                        entry.getKey(), offered, IActionSource.ofMachine(ref.buffer));
                if (inserted < 0 || inserted > offered) throw new IllegalStateException("Invalid AE insertion");
                if (inserted == 0) continue;
                IvTaskLog.event(ref.buffer, job, "OUTPUT", "OK", "key", entry.getKey().toTagGeneric().toString(),
                        "offered", offered, "inserted", inserted, "left", offered - inserted);
                if (inserted == offered) iterator.remove(); else entry.setValue(offered - inserted);
                ref.buffer.markDirty();
            }
        }
        acknowledgeDelivery(ref);
        if (!job.pending.isEmpty()) {
            waiting(ref, "gtl_enhancedcore.diagnostic.output", nativeDetail("output", job.pending.size()));
        } else if (hadPending) {
            job.error = ""; rejectionDetails.remove(job.id);
        }
    }
    private void acknowledgeDelivery(Ref ref) {
        var job = ref.job;
        if (job.pending.isEmpty() && job.pendingOperations > 0) {
            job.deliveredOperations = Math.addExact(job.deliveredOperations, job.pendingOperations); job.pendingOperations = 0;
            job.validateAccounting(); ref.buffer.markDirty();
            IvTaskLog.event(ref.buffer, job, "BATCH_DELIVERED", "OK", "deliveredOperations", job.deliveredOperations);
        }
    }
    private void waiting(Ref ref, String reason) {
        waiting(ref, reason, Component.translatable(reason));
    }
    private void waiting(Ref ref, String reason, Component detail) {
        if (!reason.equals(ref.job.error)) IvTaskLog.event(ref.buffer, ref.job, "WAIT", "PAUSED",
                "reason", reason, "remaining", ref.job.remaining, "inventory", IvTaskLog.stock(ref.job.inventory));
        ref.job.error = reason;
        rejectionDetails.put(ref.job.id, detail);
    }
    private void publish(List<Ref> all) {
        CompoundTag summary = new CompoundTag(); ListTag rows = new ListTag();
        var reasons = new ArrayList<Component>();
        for (var reason : machineDetails) if (reasons.size() < 8 && !reasons.contains(reason)) reasons.add(reason);
        BigInteger remaining = BigInteger.ZERO, running = BigInteger.ZERO;
        int active = 0, outputWaiting = 0;
        Ref shown = null;
        Map<AEKey, Long> products = new LinkedHashMap<>();
        for (var ref : all) {
            var job = ref.job;
            remaining = remaining.add(BigInteger.valueOf(job.remaining)); running = running.add(BigInteger.valueOf(job.parallel));
            if (job.active()) {
                active++; if (shown == null) shown = ref;
                job.completion.forEach((key, count) -> products.merge(key, count, (a,b) -> a > Long.MAX_VALUE-b ? Long.MAX_VALUE : a+b));
            }
            if (!job.pending.isEmpty()) outputWaiting++;
            if (!job.pending.isEmpty() && !job.halted && job.error.isEmpty())
                waiting(ref, "gtl_enhancedcore.diagnostic.output", nativeDetail("output", job.pending.size()));
            Component reason = null;
            if (!job.error.isEmpty()) {
                if (message.isEmpty()) message = job.error;
                reason = rejectionDetails.getOrDefault(job.id, Component.translatable(job.error));
                if (reasons.size() < 8 && !reasons.contains(reason)) reasons.add(reason);
            }
            if (rows.size() >= 6) continue;
            CompoundTag row = new CompoundTag(); row.putInt("slot", job.slot+1);
            row.putString("id", job.id.toString()); row.putLong("buffer", ref.buffer.getPos().asLong());
            row.putString("type", job.recipe.recipeType.registryName.toString()); row.putString("recipe", job.recipe.id.toString());
            row.putLong("total", job.totalOperations); row.putLong("remaining", job.remaining); row.putLong("running", job.parallel);
            row.putLong("completed", job.completedOperations); row.putLong("delivered", job.deliveredOperations);
            row.putLong("cancelled", job.cancelledOperations); row.putBoolean("waitingOutput", !job.pending.isEmpty());
            row.putBoolean("halted", job.halted); row.putString("reasonKey", job.error);
            if (reason != null) row.putString("reasonJson", Component.Serializer.toJson(reason));
            row.putInt("batchPercent", job.active() ? (int)Math.min(100, 100L*job.elapsed/Math.max(1, job.duration)) : 0);
            rows.add(row);
        }
        summary.put("orders", rows); summary.putInt("count", all.size()); summary.putInt("active", active);
        summary.putInt("outputWaiting", outputWaiting); summary.putString("remaining", remaining.toString()); summary.putString("running", running.toString());
        summary.putLong("parallelHatch", IvMachineScope.parallel(machine)); summary.putInt("threads", IvMachineScope.threads(machine));
        // 显示容量 = 并行 × 线程（与上方 threads 同行同源），避免未装总成时出现「线程 N 但总容量 1」。
        summary.putLong("budget", IvMachineScope.displayCapacity(machine)); summary.putLong("eut", currentEUt);
        summary.putLong("inputPower", inputPower()); summary.putBoolean("available", true);
        summary.putBoolean("enabled", logic.isWorkingEnabled()); storeReasons(summary, reasons);
        summary.putBoolean("nativeSharedBudget", !IvMachineScope.batching(machine));
        GTRecipe display = null; int elapsed = 0, ticks = 0;
        if (shown != null) {
            display = shown.job.recipe.copy(); display.outputs.clear(); display.tickInputs.clear();
            var items = new ArrayList<Content>(); var fluids = new ArrayList<Content>();
            products.forEach((key,count) -> {
                if (key instanceof AEItemKey item) {
                    var ingredient = LongIngredient.create(item.toStack(1)); ingredient.setActualAmount(count);
                    items.add(new Content(ingredient, 10000, 10000, 0, null, null));
                } else if (key instanceof AEFluidKey fluid) {
                    fluids.add(new Content(FluidIngredient.of(FluidStack.create(fluid.getFluid(), count, fluid.getTag())), 10000, 10000, 0, null, null));
                }
            });
            if (!items.isEmpty()) display.outputs.put(ItemRecipeCapability.CAP, items);
            if (!fluids.isEmpty()) display.outputs.put(FluidRecipeCapability.CAP, fluids);
            display.tickInputs.put(EURecipeCapability.CAP, List.of(new Content(currentEUt, 10000, 10000, 0, null, null)));
            IGTRecipe.of(display).setHasTick(true); elapsed = shown.job.elapsed; ticks = shown.job.duration;
        }
        access.iv$publish(summary, message, display, elapsed, ticks, !logic.isWorkingEnabled() ? RecipeLogic.Status.SUSPEND
                : advanced ? RecipeLogic.Status.WORKING : shown != null || !message.isEmpty()
                        ? RecipeLogic.Status.WAITING : RecipeLogic.Status.IDLE);
    }
}
