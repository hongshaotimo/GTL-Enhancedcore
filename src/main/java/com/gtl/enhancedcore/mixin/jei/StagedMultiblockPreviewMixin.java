package com.gtl.enhancedcore.mixin.jei;

import org.spongepowered.asm.mixin.Mixin;

/** Apply the checked private-type bridges after GTLCore and the ordinary preview Mixins. */
@Mixin(targets = "org.gtlcore.gtlcore.api.gui.PatternPreviewWidget", remap = false, priority = 100)
public abstract class StagedMultiblockPreviewMixin {}
