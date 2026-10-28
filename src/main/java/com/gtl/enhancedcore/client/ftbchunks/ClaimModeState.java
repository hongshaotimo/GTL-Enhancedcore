package com.gtl.enhancedcore.client.ftbchunks;

/** Current claim selection mode. */
public final class ClaimModeState {
    private static ClaimMode current = ClaimMode.RECTANGLE;

    private ClaimModeState() {
    }

    public static ClaimMode current() {
        return current;
    }

    public static ClaimMode cycle() {
        current = current.next();
        return current;
    }

    public static void set(ClaimMode mode) {
        current = mode;
    }
}
