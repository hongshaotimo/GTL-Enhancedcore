package com.gtl.enhancedcore.common.recipe;

import java.math.BigInteger;

/** Exact ordinary-grid batch accounting. Capacity may be unbounded; time and EU fields are finite. */
public final class GalaxyBatchAccounting {
    private GalaxyBatchAccounting() {}
    public record Plan(int duration, long eut) {}
    public record BigPlan(int duration, BigInteger eut) {}
    public static BigPlan plan(BigInteger energy, int minimumTicks, BigInteger power) {
        if (energy.signum() < 0 || minimumTicks < 1 || power.signum() < 1) throw new IllegalArgumentException("Invalid batch power");
        BigInteger required = ceiling(energy, power).max(BigInteger.valueOf(minimumTicks));
        if (required.compareTo(BigInteger.valueOf(Integer.MAX_VALUE)) > 0) return null;
        return new BigPlan(required.intValueExact(), ceiling(energy, required));
    }
    public static Plan plan(BigInteger energy, int minimumTicks, long power) {
        if (energy.signum() < 0 || minimumTicks < 1 || power < 1) throw new IllegalArgumentException("Invalid batch power");
        BigInteger required = ceiling(energy, BigInteger.valueOf(power)).max(BigInteger.valueOf(minimumTicks));
        if (required.compareTo(BigInteger.valueOf(Integer.MAX_VALUE)) > 0) return null;
        return new Plan(required.intValueExact(), ceiling(energy, required).longValueExact());
    }
    private static BigInteger ceiling(BigInteger amount, BigInteger divisor) {
        return amount.add(divisor).subtract(BigInteger.ONE).divide(divisor);
    }
}
