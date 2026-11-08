package com.gtl.enhancedcore.mixin.gtmadvancedhatch;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

/** Late marker for the guarded AH recipe block; target classes remain unloaded during discovery. */
@Pseudo
@Mixin(targets = {"com.xingmot.gtmadvancedhatch.common.data.recipe.AHRecipes",
        "com.xingmot.gtmadvancedhatch.integration.gtlcore.ExGTLRecipes"}, remap = false, priority = 500)
public abstract class AdvancedHatchLaserRecipeCompatibilityMixin {}
