package com.gtl.enhancedcore.common.recipe.iv;

import java.math.BigInteger;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

/** Bounded extra work; a blocked work step refunds the bottle surcharge and ends this pulse. */
public final class IvTimeAccelerationBudget {
    public static final int MAX_STEPS = 64;
    private IvTimeAccelerationBudget() {}

    public static int steps(int duration, int progress) {
        long remaining = Math.max(0L, (long) duration - progress);
        return remaining == 0 ? 0 : (int) Math.min(MAX_STEPS, Math.max(1, remaining / 2));
    }

    public static boolean run(int steps, Supplier<BigInteger> cost, Predicate<BigInteger> pay,
                              Consumer<BigInteger> refund, BooleanSupplier work) {
        boolean advanced = false;
        for (int i = 0; i < Math.min(MAX_STEPS, Math.max(0, steps)); i++) {
            BigInteger amount = cost.get();
            if (amount.signum() <= 0 || !pay.test(amount)) break;
            if (!work.getAsBoolean()) {
                refund.accept(amount);
                break;
            }
            advanced = true;
        }
        return advanced;
    }
}
