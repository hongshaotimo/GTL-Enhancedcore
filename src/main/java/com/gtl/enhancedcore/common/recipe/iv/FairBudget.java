package com.gtl.enhancedcore.common.recipe.iv;

import java.math.BigInteger;

/** Long arithmetic water filling, used for both parallel reservations and the one shared EU budget. */
public final class FairBudget {
    private FairBudget() {}
    /** Wide network balance; individual recipe capability requests still fit in a long. */
    public static long[] divide(BigInteger budget, long[] wants, int offset) {
        if (budget.signum() < 0) throw new IllegalArgumentException("negative budget");
        long[] result = new long[wants.length];
        for (long want : wants) if (want < 0) throw new IllegalArgumentException("negative request");
        while (budget.signum() > 0) {
            int active = 0;
            for (int i = 0; i < wants.length; i++) if (result[i] < wants[i]) active++;
            if (active == 0) break;
            long share = budget.divide(BigInteger.valueOf(active)).max(BigInteger.ONE)
                    .min(BigInteger.valueOf(Long.MAX_VALUE)).longValueExact();
            for (int step = 0; step < wants.length && budget.signum() > 0; step++) {
                int i = Math.floorMod((long)offset + step, wants.length);
                long give = budget.min(BigInteger.valueOf(Math.min(share, wants[i] - result[i]))).longValueExact();
                result[i] += give; budget = budget.subtract(BigInteger.valueOf(give));
            }
        }
        return result;
    }
    public static long[] divide(long budget, long[] wants, int offset) {
        long[] result = new long[wants.length];
        if (budget < 0) throw new IllegalArgumentException("negative budget");
        for (long want : wants) if (want < 0) throw new IllegalArgumentException("negative request");
        while (budget > 0) {
            int active = 0;
            for (int i = 0; i < wants.length; i++) if (result[i] < wants[i]) active++;
            if (active == 0) break;
            long share = Math.max(1, budget / active);
            for (int step = 0; step < wants.length && budget > 0; step++) {
                int i = Math.floorMod((long)offset + step, wants.length);
                long give = Math.min(budget, Math.min(share, wants[i] - result[i]));
                result[i] += give; budget -= give;
            }
        }
        return result;
    }
}
