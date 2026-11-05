package com.gtl.enhancedcore.common.recipe.iv;

import com.gregtechceu.gtceu.api.machine.multiblock.WorkableMultiblockMachine;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.content.ContentModifier;
import com.gtl.enhancedcore.mixin.gtladditions.IvNativeForgeRecipeAccessor;
import com.gtl.enhancedcore.mixin.gtladditions.IvNativeForgeStateAccessor;
import com.gtladd.gtladditions.common.machine.multiblock.controller.ForgeOfTheAntichrist;
import com.gtladd.gtladditions.common.machine.multiblock.controller.module.ForgeOfTheAntichristModuleBase;
import com.gtladd.gtladditions.common.machine.multiblock.controller.ApocalypticTorsionQuantumMatrix;
import com.gtladd.gtladditions.common.machine.multiblock.controller.ApocalypticTorsionQuantumMatrix.Companion.ApocalypticTorsionQuantumMatrixLogic;
import com.gtladd.gtladditions.common.machine.multiblock.controller.TaixuTurbidArray;
import com.gtl.enhancedcore.mixin.gtladditions.IvNativeApocalypticAccessor;
import com.gtl.enhancedcore.mixin.gtladditions.IvNativeTaixuAccessor;

/** Pure native recipe preparation. Never invoke calculateParallels: ADD's implementation
 * also rolls chances and consumes physical inputs inside that method. */
public final class IvNativeBatchRecipe {
    private IvNativeBatchRecipe() {}

    public static GTRecipe prepare(WorkableMultiblockMachine machine, GTRecipe recipe) {
        if (machine instanceof ApocalypticTorsionQuantumMatrix)
            return apocalyptic().iv$modifyChance(recipe);
        if (machine instanceof ForgeOfTheAntichrist forge) {
            var state = ((IvNativeForgeStateAccessor)(Object)forge).iv$recursiveReverseBuffState();
            return ((IvNativeForgeRecipeAccessor)(Object)ForgeOfTheAntichrist.Companion).iv$copyAndModifyRecipe(
                    recipe, ContentModifier.multiplier(forge.getRecipeOutputMultiply()),
                    state.getMagnetorheologicalConvergenceActive());
        }
        if (machine instanceof ForgeOfTheAntichristModuleBase module
                && module.getHost() != null && module.getRecipeLogic().enableModify(recipe.recipeType))
            return ForgeOfTheAntichristModuleBase.Companion.copyAndModifyRecipe(recipe,
                    ContentModifier.multiplier(module.getHost().getRecipeOutputMultiply()));
        if (machine instanceof com.gtladd.gtladditions.common.machine.multiblock.controller.OreProcessorMachine) {
            var hatch = IvNativeCapacity.oreHatch(machine);
            return com.gtladd.gtladditions.utils.OreProcessorRecipeHelper.INSTANCE.copyForOreProcessor(recipe,
                    hatch == null ? 1 : hatch.getItemChanceBoost(), 1,
                    hatch == null ? 1 : hatch.getFluidMultiplier(), 1);
        }
        if (machine instanceof com.gtladd.gtladditions.common.machine.multiblock.controller.SpaceInfinityIntegratedOreProcessor) {
            var hatch = IvNativeCapacity.oreHatch(machine);
            return com.gtladd.gtladditions.utils.OreProcessorRecipeHelper.INSTANCE.copyForOreProcessor(recipe,
                    hatch == null ? 1 : hatch.getItemChanceBoost(), hatch != null && hatch.getMatchAll() ? 2 : 1, 1, 1);
        }
        return recipe;
    }

    /** Taixu rolls its generated UU outputs once after selection, never while sizing wants. */
    public static GTRecipe selected(WorkableMultiblockMachine machine, GTRecipe recipe) {
        return machine instanceof TaixuTurbidArray taixu
                ? ((IvNativeTaixuAccessor)(Object)TaixuTurbidArray.Companion).iv$outputRecipe(taixu, recipe)
                : prepare(machine, recipe);
    }

    public static GTRecipe scale(WorkableMultiblockMachine machine, GTRecipe recipe, long parallel) {
        if (machine instanceof ApocalypticTorsionQuantumMatrix && parallel > 1) {
            var scaled = com.gtladd.gtladditions.utils.RecipeCalculationHelper.INSTANCE.copyFixRecipe(
                    recipe, ContentModifier.multiplier(parallel), 10);
            org.gtlcore.gtlcore.api.recipe.IGTRecipe.of(scaled).setRealParallels(parallel);
            return scaled;
        }
        return com.gtladd.gtladditions.utils.RecipeCalculationHelper.INSTANCE.multipleRecipe(recipe, parallel);
    }

    public static IvNativeApocalypticAccessor apocalyptic() {
        return (IvNativeApocalypticAccessor)(Object)ApocalypticTorsionQuantumMatrixLogic.Companion;
    }

    /** Native Taixu batch energy is independent of the number of recipes and parallels. */
    public static java.math.BigInteger fixedBatchEnergy(WorkableMultiblockMachine machine) {
        if (!(machine instanceof TaixuTurbidArray taixu)) return null;
        var energy = java.math.BigInteger.valueOf(524288L)
                .multiply(java.math.BigInteger.valueOf(com.gregtechceu.gtceu.api.GTValues.V[taixu.getTier()]))
                .multiply(java.math.BigInteger.valueOf(100));
        return taixu.getRecipeLogic().isMultipleRecipeMode() ? energy.multiply(java.math.BigInteger.valueOf(16)) : energy;
    }
}
