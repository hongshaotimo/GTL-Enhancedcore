package com.gtl.enhancedcore.mixin.gtlcore.client;

import com.gtl.enhancedcore.client.preview.PreviewStyle;
import com.lowdragmc.lowdraglib.gui.texture.ResourceBorderTexture;
import net.minecraft.client.gui.GuiGraphics;
import org.gtlcore.gtlcore.client.preview.FullscreenPreviewScreen;
import org.gtlcore.gtlcore.client.preview.PreviewControls;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** The shipped fix3 Screen override is SRG-named; widgets, navigation and tooltips render natively. */
@Mixin(value = FullscreenPreviewScreen.class, remap = false)
public abstract class FullscreenPreviewAppearanceMixin {
    @Shadow @Final private PreviewControls controls;

    @Redirect(method = "m_88315_(Lnet/minecraft/client/gui/GuiGraphics;IIF)V",
            at = @At(value = "INVOKE", target =
                    "Lcom/lowdragmc/lowdraglib/gui/texture/ResourceBorderTexture;draw(Lnet/minecraft/client/gui/GuiGraphics;IIFFII)V"),
            require = 1, allow = 1)
    private void enhanced$drawFullscreenFrame(ResourceBorderTexture texture, GuiGraphics graphics,
                                               int mouseX, int mouseY, float x, float y, int width, int height) {
        PreviewStyle.drawFrame(graphics, (int) x, (int) y, width, height, controls.scene());
    }
}
