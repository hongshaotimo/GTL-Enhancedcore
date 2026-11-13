package com.gtl.enhancedcore.common.recipe;

import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.GTRecipeSerializer;
import com.gtl.enhancedcore.common.machine.HyperstructuralChemicalDistorterMachine;
import com.gtl.enhancedcore.common.machine.galaxy.GalaxyLinks;
import com.gtl.enhancedcore.common.machine.galaxy.GalaxyPower;
import com.gtl.enhancedcore.common.recipe.iv.IvMachineScope;
import com.gtl.enhancedcore.common.recipe.iv.IvNativeAccess;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.gtlcore.gtlcore.api.machine.ISuspendableMachine;
import org.gtlcore.gtlcore.api.machine.trait.ILockRecipe;
import org.gtlcore.gtlcore.api.recipe.IGTRecipe;
import org.gtlcore.gtlcore.api.recipe.RecipeRunnerHelper;

/** Independent stellar work for ordinary recipe traits, without replacing the native trait. */
public final class GalaxyIndependentRecipeEngine {
    private final RecipeLogic logic;
    private final WorkableElectricMultiblockMachine machine;
    private final IvNativeAccess access;
    private final List<Job> jobs = new ArrayList<>();
    private int admissionRotation;
    private int workRotation;
    private boolean cycleActive;
    private boolean refill = true;

    private static final class Job {
        final GTRecipe origin;
        final GTRecipe recipe;
        final long capacity;
        int elapsed;

        Job(GTRecipe origin, GTRecipe recipe, long capacity, int elapsed) {
            this.origin = origin;
            this.recipe = recipe;
            this.capacity = capacity;
            this.elapsed = elapsed;
        }

        boolean complete() { return elapsed >= recipe.duration; }
    }

    public GalaxyIndependentRecipeEngine(RecipeLogic logic) {
        this.logic = logic;
        machine = (WorkableElectricMultiblockMachine) logic.getMachine();
        access = (IvNativeAccess) logic;
    }

    public static boolean supports(RecipeLogic logic) {
        if (!(logic.getMachine() instanceof WorkableElectricMultiblockMachine machine)
                || machine instanceof com.gtl.enhancedcore.common.machine.ExtremeReactorMachine
                || machine.getRecipeType().isFuelRecipeType()
                || IvMachineScope.crossRecipeEnabled(machine)) return false;
        // Subclasses may own input payment, power or completion. Only these known ordinary
        // traits share the GTCEu lifecycle; do not claim arbitrary custom recipe engines.
        Class<?> type = logic.getClass();
        return type == RecipeLogic.class || type == RetryableRecipeLogic.class
                || type == PlusFactoryRecipeLogic.class || GalaxyNativeRecipePreparation.supports(logic);
    }

    public boolean hasJobs() { return !jobs.isEmpty(); }

    public void tick() {
        if (!logic.isWorkingEnabled()) {
            publish(RecipeLogic.Status.SUSPEND, null);
            return;
        }
        if (!machine.isFormed() || !machine.isRecipeLogicAvailable()) {
            publish(jobs.isEmpty() ? RecipeLogic.Status.IDLE : RecipeLogic.Status.WAITING,
                    Component.translatable("gtl_enhancedcore.diagnostic.parts_unloaded"));
            return;
        }
        boolean completed = flushCompleted();
        if (completeRequestedPause()) {
            publish(RecipeLogic.Status.SUSPEND, null);
            return;
        }
        if (!GalaxyLinks.active(machine)) {
            publish(jobs.isEmpty() ? RecipeLogic.Status.IDLE : RecipeLogic.Status.WAITING,
                    Component.translatable("gtl_enhancedcore.galaxy.offline"));
            return;
        }
        boolean advanced = false;
        boolean callback = false;
        Component failure = null;
        int limit = threads(logic);
        long budget = capacityBudget();
        int count = jobs.size();
        int start = count == 0 ? 0 : Math.floorMod(workRotation++, count);
        for (int index = 0; index < count && limit > 0; index++) {
            Job job = jobs.get((start + index) % count);
            if (job.complete() || job.capacity > budget) continue;
            budget -= job.capacity;
            limit--;
            try (var context = access.iv$recipeContext(job.recipe, job.origin, job.elapsed, job.recipe.duration)) {
                if (machine instanceof HyperstructuralChemicalDistorterMachine distorter
                        && job.recipe.data.getInt("ebf_temp") > distorter.getCoilType().getCoilTemperature()) {
                    failure = Component.translatable("gtl_enhancedcore.diagnostic.temperature");
                    continue;
                }
                if (!GalaxyNativeRecipePreparation.allowed(logic, job.origin)) {
                    failure = Component.translatable("gtl_enhancedcore.diagnostic.conditions");
                    continue;
                }
                var condition = job.recipe.checkConditions(logic);
                var tickMatch = condition.isSuccess() ? job.recipe.matchTickRecipe(machine) : condition;
                if (!tickMatch.isSuccess()) {
                    if (failure == null && tickMatch.reason() != null) failure = tickMatch.reason().get();
                    continue;
                }
                if (!GalaxyPower.ready(machine, GalaxyPower.payment(job.recipe))) {
                    failure = Component.translatable("gtl_enhancedcore.diagnostic.power");
                    continue;
                }
                if (!callback) {
                    if (!access.iv$onWorking() || !logic.isWorkingEnabled()) break;
                    callback = true;
                }
                var paid = logic.handleTickRecipe(job.recipe);
                if (!paid.isSuccess()) {
                    if (failure == null && paid.reason() != null) failure = paid.reason().get();
                    continue;
                }
                job.elapsed++;
                advanced = true;
                machine.markDirty();
            }
        }
        completed |= flushCompleted();
        if (completeRequestedPause() || !logic.isWorkingEnabled()) {
            publish(RecipeLogic.Status.SUSPEND, null);
            return;
        }
        if (GalaxyLinks.crossRecipeEnabledByConnection(machine)
                && (refill || completed || machine.getOffsetTimer() % 5 == 0)) {
            refill = false;
            advanced |= admit();
        }
        if (!jobs.isEmpty()) {
            if (!advanced && failure == null) failure = Component.translatable("gtl_enhancedcore.diagnostic.output");
            publish(advanced ? RecipeLogic.Status.WORKING : RecipeLogic.Status.WAITING, failure);
        } else publish(RecipeLogic.Status.IDLE, null);
    }

