package com.gtl.enhancedcore.common.recipe.iv;

import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.storage.StorageHelper;
import com.gregtechceu.gtceu.api.recipe.*;
import com.gregtechceu.gtceu.api.recipe.content.Content;
import com.gregtechceu.gtceu.api.capability.recipe.EURecipeCapability;
import com.gtl.enhancedcore.common.machine.TieredParallelMachine;
import com.gtl.enhancedcore.common.recipe.MachineDiagnostics;
import com.gtl.enhancedcore.common.recipe.RecipePowerBudget;
import com.gtl.enhancedcore.common.util.FairRecipeSelector;
import com.gtladd.gtladditions.api.machine.logic.GTLAddMultipleRecipesLogic;
import org.gtlcore.gtlcore.common.machine.multiblock.part.ae.MEPatternBufferPartMachine;
import com.gtladd.gtladditions.utils.RecipeCalculationHelper;
import java.math.BigInteger;
import java.util.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.FluidRecipeCapability;
import com.gregtechceu.gtceu.api.recipe.ingredient.FluidIngredient;
import org.gtlcore.gtlcore.api.recipe.ingredient.LongIngredient;
import com.lowdragmc.lowdraglib.side.fluid.FluidStack;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEFluidKey;
import com.lowdragmc.lowdraglib.syncdata.annotation.DescSynced;
import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;
import com.lowdragmc.lowdraglib.syncdata.field.ManagedFieldHolder;
import org.gtlcore.gtlcore.api.recipe.IGTRecipe;
import org.gtlcore.gtlcore.api.recipe.IParallelLogic;

/** Compatible with the existing GTLAdd machine getter, but never enters its global input/merge path. */
public final class IvRecipeLogic extends GTLAddMultipleRecipesLogic {
    private static final ManagedFieldHolder IV_FIELDS = new ManagedFieldHolder(IvRecipeLogic.class, MANAGED_FIELD_HOLDER);
    private final FairRecipeSelector<Ref> selector = new FairRecipeSelector<>();
    private final FairRecipeSelector<Ref> runningSelector = new FairRecipeSelector<>();
    private final FairRecipeSelector<Ref> deliverySelector = new FairRecipeSelector<>();
    @DescSynced private String message = "";
    @Persisted @DescSynced private String fault = "";
    @Persisted @DescSynced private boolean ivWorkingEnabled = true;
    @DescSynced private int queuedTasks;
    @DescSynced private int runningTasks;
    @DescSynced private long currentEUt;
    @DescSynced private long inputPower;
    @DescSynced private long inputAmperage;
    @DescSynced private CompoundTag orderSummary = new CompoundTag();
    @DescSynced private String diagnosticJson = "";
    private final Map<UUID, Component> waitingReasons = new HashMap<>();
    private int powerRotation;
    private boolean legacy;
    private boolean advancedThisTick;
    private boolean partsAvailable;
    private record Ref(MEPatternBufferPartMachine buffer, IvJob job) {}

