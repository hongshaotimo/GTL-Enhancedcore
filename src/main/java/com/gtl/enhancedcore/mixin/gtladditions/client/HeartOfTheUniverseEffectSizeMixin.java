package com.gtl.enhancedcore.mixin.gtladditions.client;

import com.gtl.enhancedcore.common.structure.CelestialEffectSizing;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/** The heart is deferred: its visual size comes from uniforms, not the controller's pose. */
@Mixin(targets = "com.gtladd.gtladditions.client.render.machine.heart.HeartBlackHoleRenderer$HeartVolumeUniforms",
        remap = false)
public abstract class HeartOfTheUniverseEffectSizeMixin {
    @ModifyConstant(method = "applyVolumePass", constant = @Constant(floatValue = 29F), require = 1, allow = 1)
    private float enhanced$blackHoleRadius(float original) {
        return CelestialEffectSizing.HEART_RADIUS;
    }
}
