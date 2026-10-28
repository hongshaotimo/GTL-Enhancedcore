package com.gtl.enhancedcore.common.recipe.iv;

import org.gtlcore.gtlcore.utils.datastructure.Int128;

/** Projects one consumable long-sized batch without narrowing the wide stock itself. */
public final class SuperBufferLongBatch {
    private SuperBufferLongBatch() {}

    public static long available(Int128 stock) {
        if (stock == null || !stock.isPositive()) return 0;
        long high = stock.getHigh();
        long low = stock.getLow();
        if (high > 0 || low < 0) return Long.MAX_VALUE;
        return low;
    }

    public static boolean consume(Int128 stock, long amount) {
        if (stock == null || amount <= 0 || available(stock) < amount) return false;
        stock.add(-amount);
        return true;
    }
}
