package com.gtl.enhancedcore.mixin.gtlcore.client;

import com.gtl.enhancedcore.client.preview.PreviewMaterialAccess;
import com.lowdragmc.lowdraglib.gui.widget.SceneWidget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import org.gtlcore.gtlcore.client.preview.PreviewControls;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = PreviewControls.class, remap = false)
public abstract class PreviewMaterialLayoutMixin {
    @Shadow @Final private WidgetGroup widget;
    @Shadow @Final private SceneWidget scene;
    @Shadow private boolean fullscreen;
    @Shadow private int width;
    @Shadow private int screenHeight;

    @Inject(method = "layout", at = @At("TAIL"), require = 1)
    private void enhanced$layoutMaterialRows(CallbackInfo ci) {
        if (!fullscreen || !(widget instanceof PreviewMaterialAccess.Rows rows)) return;
        rows.enhanced$layoutRows(width, screenHeight);
        scene.setSize(Math.max(1, width - 8), Math.max(1, screenHeight - scene.getSelfPosition().y - 54));
    }
}
