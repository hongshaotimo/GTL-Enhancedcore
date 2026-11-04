package com.gtl.enhancedcore.mixin.gtlcore.client;

import com.gtl.enhancedcore.common.structure.CelestialEffectSizing;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(targets = {
        "org.gtlcore.gtlcore.client.renderer.machine.EyeOfHarmonyRenderer",
        "com.gtladd.gtladditions.client.render.machine.ArcanicAstrographRender",
        "com.gtladd.gtladditions.client.render.machine.HeartOfTheUniverseRenderer"
}, remap = false)
public abstract class CelestialEffectViewDistanceMixin {
    @ModifyReturnValue(method = "getViewDistance", at = @At("RETURN"), require = 1, allow = 1)
    private int enhanced$blueprintViewDistance(int original) {
        return Math.max(original, CelestialEffectSizing.VIEW_DISTANCE);
    }
}
