package com.gtl.enhancedcore.common.recipe.iv;

import java.math.*;

/** Exact unpaid-energy timing and bounded per-tick sharing for stellar isolated batches. */
public final class IvWideEnergy {
    private IvWideEnergy() {}
    public record Timing(BigInteger eut, int duration) {}
    public static Timing timing(BigInteger energy, BigInteger power, int minimumTicks) {
        if (energy.signum() < 0 || power.signum() <= 0) throw new IllegalArgumentException("Invalid wide energy");
        BigInteger ticks = ceil(energy, power).max(BigInteger.valueOf(Math.max(1, minimumTicks)));
        return new Timing(ceil(energy, ticks), ticks.intValueExact());
    }
    public static BigInteger total(long eut, int duration, long parallel, double multiplier) {
        if (eut < 0 || duration < 1 || parallel < 1 || !Double.isFinite(multiplier) || multiplier < 0)
            throw new IllegalArgumentException("Invalid stellar batch");
        return new BigDecimal(BigInteger.valueOf(eut).multiply(BigInteger.valueOf(duration)).multiply(BigInteger.valueOf(parallel)))
                .multiply(BigDecimal.valueOf(multiplier)).setScale(0, RoundingMode.CEILING).toBigIntegerExact();
    }
    public static BigInteger[] divide(BigInteger budget, BigInteger[] wants, int offset) {
        var granted = new BigInteger[wants.length]; java.util.Arrays.fill(granted, BigInteger.ZERO);
        if (budget.signum() < 0) throw new IllegalArgumentException("Negative budget");
        for (var want : wants) if (want.signum() < 0) throw new IllegalArgumentException("Negative request");
        while (budget.signum() > 0) {
            int active = 0; for (int i = 0; i < wants.length; i++) if (granted[i].compareTo(wants[i]) < 0) active++;
            if (active == 0) break;
            BigInteger share = budget.divide(BigInteger.valueOf(active)).max(BigInteger.ONE);
            for (int step = 0; step < wants.length && budget.signum() > 0; step++) {
                int i = Math.floorMod((long)offset + step, wants.length);
                BigInteger give = share.min(wants[i].subtract(granted[i])).min(budget);
                granted[i] = granted[i].add(give); budget = budget.subtract(give);
            }
        }
        return granted;
    }
    private static BigInteger ceil(BigInteger n, BigInteger d) { return n.add(d).subtract(BigInteger.ONE).divide(d); }
}
