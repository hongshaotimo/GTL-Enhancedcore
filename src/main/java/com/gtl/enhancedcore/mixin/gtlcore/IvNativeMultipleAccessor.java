package com.gtl.enhancedcore.mixin.gtlcore;

import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import org.gtlcore.gtlcore.common.machine.trait.MultipleRecipesLogic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(value = MultipleRecipesLogic.class, remap = false)
public interface IvNativeMultipleAccessor {
    @Invoker("getTotalEuOfRecipe") double iv$totalEu(GTRecipe recipe);
    @Invoker("getEuMultiplier") double iv$euMultiplier();
}
