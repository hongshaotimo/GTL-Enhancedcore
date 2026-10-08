package com.gtl.enhancedcore.common.recipe.iv;

import java.math.BigInteger;
import java.util.List;

public final class IvNativeRecoveryState {
    public record PowerInput(long voltage, long amperage) {}
    public record PaidTick(BigInteger energyLeft, int elapsed, boolean complete) {}

    private IvNativeRecoveryState() {}

    public static boolean enabled(boolean stored, boolean enabled, boolean suspended) {
        return stored ? enabled : !suspended;
    }

    public static long inputPower(List<PowerInput> inputs) {
        BigInteger power = BigInteger.ZERO;
        for (var input : inputs) {
            if (input.voltage() < 0 || input.amperage() < 0)
                throw new IllegalArgumentException("Negative native energy input");
            power = power.add(BigInteger.valueOf(input.voltage()).multiply(BigInteger.valueOf(input.amperage())));
        }
        return power.min(BigInteger.valueOf(Long.MAX_VALUE)).longValueExact();
    }

    public static boolean canPay(BigInteger payment, long inputPower, long stored) {
        if (payment.signum() < 0) throw new IllegalArgumentException("Negative native payment");
        return payment.compareTo(BigInteger.valueOf(Math.max(0, inputPower))) <= 0
                && payment.compareTo(BigInteger.valueOf(Math.max(0, stored))) <= 0;
    }

    public static PaidTick paid(BigInteger energyLeft, BigInteger payment, int elapsed, int duration) {
        if (duration < 1 || elapsed < 0 || elapsed >= duration || energyLeft.signum() < 0
                || payment.signum() < 0 || payment.compareTo(energyLeft) > 0)
            throw new IllegalArgumentException("Invalid native paid tick");
        var remaining = energyLeft.subtract(payment);
        int progressed = Math.incrementExact(elapsed);
        if (progressed == duration && remaining.signum() != 0)
            throw new IllegalStateException("Native duration finished before energy payment");
        return new PaidTick(remaining, progressed, progressed == duration && remaining.signum() == 0);
    }
}
