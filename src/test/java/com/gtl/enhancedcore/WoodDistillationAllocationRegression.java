package com.gtl.enhancedcore;

import com.gtl.enhancedcore.common.recipe.WoodDistillationAllocation;
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
        return 3;
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
