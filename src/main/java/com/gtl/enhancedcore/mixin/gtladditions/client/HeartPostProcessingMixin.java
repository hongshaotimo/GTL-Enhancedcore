package com.gtl.enhancedcore.mixin.gtladditions.client;

import com.gtl.enhancedcore.client.renderer.celestial.CelestialVfxState;
import com.gtladd.gtladditions.client.render.machine.heart.HeartBlackHoleCompositeRenderer;
import com.gtladd.gtladditions.client.render.machine.heart.HeartBlackHoleRenderer;
import com.gtladd.gtladditions.client.render.machine.heart.PendingHeartComposite;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.List;
import net.minecraft.client.renderer.ShaderInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Scope the two real draws, not empty HUD events or shadow passes. */
@Mixin(value = HeartBlackHoleRenderer.class, remap = false)
public abstract class HeartPostProcessingMixin {
    @WrapMethod(method = "renderPostProcessed", remap = false)
    private void enhancedcore$worldPass(List<?> commands, PoseStack pose, ShaderInstance volume,
                                        ShaderInstance blur, Operation<Void> original) {
        try (var state = new CelestialVfxState()) {
            original.call(commands, pose, volume, blur);
        }
    }

    @WrapOperation(method = "renderPendingComposite", at = @At(value = "INVOKE", target =
            "Lcom/gtladd/gtladditions/client/render/machine/heart/HeartBlackHoleCompositeRenderer;renderPendingComposite(IILcom/gtladd/gtladditions/client/render/machine/heart/PendingHeartComposite;)V"),
            require = 1, allow = 1, remap = false)
    private static void enhancedcore$beforeHud(HeartBlackHoleCompositeRenderer renderer, int width, int height,
                                               PendingHeartComposite pending, Operation<Void> original) {
        try (var state = new CelestialVfxState()) {
            original.call(renderer, width, height, pending);
        }
    }
}
