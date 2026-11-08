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
    @Shadow @Final private com.lowdragmc.lowdraglib.gui.widget.ButtonWidget replaceableToggle;
    @Shadow private boolean fullscreen;
    @Shadow private int width;
    @Shadow private int screenHeight;

    @Inject(method = "<init>", at = @At("TAIL"), require = 1)
    private void enhanced$removeDuplicateHighlightControl(CallbackInfo ci) {
        widget.removeWidget(replaceableToggle);
    }

    @Inject(method = "layout", at = @At("TAIL"), require = 1)
    private void enhanced$layoutMaterialRows(CallbackInfo ci) {
        widget.removeWidget(replaceableToggle);
        if (!fullscreen || !(widget instanceof PreviewMaterialAccess.Rows rows)) return;
        rows.enhanced$layoutRows(width, screenHeight);
        scene.setSize(Math.max(1, width - 8), Math.max(1, screenHeight - scene.getSelfPosition().y - 54));
    }

    // 山海：a page created in fullscreen may have a stale native compact-geometry snapshot.
    @Inject(method = "exit", at = @At("TAIL"), require = 1)
    private void enhanced$restoreMaterialRows(CallbackInfo ci) {
        if (widget instanceof PreviewMaterialAccess.Rows rows) {
            rows.enhanced$restoreRows();
            scene.setSize(150, 104);
        }
    }
}
