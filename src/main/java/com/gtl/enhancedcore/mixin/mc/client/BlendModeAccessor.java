package com.gtl.enhancedcore.mixin.mc.client;

import com.mojang.blaze3d.shaders.BlendMode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Vanilla caches the shader blend mode separately from the actual GL factors. */
@Mixin(BlendMode.class)
public interface BlendModeAccessor {
    @Accessor("lastApplied")
    static BlendMode enhancedcore$lastApplied() { throw new AssertionError(); }
    @Accessor("lastApplied")
    static void enhancedcore$lastApplied(BlendMode mode) { throw new AssertionError(); }
}
