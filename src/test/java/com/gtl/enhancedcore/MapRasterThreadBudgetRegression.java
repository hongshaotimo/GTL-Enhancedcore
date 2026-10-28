package com.gtl.enhancedcore;

import com.gtl.enhancedcore.common.performance.MapRasterThreadBudget;

final class MapRasterThreadBudgetRegression {
    private static int assertions;

    static int run() {
        assertions = 0;
        check(MapRasterThreadBudget.plan(16, 12L << 30, 0, true) == 2, "16 CPUs select two automatic map workers");
        check(MapRasterThreadBudget.plan(8, 12L << 30, 0, true) == 1, "8 CPUs retain the serial map path");
        check(MapRasterThreadBudget.plan(32, 12L << 30, 0, true) == 4, "Automatic map workers are capped");
        check(MapRasterThreadBudget.plan(32, 6L << 30, 0, true) == 1, "Small heaps use the serial map path");
        check(MapRasterThreadBudget.plan(32, Long.MAX_VALUE, 3, false) == 3, "Manual map worker count is honored");
        check(MapRasterThreadBudget.plan(32, Long.MAX_VALUE, 0, false) == 1, "Disabled automatic workers stay serial");
        invalid(-1); invalid(9);
        return assertions;
    }

    private static void invalid(int count) {
        try { MapRasterThreadBudget.plan(16, 12L << 30, count, true); }
        catch (IllegalArgumentException expected) { assertions++; return; }
        throw new AssertionError("Rejected map worker count " + count);
    }

    private static void check(boolean value, String message) {
        assertions++;
        if (!value) throw new AssertionError(message);
    }
}
