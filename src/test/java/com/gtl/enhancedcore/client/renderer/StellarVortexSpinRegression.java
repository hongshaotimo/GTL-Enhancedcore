package com.gtl.enhancedcore.client.renderer;

public final class StellarVortexSpinRegression {
    private StellarVortexSpinRegression() {}

    public static int run() {
        double angle = StellarVortexMotion.rotationAngle(1);
        double z = Math.sin(angle);
        if (!(z < 0)) throw new AssertionError("The stellar disk must rotate opposite to the previous direction");
        return 1;
    }
}