    private long capacityBudget() {
        return GalaxyRecipeBatch.capacity(machine);
    }

    public static int threads(RecipeLogic logic) {
        return GalaxyNativeRecipePreparation.supports(logic) ? GalaxyNativeRecipePreparation.threads(logic)
                : GalaxyLinks.threads((WorkableElectricMultiblockMachine) logic.getMachine(), 1);
    }

    private boolean admit() {
        if (machine instanceof ISuspendableMachine suspendable && suspendable.gtlcore$isSuspendAfterFinish())
            return false;
        var running = new HashSet<ResourceLocation>();
        long reserved = 0;
        BigInteger reservedPower = BigInteger.ZERO;
        for (Job job : jobs) {
            running.add(job.origin.id);
            if (!job.complete()) {
                reserved += job.capacity;
                reservedPower = reservedPower.add(GalaxyPower.eut(job.recipe));
            }
        }
        int activeJobs = (int) jobs.stream().filter(job -> !job.complete()).count();
        int slots = Math.max(0, threads(logic) - activeJobs);
        var lock = (ILockRecipe)logic;
        if (lock.isLock()) slots = Math.min(slots, 1);
        if (slots == 0 || reserved >= capacityBudget()) return false;
        var candidates = GalaxyRecipeBatch.prepareIndependent(machine, logic, admissionRotation++,
                running, reserved, slots, reservedPower);
        if (candidates.isEmpty() || !GalaxyNativeRecipePreparation.beforeAdmission(logic)) return false;
        boolean started = false;
        for (var candidate : candidates) {
            GTRecipe recipe = candidate.recipe();
            try (var context = access.iv$recipeContext(recipe, candidate.origin(), 0, recipe.duration)) {
                if (!recipe.checkConditions(logic).isSuccess()
                        || !RecipeRunnerHelper.matchRecipeInputNocache(machine, recipe)
                        || !RecipeRunnerHelper.matchRecipeOutput(machine, recipe)
                        || !machine.beforeWorking(recipe)) continue;
                recipe.preWorking(machine);
                if (!RecipeRunnerHelper.handleRecipeInputNocache(machine, recipe)) continue;
                jobs.add(new Job(candidate.origin(), recipe, candidate.capacity(), 0));
                if (lock.isLock() && lock.getLockRecipe() == null) lock.setLockRecipe(candidate.origin());
                cycleActive = true;
                started = true;
                machine.markDirty();
            }
        }
        return started;
    }

    private boolean completeRequestedPause() {
        if (!jobs.isEmpty() || !(machine instanceof ISuspendableMachine suspendable)
                || !suspendable.gtlcore$isSuspendAfterFinish()) return false;
        logic.setWorkingEnabled(false);
        suspendable.gtlcore$setSuspendAfterFinish(false);
        machine.markDirty();
        return true;
    }

