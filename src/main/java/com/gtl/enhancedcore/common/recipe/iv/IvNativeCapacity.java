package com.gtl.enhancedcore.common.recipe.iv;

import com.gregtechceu.gtceu.api.machine.multiblock.WorkableMultiblockMachine;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;

/** Per-recipe admission and bounded shared capacity for isolated IV orders. */
public final class IvNativeCapacity {
    private IvNativeCapacity() {}

    /** Preserve recipe-specific limits before sharing the controller budget. */
    public static long candidateCapacity(WorkableMultiblockMachine machine, GTRecipe recipe, long budget) {
        long perRecipe = IvMachineScope.parallel(machine, recipe);
        if (recipe.recipeType == org.gtlcore.gtlcore.common.data.GTLRecipeTypes.PRECISION_LASER_ENGRAVER_RECIPES)
            return Math.min(1, budget);
        return Math.min(budget, perRecipe);
    }

    /** The native modifier still clips this declaration against real order stock and power. */
    public static int modifierParallel(WorkableMultiblockMachine machine) {
        if (!com.gtl.enhancedcore.common.config.MachineOverrideConfig.current().enhanceOriginalMachines()) return -1;
        var id = machine.getDefinition().getId();
        return com.gtl.enhancedcore.common.recipe.ModifierParallelCapacity.limit(id.getNamespace(), id.getPath());
    }

    public static long budget(long parallel, int threads) {
        return parallel > Long.MAX_VALUE / Math.max(1, threads) ? Long.MAX_VALUE : parallel * Math.max(1, threads);
    }
}
