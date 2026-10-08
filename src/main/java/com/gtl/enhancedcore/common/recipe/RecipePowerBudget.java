package com.gtl.enhancedcore.common.recipe;

public final class RecipePowerBudget {
    private RecipePowerBudget() {}

    public static long power(long voltage, long amperage) {
        if (voltage <= 0 || amperage <= 0) return 0;
        return voltage > Long.MAX_VALUE / amperage ? Long.MAX_VALUE : voltage * amperage;
    }

    public static long add(long current, long extra) {
        long positiveCurrent = Math.max(0, current);
        long positiveExtra = Math.max(0, extra);
        return positiveCurrent > Long.MAX_VALUE - positiveExtra ? Long.MAX_VALUE : positiveCurrent + positiveExtra;
    }
}
