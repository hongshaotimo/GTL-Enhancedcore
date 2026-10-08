package com.gtl.enhancedcore.client.ftbchunks;

import dev.ftb.mods.ftblibrary.math.XZ;

/**
 * 矩形框选的起始区块（拖拽起点）。
 */
public final class RectangleSelectionState {
    private static XZ firstChunk = null;

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
