package com.gtl.enhancedcore.common.machine.hatch;

import java.util.function.IntPredicate;

public final class CellSlotScan {
    private CellSlotScan() {}

    public static int nextEmpty(int slotCount, int firstSlot, IntPredicate isEmpty) {
        for (int slot = Math.max(0, firstSlot); slot < slotCount; slot++) {
            if (isEmpty.test(slot)) return slot;
        }
        return -1;
    }
}
