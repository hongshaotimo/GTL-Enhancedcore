package com.gtl.enhancedcore.mixin.gtlcore.client;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.network.chat.Component;
import org.gtlcore.gtlcore.client.preview.PreviewControls;
import org.gtlcore.gtlcore.client.preview.PreviewHeight;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Adds the current page's footprint without replacing native preview layout or drawing. */
@Mixin(value = PreviewControls.class, remap = false)
public abstract class PreviewFootprintInfoMixin {
    @Shadow public abstract PreviewHeight.Dimensions dimensions();

    @Inject(method = "heightText()Lnet/minecraft/network/chat/Component;",
            at = @At("RETURN"), cancellable = true, require = 1)
    private void enhanced$includeFootprint(CallbackInfoReturnable<Component> cir) {
        // Native compact rendering wraps this component; fullscreen also uses it in the info tooltip.
        cir.setReturnValue(cir.getReturnValue().copy().append("\n").append(enhanced$footprint()));
    }

    @ModifyExpressionValue(method = "drawInfo(Lnet/minecraft/client/gui/GuiGraphics;)V",
            at = @At(value = "FIELD",
                    target = "Lorg/gtlcore/gtlcore/client/preview/PreviewControls;title:Lnet/minecraft/network/chat/Component;"),
            require = 1)
    private Component enhanced$fullscreenFootprint(Component title) {
        // Show the footprint before a potentially long title; native clipping still clears the toolbar.
        return enhanced$footprint().copy().append(" · ").append(title);
    }

    @Unique
    private Component enhanced$footprint() {
        var size = dimensions();
        if (!size.known()) {
            return Component.translatable("gtl_enhancedcore.jei.footprint_unknown");
        }
        return Component.translatable("gtl_enhancedcore.jei.footprint",
                Math.floorDiv(size.width() - 1, 16) + 1,
                Math.floorDiv(size.depth() - 1, 16) + 1);
    }
}
