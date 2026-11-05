package com.gtl.enhancedcore.mixin.gtlcore.client;

import com.gtl.enhancedcore.client.preview.PreviewMaterialAccess;
import com.gtl.enhancedcore.client.preview.PreviewMaterialRows;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.lowdragmc.lowdraglib.gui.util.ClickData;
import com.lowdragmc.lowdraglib.gui.widget.DraggableScrollableWidgetGroup;
import com.lowdragmc.lowdraglib.gui.widget.DraggableScrollableWidgetGroup.ScrollWheelDirection;
import com.lowdragmc.lowdraglib.gui.widget.SceneWidget;
import com.lowdragmc.lowdraglib.gui.widget.SlotWidget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import java.util.List;
import org.gtlcore.gtlcore.api.gui.PatternPreviewWidget;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = PatternPreviewWidget.class, remap = false)
public abstract class PreviewMaterialRowsMixin {
    @Shadow private DraggableScrollableWidgetGroup scrollableWidgetGroup;
    @Shadow @Final private List<SlotWidget> slotWidgets;
    @Unique private PreviewMaterialAccess enhanced$materialPattern;
    @Unique private DraggableScrollableWidgetGroup enhanced$candidateRow;

    @Inject(method = "setupScene", at = @At("HEAD"), require = 1)
    private void enhanced$capturePattern(@Coerce Object pattern, CallbackInfo ci) {
        enhanced$materialPattern = (PreviewMaterialAccess) pattern;
    }

    @Inject(method = "setPage", at = @At("TAIL"), require = 1)
    private void enhanced$materialRows(int index, ClickData click, CallbackInfo ci) {
        if (enhanced$materialPattern == null) return;
        var widget = (WidgetGroup) (Object) this;
        if (enhanced$candidateRow != null) widget.removeWidget(enhanced$candidateRow);
        enhanced$candidateRow = new DraggableScrollableWidgetGroup(3, 134, 154, 22);
        enhanced$candidateRow.setXScrollBarHeight(4);
        enhanced$candidateRow.setXBarStyle(GuiTextures.SLIDER_BACKGROUND, GuiTextures.BUTTON);
        enhanced$candidateRow.setScrollable(true);
        enhanced$candidateRow.setDraggable(false);
        enhanced$candidateRow.setScrollWheelDirection(ScrollWheelDirection.HORIZONTAL);
        widget.addWidget(enhanced$candidateRow);
        scrollableWidgetGroup.setSelfPosition(3, 110);
        scrollableWidgetGroup.setDraggable(false);
        PreviewMaterialRows.populate(enhanced$materialPattern, scrollableWidgetGroup, enhanced$candidateRow, slotWidgets);
        var preview = (PatternPreviewWidget) (Object) this;
        var controls = preview.getPreviewControls();
        if (controls == null || !controls.fullscreen()) {
            widget.widgets.stream().filter(SceneWidget.class::isInstance).forEach(scene -> scene.setSize(150, 104));
        }
        if (controls != null) controls.layout();
    }
}
