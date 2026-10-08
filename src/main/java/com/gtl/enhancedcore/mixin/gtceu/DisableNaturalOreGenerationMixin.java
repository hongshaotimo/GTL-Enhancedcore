package com.gtl.enhancedcore.mixin.gtceu;

import com.gregtechceu.gtceu.api.data.worldgen.ores.OrePlacer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Skyblock pack: skip natural veins and indicators, not ore definitions or recipes. */
@Mixin(value = OrePlacer.class, remap = false)
public abstract class DisableNaturalOreGenerationMixin {
    @Inject(
            method = "placeOres(Lnet/minecraft/world/level/WorldGenLevel;"
                    + "Lnet/minecraft/world/level/chunk/ChunkGenerator;"
                    + "Lnet/minecraft/world/level/chunk/ChunkAccess;)V",
            at = @At("HEAD"), cancellable = true, require = 1, remap = false)
    private void gtl_enhancedcore$skipNaturalOres(CallbackInfo ci) {
        // Cancel before cache consumption and noise evaluation, not only block placement.
        ci.cancel();
    }
}
