package com.gtl.enhancedcore.audit.mixin;

import com.gtl.enhancedcore.client.renderer.StellarForgeRenderer;
import com.gtl.enhancedcore.audit.StellarForgeClientChecks;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = StellarForgeRenderer.class, remap = false)
public abstract class StellarForgeRenderAuditMixin {
    @Inject(method = "render", at = @At("RETURN"))
    private static void audit$draw(Matrix4f frame, double seconds, double distance, CallbackInfo ci) {
        StellarForgeClientChecks.draws++;
    }
}