    private boolean flushCompleted() {
        boolean completed = false;
        Job lastCompleted = null;
        var iterator = jobs.iterator();
        while (iterator.hasNext()) {
            Job job = iterator.next();
            if (!job.complete()) continue;
            try (var context = access.iv$recipeContext(job.recipe, job.origin, job.elapsed, job.recipe.duration)) {
                if (!RecipeRunnerHelper.matchRecipeOutput(machine, job.recipe)
                        || !RecipeRunnerHelper.handleRecipeOutput(machine, job.recipe)) continue;
                job.recipe.postWorking(machine);
            }
            iterator.remove();
            completed = true;
            lastCompleted = job;
            machine.markDirty();
        }
        if (jobs.isEmpty() && cycleActive) {
            cycleActive = false;
            if (lastCompleted != null) try (var context = access.iv$recipeContext(lastCompleted.recipe,
                    lastCompleted.origin, lastCompleted.elapsed, lastCompleted.recipe.duration)) {
                machine.afterWorking();
            }
            machine.markDirty();
        }
        return completed;
    }

    private void publish(RecipeLogic.Status status, Component reason) {
        var summary = new CompoundTag();
        if (reason != null) {
            var reasons = new ListTag();
            reasons.add(net.minecraft.nbt.StringTag.valueOf(Component.Serializer.toJson(reason)));
            summary.put("reasons", reasons);
        }
        if (jobs.isEmpty()) {
            access.iv$publish(summary, "", null, 0, 0, status);
            return;
        }
        Job shown = jobs.getFirst();
        GTRecipe display = shown.recipe.copy();
        display.data = shown.recipe.data.copy();
        display.inputs.clear();
        display.outputs.clear();
        display.tickInputs.clear();
        display.tickOutputs.clear();
        long operations = 0;
        BigInteger power = BigInteger.ZERO;
        BigInteger left = BigInteger.ZERO;
        for (Job job : jobs) {
            operations += job.recipe.data.getLong(GalaxyRecipeBatch.OPERATIONS_TAG);
            if (!job.complete()) power = power.add(GalaxyPower.payment(job.recipe));
            left = left.add(new BigInteger(job.recipe.data.getString(GalaxyPower.LEFT_TAG)));
            job.recipe.outputs.forEach((cap, contents) -> display.outputs
                    .computeIfAbsent(cap, ignored -> new ArrayList<>()).addAll(contents));
        }
        display.data.putLong(GalaxyRecipeBatch.OPERATIONS_TAG, operations);
        display.data.putLong(GalaxyRecipeBatch.RECIPES_TAG, jobs.size());
        display.data.putString(GalaxyPower.EU_TAG, power.toString());
        display.data.putString(GalaxyPower.LEFT_TAG, left.toString());
        IGTRecipe.of(display).setRealParallels(1);
        access.iv$publish(summary, "", display, shown.elapsed, shown.recipe.duration, status);
    }

    public CompoundTag save() {
        var tag = new CompoundTag();
        var entries = new ListTag();
        for (Job job : jobs) {
            var entry = new CompoundTag();
            entry.put("recipe", GTRecipeSerializer.CODEC.encodeStart(NbtOps.INSTANCE, job.recipe)
                    .getOrThrow(false, ignored -> {}));
            entry.put("origin", GTRecipeSerializer.CODEC.encodeStart(NbtOps.INSTANCE, job.origin)
                    .getOrThrow(false, ignored -> {}));
            entry.putString("recipeId", job.recipe.id.toString());
            entry.putString("originId", job.origin.id.toString());
            entry.putLong("capacity", job.capacity);
            entry.putInt("elapsed", job.elapsed);
            entries.add(entry);
        }
        tag.put("jobs", entries);
        tag.putInt("admissionRotation", admissionRotation);
        tag.putInt("workRotation", workRotation);
        tag.putBoolean("cycleActive", cycleActive);
        return tag;
    }

    public void load(CompoundTag tag) {
        jobs.clear();
        var entries = tag.getList("jobs", Tag.TAG_COMPOUND);
        for (int index = 0; index < entries.size(); index++) {
            var entry = entries.getCompound(index);
            GTRecipe recipe = GTRecipeSerializer.CODEC.parse(NbtOps.INSTANCE, entry.get("recipe"))
                    .getOrThrow(false, ignored -> {});
            GTRecipe origin = GTRecipeSerializer.CODEC.parse(NbtOps.INSTANCE, entry.get("origin"))
                    .getOrThrow(false, ignored -> {});
            recipe.id = new ResourceLocation(entry.getString("recipeId"));
            origin.id = new ResourceLocation(entry.getString("originId"));
            jobs.add(new Job(origin, recipe, entry.getLong("capacity"), entry.getInt("elapsed")));
        }
        admissionRotation = tag.getInt("admissionRotation");
        workRotation = tag.getInt("workRotation");
        cycleActive = tag.getBoolean("cycleActive");
        refill = true;
    }
}
