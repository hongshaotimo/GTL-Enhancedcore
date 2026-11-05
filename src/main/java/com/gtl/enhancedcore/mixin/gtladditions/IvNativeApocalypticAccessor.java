package com.gtl.enhancedcore.mixin.gtladditions;

import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gtladd.gtladditions.common.machine.multiblock.controller.ApocalypticTorsionQuantumMatrix;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(value = ApocalypticTorsionQuantumMatrix.Companion.ApocalypticTorsionQuantumMatrixLogic.Companion.class, remap = false)
public interface IvNativeApocalypticAccessor {
    @Invoker("modifyChance") GTRecipe iv$modifyChance(GTRecipe recipe);
    @Invoker("applyOutputMultiplier") void iv$multiplyOutputs(Iterable<? extends GTRecipe> recipes, double multiplier);
}
