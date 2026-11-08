package com.gtl.enhancedcore.mixin.gtladditions;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

/** Applies the common logical-side adapter after ordinary ADD widget mixins. */
@Pseudo
@Mixin(targets = "com.gtladd.gtladditions.api.gui.widget.SliderTextFieldWidget", remap = false, priority = 500)
public abstract class SliderTextFieldSideCompatibilityMixin {}
