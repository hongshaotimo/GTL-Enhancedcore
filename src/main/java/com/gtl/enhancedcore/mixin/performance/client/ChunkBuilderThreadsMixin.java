package com.gtl.enhancedcore.mixin.performance.client;

import com.gtl.enhancedcore.common.performance.ClientThreadSettings;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Pseudo
@Mixin(targets = "me.jellysquid.mods.sodium.client.render.chunk.compile.executor.ChunkBuilder", remap = false)
public abstract class ChunkBuilderThreadsMixin {
    @ModifyVariable(method = "<init>", at = @At("STORE"), ordinal = 0, require = 1)
    private int enhanced$automaticWorkers(int upstream) {
        return ClientThreadSettings.builderThreads(upstream);
    }
}
