package com.gtl.enhancedcore.mixin.gtlcore.client;

import com.gtl.enhancedcore.client.preview.PreviewStyle;
import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import org.gtlcore.gtlcore.api.gui.PatternPreviewWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.Supplier;

/** Retains the original dynamic labels and delegates compact JEI rendering unchanged. */
@Mixin(value = PatternPreviewWidget.class, remap = false)
public abstract class PreviewTextControlAppearanceMixin {
    @Inject(method = "controlTexture(Ljava/util/function/Supplier;)Lcom/lowdragmc/lowdraglib/gui/texture/IGuiTexture;",
            at = @At("RETURN"), cancellable = true, require = 1, allow = 1)
    private void enhanced$fullscreenTextButton(Supplier<String> label, CallbackInfoReturnable<IGuiTexture> cir) {
        var preview = (PatternPreviewWidget) (Object) this;
        cir.setReturnValue(PreviewStyle.textControl(cir.getReturnValue(), label, () -> {
            var controls = preview.getPreviewControls();
            return controls != null && controls.fullscreen();
        }));
    }
}
