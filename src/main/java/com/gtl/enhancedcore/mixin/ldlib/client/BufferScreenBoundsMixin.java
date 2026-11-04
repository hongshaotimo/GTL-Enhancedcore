package com.gtl.enhancedcore.mixin.ldlib.client;

import com.gtl.enhancedcore.common.gui.BufferViewportWidget;
import com.gtl.enhancedcore.common.gui.PagedInventoryWidget;
import com.gtl.enhancedcore.common.cloud.ui.CloudPatternPanel;
import com.gregtechceu.gtceu.api.gui.fancy.ConfiguratorPanel;
import com.lowdragmc.lowdraglib.gui.widget.DraggableScrollableWidgetGroup;
import com.lowdragmc.lowdraglib.gui.modular.ModularUI;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import com.lowdragmc.lowdraglib.utils.Position;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Include Fancy's external title, tabs and configuration controls when placing buffer menus. */
@Mixin(value = ModularUI.class, remap = false)
public abstract class BufferScreenBoundsMixin {
    @Inject(method = "updateScreenSize", at = @At("RETURN"), require = 1)
    private void enhancedcore$fitBufferControls(int width, int height, CallbackInfo ci) {
        var ui = (ModularUI)(Object)this;
        if (ui.mainGroup.getWidgetsByType(BufferViewportWidget.class).isEmpty()
                && ui.mainGroup.getWidgetsByType(CloudPatternPanel.class).isEmpty()
                && ui.mainGroup.getWidgetsByType(PagedInventoryWidget.class).isEmpty()
                && ui.mainGroup.getWidgetsByType(WidgetGroup.class).stream()
                        .noneMatch(group -> "enhancedcore.circuit_encoder".equals(group.getId()))) return;
        int[] bounds = {Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};
        enhancedcore$visibleBounds(ui.mainGroup, bounds);
        int dx = bounds[0] < 2 ? 2 - bounds[0] : Math.min(0, width - 2 - bounds[2]);
        int dy = bounds[1] < 2 ? 2 - bounds[1] : Math.min(0, height - 2 - bounds[3]);
        ui.mainGroup.setParentPosition(new Position(ui.getGuiLeft() + dx, ui.getGuiTop() + dy));
    }

    @Unique private static void enhancedcore$visibleBounds(Widget widget, int[] bounds) {
        if (!widget.isVisible()) return;
        var position = widget.getPosition();
        // Native configurators retain a one-column size after their tabs wrap into columns.
        // The panel paints only its children, so its empty declared rectangle is not a screen extent.
        if (!(widget instanceof ConfiguratorPanel)) {
            bounds[0] = Math.min(bounds[0], position.x);
            bounds[1] = Math.min(bounds[1], position.y);
            bounds[2] = Math.max(bounds[2], position.x + widget.getSizeWidth());
            bounds[3] = Math.max(bounds[3], position.y + widget.getSizeHeight());
        }
        if (widget instanceof WidgetGroup group && !(widget instanceof DraggableScrollableWidgetGroup))
            for (var child : group.widgets) enhancedcore$visibleBounds(child, bounds);
    }
}
