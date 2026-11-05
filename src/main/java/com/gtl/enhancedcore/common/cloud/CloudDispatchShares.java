package com.gtl.enhancedcore.common.cloud;

import java.math.BigInteger;
import java.util.List;

/** Read-only routing allowance in whole AE pattern repetitions; accepted work is never moved. */
public final class CloudDispatchShares {
    private CloudDispatchShares() {}

    public static BigInteger repetitions(long remaining, long active, long operationsPerPattern) {
        if (remaining < 0 || active < 0 || operationsPerPattern < 1)
            throw new IllegalArgumentException("Invalid cloud operation counters or pattern scale");
        var operations = BigInteger.valueOf(remaining).add(BigInteger.valueOf(active));
        var scale = BigInteger.valueOf(operationsPerPattern);
        return operations.add(scale.subtract(BigInteger.ONE)).divide(scale);
    }

    /** Priority differences set positive relative weights. Zero yields to another ready provider. */
    public static long share(long requested, List<BigInteger> pending, List<Integer> priorities, int selected) {
        if (pending.isEmpty() || pending.size() != priorities.size() || selected < 0 || selected >= pending.size())
            throw new IllegalArgumentException("Invalid cloud routing group");
        int minimum = priorities.stream().mapToInt(Integer::intValue).min().orElseThrow();
        var total = BigInteger.valueOf(Math.max(0, requested));
        var weights = BigInteger.ZERO;
        for (int i = 0; i < pending.size(); i++) {
            var backlog = pending.get(i);
            if (backlog.signum() < 0) throw new IllegalArgumentException("Negative cloud routing backlog");
            total = total.add(backlog);
            weights = weights.add(BigInteger.valueOf(1L + priorities.get(i) - minimum));
        }
        if (requested < 1) return 0;
        var ownWeight = BigInteger.valueOf(1L + priorities.get(selected) - minimum);
        var target = total.multiply(ownWeight).add(weights.subtract(BigInteger.ONE)).divide(weights);
        return target.subtract(pending.get(selected)).max(BigInteger.ZERO)
                .min(BigInteger.valueOf(requested)).longValueExact();
    }
}
