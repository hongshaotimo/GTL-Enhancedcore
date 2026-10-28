package com.gtl.enhancedcore.client.ftbchunks;

import dev.ftb.mods.ftblibrary.math.XZ;

/** Start chunk for the existing draw-driven rectangle selector. */
public final class RectangleSelectionState {
    private static XZ firstChunk;

    private RectangleSelectionState() {
    }

    public static XZ getFirstChunk() {
        return firstChunk;
    }

    public static void setFirstChunk(XZ pos) {
        firstChunk = pos;
    }

    public static void reset() {
        firstChunk = null;
    }
}
