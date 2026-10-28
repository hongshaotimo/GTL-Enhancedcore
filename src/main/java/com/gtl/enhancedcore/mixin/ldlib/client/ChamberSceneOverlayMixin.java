package com.gtl.enhancedcore.mixin.ldlib.client;

import com.lowdragmc.lowdraglib.gui.widget.SceneWidget;
import net.minecraft.client.renderer.MultiBufferSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Append drawing to the existing render method; never replace upstream callbacks. */
@Mixin(value = SceneWidget.class, remap = false)
public abstract class ChamberSceneOverlayMixin {
    @Inject(method = "renderBeforeBatchEnd", at = @At("TAIL"), require = 1)
    private void enhanced$draw(MultiBufferSource buffers, float partialTick, CallbackInfo ci) {
        if (!com.gtl.enhancedcore.client.preview.ChamberPreviewOverlay.contains((SceneWidget)(Object)this)) return;
        if (buffers instanceof MultiBufferSource.BufferSource source) source.endBatch();
        com.gtl.enhancedcore.client.preview.ChamberPreviewOverlay.render((SceneWidget)(Object)this);
    }
    @Inject(method = "releaseCacheBuffer", at = @At("HEAD"), require = 1)
    private void enhanced$clear(CallbackInfo ci) {
        com.gtl.enhancedcore.client.preview.ChamberPreviewOverlay.clear((SceneWidget)(Object)this);
    }
}
