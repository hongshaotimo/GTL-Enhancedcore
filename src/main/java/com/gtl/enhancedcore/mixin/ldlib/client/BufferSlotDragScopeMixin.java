package com.gtl.enhancedcore.mixin.ldlib.client;

import com.gtl.enhancedcore.common.gui.BufferViewportWidget;
import com.lowdragmc.lowdraglib.gui.modular.ModularUIGuiContainer;
import com.lowdragmc.lowdraglib.gui.widget.SlotWidget;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Match LDLib's click/release slot scope while dragging over a clipped buffer menu. */
@Mixin(value = SlotWidget.class, remap = false)
public abstract class BufferSlotDragScopeMixin {
    @Shadow protected static Slot HOVER_SLOT;
    @Redirect(method = "mouseDragged", at = @At(value = "INVOKE",
            target = "Lcom/lowdragmc/lowdraglib/gui/modular/ModularUIGuiContainer;superMouseDragged(DDIDD)V"), require = 1)
    private void enhancedcore$scopeBufferDrag(ModularUIGuiContainer gui, double x, double y,
                                              int button, double dx, double dy) {
        if (gui.modularUI.mainGroup.getWidgetsByType(BufferViewportWidget.class).isEmpty()) {
            gui.superMouseDragged(x, y, button, dx, dy);
            return;
        }
        var previous = HOVER_SLOT;
        HOVER_SLOT = ((SlotWidget)(Object)this).getHandler();
        try {
            gui.superMouseDragged(x, y, button, dx, dy);
        } finally {
            HOVER_SLOT = previous;
        }
    }
}
