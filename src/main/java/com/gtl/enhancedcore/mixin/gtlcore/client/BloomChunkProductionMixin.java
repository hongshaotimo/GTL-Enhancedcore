package com.gtl.enhancedcore.mixin.gtlcore.client;

import org.gtlcore.gtlcore.client.bloom.BloomConfig;
import org.gtlcore.gtlcore.config.BloomOptions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Avoid allocating the chunk-load runnable while explicit OFF suspends local meshes. */
@Mixin(targets = "org.gtlcore.gtlcore.client.bloom.BloomClient", remap = false)
public abstract class BloomChunkProductionMixin {
    @Inject(method = "chunkLoad(Lnet/minecraftforge/event/level/ChunkEvent$Load;)V",
            at = @At("HEAD"), cancellable = true, require = 1, allow = 1, remap = false)
    private static void enhancedcore$skipOffChunkLoad(CallbackInfo ci) {
        if (BloomConfig.mode() == BloomOptions.Mode.OFF) ci.cancel();
    }
}
