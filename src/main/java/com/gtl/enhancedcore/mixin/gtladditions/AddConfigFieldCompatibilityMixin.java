package com.gtl.enhancedcore.mixin.gtladditions;

import org.spongepowered.asm.mixin.Mixin;

/** Runs after ADD's ordinary class mixins without resolving any target through a class literal. */
@Mixin(targets = {
        "com.gtladd.gtladditions.common.machine.multiblock.MultiBlockMachine",
        "com.gtladd.gtladditions.common.modify.RecipesModify",
        "com.gtladd.gtladditions.common.modify.SkyTearsAndGregHeart",
        "com.gtladd.gtladditions.data.recipes.IntegratedOreProcessor",
        "com.gtladd.gtladditions.data.recipes.Misc",
        "com.gtladd.gtladditions.data.recipes.NewMultiBlockMachineController",
        "com.gtladd.gtladditions.utils.ThreadMultiplierStrategy"
}, remap = false, priority = 500)
public abstract class AddConfigFieldCompatibilityMixin {}