    public IvRecipeLogic(TieredParallelMachine machine) { super(machine); }
    @Override public int getMultipleThreads() {
        TieredParallelMachine machine = (TieredParallelMachine)getMachine();
        return IvMachineScope.crossRecipeEnabled(machine) ? machine.getThreadsForTier() : 1;
    }
    @Override protected double getEuMultiplier() {
        if (!((TieredParallelMachine)getMachine()).ivMaintenancePenalty()) return super.getEuMultiplier();
        var maintenance = ((org.gtlcore.gtlcore.api.machine.trait.IRecipeCapabilityMachine)getMachine()).getMaintenanceMachine();
        double multiplier = maintenance == null ? 1.0 : maintenance.getDurationMultiplier();
        return getReductionEUt() * getReductionDuration()
                * com.gtl.enhancedcore.common.recipe.IvMaintenancePolicy.durationMultiplier(multiplier);
    }
    public String getMessage() { return fault.isEmpty() ? message : fault; }
    public Component getDiagnosticReason() {
        if (!fault.isEmpty()) return Component.translatable(fault);
        Component detail = MachineDiagnostics.decode(diagnosticJson);
        return detail != null ? detail : message.isEmpty() ? null : Component.translatable(message);
    }
    public int getQueuedTasks() { return queuedTasks; }
    public int getRunningTasks() { return runningTasks; }
    public long getCurrentEUt() { return currentEUt; }
    public long getInputPower() { return inputPower; }
    public long getInputAmperage() { return inputAmperage; }
    public void clearInputPower() { inputPower=0; inputAmperage=0; currentEUt=0; partsAvailable=false; }
    public void refreshInputPower() {
        var machine=(TieredParallelMachine)getMachine();
        long power=0,amps=0;
        var seen = Collections.newSetFromMap(new IdentityHashMap<com.gregtechceu.gtceu.api.capability.IEnergyContainer, Boolean>());
        var inputs=machine.getCapabilitiesProxy().get(com.gregtechceu.gtceu.api.capability.recipe.IO.IN,EURecipeCapability.CAP);
        if(inputs!=null)for(var input:inputs)if(input instanceof com.gregtechceu.gtceu.api.capability.IEnergyContainer energy && seen.add(energy)){
            long voltage=Math.max(0,energy.getInputVoltage()),amperage=Math.max(0,energy.getInputAmperage());
            power=RecipePowerBudget.add(power, RecipePowerBudget.power(voltage, amperage));
            amps=RecipePowerBudget.add(amps, amperage);
        }
        boolean changed = inputPower != power || inputAmperage != amps;
        inputPower=power;
        inputAmperage=amps;
        if (!changed) return;
        for(var buffer:IvBuffers.collect(machine))
            IvTaskLog.event(buffer,null,"REFORM_POWER","RECALCULATED","inputPower",inputPower,"inputAmperage",inputAmperage,"tier",machine.getTier());
    }
    public CompoundTag getOrderSummary() { return orderSummary.copy(); }
    @Override public ManagedFieldHolder getFieldHolder() { return IV_FIELDS; }
    @Override public boolean isWorkingEnabled() { return ivWorkingEnabled; }
    @Override public void setWorkingEnabled(boolean enabled) {
        if (enabled) fault = "";
        ivWorkingEnabled = enabled;
        super.setWorkingEnabled(enabled);
        updateTickSubscription(); getMachine().markDirty();
        if (!getMachine().isRemote()) for (var buffer : IvBuffers.collect((TieredParallelMachine)getMachine()))
            IvTaskLog.event(buffer, null, "WORKING_ENABLED", enabled ? "RESUME" : "PAUSE");
    }
    @Override public void findAndHandleRecipe() {
        if (!IvMachineScope.crossRecipeEnabled((TieredParallelMachine)getMachine())) super.findAndHandleRecipe();
    }
    @Override public void onRecipeFinish() {
        if (!IvMachineScope.crossRecipeEnabled((TieredParallelMachine)getMachine())) super.onRecipeFinish();
    }
    @Override public void resetRecipeLogic() {
        // All real tasks live in their buffers. A structural reset only clears the display.
        if (!IvMachineScope.crossRecipeEnabled((TieredParallelMachine)getMachine()) || !legacy) super.resetRecipeLogic();
    }
    @Override public void loadCustomPersistedData(CompoundTag tag) {
        super.loadCustomPersistedData(tag); legacy = !tag.getBoolean("ivManagedLogic");
    }
    @Override public void saveCustomPersistedData(CompoundTag tag, boolean forDrop) {
        super.saveCustomPersistedData(tag, forDrop); tag.putBoolean("ivManagedLogic", !legacy);
    }
    @Override public void serverTick() {
        if (!IvMachineScope.crossRecipeEnabled((TieredParallelMachine)getMachine())) {
            super.serverTick();
            return;
        }
        if (!fault.isEmpty()) return;
        try { tickTasks(); }
        catch (RuntimeException failure) {
            fault = "gtl_enhancedcore.diagnostic.iv_failure";
            currentEUt = 0;
            ivWorkingEnabled = false; setStatus(Status.SUSPEND); getMachine().markDirty();
            IvTaskLog.error(null, null, "MACHINE_TICK", failure);
        }
    }
    private void tickTasks() {
        TieredParallelMachine machine = (TieredParallelMachine)getMachine();
        if (machine.isRemote()) return;
        boolean enabled = isWorkingEnabled();
        advancedThisTick = false;
        currentEUt = 0;
        if (!machine.isRecipeLogicAvailable()) {
            partsAvailable = false;
            message = machine.isFormed() ? "gtl_enhancedcore.diagnostic.parts_unloaded" : "";
            diagnosticJson = "";
            return;
        }
        if (!partsAvailable || Math.floorMod(machine.getOffsetTimer(), 20) == 0) refreshInputPower();
        partsAvailable = true;
        List<MEPatternBufferPartMachine> buffers = IvBuffers.collect(machine);
        List<Ref> all = new ArrayList<>();
        message = buffers.isEmpty() ? "gtl_enhancedcore.diagnostic.iv_native_buffer" : "";
        diagnosticJson = "";
        for (var buffer : buffers) {
            if (!IvBuffers.bind(buffer, machine)) { message = IvBuffers.state(buffer).message; continue; }
            for (IvJob job : IvBuffers.state(buffer).jobs) all.add(new Ref(buffer, job));
        }
        waitingReasons.keySet().retainAll(all.stream().map(ref -> ref.job.id).collect(java.util.stream.Collectors.toSet()));
        for (Ref ref : all) ref.job.validateAccounting();
        for (Ref ref : all) if(ref.job.active() && inputPower>0 && ref.job.supplyPower!=inputPower) retime(ref,machine);
        if (legacy && lastRecipe != null && duration > 0 && !isIdle()) {
            // Never guess which old buffer paid for an already-started pre-upgrade batch.
            message = "gtl_enhancedcore.diagnostic.iv_legacy";
            ivWorkingEnabled = false; setStatus(Status.SUSPEND); return;
        }
        legacy = false;
        var pendingDeliveries = new ArrayList<Ref>();
        for (Ref ref : all) {
            if (ref.job.pending.isEmpty()) flush(ref);
            else pendingDeliveries.add(ref);
        }
        for (Ref ref : IvWorkBudget.deliveries(pendingDeliveries, deliverySelector, ref -> ref.job.id.toString())) flush(ref);
        for (var buffer : buffers) {
            IvBufferState state = IvBuffers.state(buffer);
            if (state != null && state.jobs.removeIf(job -> {
                if (!job.done()) return false;
                waitingReasons.remove(job.id);
                IvTaskLog.event(buffer, job, "RETIRE", job.cancelled?"CANCELLED_AND_DRAINED":"FULLY_DELIVERED", "cancelledOperations",job.cancelledOperations,"completedOperations",job.completedOperations,
                        "deliveredOperations",job.deliveredOperations,"totalOperations",job.totalOperations); return true;
            })) {
                if (state.jobs.isEmpty()) state.message="";
                buffer.markDirty();
            }
        }
        all.removeIf(ref -> ref.job.done());
        if (inputPower <= 0) for (Ref ref : all) {
            if (!ref.job.halted && ref.job.pending.isEmpty()) waiting(ref, "gtl_enhancedcore.diagnostic.power",
                    MachineDiagnostics.power(machine, ref.job.active() ? ref.job.eut : RecipeHelper.getInputEUt(ref.job.recipe)));
        }
        if (enabled && inputPower > 0 && !buffers.isEmpty()) {
            start(all, machine);
            advance(all, machine);
        }
        Ref shown = all.stream().filter(ref -> ref.job.active()).findFirst().orElse(null);
        queuedTasks = all.size(); runningTasks = (int)all.stream().filter(ref -> ref.job.active()).count();
        updateOrderSummary(all, machine);
        if (shown == null) {
            lastRecipe = null; progress = 0; duration = 0; setStatus(enabled ? Status.IDLE : Status.SUSPEND);
        } else {
            lastRecipe = shown.job.recipe.copy();
            lastRecipe.outputs.clear();
            Map<AEKey, Long> batchOutputs = new LinkedHashMap<>();
            // Display totals must not pause valid, independently accounted tasks on overflow.
            for (Ref ref : all) if (ref.job.active()) ref.job.completion.forEach((key, amount) ->
                    batchOutputs.merge(key, amount, (left, right) -> left > Long.MAX_VALUE - right ? Long.MAX_VALUE : left + right));
            List<Content> items = new ArrayList<>(), fluids = new ArrayList<>();
            batchOutputs.forEach((key, count) -> {
                if (key instanceof AEItemKey item) {
                    var ingredient = LongIngredient.create(item.toStack(1)); ingredient.setActualAmount(count);
                    items.add(new Content(ingredient, 10000, 10000, 0, null, null));
                } else if (key instanceof AEFluidKey fluid) fluids.add(new Content(FluidIngredient.of(FluidStack.create(fluid.getFluid(), count, fluid.getTag())),10000,10000,0,null,null));
            });
            if (!items.isEmpty()) lastRecipe.outputs.put(ItemRecipeCapability.CAP, items);
            if (!fluids.isEmpty()) lastRecipe.outputs.put(FluidRecipeCapability.CAP, fluids);
            lastRecipe.tickInputs.put(EURecipeCapability.CAP, List.of(new Content(currentEUt, 10000, 10000, 0, null, null)));
            IGTRecipe.of(lastRecipe).setHasTick(true);
            duration = Math.max(1, shown.job.duration);
            progress = shown.job.elapsed;
            setStatus(!enabled ? Status.SUSPEND : advancedThisTick ? Status.WORKING : Status.WAITING);
        }
        if (message.isEmpty()) for (Ref ref : all) if (!ref.job.error.isEmpty()) {
            message = ref.job.error;
            diagnosticJson = Component.Serializer.toJson(waitingReasons.getOrDefault(ref.job.id, Component.translatable(message)));
            break;
        }
        if (message.isEmpty()) for (var buffer : buffers) if (!IvBuffers.state(buffer).message.isEmpty()) { message = IvBuffers.state(buffer).message; break; }
    }
    private void retime(Ref ref,TieredParallelMachine machine) {
        IvJob job=ref.job;
        try {
            var timing=IvRemainingTime.calculate(job.energyLeft,inputPower,job.elapsed,Math.max(1,machine.getLimitedDuration()));
            long oldEut=job.eut;int oldDuration=job.duration;
            job.eut=timing.eut();job.duration=timing.duration();job.supplyPower=inputPower;
            ref.buffer.markDirty();
            IvTaskLog.event(ref.buffer,job,"RETIME","UNPAID_ENERGY_PRESERVED","oldEut",oldEut,"eut",job.eut,"oldDuration",oldDuration,
                    "duration",job.duration,"elapsed",job.elapsed,"energyLeft",job.energyLeft.toString(),"energyTotal",job.energyTotal.toString(),"inputPower",inputPower);
        } catch(ArithmeticException failure) {
            job.error="gtl_enhancedcore.diagnostic.iv_retime";
            // Retry after another reformation; the tick payment cap still obeys the actual input power.
            job.supplyPower=inputPower;ref.buffer.markDirty();IvTaskLog.error(ref.buffer,job,"RETIME",failure);
        }
    }
    private void start(List<Ref> all, TieredParallelMachine machine) {
        long budget = Math.multiplyExact((long)machine.getMaxParallel(), getMultipleThreads());
        int slots = getMultipleThreads();
        for (Ref ref : all) if (ref.job.active()) { budget = Math.max(0, budget - ref.job.parallel); slots--; }
        if (slots <= 0 || budget <= 0) {
            for (Ref ref : all) if (!ref.job.halted && !ref.job.active() && ref.job.pending.isEmpty() && ref.job.remaining > 0)
                waiting(ref, slots <= 0 ? "gtl_enhancedcore.diagnostic.iv_threads" : "gtl_enhancedcore.diagnostic.iv_parallel");
            return;
        }
        List<Ref> ready = new ArrayList<>();
        for (Ref ref : all) {
            IvJob job = ref.job;
            if (job.halted || job.active() || !job.pending.isEmpty() || job.remaining <= 0) continue;
            if (isLock() && getLockRecipe() != null && (!getLockRecipe().id.equals(job.recipe.id)
                    || getLockRecipe().recipeType != job.recipe.recipeType)) { waiting(ref, "gtl_enhancedcore.diagnostic.iv_lock"); continue; }
            if (IGTRecipe.of(job.recipe).getEuTier() > machine.getTier()) {
                waiting(ref, "gtl_enhancedcore.diagnostic.voltage", MachineDiagnostics.voltage(machine, job.recipe));
                continue;
            }
            var condition = job.recipe.checkConditions(this);
            if (!condition.isSuccess()) { conditionFailure(ref, condition); continue; }
            ready.add(ref);
        }
        List<Ref> chosen = new ArrayList<>(selector.select(ready, slots, ref -> ref.job.id.toString()));
        long[] wants = new long[chosen.size()];
        for (int i = 0; i < chosen.size(); i++) {
            IvJob job = chosen.get(i).job;
            wants[i] = IvRecipeInputs.maxParallel(job.recipe, job.inventory, availableVirtual(chosen.get(i)), Math.min(job.remaining, budget));
            if (wants[i] == 0) {
                Ref ref = chosen.get(i);
                String reason = "gtl_enhancedcore.diagnostic.iv_catalyst";
                if (!reason.equals(job.error)) IvTaskLog.event(ref.buffer, job, "CATALYST_CHECK", "INPUTS_MISSING",
                        "sharedCatalysts", IvTaskLog.stock(IvBuffers.sharedCatalysts(ref.buffer)),
                        "availableReadOnly", IvTaskLog.stock(availableVirtual(ref)));
                waiting(ref, reason);
            }
        }
        long[] allocation = FairBudget.divide(budget, wants, 0);
        String cycle = UUID.randomUUID().toString();
        for (int i = 0; i < chosen.size(); i++) if (allocation[i] > 0) {
            Ref ref = chosen.get(i);
            if (isLock() && getLockRecipe() != null && (!ref.job.recipe.id.equals(getLockRecipe().id)
                    || ref.job.recipe.recipeType != getLockRecipe().recipeType)) {
                waiting(ref, "gtl_enhancedcore.diagnostic.iv_lock");
                continue;
            }
            IvTaskLog.event(ref.buffer, ref.job, "ALLOCATE", "OK", "requested", wants[i], "granted", allocation[i], "sharedBudget", budget, "threadLimit", getMultipleThreads());
            prepare(ref, allocation[i], machine, cycle);
            if (isLock() && getLockRecipe() == null && ref.job.active() && !ref.job.halted) setLockRecipe(ref.job.recipe);
        }
    }
    private void prepare(Ref ref, long parallel, TieredParallelMachine machine, String cycle) {
        IvJob job = ref.job;
        try {
            GTRecipe scaled = RecipeCalculationHelper.INSTANCE.multipleRecipe(job.recipe, parallel);
            if (IvRecipeInputs.plan(scaled, job.inventory, availableVirtual(ref), 1) == null) throw new IllegalStateException("整批原料预留未通过");
            double totalEu = getTotalEuOfRecipe(job.recipe) * parallel * getEuMultiplier();
            if (!Double.isFinite(totalEu) || totalEu < 0 || totalEu / inputPower > Integer.MAX_VALUE)
                throw new ArithmeticException("任务能量或计时超出有效范围");
            GTRecipe timing = RecipeCalculationHelper.INSTANCE.buildNormalRecipe(List.of(), List.of(), totalEu,
                    inputPower, Math.max(1, machine.getLimitedDuration()));
            long eut = RecipeHelper.getInputEUt(timing);
            if (eut < 0 || timing.duration < 1) throw new IllegalStateException("任务计时无效");
            if (!machine.beforeWorking(scaled)) { waiting(ref, "gtl_enhancedcore.diagnostic.start_rejected"); return; }
            // Roll once, only after all deterministic input checks. Keep the result with the active task.
            GTRecipe outputs = IParallelLogic.getRecipeOutputChance(machine, scaled);
            GTRecipe inputRoll = scaled.copy(); inputRoll.outputs.clear();
            for (var entry : scaled.inputs.entrySet()) {
                List<Content> consumed = entry.getValue().stream().filter(content -> content.chance > 0).toList();
                if (!consumed.isEmpty()) inputRoll.outputs.put(entry.getKey(), consumed);
            }
            var rolledInputs = IParallelLogic.getRecipeOutputChance(machine, inputRoll).outputs;
            Map<AEKey, Long> consume = IvRecipeInputs.plan(rolledInputs, job.inventory, Map.of(), 1);
            if (consume == null) throw new IllegalStateException("任务原料在提交前发生变化");
            Map<AEKey, Long> produced = IvRecipeInputs.outputs(outputs.outputs);
            IvRecipeInputs.subtract(job.inventory, consume);
            job.completion.clear(); job.completion.putAll(produced);
            job.remaining -= parallel; job.parallel = parallel;
            job.cycle = cycle;
            job.eut = eut; job.duration = timing.duration; job.elapsed = 0;
            job.energyLeft = BigInteger.valueOf(eut).multiply(BigInteger.valueOf(timing.duration));
            job.energyTotal=job.energyLeft;job.supplyPower=inputPower;
            job.error = ""; waitingReasons.remove(job.id); ref.buffer.markDirty();
            job.validateAccounting();
            IvTaskLog.event(ref.buffer, job, "COMMIT_INPUT", "OK", "consumed", IvTaskLog.stock(consume), "left", IvTaskLog.stock(job.inventory));
            IvTaskLog.event(ref.buffer, job, "START", "OK", "parallel", parallel, "remaining", job.remaining,
                    "eut", eut, "duration", job.duration, "energy", job.energyLeft.toString(), "cycle", job.cycle, "completion", IvTaskLog.stock(job.completion),
                    "sharedCatalysts", IvTaskLog.stock(IvBuffers.sharedCatalysts(ref.buffer)));
        } catch (RuntimeException failure) { job.halted = true; job.error = "gtl_enhancedcore.diagnostic.iv_failure"; ref.buffer.markDirty(); IvTaskLog.error(ref.buffer, job, "PREPARE", failure); }
    }
    private void advance(List<Ref> all, TieredParallelMachine machine) {
        long reservationBudget = Math.multiplyExact((long)machine.getMaxParallel(), getMultipleThreads());
        List<Ref> running = new ArrayList<>();
        for (Ref ref : runningSelector.select(all.stream().filter(ref -> ref.job.active() && !ref.job.halted).toList(), getMultipleThreads(), ref -> ref.job.id.toString())) {
            if (ref.job.parallel > reservationBudget) { waiting(ref, "gtl_enhancedcore.diagnostic.iv_parallel"); continue; }
            running.add(ref); reservationBudget -= ref.job.parallel;
        }
        if (running.isEmpty()) return;
        if (!machine.onWorking()) { message = "gtl_enhancedcore.diagnostic.conditions"; return; }
        long[] wants = new long[running.size()];
        boolean[] eligible = new boolean[running.size()];
        for (int i = 0; i < wants.length; i++) {
            IvJob job = running.get(i).job;
            if (IGTRecipe.of(job.recipe).getEuTier() > machine.getTier()) {
                waiting(running.get(i), "gtl_enhancedcore.diagnostic.voltage", MachineDiagnostics.voltage(machine, job.recipe));
                continue;
            }
            var condition = job.recipe.checkConditions(this);
            if (!condition.isSuccess()) { conditionFailure(running.get(i), condition); continue; }
            eligible[i] = true;
            wants[i] = job.energyLeft.min(BigInteger.valueOf(job.eut)).longValueExact();
        }
        var energy = machine.getEnergyContainer();
        long available = energy == null ? 0 : Math.min(inputPower, energy.getEnergyStored());
        long[] grants = FairBudget.divide(Math.max(0, available), wants, powerRotation++);
        long requested = 0; for (long grant : grants) requested = Math.addExact(requested, grant);
        long removed = requested == 0 ? 0 : energy.removeEnergy(requested);
        if (removed < 0 || removed > requested) throw new IllegalStateException("能源容器返回了无效扣款量");
        currentEUt = removed;
        grants = FairBudget.divide(removed, grants, powerRotation);
        for (int i = 0; i < running.size(); i++) {
            Ref ref = running.get(i); IvJob job = ref.job;
            if (!eligible[i]) continue;
            if (grants[i] == 0 && job.energyLeft.signum() > 0) {
                waiting(ref, "gtl_enhancedcore.diagnostic.power", MachineDiagnostics.power(machine, job.eut));
                continue;
            }
            advancedThisTick = true; job.error = ""; waitingReasons.remove(job.id);
            // The machine's input power is shared. Estimate with this order's actual allocation,
            // without turning paid-energy percentage into fictitious elapsed seconds.
            if (grants[i] > 0) job.duration = IvRemainingTime.estimatedDuration(job.energyLeft, grants[i],
                    job.elapsed, Math.max(1, machine.getLimitedDuration()));
            job.energyLeft = job.energyLeft.subtract(BigInteger.valueOf(grants[i]));
            if (job.elapsed < job.duration) job.elapsed++;
            if (IvTaskLog.TRACE_TICKS) IvTaskLog.event(ref.buffer, job, "ADVANCE", "OK", "requestedEU", wants[i], "paidEU", grants[i],
                    "energyLeft", job.energyLeft.toString(), "elapsed", job.elapsed, "minimumTicks", job.duration);
            if (job.energyLeft.signum() == 0 && job.elapsed >= job.duration) {
                job.completeBatch();
                // Maintenance is charged once for the admitted group, not once per parallel task.
                if (all.stream().noneMatch(other -> other.job.active() && other.job.cycle.equals(job.cycle))) {
                    try { machine.afterWorking(); }
                    catch (RuntimeException failure) { IvTaskLog.error(ref.buffer, job, "FINISH_CALLBACK", failure); }
                }
                IvTaskLog.event(ref.buffer, job, "BATCH_COMPLETE", "OK", "remaining", job.remaining, "completedOperations", job.completedOperations, "totalOperations", job.totalOperations, "pending", IvTaskLog.stock(job.pending));
            }
            ref.buffer.markDirty();
        }
    }
    private void flush(Ref ref) {
        if (ref.job.pending.isEmpty()) {
            acknowledgeDelivery(ref); return;
        }
        var grid = ref.buffer.getGrid();
        if (grid == null || !ref.buffer.getMainNode().isActive()) {
            if (!ref.job.pending.isEmpty()) waiting(ref, "gtl_enhancedcore.diagnostic.iv_ae");
            return;
        }
        waiting(ref, "gtl_enhancedcore.diagnostic.output");
        int transfers = 0;
        var iterator = ref.job.pending.entrySet().iterator();
        while (iterator.hasNext() && transfers++ < IvWorkBudget.TRANSFERS_PER_JOB) {
            var entry = iterator.next();
            long inserted = StorageHelper.poweredInsert(grid.getEnergyService(), grid.getStorageService().getInventory(),
                    entry.getKey(), entry.getValue(), IActionSource.ofMachine(ref.buffer));
            if (inserted < 0 || inserted > entry.getValue()) throw new IllegalStateException("AE 返回了无效插入量");
            if (inserted == 0) {
                if (IvTaskLog.TRACE_TICKS) IvTaskLog.event(ref.buffer, ref.job, "OUTPUT", "BLOCKED", "key", entry.getKey().toTagGeneric().toString(), "pending", entry.getValue());
                continue;
            }
            IvTaskLog.event(ref.buffer, ref.job, "OUTPUT", "OK", "key", entry.getKey().toTagGeneric().toString(), "offered", entry.getValue(), "inserted", inserted, "left", entry.getValue() - inserted);
            if (inserted == entry.getValue()) iterator.remove(); else entry.setValue(entry.getValue() - inserted);
            ref.buffer.markDirty();
        }
        if (ref.job.pending.isEmpty()) acknowledgeDelivery(ref);
    }
    private void acknowledgeDelivery(Ref ref) {
        if (ref.job.pendingOperations == 0) return;
        ref.job.error = "";
        waitingReasons.remove(ref.job.id);
        ref.job.deliveredOperations = Math.addExact(ref.job.deliveredOperations, ref.job.pendingOperations);
        ref.job.pendingOperations = 0; ref.job.validateAccounting(); ref.buffer.markDirty();
        IvTaskLog.event(ref.buffer, ref.job, "BATCH_DELIVERED", "OK", "deliveredOperations", ref.job.deliveredOperations, "totalOperations", ref.job.totalOperations, "remaining", ref.job.remaining);
    }
    private void updateOrderSummary(List<Ref> all, TieredParallelMachine machine) {
        CompoundTag summary = new CompoundTag(); ListTag rows = new ListTag();
        BigInteger left = BigInteger.ZERO, inProgress = BigInteger.ZERO;
        int outputWaiting = 0;
        for (Ref ref : all) {
            IvJob job = ref.job;
            left = left.add(BigInteger.valueOf(job.remaining)); inProgress = inProgress.add(BigInteger.valueOf(job.parallel));
            if (!job.pending.isEmpty()) outputWaiting++;
            if (rows.size() >= 6) continue;
            CompoundTag row = new CompoundTag(); row.putInt("slot",job.slot+1);
            row.putString("type",job.recipe.recipeType.registryName.toString()); row.putString("recipe",job.recipe.id.toString());
            row.putLong("total",job.totalOperations); row.putLong("remaining",job.remaining); row.putLong("running",job.parallel);
            row.putLong("completed",job.completedOperations); row.putLong("delivered",job.deliveredOperations);
            row.putLong("cancelled",job.cancelledOperations);
            row.putBoolean("waitingOutput",!job.pending.isEmpty()); row.putBoolean("recoveredTail",job.recoveredTail);
            row.putInt("batchPercent",job.active() ? batchPercent(job) : 0); rows.add(row);
            if (!job.error.isEmpty()) row.putString("reason", Component.Serializer.toJson(
                    waitingReasons.getOrDefault(job.id, Component.translatable(job.error))));
        }
        summary.put("orders",rows); summary.putInt("count",all.size()); summary.putInt("active",runningTasks);
        summary.putInt("outputWaiting",outputWaiting); summary.putString("remaining",left.toString()); summary.putString("running",inProgress.toString());
        summary.putLong("parallelHatch",machine.getMaxParallel()); summary.putInt("threads",getMultipleThreads());
        summary.putLong("budget",(long)machine.getMaxParallel()*getMultipleThreads()); summary.putLong("eut",currentEUt);
        orderSummary = summary;
    }
    private static int batchPercent(IvJob job) {
        return (int)Math.min(100, (long)job.elapsed*100/Math.max(1,job.duration));
    }
    private void waiting(Ref ref, String reason) {
        waiting(ref, reason, Component.translatable(reason));
    }
    private void waiting(Ref ref, String reason, Component detail) {
        if (!reason.equals(ref.job.error)) IvTaskLog.event(ref.buffer, ref.job, "WAIT", "PAUSED", "reason", reason, "remaining", ref.job.remaining, "inventory", IvTaskLog.stock(ref.job.inventory));
        ref.job.error = reason;
        waitingReasons.put(ref.job.id, detail);
    }
    private void conditionFailure(Ref ref, GTRecipe.ActionResult condition) {
        Component detail = condition.reason() == null ? null : condition.reason().get();
        waiting(ref, "gtl_enhancedcore.diagnostic.conditions", detail == null ? MachineDiagnostics.text("conditions")
                : Component.translatable("gtl_enhancedcore.diagnostic.condition_detail", detail));
    }
    private Map<AEKey, Long> availableVirtual(Ref ref) {
        return IvBuffers.availableVirtual(ref.buffer, ref.job);
    }
}
