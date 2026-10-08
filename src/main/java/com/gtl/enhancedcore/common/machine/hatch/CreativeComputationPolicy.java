package com.gtl.enhancedcore.common.machine.hatch;

import java.util.List;

/** Arithmetic shared by the creative receiver's recipe and optical-provider paths. */
public final class CreativeComputationPolicy {

    public static final int MAX_CWU_PER_TICK = 2_100_000_000;

    private CreativeComputationPolicy() {}

    public static int suppliedCWUt(int requested, boolean attachedToFormedMultiblock) {
        return attachedToFormedMultiblock ? Math.min(Math.max(requested, 0), MAX_CWU_PER_TICK) : 0;
    }

    /** Returns -1 for an invalid negative amount; uses long so multiple entries cannot wrap. */
    public static long requiredCWUt(List<Integer> left) {
        long required = 0;
        for (int amount : left) {
            if (amount < 0) return -1;
            required += amount;
        }
        return required;
    }

    public static int progressBeforeTickIncrement(int current, int duration, int supplied) {
        // GTCEu adds its normal +1 after the hatch returns. Leave room for that increment.
        long advanced = (long) current - 1 + Math.max(supplied, 0);
        long beforeNextTick = Math.max((long) duration - 1, 0);
        return (int) Math.max(0, Math.min(beforeNextTick, advanced));
    }
}
