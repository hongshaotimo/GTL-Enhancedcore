package com.gtl.enhancedcore.mixin.gtlcore.client;

import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gtl.enhancedcore.client.preview.PreviewStyle;
import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib.gui.widget.ButtonWidget;
import com.lowdragmc.lowdraglib.gui.widget.DraggableScrollableWidgetGroup;
import org.gtlcore.gtlcore.client.preview.PreviewCandidatePanel;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** This panel exists only in fullscreen; predicate grouping, slots and selection remain native. */
@Mixin(value = PreviewCandidatePanel.class, remap = false)
public abstract class PreviewCandidateAppearanceMixin {
    @Shadow @Final private DraggableScrollableWidgetGroup content;
    @Shadow @Final private ButtonWidget close;

    @Inject(method = "<init>", at = @At("TAIL"), require = 1, allow = 1)
    private void enhanced$lightCandidateFrame(CallbackInfo ci) {
        ((PreviewCandidatePanel) (Object) this).setBackground(PreviewStyle.PANEL);
        close.setButtonTexture(PreviewStyle.iconButton(GuiTextures.CLOSE_ICON));
        close.setHoverTexture(IGuiTexture.EMPTY);
        content.setYBarStyle(PreviewStyle.SCROLL_TRACK, PreviewStyle.SCROLL_THUMB);
    }

    @Inject(method = "rebuild()V", at = @At("TAIL"), require = 1, allow = 1)
    private void enhanced$quietCandidateGroups(CallbackInfo ci) {
        for (var group : content.widgets) group.setBackground(PreviewStyle.CANDIDATE_GROUP);
    }
}
