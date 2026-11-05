package com.gtl.enhancedcore.mixin.gtladditions;

import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gtladd.gtladditions.common.machine.multiblock.controller.TaixuTurbidArray;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(value = TaixuTurbidArray.Companion.class, remap = false)
public interface IvNativeTaixuAccessor {
    @Invoker("outputWithoutEutModifier") GTRecipe iv$outputRecipe(TaixuTurbidArray machine, GTRecipe recipe);
}
