package com.gtl.enhancedcore.common.recipe;

import java.util.Collection;
import java.util.function.ToLongBiFunction;

/** Gives each matching wood process one admission before allocating the shared ADD budget in bulk. */
public final class WoodDistillationAllocation {
    private WoodDistillationAllocation() {}

    /**
     * The callback performs the real admission and returns its consumed budget, or zero on failure.
     * At most two visits per candidate keep very large parallel hatches and Ω threads bounded.
     */
    public static <T> long allocate(Collection<T> candidates, long budget,
                                    ToLongBiFunction<T, Long> admit) {
        long remaining = Math.max(0, budget);
        for (int pass = 0; pass < 2 && remaining > 0; pass++) {
            for (T candidate : candidates) {
                if (remaining == 0) break;
                long limit = pass == 0 ? 1 : remaining;
                long consumed = admit.applyAsLong(candidate, limit);
                if (consumed < 0 || consumed > limit) {
                    throw new IllegalArgumentException("Wood admission exceeded its parallel allowance");
                }
                remaining -= consumed;
            }
        }
        return budget > 0 ? budget - remaining : 0;
    }
}
