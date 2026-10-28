package com.gtl.enhancedcore.common.recipe;

import com.gregtechceu.gtceu.api.capability.recipe.*;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gregtechceu.gtceu.api.recipe.*;
import com.gregtechceu.gtceu.api.recipe.content.Content;
import com.gregtechceu.gtceu.api.recipe.logic.*;
import com.gtl.enhancedcore.common.machine.galaxy.GalaxyLinks;
import com.gtl.enhancedcore.common.machine.galaxy.GalaxyPower;
import com.gtladd.gtladditions.api.machine.logic.MutableRecipesLogic;
import com.gtladd.gtladditions.utils.RecipeCalculationHelper;
import java.math.BigInteger;
import java.util.*;
import org.gtlcore.gtlcore.api.recipe.IGTRecipe;
import org.gtlcore.gtlcore.api.recipe.IParallelLogic;
import org.gtlcore.gtlcore.api.recipe.RecipeRunnerHelper;
import org.gtlcore.gtlcore.common.machine.trait.MultipleRecipesLogic;
import org.gtlcore.gtlcore.api.machine.trait.ILockRecipe;

/** Shared-inventory fallback for machines that do not already have a multiple-recipe engine.
 * Admission is simulated as a union; setupRecipe alone withdraws inputs. Batches use the
 * original RecipeLogic's persistence, tick payment, callbacks and output retry state machine.
 */
