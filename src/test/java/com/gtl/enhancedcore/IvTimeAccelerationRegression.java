package com.gtl.enhancedcore;

import com.gtl.enhancedcore.common.recipe.iv.IvTimeAccelerationBudget;
import java.math.BigInteger;

public final class IvTimeAccelerationRegression {
    public static int run() {
        check(IvTimeAccelerationBudget.steps(Integer.MAX_VALUE, 0) == 64, "bounded long recipe");
        check(IvTimeAccelerationBudget.steps(100, 40) == 30, "half remaining");
        check(IvTimeAccelerationBudget.steps(1, 0) == 1, "last tick advances");
        check(IvTimeAccelerationBudget.steps(1, 1) == 0, "complete has no work");
        BigInteger[] bank = {BigInteger.valueOf(Long.MAX_VALUE).multiply(BigInteger.valueOf(16))};
        BigInteger initial = bank[0];
        BigInteger cost = BigInteger.valueOf(Long.MAX_VALUE).multiply(BigInteger.valueOf(8));
        int[] work = {0};
        boolean result = IvTimeAccelerationBudget.run(64, () -> cost, amount -> {
            if (bank[0].compareTo(amount) < 0) return false;
            bank[0] = bank[0].subtract(amount); return true;
        }, amount -> bank[0] = bank[0].add(amount), () -> { work[0]++; return true; });
        check(result && work[0] == 2 && bank[0].signum() == 0, "wide EU pays before each step, stops when exhausted");
        bank[0] = initial; work[0] = 0;
        result = IvTimeAccelerationBudget.run(64, () -> cost, amount -> {
            bank[0] = bank[0].subtract(amount); return true;
        }, amount -> bank[0] = bank[0].add(amount), () -> { work[0]++; return false; });
        check(!result && work[0] == 1 && bank[0].equals(initial), "blocked step refunds and stops");
        work[0] = 0;
        result = IvTimeAccelerationBudget.run(Integer.MAX_VALUE, () -> BigInteger.ONE, amount -> true,
                amount -> { throw new AssertionError("unexpected refund"); }, () -> { work[0]++; return true; });
        check(result && work[0] == 64, "caller cannot exceed pulse limit");
        work[0] = 0;
        result = IvTimeAccelerationBudget.run(64, () -> BigInteger.ZERO, amount -> {
            throw new AssertionError("free work must not be charged");
        }, amount -> {}, () -> { work[0]++; return true; });
        check(!result && work[0] == 0, "zero cost does not execute work");
        return 8;
    }

    private static void check(boolean valid, String message) {
        if (!valid) throw new AssertionError(message);
    }
}
