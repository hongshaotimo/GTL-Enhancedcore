package com.gtl.enhancedcore.mixin.gtlcore.client;

import com.lowdragmc.lowdraglib.gui.widget.SceneWidget;
import com.lowdragmc.lowdraglib.gui.widget.ButtonWidget;
import com.lowdragmc.lowdraglib.gui.texture.GuiTextureGroup;
import com.lowdragmc.lowdraglib.gui.texture.TextTexture;
import com.lowdragmc.lowdraglib.gui.editor.ColorPattern;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "org.gtlcore.gtlcore.api.gui.PatternPreviewWidget", remap = false)
public abstract class ChamberPreviewWidgetMixin {
    @Shadow @org.spongepowered.asm.mixin.Final private SceneWidget sceneWidget;
    @org.spongepowered.asm.mixin.Unique private boolean enhanced$showChambers;
    @Inject(method = "<init>", at = @At("TAIL"), require = 1)
    private void enhanced$chamberToggle(com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition definition,
            CallbackInfo ci) {
        var label = new TextTexture("").setSupplier(() -> net.minecraft.network.chat.Component.translatable(
                enhanced$showChambers ? "gtl_enhancedcore.chambers.preview_on" : "gtl_enhancedcore.chambers.preview_off").getString());
        var widget = (org.gtlcore.gtlcore.api.gui.PatternPreviewWidget)(Object)this;
        // Native PreviewControls moves every ButtonWidget into this same column in fullscreen.
        // Match its geometry before it captures the original layout, including the optional module button.
        var all = widget.widgets.stream().filter(ButtonWidget.class::isInstance).map(ButtonWidget.class::cast)
                .filter(control -> control.getSelfPositionY() == 50).findFirst().orElseThrow();
        var last = widget.widgets.stream().filter(ButtonWidget.class::isInstance).map(ButtonWidget.class::cast)
                .filter(control -> control.getSelfPositionX() == all.getSelfPositionX()
                        && control.getSizeWidth() == all.getSizeWidth()
                        && control.getSizeHeight() == all.getSizeHeight())
                .max(java.util.Comparator.comparingInt(ButtonWidget::getSelfPositionY)).orElseThrow();
        int gap = all.getSelfPositionY() - 30 - all.getSizeHeight();
        var button = new ButtonWidget(last.getSelfPositionX(),
                last.getSelfPositionY() + last.getSizeHeight() + gap, last.getSizeWidth(), last.getSizeHeight(),
                new GuiTextureGroup(ColorPattern.T_GRAY.rectTexture(), label),
                data -> {
                    enhanced$showChambers = !enhanced$showChambers;
                    com.gtl.enhancedcore.client.preview.ChamberPreviewOverlay.setEnabled(sceneWidget, enhanced$showChambers);
                });
        button.setHoverBorderTexture(1, -1);
        button.setHoverTooltips(net.minecraft.network.chat.Component.translatable("gtl_enhancedcore.chambers.preview_toggle"));
        widget.addWidget(button);
    }
    @Inject(method = "setupScene", at = @At("TAIL"), require = 1)
    private void enhanced$chambers(@Coerce Object pattern, CallbackInfo ci) {
        com.gtl.enhancedcore.client.preview.ChamberPreviewOverlay.update(sceneWidget,
                (com.gtl.enhancedcore.client.preview.PreviewPatternMemory)pattern, enhanced$showChambers);
    }
}
