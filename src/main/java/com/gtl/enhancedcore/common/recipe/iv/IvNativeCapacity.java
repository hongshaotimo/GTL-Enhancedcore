package com.gtl.enhancedcore.common.recipe.iv;

import com.gregtechceu.gtceu.api.machine.multiblock.WorkableMultiblockMachine;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gtladd.gtladditions.common.machine.multiblock.controller.*;
import com.gtladd.gtladditions.common.machine.multiblock.controller.module.ForgeOfTheAntichristModuleBase;
import com.gtladd.gtladditions.common.machine.multiblock.controller.module.HelioFusionExoticizer;

/** Capacities declared by ADD's custom calculateParallels overrides, rather than
 * its inherited int getters. Infinite capacity still uses bounded long orders. */
public final class IvNativeCapacity {
    private IvNativeCapacity() {}

    public static boolean unlimitedParallel(WorkableMultiblockMachine machine, GTRecipe recipe) {
        return machine instanceof SpaceScalingInstrument || machine instanceof ForgeOfTheAntichrist
                || machine instanceof ForgeOfTheAntichristModuleBase
                || machine instanceof SpaceInfinityIntegratedOreProcessor
                || machine instanceof MacroAtomicResonantFragmentStripper
                && (recipe == null ? machine.getRecipeType() : recipe.recipeType)
                        != com.gtladd.gtladditions.common.recipe.GTLAddRecipesTypes.INSTANCE.getSTAR_CORE_STRIPPER();
    }

    public static boolean unlimitedThreads(WorkableMultiblockMachine machine) {
        return machine instanceof SpaceScalingInstrument || machine instanceof ForgeOfTheAntichrist
                || machine instanceof ForgeOfTheAntichristModuleBase && !(machine instanceof HelioFusionExoticizer)
                || machine instanceof SpaceInfinityIntegratedOreProcessor
                || machine instanceof MacroAtomicResonantFragmentStripper;
    }

    /** HelioFusionExoticizer.lookupRecipeIterator deliberately locks one recipe. */
    public static boolean singleRecipe(WorkableMultiblockMachine machine) {
        return machine instanceof HelioFusionExoticizer;
    }

    /** These native planners allocate each candidate independently; no shared long
     * counter limits the sum across recipes (including finite star-core batches). */
    public static boolean independentRecipeParallel(WorkableMultiblockMachine machine) {
        return machine instanceof SpaceScalingInstrument || machine instanceof ForgeOfTheAntichrist
                || machine instanceof ForgeOfTheAntichristModuleBase
                || machine instanceof SpaceInfinityIntegratedOreProcessor
                || machine instanceof MacroAtomicResonantFragmentStripper;
    }

    /** Native multi-recipe planners let a selected candidate use the remaining
     * aggregate thread budget. Existing explicit IV and precision rules stay exact. */
    public static long candidateCapacity(WorkableMultiblockMachine machine, GTRecipe recipe, long budget) {
        long perRecipe = IvMachineScope.parallel(machine, recipe);
        if (recipe.recipeType == org.gtlcore.gtlcore.common.data.GTLRecipeTypes.PRECISION_LASER_ENGRAVER_RECIPES)
            return Math.min(1, budget);
        return IvBuffers.genericCloudTarget(machine) && IvMachineScope.batching(machine)
                && !independentRecipeParallel(machine) ? budget : Math.min(budget, perRecipe);
    }

    /** -1 means use the controller's usual int parallel getter. */
    public static long parallel(WorkableMultiblockMachine machine, GTRecipe recipe) {
        if (unlimitedParallel(machine, recipe)) return Long.MAX_VALUE;
        long modifier = modifierParallel(machine);
        if (modifier > 0) return modifier;
        if (machine instanceof MacroAtomicResonantFragmentStripper stripper) return Math.max(1, stripper.getRealParallel());
        if (machine instanceof TaixuTurbidArray taixu) return Math.max(1, taixu.getRealParallel());
        if (machine instanceof ConversationMachine conversation) return Math.max(1, conversation.getParallel());
        if (machine instanceof BasicOreProcessorMachine ore) return Math.max(1, ore.getParallel());
        return -1;
    }

    /** The native modifier still clips this declaration against real order stock and power. */
    public static int modifierParallel(WorkableMultiblockMachine machine) {
        if (!com.gtl.enhancedcore.common.config.MachineOverrideConfig.current().enhanceOriginalMachines()) return -1;
        var id = machine.getDefinition().getId();
        return com.gtl.enhancedcore.common.recipe.ModifierParallelCapacity.limit(id.getNamespace(), id.getPath());
    }

    public static com.gtladd.gtladditions.common.machine.hatch.OreProcessorHatch oreHatch(WorkableMultiblockMachine machine) {
        return machine.getParts().stream()
                .filter(com.gtladd.gtladditions.common.machine.hatch.OreProcessorHatch.class::isInstance)
                .map(com.gtladd.gtladditions.common.machine.hatch.OreProcessorHatch.class::cast).findFirst().orElse(null);
    }

    public static long budget(WorkableMultiblockMachine machine, long parallel, int threads) {
        long capacity = budget(parallel, threads);
        var hatch = machine instanceof OreProcessorMachine ? oreHatch(machine) : null;
        return hatch != null && hatch.getMatchAll() ? budget(capacity, 2) : capacity;
    }

    public static long budget(long parallel, int threads) {
        return parallel > Long.MAX_VALUE / Math.max(1, threads) ? Long.MAX_VALUE : parallel * Math.max(1, threads);
    }
}
