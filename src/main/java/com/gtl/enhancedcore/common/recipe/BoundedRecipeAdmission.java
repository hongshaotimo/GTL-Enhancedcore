package com.gtl.enhancedcore.common.recipe;

import java.util.function.LongPredicate;

public final class BoundedRecipeAdmission {
    private BoundedRecipeAdmission() {}

    public static long maximum(long requested, LongPredicate fits) {
        if (requested <= 0) return 0;
        if (fits.test(requested)) return requested;
        long lower = 0;
        long upper = requested - 1;
        while (lower < upper) {
            long distance = upper - lower;
            long middle = lower + distance / 2 + distance % 2;
            if (fits.test(middle)) lower = middle;
            else upper = middle - 1;
        }
        return lower;
    }
}
