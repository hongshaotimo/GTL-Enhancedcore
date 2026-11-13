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
    public static final String OPERATIONS_TAG = "gtle_galaxy_operations";
    public static final String RECIPES_TAG = "gtle_galaxy_recipes";
    private static final ThreadLocal<Boolean> MODIFYING = ThreadLocal.withInitial(() -> false);
    private static final ThreadLocal<Boolean> INDEPENDENT = ThreadLocal.withInitial(() -> false);
    public static boolean modifying() { return MODIFYING.get(); }
    public static boolean preparingIndependent() { return INDEPENDENT.get(); }
    private record Candidate(GTRecipe recipe, long capacity) {}
    public record Prepared(GTRecipe origin, GTRecipe recipe, long capacity) {}
    private GalaxyRecipeBatch() {}
    public static boolean applicable(RecipeLogic logic) {
        return !(logic instanceof MultipleRecipesLogic) && !(logic instanceof MutableRecipesLogic<?>)
                && !(logic instanceof com.gtladd.gtladditions.api.machine.logic.GTLAddMultipleRecipesLogic)
                && logic.getMachine() instanceof WorkableElectricMultiblockMachine m && GalaxyLinks.active(m);
    }
    public static GTRecipe find(WorkableElectricMultiblockMachine machine, RecipeLogic logic, int rotation) {
        if (!machine.hasProxies() || !machine.isRecipeLogicAvailable()) return null;
        var candidates = candidates(machine, logic, rotation);
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
    private static ArrayList<GTRecipe> candidates(WorkableElectricMultiblockMachine machine, RecipeLogic logic, int rotation) {
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
        return candidates;
    }
    /** Independent ordinary jobs retain their own metadata, duration and frozen output. */
    public static List<Prepared> prepareIndependent(WorkableElectricMultiblockMachine machine, RecipeLogic logic,
            int rotation, Set<net.minecraft.resources.ResourceLocation> running, long reservedCapacity,
            int slots, BigInteger reservedPower) {
        if (slots <= 0 || !machine.hasProxies() || !GalaxyLinks.crossRecipeEnabledByConnection(machine)) return List.of();
        long remaining = Math.max(0, capacity(machine) - reservedCapacity);
        var admitted = new ArrayList<Prepared>();
        boolean previousModifying = MODIFYING.get();
        MODIFYING.set(true);
        INDEPENDENT.set(true);
        try {
            for (var origin : candidates(machine, logic, rotation)) {
                if (admitted.size() >= slots) break;
                if (running.contains(origin.id)) continue;
                if (!origin.checkConditions(logic).isSuccess()
                        || !GalaxyNativeRecipePreparation.allowed(logic, origin)) continue;
                GTRecipe copy = origin.copy(); copy.data = origin.data.copy();
                boolean nativeBatch = GalaxyNativeRecipePreparation.supports(logic);
                GTRecipe recipe = nativeBatch ? GalaxyNativeRecipePreparation.prepare(logic, copy)
                        : machine.fullModifyRecipe(copy, new OCParams(), new OCResult());
                long occupied = recipe == null ? 1 : Math.max(1, IGTRecipe.of(recipe).getRealParallels());
                if (!nativeBatch) recipe = RecipeBatchPreparation.apply(machine, origin, recipe);
                if (recipe == null || !mergeable(recipe) || recipe.data.getBoolean("duration_is_total_cwu")
                        || occupied > remaining || !recipe.checkConditions(logic).isSuccess()) continue;
                var candidate = new Prepared(origin, recipe, occupied);
                var proposed = new ArrayList<>(admitted); proposed.add(candidate);
                if (!fitsIndependent(machine, proposed, reservedPower)) continue;
                admitted.add(candidate); remaining -= occupied;
            }
            // Reserve a runnable group for each recipe before distributing the remaining stock.
            for (int index = 0; index < admitted.size(); index++) {
                final int position = index;
                Prepared candidate = admitted.get(index);
                long maximum = Math.min(Long.MAX_VALUE / Math.max(1, IGTRecipe.of(candidate.recipe()).getRealParallels()),
                        1 + remaining / candidate.capacity());
                long multiplier = BoundedRecipeAdmission.maximum(Math.max(1, maximum), n -> {
                    try {
                        var proposed = new ArrayList<>(admitted);
                        proposed.set(position, new Prepared(candidate.origin(), scale(candidate.recipe(), n),
                                Math.multiplyExact(candidate.capacity(), n)));
                        return fitsIndependent(machine, proposed, reservedPower);
                    } catch (ArithmeticException overflow) { return false; }
                });
                if (multiplier > 1) {
                    long occupied = Math.multiplyExact(candidate.capacity(), multiplier);
                    admitted.set(index, new Prepared(candidate.origin(), scale(candidate.recipe(), multiplier), occupied));
                    remaining -= occupied - candidate.capacity();
                }
            }
            var prepared = new ArrayList<Prepared>();
            for (var candidate : admitted) {
                GTRecipe recipe = build(machine, List.of(candidate.recipe()), true);
                if (recipe != null) prepared.add(new Prepared(candidate.origin(), recipe, candidate.capacity()));
            }
            return prepared;
        } finally {
            INDEPENDENT.remove();
            if (previousModifying) MODIFYING.set(true); else MODIFYING.remove();
        }
    }
    public static long capacity(WorkableElectricMultiblockMachine machine) {
        if (GalaxyNativeRecipePreparation.supports(machine.getRecipeLogic()))
            return GalaxyLinks.unlimited(machine) ? (long)Integer.MAX_VALUE * Integer.MAX_VALUE
                    : (long)((org.gtlcore.gtlcore.api.machine.multiblock.ParallelMachine)machine).getMaxParallel()
                            * GalaxyIndependentRecipeEngine.threads(machine.getRecipeLogic());
        return GalaxyLinks.unlimited(machine) ? (long)Integer.MAX_VALUE * Integer.MAX_VALUE
                : (long)com.gtl.enhancedcore.common.recipe.iv.IvMachineScope.parallel(machine) * GalaxyLinks.threads(machine, 1);
    }
    private static boolean fitsIndependent(WorkableElectricMultiblockMachine machine, List<Prepared> candidates,
            BigInteger reservedPower) {
        GTRecipe union = candidates.getFirst().recipe().copy();
        union.data = union.data.copy();
        union.inputs.clear(); union.outputs.clear(); union.tickInputs.clear(); union.tickOutputs.clear();
        var inputs = new RecipeInputAdmission();
        BigInteger power = reservedPower;
        for (var candidate : candidates) {
            GTRecipe recipe = candidate.recipe();
            power = power.add(GalaxyPower.eut(recipe));
            inputs.include(machine, recipe, false);
            append(union.outputs, recipe.outputs, false);
            append(union.tickInputs, recipe.tickInputs, true);
            append(union.tickOutputs, recipe.tickOutputs, false);
        }
        inputs.writeTo(union.inputs);
        return power.compareTo(GalaxyPower.availablePower(machine)) <= 0 && FactoryRecipeSafety.valid(union)
                && union.matchTickRecipe(machine).isSuccess()
                && RecipeRunnerHelper.matchRecipeInputNocache(machine, union)
                && RecipeRunnerHelper.matchRecipeOutput(machine, union);
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
            long max = Math.max(1, Long.MAX_VALUE / Math.max(1, IGTRecipe.of(recipe).getRealParallels()));
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
        for (var contents : List.of(recipe.inputs, recipe.outputs, recipe.tickInputs, recipe.tickOutputs)) {
            for (var content : contents.getOrDefault(ItemRecipeCapability.CAP, List.of())) {
                if (content.chance > 0) Math.multiplyExact(
                        com.gtl.enhancedcore.common.recipe.iv.IvRecipeInputs.itemAmount(
                                ItemRecipeCapability.CAP.of(content.content)), parallel);
            }
            for (var content : contents.getOrDefault(FluidRecipeCapability.CAP, List.of()))
                if (content.chance > 0) Math.multiplyExact(FluidRecipeCapability.CAP.of(content.content).getAmount(), parallel);
        }
        GTRecipe copy = recipe.copy();
        copy.data = recipe.data.copy();
        BigInteger energy = GalaxyPower.eut(recipe).multiply(BigInteger.valueOf(parallel));
        copy.tickInputs.remove(EURecipeCapability.CAP);
        org.gtlcore.gtlcore.api.recipe.RecipeExtensionCopier.copy(recipe, copy);
        GTRecipe scaled=RecipeCalculationHelper.INSTANCE.multipleRecipe(copy,parallel);
        scaled.data = copy.data.copy();
        scaled.data.putString(GalaxyPower.EU_TAG, energy.toString());
        if (recipe.data.contains(GalaxyPower.LEFT_TAG))
            scaled.data.putString(GalaxyPower.LEFT_TAG,
                    new BigInteger(recipe.data.getString(GalaxyPower.LEFT_TAG)).multiply(BigInteger.valueOf(parallel)).toString());
        if (recipe.data.contains(GalaxyNativeRecipePreparation.EXACT_ENERGY_TAG)) {
            var exactEnergy = new java.math.BigDecimal(recipe.data.getString(GalaxyNativeRecipePreparation.EXACT_ENERGY_TAG))
                    .multiply(java.math.BigDecimal.valueOf(parallel));
            BigInteger total = exactEnergy.toBigInteger();
            BigInteger ticks = BigInteger.valueOf(recipe.duration);
            scaled.data.putString(GalaxyNativeRecipePreparation.EXACT_ENERGY_TAG, exactEnergy.toPlainString());
            scaled.data.putString(GalaxyPower.LEFT_TAG, total.toString());
            scaled.data.putString(GalaxyPower.EU_TAG, total.add(ticks.subtract(BigInteger.ONE)).divide(ticks).toString());
        }
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
        long operations = 0;
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
            energy = energy.add(recipe.data.contains(GalaxyPower.LEFT_TAG)
                    ? new BigInteger(recipe.data.getString(GalaxyPower.LEFT_TAG))
                    : GalaxyPower.eut(recipe).multiply(BigInteger.valueOf(recipe.duration)));
            operations = Math.addExact(operations, Math.max(1, IGTRecipe.of(recipe).getRealParallels()));
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
        combined.data.putLong(OPERATIONS_TAG, operations);
        combined.data.putLong(RECIPES_TAG, recipes.size());
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
