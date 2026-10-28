package com.gtl.enhancedcore.common.machine;

import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gtl.enhancedcore.common.recipe.WoodDistillationAllocation;
import com.gtladd.gtladditions.api.machine.logic.MutableRecipesLogic;
import com.gtladd.gtladditions.api.machine.mutable.MutableElectricParallelHatchMultiblockMachine;
import com.gtladd.gtladditions.common.data.ParallelData;
import com.gtladd.gtladditions.utils.RecipeCalculationHelper;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import org.gtlcore.gtlcore.api.recipe.IParallelLogic;
import org.gtlcore.gtlcore.api.recipe.RecipeRunnerHelper;

import java.util.ArrayList;
import java.util.Comparator;

/** Wood distillation uses ADD's cross-recipe scheduler with 15 base threads plus Ω. */
public final class WoodDistillationMutableMachine extends AddThreadMutableMachine {
    public static final int CROSS_RECIPE_THREADS = 15;

    public WoodDistillationMutableMachine(IMachineBlockEntity holder) {
        super(holder, "wood_distillation");
    }

    @Override
    protected int threadBaseline() {
        return CROSS_RECIPE_THREADS;
    }

    @Override
    protected RecipeLogic createRecipeLogic(Object... args) {
        return new WoodRecipeLogic(this);
    }

    @Override
    public void onStructureFormed() {
        super.onStructureFormed();
        getRecipeLogic().setUseMultipleRecipes(true);
    }

    private static final class WoodRecipeLogic
            extends MutableRecipesLogic<MutableElectricParallelHatchMultiblockMachine> {
        private final WoodDistillationMutableMachine owner;

        private WoodRecipeLogic(WoodDistillationMutableMachine owner) {
            super(owner);
            this.owner = owner;
        }

        @Override
        public int getMultipleThreads() {
            if (com.gtl.enhancedcore.common.machine.galaxy.GalaxyLinks.active(owner))
                return com.gtl.enhancedcore.common.machine.galaxy.GalaxyLinks.threads(owner, (int)Math.min(Integer.MAX_VALUE, (long)owner.threadBaseline() + Math.max(0, owner.getAdditionalThread())));
            return (int) Math.min(Integer.MAX_VALUE,
                    (long) CROSS_RECIPE_THREADS + Math.max(0, owner.getAdditionalThread()));
        }

        @Override
        protected ParallelData calculateParallels() {
            // Keep ADD's recipe lock, lookup and checks. Only replace its first-candidate-wins allocation:
            // steam, water and nitrogen all consume logs, so bulk admission must follow the first pass.
            var candidates = new ArrayList<>(lookupRecipeSet());
            candidates.sort(Comparator.comparing(recipe -> recipe.id.toString()));
            var origins = new ArrayList<GTRecipe>();
            var processed = new ArrayList<GTRecipe>();
            var parallels = new LongArrayList();
            long budget = (long) com.gtl.enhancedcore.common.recipe.iv.IvMachineScope.parallel(owner) * getMultipleThreads();
            WoodDistillationAllocation.allocate(candidates, budget, (recipe, limit) -> {
                long parallel = calculateParallel(machine, recipe, limit).firstLong();
                if (parallel <= 0) return 0;
                GTRecipe admitted = IParallelLogic.getRecipeOutputChance(owner,
                        RecipeCalculationHelper.INSTANCE.multipleRecipe(recipe, parallel));
                if (!RecipeRunnerHelper.handleRecipeInput(owner, admitted)) return 0;
                origins.add(recipe);
                processed.add(admitted);
                parallels.add(parallel);
                return parallel;
            });
            // As in ADD's greedy helper, false means the input and chances are already settled.
            // Normal/wireless recipe construction, energy payment and output delivery stay in ADD.
            return origins.isEmpty() ? null : new ParallelData(origins, parallels.toLongArray(), false, processed);
        }
    }
}
