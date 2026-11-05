package com.gtl.enhancedcore;

import com.gtl.enhancedcore.common.recipe.WoodDistillationAllocation;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Regression for fair cross-recipe wood allocation. */
final class WoodDistillationAllocationRegression {
    static int run() {
        var recipes = List.of("nitrogen", "steam", "water");
        var capacities = Map.of("nitrogen", 15L, "steam", 15L, "water", 15L);
        var admitted = new java.util.LinkedHashMap<String, Long>();
        long used = WoodDistillationAllocation.allocate(recipes, 15,
                (recipe, limit) -> Math.min(limit, capacities.get(recipe)),
                (recipe, limit) -> { admitted.put(recipe, limit); return limit; });
        check(used == 15, "all shared budget is admitted");
        check(admitted.equals(Map.of("nitrogen", 5L, "steam", 5L, "water", 5L)),
                "three wood recipes receive an equal share instead of first recipe starvation");

        admitted.clear();
        used = WoodDistillationAllocation.allocate(recipes, 16,
                (recipe, limit) -> capacities.get(recipe),
                (recipe, limit) -> { admitted.put(recipe, limit); return limit; });
        check(used == 16 && admitted.equals(Map.of("nitrogen", 6L, "steam", 5L, "water", 5L)),
                "one indivisible remainder is stable and does not starve a recipe");
        int assertions = 3;
        // Exhaustively compare against the old per-parallel allocator, including exhausted
        // recipes, uneven capacities, empty budgets and stable incomplete rounds.
        for (int count = 1; count <= 4; count++) {
            int combinations = (int) Math.pow(5, count);
            for (int combination = 0; combination < combinations; combination++) {
                var limits = new LinkedHashMap<Integer, Long>();
                int digits = combination;
                for (int index = 0; index < count; index++) {
                    limits.put(index, (long) (digits % 5));
                    digits /= 5;
                }
                for (long budget = 0; budget <= count * 4L + 2; budget++) {
                    var actual = new LinkedHashMap<Integer, Long>();
                    long actualUsed = WoodDistillationAllocation.allocate(limits.keySet(), budget,
                            (recipe, allowance) -> Math.min(allowance, limits.get(recipe)),
                            (recipe, allowance) -> { actual.put(recipe, allowance); return allowance; });
                    var expected = reference(limits, budget);
                    check(actual.equals(expected) && new ArrayList<>(actual.keySet()).equals(new ArrayList<>(expected.keySet()))
                                    && actualUsed == expected.values().stream().mapToLong(Long::longValue).sum(),
                            "arithmetic shares preserve old round-robin results and admission order");
                    assertions++;
                }
            }
        }
        for (long budget : new long[]{100_000, 1_500_000, Integer.MAX_VALUE,
                (long) Integer.MAX_VALUE * 15, Long.MAX_VALUE}) {
            admitted.clear();
            int[] calls = new int[2];
            used = WoodDistillationAllocation.allocate(recipes, budget,
                    (recipe, allowance) -> { calls[0]++; return allowance; },
                    (recipe, allowance) -> { calls[1]++; admitted.put(recipe, allowance); return allowance; });
            long base = budget / recipes.size();
            long remainder = budget % recipes.size();
            check(used == budget && calls[0] == 3 && calls[1] == 3,
                    "large budgets are conserved with one capacity probe and admission per recipe");
            for (int index = 0; index < recipes.size(); index++)
                check(admitted.get(recipes.get(index)) == base + (index < remainder ? 1 : 0),
                        "large budget shares preserve exact integer remainder order");
            assertions += 4;
        }
        admitted.clear();
        used = WoodDistillationAllocation.allocate(recipes, Long.MAX_VALUE,
                (recipe, allowance) -> recipe.equals("nitrogen") ? 1 : allowance,
                (recipe, allowance) -> { admitted.put(recipe, allowance); return allowance; });
        check(used == Long.MAX_VALUE && admitted.equals(Map.of("nitrogen", 1L,
                        "steam", (Long.MAX_VALUE - 1) / 2, "water", (Long.MAX_VALUE - 1) / 2)),
                "exhausted recipe shares redistribute at the long boundary without overflow");
        assertions++;
        admitted.clear();
        used = WoodDistillationAllocation.allocate(recipes, 15,
                (recipe, allowance) -> allowance,
                (recipe, allowance) -> { admitted.put(recipe, allowance); return recipe.equals("nitrogen") ? 0 : allowance; });
        check(used == 10 && admitted.equals(Map.of("nitrogen", 5L, "steam", 5L, "water", 5L)),
                "failed physical admission does not reassign an already planned material share");
        return assertions + 1;
    }

    private static Map<Integer, Long> reference(Map<Integer, Long> capacities, long budget) {
        var plan = new LinkedHashMap<Integer, Long>();
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
        plan.values().removeIf(value -> value == 0);
        return plan;
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
