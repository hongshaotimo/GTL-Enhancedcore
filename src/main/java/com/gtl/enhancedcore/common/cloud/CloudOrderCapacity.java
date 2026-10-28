package com.gtl.enhancedcore.common.cloud;

import java.util.Map;

/** Upper bound for a graph dispatch before long-valued input and operation counters are multiplied. */
public final class CloudOrderCapacity {
    private CloudOrderCapacity() {}

    public static long limit(long requested, long operationsPerPattern, Map<?, Long> inputPerPattern) {
        if (requested < 1 || operationsPerPattern < 1) return 0;
        long result = Math.min(requested, Long.MAX_VALUE / operationsPerPattern);
        for (long amount : inputPerPattern.values()) {
            if (amount < 1) return 0;
            result = Math.min(result, Long.MAX_VALUE / amount);
        }
        return result;
    }

    public static long refillAmount(long available, long perPattern, long batch) {
        if (available < 0 || perPattern < 1 || batch < 1) return 0;
        long whole = Math.min(batch, Long.MAX_VALUE / perPattern);
        return Math.max(0, whole * perPattern - available);
    }
}
