package com.gtl.enhancedcore.mixin.gtlcore.client;

import com.gtl.enhancedcore.client.preview.LazyMultiblockPreviewWidget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.gtlcore.gtlcore.client.preview.PreviewControls;
import org.gtlcore.gtlcore.client.preview.PreviewHeaderBounds;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = PreviewControls.class, remap = false)
public abstract class PreviewFootprintInfoMixin {
    @Shadow @Final private WidgetGroup widget;
    @Shadow private boolean fullscreen;
    @Shadow private int width;
    @Shadow private Component heightText() { throw new AssertionError(); }
    @Shadow private Component dimensionsText() { throw new AssertionError(); }

    @Inject(method = "headerHeight", at = @At("RETURN"), cancellable = true)
    private void enhanced$includeFootprintHeight(CallbackInfoReturnable<Integer> cir) {
        if (!(widget.getParent() instanceof LazyMultiblockPreviewWidget wrapper)) return;
        String value = wrapper.footprintText();
        if (value == null || value.isBlank()) return;
        Font font = Minecraft.getInstance().font;
        cir.setReturnValue(cir.getReturnValue()
                + font.split(Component.literal(value), Math.max(20, width - 40)).size() * font.lineHeight);
    }

    @Inject(method = "drawInfo", at = @At("HEAD"), cancellable = true)
    private void gtlEnhancedcore$drawFootprintAboveHeight(GuiGraphics graphics, CallbackInfo ci) {
        if (!(widget.getParent() instanceof LazyMultiblockPreviewWidget wrapper)) return;
        String value = wrapper.footprintText();
        if (value == null || value.isBlank()) return;

        Font font = Minecraft.getInstance().font;
        int available = fullscreen ? Math.max(20, width - 40) : widget.getSizeWidth();
        List<FormattedCharSequence> lines = new ArrayList<>(font.split(Component.literal(value), available));
        lines.addAll(font.split(heightText(), available));
        if (fullscreen) lines.addAll(font.split(dimensionsText(), available));
        int y = fullscreen ? 20 : widget.getPositionY() - 5 - lines.size() * font.lineHeight;
        int center = fullscreen ? (width - 24) / 2
                : widget.getPositionX() + widget.getSizeWidth() / 2;

        if (!fullscreen && Minecraft.getInstance().screen instanceof PreviewHeaderBounds header) {
            float screenY = graphics.pose().last().pose()
                    .transformPosition(0, y, 0, new Vector3f()).y;
            if (screenY < header.gtlcore$headerBottom() + 5) {
                available = Math.max(20, available - 28);
                lines = new ArrayList<>(font.split(Component.literal(value), available));
                lines.addAll(font.split(heightText(), available));
                y = widget.getPositionY() + 16;
                center -= 12;
            }
        }

        for (FormattedCharSequence line : lines) {
            graphics.drawString(font, line, center - font.width(line) / 2, y, 0xFFE4E4E4, true);
            y += font.lineHeight;
        }
        ci.cancel();
    }
}
