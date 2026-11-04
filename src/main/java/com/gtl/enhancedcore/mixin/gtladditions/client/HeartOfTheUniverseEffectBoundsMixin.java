package com.gtl.enhancedcore.mixin.gtladditions.client;

import com.gtl.enhancedcore.common.structure.CelestialEffectSizing;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Mixin(targets = "com.gtladd.gtladditions.client.render.machine.heart.HeartDeferredRenderer$Entry", remap = false)
public abstract class HeartOfTheUniverseEffectBoundsMixin {
    @ModifyConstant(method = "isVisible", constant = @Constant(doubleValue = 40.25D), require = 1, allow = 1)
    private double enhanced$volumeBounds(double original) {
        return CelestialEffectSizing.HEART_CULL_RADIUS;
    }

    @ModifyConstant(method = "isVisible", constant = @Constant(doubleValue = 384D), require = 1, allow = 1)
    private double enhanced$viewDistance(double original) {
        return CelestialEffectSizing.VIEW_DISTANCE;
    }
}
