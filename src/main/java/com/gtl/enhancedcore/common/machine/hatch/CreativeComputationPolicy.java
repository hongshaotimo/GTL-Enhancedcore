package com.gtl.enhancedcore.common.machine.hatch;

import java.math.BigInteger;

/** Creative computation has no numeric quota: BigInteger itself has no fixed maximum. */
public final class CreativeComputationPolicy {
    private CreativeComputationPolicy() {}

    public static BigInteger grant(BigInteger requested, boolean online) {
        return online && requested.signum() > 0 ? requested : BigInteger.ZERO;
    }
}
