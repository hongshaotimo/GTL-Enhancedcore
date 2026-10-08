package com.gtl.enhancedcore.client.renderer;

/** Bounded, continuous nine-second industrial cycle; no random flashing. */
public final class StellarForgeCycle {
    public static final float PERIOD = 9;
    private StellarForgeCycle() {}
    public static float phase(double seconds) { return (float)(seconds - Math.floor(seconds / PERIOD) * PERIOD); }
    public static float smooth(float a, float b, float x) {
        float t = Math.max(0, Math.min(1, (x-a)/(b-a)));
        return t*t*(3-2*t);
    }
    public static float pressure(float phase) {
        return smooth(2.6F,5.8F,phase) * (1-smooth(6.3F,8.8F,phase));
    }
    public static float pulse(float phase) {
        return smooth(5.72F,5.96F,phase) * (1-smooth(6.12F,6.5F,phase));
    }
    public static float radius(float phase) { return 11.8F - .65F*pressure(phase); }
    public static float wave(float phase, float delay) {
        return smooth(6.18F+delay,6.30F+delay,phase) * (1-smooth(6.53F+delay,6.83F+delay,phase));
    }
    public static float visibility(double distance) { return 1-smooth(280,320,(float)distance); }
    public static int stride(double distance) { return distance < 100 ? 1 : distance < 190 ? 2 : 4; }
}
