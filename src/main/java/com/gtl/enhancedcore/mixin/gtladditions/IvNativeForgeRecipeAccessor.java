package com.gtl.enhancedcore.mixin.gtladditions;

import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.content.ContentModifier;
import com.gtladd.gtladditions.common.machine.multiblock.controller.ForgeOfTheAntichrist;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(value = ForgeOfTheAntichrist.Companion.class, remap = false)
public interface IvNativeForgeRecipeAccessor {
    @Invoker("copyAndModifyRecipe") GTRecipe iv$copyAndModifyRecipe(
            GTRecipe recipe, ContentModifier modifier, boolean focusFluid);
}
