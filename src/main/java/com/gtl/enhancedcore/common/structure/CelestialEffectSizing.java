package com.gtl.enhancedcore.common.structure;

/** Visual dimensions for the replacement blueprints; never used for machine IO or recipes. */
public final class CelestialEffectSizing {
    // The original inward-facing space mesh has radius 74 * .175.
    // At 2.75x the complete shell fits inside the reserved radius of 36 blocks.
    public static final float EYE_SCALE = 2.75F;
    public static final float ASTROGRAPH_SCALE = 2.75F;
    public static final float STAR_SCALE = 1.45F;
    public static final float EYE_ORBIT_SCALE = 1.2F;
    public static final float ASTROGRAPH_PLANET_SCALE = 1.15F;
    public static final double ASTROGRAPH_INNER_ORBIT = 3.2D;
    // Heart's shader uses radius * fade for its full envelope: 32 * 1.25 = 40.
    public static final float HEART_RADIUS = 32F;
    public static final float HEART_FADE_RADIUS = 1.25F;
    public static final double HEART_CULL_RADIUS = 44D;
    public static final int VIEW_DISTANCE = 512;

    private CelestialEffectSizing() {}
}
