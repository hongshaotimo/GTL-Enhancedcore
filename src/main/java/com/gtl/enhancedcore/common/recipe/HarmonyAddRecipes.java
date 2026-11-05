package com.gtl.enhancedcore.common.recipe;

import com.gregtechceu.gtceu.api.capability.recipe.RecipeCapability;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.content.Content;
import com.gtl.enhancedcore.common.config.MachineOverrideConfig;
import com.gtladd.gtladditions.api.machine.IHarmonyMachineAccessor;
import com.gtladd.gtladditions.common.data.ParallelData;
import com.gtladd.gtladditions.common.machine.multiblock.controller.ArcanicAstrograph;
import com.gtladd.gtladditions.utils.RecipeCalculationHelper;
import it.unimi.dsi.fastutil.longs.LongLongPair;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import net.minecraft.network.chat.Component;
import org.gtlcore.gtlcore.api.machine.trait.ILockRecipe;
import org.gtlcore.gtlcore.api.recipe.IGTRecipe;
import org.gtlcore.gtlcore.api.recipe.IAdditionalRecipeIterator;
import org.gtlcore.gtlcore.api.recipe.IParallelLogic;
import org.gtlcore.gtlcore.api.recipe.RecipeRunnerHelper;
import org.gtlcore.gtlcore.common.data.GTLRecipeTypes;
import org.gtlcore.gtlcore.common.machine.multiblock.electric.HarmonyMachine;

/** ADD allocation for the two no-energy Harmony controllers; their native entities stay intact. */
public final class HarmonyAddRecipes {
    public static final int THREADS = 128;
    public static final String RECIPE_COUNT = "gtleHarmonyAddRecipeCount";

    private HarmonyAddRecipes() {}

    public static boolean enabled(MetaMachine machine) {
        if (!MachineOverrideConfig.current().enhanceOriginalMachines()) return false;
        var id = machine.getDefinition().getId();
        return id.getNamespace().equals("gtceu") && id.getPath().equals("eye_of_harmony")
                || id.getNamespace().equals("gtladditions") && id.getPath().equals("arcanic_astrograph");
    }

    /** One native startup payment per admitted recipe; Arcanic retains its native per-recipe parallel. */
    public static GTRecipe cosmos(HarmonyMachine owner, RecipeLogic logic) {
        if (!owner.hasProxies()) return null;
        var access = (IHarmonyMachineAccessor) owner;
        var candidates = new ArrayList<GTRecipe>();
        var lock = (ILockRecipe) logic;
        if (lock.isLock() && lock.getLockRecipe() != null) {
            var recipe = lock.getLockRecipe();
            if (recipe.recipeType == GTLRecipeTypes.COSMOS_SIMULATION_RECIPES) candidates.add(recipe);
        } else {
            var iterator = GTLRecipeTypes.COSMOS_SIMULATION_RECIPES.getLookup().getRecipeIterator(owner,
                    recipe -> RecipeRunnerHelper.matchRecipe(owner, recipe)
                            && recipe.checkConditions(logic).isSuccess());
            // ADD enables exhaustive traversal automatically only for electric Mutable/Multiple
            // logic. Harmony is a NoEnergy controller, so opt this iterator into the same API.
            ((IAdditionalRecipeIterator) iterator).setUseDiveIngredientTreeFind(true);
            var distinct = new LinkedHashSet<GTRecipe>();
            while (iterator.hasNext()) distinct.add(iterator.next());
            candidates.addAll(distinct);
        }
        candidates.sort(Comparator.comparing(recipe -> recipe.id.toString()));
        if (lock.isLock() && lock.getLockRecipe() == null && !candidates.isEmpty()) {
            lock.setLockRecipe(candidates.getFirst());
            candidates.subList(1, candidates.size()).clear();
        }
        var prepared = new ArrayList<GTRecipe>(candidates.size());
        for (var recipe : candidates) {
            if (!RecipeRunnerHelper.matchRecipeInput(owner, recipe) || !recipe.checkConditions(logic).isSuccess()) continue;
            var copy = access.applyCreateDataOutput(recipe);
            copy.duration = access.getHarmonyDuration();
            prepared.add(copy);
        }
        var preview = new ArrayList<GTRecipe>();
        long maximum = owner instanceof ArcanicAstrograph astrograph ? astrograph.getParallelAmount() : 1L;
        // ADD settles output chances and consumes each admitted input once. A consumed ParallelData
        // is never submitted to the native setup path a second time with those inputs attached.
        ParallelData data = RecipeCalculationHelper.INSTANCE.calculateParallelsWithGreedyAllocation(
                prepared, THREADS, owner, (recipe, remaining) -> {
                    long parallel = IParallelLogic.getMaxParallel(owner, recipe, maximum);
                    if (parallel <= 0) return LongLongPair.of(0, 0);
                    var scaled = RecipeCalculationHelper.INSTANCE.multipleRecipe(recipe.copy(), parallel);
                    if (!RecipeRunnerHelper.matchRecipeInput(owner, scaled)) return LongLongPair.of(0, 0);
                    preview.add(scaled);
                    var proposed = merge(preview);
                    if (!RecipeRunnerHelper.matchRecipeOutput(owner, proposed) || !access.consumeCosmosStartup()) {
                        preview.removeLast();
                        return LongLongPair.of(0, 0);
                    }
                    return LongLongPair.of(parallel, 1);
                });
        if (data == null) return null;
        GTRecipe batch = merge(data.getProcessedRecipeList());
        batch.data.putInt(RECIPE_COUNT, data.getOriginRecipeList().size());
        IGTRecipe.of(batch).setRealParallels(java.util.Arrays.stream(data.getParallels()).sum());
        IGTRecipe.of(batch).setBatchProcessed(true);
        return batch;
    }

    private static GTRecipe merge(List<GTRecipe> recipes) {
        GTRecipe result = recipes.getFirst().copy();
        result.data = result.data.copy();
        result.inputs.clear();
        result.outputs.clear();
        result.tickInputs.clear();
        result.tickOutputs.clear();
        result.conditions.clear();
        for (GTRecipe recipe : recipes) {
            append(result.outputs, recipe.outputs);
            append(result.tickInputs, recipe.tickInputs);
            append(result.tickOutputs, recipe.tickOutputs);
            result.conditions.addAll(recipe.conditions);
        }
        return result;
    }

    private static void append(Map<RecipeCapability<?>, List<Content>> target,
                               Map<RecipeCapability<?>, List<Content>> source) {
        source.forEach((capability, contents) -> target.computeIfAbsent(capability, ignored -> new ArrayList<>())
                .addAll(contents));
    }

    public static void appendDisplay(HarmonyMachine machine, List<Component> lines) {
        if (!enabled(machine) || !machine.isFormed()) return;
        lines.add(Component.translatable("gtl_enhancedcore.gui.harmony_add_threads", THREADS));
        var logic = machine.getRecipeLogic();
        var recipe = logic.getLastRecipe();
        int active = recipe != null && !logic.isIdle() && !logic.isSuspend() ? recipe.data.getInt(RECIPE_COUNT) : 0;
        lines.add(Component.translatable("gtl_enhancedcore.gui.harmony_add_active", active));
    }
}
