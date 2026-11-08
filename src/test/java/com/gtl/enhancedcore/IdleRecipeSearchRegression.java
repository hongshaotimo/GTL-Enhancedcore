package com.gtl.enhancedcore;

import com.gtl.enhancedcore.common.performance.IdleRecipeSearchState;

final class IdleRecipeSearchRegression {
    private static int assertions;

    static int run() {
        var state = new IdleRecipeSearchState();
        Object type = new Object();
        int searches = 0;
        for (long tick = 0; tick < 1200; tick++) {
            // Repeated inventory subscription notifications intentionally leave the state alone.
            for (int notification = 0; notification < 8; notification++) {
                if (state.shouldSearch(tick, true, false, false, type, false, null)) {
                    searches++;
                    state.searched(tick, true);
                }
            }
        }
        check(searches == 60, "Empty searches stay bounded despite repeated same-tick notification noise");
        check(!state.shouldSearch(1199, true, false, false, type, false, null), "The final cached failure remains valid");
        check(state.shouldSearch(1200, true, false, false, type, false, null), "A failed result expires at exactly twenty ticks");
        state.searched(1200, true);
        check(state.shouldSearch(1190, true, false, false, type, false, null), "Clock rollback permits a fresh search");
        state.searched(1190, true);
        check(state.shouldSearch(1191, true, false, true, type, false, null), "Explicit dirty state bypasses a cached failure");
        state.searched(1191, true);
        state.invalidate();
        check(state.shouldSearch(1192, true, false, false, type, false, null), "Lifecycle and explicit invalidation wake immediately");
        state.searched(1192, true);
        check(state.shouldSearch(1193, false, true, false, type, false, null), "Running batches bypass throttling");
        state.searched(1193, true);
        check(state.shouldSearch(1194, true, true, false, type, false, null), "An idle paid batch is preserved");
        state.searched(1194, true);
        check(state.shouldSearch(1195, false, false, false, type, false, null), "Non-idle waiting states bypass throttling");
        state.searched(1195, true);
        Object nextType = new Object();
        check(state.shouldSearch(1196, true, false, false, nextType, false, null), "Recipe type switches invalidate the old search result");
        state.searched(1196, true);
        check(state.shouldSearch(1197, true, false, false, nextType, true, null), "Lock toggles invalidate the old result");
        state.searched(1197, true);
        Object lockedRecipe = new Object();
        check(state.shouldSearch(1198, true, false, false, nextType, true, lockedRecipe), "Lock recipe changes invalidate the old result");
        state.searched(1198, false);
        check(state.shouldSearch(1199, true, false, false, nextType, true, lockedRecipe), "A successful search does not create a negative result");
        var zero = new IdleRecipeSearchState();
        check(zero.shouldSearch(0, true, false, false, null, false, null), "Zero-time first searches are allowed");
        zero.searched(0, true);
        check(!zero.shouldSearch(0, true, false, false, null, false, null), "Zero is a valid failure timestamp");
        check(zero.shouldSearch(20, true, false, false, null, false, null), "Zero-time failures expire normally");
        return assertions;
    }

    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }
}
