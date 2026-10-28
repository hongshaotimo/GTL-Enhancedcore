package com.gtl.enhancedcore.common.cloud;

import java.math.BigInteger;

/** Native modules whose power belongs to an external host, rather than local input hatches. */
public interface CloudPowerAccess {
    long cloudPowerLimit();
    boolean cloudPowerReady(BigInteger payment);
    /** Null rejects payment. A successful receipt owns its exact source and can refund at most once. */
    Payment cloudPowerPay(BigInteger payment);
    @FunctionalInterface interface Payment { void refund(); }
    final class OncePayment implements Payment {
        private Runnable undo;
        public OncePayment(Runnable undo) { this.undo = java.util.Objects.requireNonNull(undo); }
        @Override public void refund() {
            Runnable action = undo; undo = null;
            if (action != null) action.run();
        }
    }
}
