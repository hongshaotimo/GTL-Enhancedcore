package com.gtl.enhancedcore.client.pattern;

import java.util.Arrays;
import java.util.function.IntPredicate;

/** Maps a compact client display back to unchanged provider inventory indices. */
public final class PatternTerminalSlotProjection {
    private PatternTerminalSlotProjection() {}

    public static int[] occupiedSlots(int size, IntPredicate occupied) {
        int[] indices = new int[size];
        int count = 0;
        for (int slot = 0; slot < size; slot++) {
            if (occupied.test(slot)) indices[count++] = slot;
        }
        return Arrays.copyOf(indices, count);
    }
}
