package com.gtl.enhancedcore.client.renderer;

import com.mojang.blaze3d.vertex.BufferBuilder;

/** Render-thread scratch for immediate draws only: each caller submits before the next effect begins. */
public final class MachineEffectGeometry {
    private static final BufferBuilder VERTICES = new BufferBuilder(160 * 1024);

    private MachineEffectGeometry() {}

    public static BufferBuilder vertices() { return VERTICES; }
}
