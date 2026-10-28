package com.gtl.enhancedcore.client.renderer;

public final class StellarVortexMotion {
    private StellarVortexMotion() {}

    public static double rotationAngle(double seconds) {
        return -seconds * Math.PI / 120;
    }
}
