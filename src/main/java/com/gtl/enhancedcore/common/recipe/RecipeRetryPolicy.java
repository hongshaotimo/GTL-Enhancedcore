package com.gtl.enhancedcore.common.recipe;

public final class RecipeRetryPolicy {
    public static final int IDLE_RETRY_TICKS = 20;

    private RecipeRetryPolicy() {}

    public static boolean shouldTick(long offsetTimer, boolean idle, boolean hasRecipe, boolean dirty) {
        return !idle || hasRecipe || dirty || Math.floorMod(offsetTimer, IDLE_RETRY_TICKS) == 0;
    }
}
