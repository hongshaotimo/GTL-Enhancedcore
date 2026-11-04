package com.gtl.enhancedcore.mixin.jei;

import com.gtl.enhancedcore.client.preview.PreviewPreparation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Rebind JEI to the native material slots when a staged page becomes ready. */
@Mixin(targets = "mezz.jei.gui.recipes.RecipesGui", remap = false)
public abstract class PreviewPreparationLayoutMixin {
    @Invoker("updateLayout") protected abstract void enhanced$updatePreviewLayout();

    @Inject(method = "m_86600_", at = @At("TAIL"), require = 1, allow = 1)
    private void enhanced$completedPreview(CallbackInfo ci) {
        if (PreviewPreparation.consumeLayoutRefresh()) enhanced$updatePreviewLayout();
    }
}