public final class GalaxyRecipeBatch {
    public static final String MARKER = "gtle_galaxy_batch";
    private static final ThreadLocal<Boolean> MODIFYING = ThreadLocal.withInitial(() -> false);
    public static boolean modifying() { return MODIFYING.get(); }
    private record Candidate(GTRecipe recipe, long capacity) {}
    private GalaxyRecipeBatch() {}
    public static boolean applicable(RecipeLogic logic) {
        return !(logic instanceof MultipleRecipesLogic) && !(logic instanceof MutableRecipesLogic<?>)
                && !(logic instanceof com.gtladd.gtladditions.api.machine.logic.GTLAddMultipleRecipesLogic)
                && logic.getMachine() instanceof WorkableElectricMultiblockMachine m && GalaxyLinks.active(m);
    }
    public static GTRecipe find(WorkableElectricMultiblockMachine machine, RecipeLogic logic, int rotation) {
        if (!machine.hasProxies() || !machine.isRecipeLogicAvailable()) return null;
        var origins = new LinkedHashSet<GTRecipe>();
        var lock = (ILockRecipe)(Object)logic;
        if (lock.isLock() && lock.getLockRecipe() != null) origins.add(lock.getLockRecipe());
        else {
            var iterator = machine.getRecipeType().getLookup().getRecipeIterator(machine, recipe -> true);
            ((org.gtlcore.gtlcore.api.recipe.IAdditionalRecipeIterator)iterator).setUseDiveIngredientTreeFind(true);
            while (iterator.hasNext()) {
                var origin = iterator.next();
                if (origin != null) origins.add(origin);
            }
            for (var runner : machine.getRecipeType().getCustomRecipeLogicRunners()) {
                var origin = runner.createCustomRecipe(machine);
                if (origin != null) origins.add(origin);
            }
        }
        var candidates = new ArrayList<>(origins);
        candidates.sort(Comparator.comparing(recipe -> recipe.id.toString()));
        if (!candidates.isEmpty()) Collections.rotate(candidates, -Math.floorMod(rotation, candidates.size()));
        int threads = GalaxyLinks.threads(machine, 1);
        if (candidates.size() > threads) candidates.subList(threads, candidates.size()).clear();
        var modified = new ArrayList<Candidate>();
        boolean previousModifying = MODIFYING.get();
        MODIFYING.set(true);
        try {
            for (var origin : candidates) {
                if (!origin.checkConditions(logic).isSuccess()) continue;
                GTRecipe copy = origin.copy(); copy.data = origin.data.copy();
                GTRecipe recipe = machine.fullModifyRecipe(copy, new OCParams(), new OCResult());
                long capacity = recipe == null ? 1 : Math.max(1, IGTRecipe.of(recipe).getRealParallels());
                recipe = RecipeBatchPreparation.apply(machine, origin, recipe);
                if (recipe != null && mergeable(recipe) && recipe.checkConditions(logic).isSuccess())
                    modified.add(new Candidate(recipe, capacity));
            }
        } finally { if (previousModifying) MODIFYING.set(true); else MODIFYING.remove(); }
        return allocate(machine, logic, modified);
    }
    /** Also covers custom searches which override findAndHandleRecipe and call setup directly. */
    public static GTRecipe expand(RecipeLogic logic, GTRecipe recipe) {
        if (recipe.data.getBoolean(MARKER) || !applicable(logic) || !mergeable(recipe)) return recipe;
        var machine = (WorkableElectricMultiblockMachine)logic.getMachine();
        long capacity = Math.max(1, IGTRecipe.of(recipe).getRealParallels());
        GTRecipe copy = recipe.copy(); copy.data = recipe.data.copy();
        org.gtlcore.gtlcore.api.recipe.RecipeExtensionCopier.copy(recipe, copy);
        GTRecipe prepared = RecipeBatchPreparation.apply(machine, null, copy);
        if (prepared == null) return recipe;
        GTRecipe expanded = allocate(machine, logic, List.of(new Candidate(prepared, capacity)));
        return expanded == null ? recipe : expanded;
    }
    private static boolean mergeable(GTRecipe recipe) {
        return RecipeHelper.getInputEUt(recipe) >= 0;
    }
    private static GTRecipe allocate(WorkableElectricMultiblockMachine machine, RecipeLogic logic, List<Candidate> candidates) {
        var admitted = new ArrayList<GTRecipe>();
        var selected = new ArrayList<GTRecipe>();
        var capacities = new ArrayList<Long>();
        long budget = GalaxyLinks.unlimited(machine) ? (long)Integer.MAX_VALUE * Integer.MAX_VALUE
                : (long)com.gtl.enhancedcore.common.recipe.iv.IvMachineScope.parallel(machine)
                        * GalaxyLinks.threads(machine, 1);
        long used = 0;
        // Reserve one of every compatible recipe before any candidate can consume shared stock.
        for (Candidate candidate : candidates) {
            GTRecipe recipe = candidate.recipe();
            if (candidate.capacity() > budget - used) continue;
            var proposed = new ArrayList<>(admitted); proposed.add(recipe);
            if (fits(machine, logic, proposed)) {
                admitted.add(recipe); selected.add(recipe); capacities.add(candidate.capacity()); used += candidate.capacity();
            }
        }
        for (int i = 0; i < admitted.size(); i++) {
            final int index = i;
            GTRecipe recipe = selected.get(i);
            long max = Math.max(1, Integer.MAX_VALUE / Math.max(1, IGTRecipe.of(recipe).getRealParallels()));
            max = Math.min(max, 1 + (budget - used) / capacities.get(i));
            long parallel = BoundedRecipeAdmission.maximum(max, n -> {
                var proposed = new ArrayList<>(admitted);
                try {
                    proposed.set(index, scale(recipe, n));
                    return fits(machine, logic, proposed);
                } catch (ArithmeticException overflow) { return false; }
            });
            if (parallel > 1) {
                admitted.set(i, scale(recipe, parallel));
                used += capacities.get(i) * (parallel - 1);
            }
        }
        return admitted.isEmpty() ? null : build(machine, admitted, true);
    }
    private static GTRecipe scale(GTRecipe recipe, long parallel) {
        GTRecipe copy = recipe.copy();
        copy.data = recipe.data.copy();
        BigInteger energy = GalaxyPower.eut(recipe).multiply(BigInteger.valueOf(parallel));
        copy.tickInputs.remove(EURecipeCapability.CAP);
        org.gtlcore.gtlcore.api.recipe.RecipeExtensionCopier.copy(recipe, copy);
        GTRecipe scaled=RecipeCalculationHelper.INSTANCE.multipleRecipe(copy,parallel);
        scaled.data = copy.data.copy();
        scaled.data.putString(GalaxyPower.EU_TAG, energy.toString());
        // ADD's helper replaces realParallels with its multiplier. This input already
        // contains native parallel/subtick/time-window cycles; chance rolls need all of them.
        IGTRecipe.of(scaled).setRealParallels(Math.multiplyExact(
                Math.max(1, IGTRecipe.of(recipe).getRealParallels()), parallel));
        if(recipe.data.getBoolean("duration_is_total_cwu")) {
            scaled.data=recipe.data.copy();
            scaled.data.putString(GalaxyPower.EU_TAG, energy.toString());
            scaled.data.putLong(org.gtlcore.gtlcore.api.machine.computation.ComputationAmounts.TOTAL_CWU,
                    Math.multiplyExact(org.gtlcore.gtlcore.api.machine.computation.ComputationAmounts.total(recipe),parallel));
        }
        return scaled;
    }
    private static boolean fits(WorkableElectricMultiblockMachine machine, RecipeLogic logic, List<GTRecipe> proposed) {
        try {
            GTRecipe batch = build(machine, proposed, false);
            return batch != null && FactoryRecipeSafety.valid(batch)
                    && batch.checkConditions(logic).isSuccess() && batch.matchTickRecipe(machine).isSuccess()
                    && RecipeRunnerHelper.matchRecipeInputNocache(machine, batch)
                    && RecipeRunnerHelper.matchRecipeOutput(machine, batch);
        } catch (ArithmeticException overflow) { return false; }
    }
    private static GTRecipe build(WorkableElectricMultiblockMachine machine, List<GTRecipe> recipes, boolean settle) {
        GTRecipe first = recipes.getFirst();
        GTRecipe combined = first.copy();
        combined.data = first.data.copy(); // GTCEu copy() shares data with its source recipe.
        combined.inputs.clear(); combined.outputs.clear();
        combined.tickInputs.clear(); combined.tickOutputs.clear();
        combined.conditions.clear();
        var inputs = new RecipeInputAdmission();
        BigInteger energy = BigInteger.ZERO;
        int ticks = 1;
        boolean tickProducer=recipes.stream().anyMatch(recipe->!recipe.tickOutputs.isEmpty());
        for (GTRecipe recipe : recipes) {
            // Keep machine-specific data on the batch; conflicting metadata cannot be merged.
            var firstData = first.data.copy(); firstData.remove(GalaxyPower.EU_TAG);
            var recipeData = recipe.data.copy(); recipeData.remove(GalaxyPower.EU_TAG);
            if (!firstData.equals(recipeData)) return null;
            if (recipes.size()>1 && recipe.data.getBoolean("duration_is_total_cwu")) return null;
            // A per-tick producer must keep its exact run length; unequal durations
            // cannot be collapsed without changing the quantity produced each tick.
            if (tickProducer && recipe.duration!=first.duration) return null;
            inputs.include(machine, recipe, settle);
            append(combined.outputs, (settle ? IParallelLogic.getRecipeOutputChance(machine, recipe) : recipe).outputs, false);
            append(combined.tickInputs, recipe.tickInputs, true);
            append(combined.tickOutputs, recipe.tickOutputs, false);
            combined.conditions.addAll(recipe.conditions);
            ticks = Math.max(ticks, recipe.duration);
            energy = energy.add(GalaxyPower.eut(recipe)
                    .multiply(BigInteger.valueOf(recipe.duration)));
        }
        inputs.writeTo(combined.inputs);
        // Preserve total EU and account for real input power. Additional parallel work extends
        // duration when power is finite, just as ADD's ordinary multi-recipe batching does.
        BigInteger power = GalaxyPower.availablePower(machine);
        if (energy.signum() == 0) power = power.max(BigInteger.ONE);
        if (power.signum() <= 0) return null;
        var plan = GalaxyBatchAccounting.plan(energy, ticks, power);
        if (plan == null) return null;
        if (tickProducer && plan.duration()!=ticks) return null;
        combined.duration = plan.duration();
        combined.data.putString(GalaxyPower.EU_TAG, plan.eut().toString());
        combined.data.putString(GalaxyPower.LEFT_TAG, energy.toString());
        combined.data.putBoolean(MARKER, true);
        IGTRecipe.of(combined).setHasTick(energy.signum() > 0 || !combined.tickInputs.isEmpty() || !combined.tickOutputs.isEmpty());
        IGTRecipe.of(combined).setRealParallels(1); // Each source's chance multiplier was encoded when scaled.
        IGTRecipe.of(combined).setBatchProcessed(true); // Do not apply GTLCore time-window batching again at input payment.
        return combined;
    }
    private static void append(Map<RecipeCapability<?>, List<Content>> target,
                               Map<RecipeCapability<?>, List<Content>> source, boolean omitEU) {
        source.forEach((cap, values) -> {
            if (!omitEU || cap != EURecipeCapability.CAP)
                target.computeIfAbsent(cap, ignored -> new ArrayList<>()).addAll(values);
        });
    }
}
