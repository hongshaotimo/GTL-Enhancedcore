package com.gtl.enhancedcore.mixin.gtlcore.client;

import net.minecraft.client.gui.GuiGraphics;
import org.gtlcore.gtlcore.client.preview.PreviewControls;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = PreviewControls.class, remap = false)
public abstract class PreviewControlsBehaviorMixin {
    @Shadow private boolean fullscreen;
    @Shadow private boolean showCandidates;

    @Inject(method = "enter", at = @At("HEAD"), require = 1)
    private void enhanced$startWithCandidatesClosed(int width, int height, CallbackInfo ci) {
        // enter also runs during resizing; keep an already-open panel in that case.
        if (!fullscreen) showCandidates = false;
    }

    @Inject(method = "drawRotationOrigin", at = @At("HEAD"), cancellable = true, require = 1, allow = 1)
    private void enhanced$hideRotationOrigin(GuiGraphics graphics, CallbackInfo ci) {
        ci.cancel();
    }
}
