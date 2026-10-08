package com.gtl.enhancedcore.common.recipe.iv;

import java.math.BigInteger;

/** Reschedule only unpaid energy. Rounding may delay a tick, never forgive an EU. */
public record IvRemainingTime(long eut, int duration) {
    public static IvRemainingTime calculate(BigInteger unpaid, long power, int elapsed, int minimumDuration) {
        if (unpaid.signum()<0 || power<=0 || elapsed<0) throw new IllegalArgumentException("Invalid remaining work");
        BigInteger ticks = ceil(unpaid, BigInteger.valueOf(power))
                .max(BigInteger.valueOf(Math.max(1L,(long)minimumDuration-elapsed)));
        int duration = ticks.add(BigInteger.valueOf(elapsed)).intValueExact();
        return new IvRemainingTime(ceil(unpaid,ticks).longValueExact(), duration);
    }
    /** Display estimate only: large orders must not fault or forgive energy when seconds exceed int ticks. */
    public static int estimatedDuration(BigInteger unpaid, long allocatedPower, int elapsed, int minimumDuration) {
        if (unpaid.signum()<0 || allocatedPower<=0 || elapsed<0) throw new IllegalArgumentException("Invalid remaining work");
        BigInteger remaining = ceil(unpaid, BigInteger.valueOf(allocatedPower))
                .max(BigInteger.valueOf(Math.max(1L, (long)minimumDuration-elapsed)));
        return remaining.add(BigInteger.valueOf(elapsed)).min(BigInteger.valueOf(Integer.MAX_VALUE)).intValueExact();
    }
    private static BigInteger ceil(BigInteger value, BigInteger divisor) {
        return value.add(divisor).subtract(BigInteger.ONE).divide(divisor);
    }
}
