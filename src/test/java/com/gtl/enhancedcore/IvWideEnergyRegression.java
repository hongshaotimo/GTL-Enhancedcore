package com.gtl.enhancedcore;

import com.gtl.enhancedcore.common.recipe.iv.IvWideEnergy;
import java.math.BigInteger;
import java.util.Arrays;

public final class IvWideEnergyRegression {
    public static int run() {
        int checks = 0;
        BigInteger power = BigInteger.valueOf(Long.MAX_VALUE).shiftLeft(12);
        BigInteger energy = power.multiply(BigInteger.valueOf(27)).add(BigInteger.valueOf(13));
        var timing = IvWideEnergy.timing(energy, power, 20);
        check(timing.duration() == 28 && timing.eut().compareTo(power) <= 0, "wide timing obeys independent per-tick ceiling"); checks++;
        BigInteger left = energy, paid = BigInteger.ZERO;
        for (int tick = 0; tick < timing.duration(); tick++) {
            BigInteger payment = timing.eut().min(left); paid = paid.add(payment); left = left.subtract(payment);
        }
        check(left.signum() == 0 && paid.equals(energy), "last partial tick pays exact original total"); checks++;
        check(IvWideEnergy.total(Long.MAX_VALUE, 200, Integer.MAX_VALUE, 0.8).equals(
                BigInteger.valueOf(Long.MAX_VALUE).multiply(BigInteger.valueOf(200)).multiply(BigInteger.valueOf(Integer.MAX_VALUE))
                        .multiply(BigInteger.valueOf(8)).divide(BigInteger.TEN)), "batch total avoids double overflow and rounding loss"); checks++;
        for (int rotation = 0; rotation < 4; rotation++) {
            var wants = new BigInteger[]{power, power, BigInteger.valueOf(3)};
            var grants = IvWideEnergy.divide(power, wants, rotation);
            check(Arrays.stream(grants).reduce(BigInteger.ZERO, BigInteger::add).equals(power), "shared ceiling is conserved"); checks++;
            for (int i = 0; i < wants.length; i++) { check(grants[i].signum() >= 0 && grants[i].compareTo(wants[i]) <= 0, "no order overpaid"); checks++; }
        }
        check(Arrays.stream(IvWideEnergy.divide(BigInteger.ZERO, new BigInteger[]{energy}, 0)).allMatch(BigInteger.ZERO::equals),
                "zero actual balance never grants energy"); checks++;
        var zero = IvWideEnergy.timing(BigInteger.ZERO, power, 20);
        check(zero.eut().signum() == 0 && zero.duration() == 20, "zero energy retains minimum ticks without a payment"); checks++;
        var remaining = IvWideEnergy.timing(BigInteger.ZERO, power, 20 - 7);
        check(remaining.eut().signum() == 0 && 7 + remaining.duration() == 20, "switching a paid batch preserves remaining minimum ticks"); checks++;
        check(IvWideEnergy.timing(BigInteger.ZERO, power, 0).duration() == 1, "zero energy still has one completion tick"); checks++;
        check(Arrays.stream(IvWideEnergy.divide(power, new BigInteger[]{BigInteger.ZERO, BigInteger.ZERO}, 0))
                .allMatch(BigInteger.ZERO::equals), "zero demand never creates a debit even with a positive balance"); checks++;
        var grid = BigInteger.TEN.pow(72).add(BigInteger.valueOf(43));
        var wideTotal = grid.multiply(BigInteger.valueOf(20)).subtract(BigInteger.valueOf(7));
        var widePlan = IvWideEnergy.timing(wideTotal, grid, 20);
        check(widePlan.eut().equals(grid) && widePlan.duration() == 20,
                "arbitrary precision grid power is not restricted to long or a fixed tier"); checks++;
        BigInteger remainingEnergy = wideTotal, totalPaid = BigInteger.ZERO;
        for (int tick = 0; tick < 20; tick++) {
            var replanned = IvWideEnergy.timing(remainingEnergy, grid, 20 - tick);
            var debit = replanned.eut().min(remainingEnergy);
            totalPaid = totalPaid.add(debit); remainingEnergy = remainingEnergy.subtract(debit);
        }
        check(totalPaid.equals(wideTotal) && remainingEnergy.signum() == 0,
                "dynamic retiming preserves exact unpaid energy and minimum processing time"); checks++;
        return checks;
    }
    public static void main(String[] args) { System.out.println("Wide IV energy checks: " + run()); }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
