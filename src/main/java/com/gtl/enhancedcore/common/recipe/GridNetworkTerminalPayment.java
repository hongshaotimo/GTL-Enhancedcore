package com.gtl.enhancedcore.common.recipe;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

/** Plans a bounded long-facing recipe payment against the terminal's wide EU balance. */
public final class GridNetworkTerminalPayment {
    private GridNetworkTerminalPayment() {}

    public static Payment plan(List<Long> amounts, BigInteger balance, boolean simulate) {
        BigInteger required = BigInteger.ZERO;
        for (Long amount : amounts) {
            if (amount == null || amount < 0) return new Payment(false, BigInteger.ZERO, amounts);
            required = required.add(BigInteger.valueOf(amount));
        }

        BigInteger available = balance.max(BigInteger.ZERO);
        BigInteger withdrawn = required.min(available);
        BigInteger missing = required.subtract(withdrawn);
        List<Long> remaining = null;
        if (missing.signum() > 0) {
            // These are real debts handed to the next recipe handler, not a display sentinel.
            var unpaid = new ArrayList<Long>();
            BigInteger covered = withdrawn;
            for (long amount : amounts) {
                BigInteger paid = covered.min(BigInteger.valueOf(amount));
                long rest = amount - paid.longValueExact();
                if (rest > 0) unpaid.add(rest);
                covered = covered.subtract(paid);
            }
            remaining = List.copyOf(unpaid);
        }
        return new Payment(true, simulate ? BigInteger.ZERO : withdrawn, remaining);
    }

    public record Payment(boolean valid, BigInteger withdrawn, List<Long> remaining) {}
}
