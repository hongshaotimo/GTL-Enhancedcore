package com.gtl.enhancedcore.common.performance;

/** Instance-local negative search result. Inventory notifications do not invalidate its short lifetime. */
public final class IdleRecipeSearchState {
    public static final int RETRY_TICKS = 20;
    private boolean failed;
    private long failedAt;
    private Object recipeType;
    private Object lockedRecipe;
    private boolean locked;

    public boolean shouldSearch(long now, boolean idle, boolean hasRecipe, boolean dirty,
            Object currentType, boolean currentLocked, Object currentLockedRecipe) {
        if (recipeType != currentType || locked != currentLocked || lockedRecipe != currentLockedRecipe) {
            invalidate();
            recipeType = currentType;
            locked = currentLocked;
            lockedRecipe = currentLockedRecipe;
        }
        if (!idle || hasRecipe || dirty) {
            invalidate();
            return true;
        }
        if (!failed) return true;
        long elapsed = now - failedAt;
        if (elapsed < 0 || elapsed >= RETRY_TICKS) {
            invalidate();
            return true;
        }
        return false;
    }

    /** Called only after the real search returned; successful or paid batches never become negative results. */
    public void searched(long now, boolean idleWithoutRecipe) {
        failed = idleWithoutRecipe;
        failedAt = now;
    }

    public void invalidate() {
        failed = false;
    }
}
