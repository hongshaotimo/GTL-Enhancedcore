package com.gtl.enhancedcore.common.recipe;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.ToLongBiFunction;

/** Shares the available ADD budget equally before committing any shared wood inputs. */
public final class WoodDistillationAllocation {
    private WoodDistillationAllocation() {}

    /**
     * Capacity is probed before any input is committed. Shares are equal where possible; a recipe
     * with a lower capacity returns its unused share to the remaining candidates.
     */
    public static <T> long allocate(Collection<T> candidates, long budget,
                                    ToLongBiFunction<T, Long> capacity,
                                    ToLongBiFunction<T, Long> admit) {
        if (budget <= 0 || candidates.isEmpty()) return 0;
        var capacities = new LinkedHashMap<T, Long>();
        long available = 0;
        for (T candidate : candidates) {
            long maximum = capacity.applyAsLong(candidate, budget);
            if (maximum < 0 || maximum > budget)
                throw new IllegalArgumentException("Wood capacity exceeded its parallel allowance");
            capacities.put(candidate, maximum);
            available = Math.min(budget, available + maximum);
        }
        if (available == 0) return 0;
        var plan = shares(capacities, available);
        long consumed = 0;
        for (var entry : plan.entrySet()) {
            long limit = entry.getValue();
            if (limit == 0) continue;
            long accepted = admit.applyAsLong(entry.getKey(), limit);
            if (accepted < 0 || accepted > limit)
                throw new IllegalArgumentException("Wood admission exceeded its parallel allowance");
            consumed += accepted;
        }
        return consumed;
    }

    /** Compatibility overload for callers that do not expose a per-recipe capacity probe. */
    public static <T> long allocate(Collection<T> candidates, long budget,
                                    ToLongBiFunction<T, Long> admit) {
        return allocate(candidates, budget, (candidate, limit) -> limit, admit);
    }

    private static <T> Map<T, Long> shares(Map<T, Long> capacities, long budget) {
        var plan = new LinkedHashMap<T, Long>();
        capacities.keySet().forEach(key -> plan.put(key, 0L));
        long left = budget;
        while (left > 0) {
            boolean moved = false;
            for (var entry : capacities.entrySet()) {
                long current = plan.get(entry.getKey());
                if (current < entry.getValue()) {
                    plan.put(entry.getKey(), current + 1);
                    left--;
                    moved = true;
                    if (left == 0) break;
                }
            }
            if (!moved) break;
        }
        return plan;
    }
}
