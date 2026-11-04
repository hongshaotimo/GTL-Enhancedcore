package com.gtl.enhancedcore.common.machine.hatch;

import org.gtlcore.gtlcore.api.machine.computation.ComputationSource;

import java.util.Objects;
import java.math.BigInteger;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/** One physical source for all routes, with exact BigInteger delivery and refundable receipts. */
public final class CreativeComputationSource implements ComputationSource {
    private final LongSupplier tick;
    private final Supplier<BigInteger> capacity;
    private final BooleanSupplier online;
    private long epoch = Long.MIN_VALUE;
    private BigInteger delivered = BigInteger.ZERO;

    /** The actual creative hatch: any positive BigInteger demand can be supplied. */
    public CreativeComputationSource(LongSupplier tick, BooleanSupplier online) {
        this.tick = Objects.requireNonNull(tick);
        this.online = Objects.requireNonNull(online);
        this.capacity = null;
    }

    public CreativeComputationSource(LongSupplier tick, LongSupplier capacity, BooleanSupplier online) {
        this(tick, () -> BigInteger.valueOf(capacity.getAsLong()), online);
    }

    public CreativeComputationSource(LongSupplier tick, Supplier<BigInteger> capacity, BooleanSupplier online) {
        this.tick = Objects.requireNonNull(tick);
        this.capacity = Objects.requireNonNull(capacity);
        this.online = Objects.requireNonNull(online);
    }

    public boolean unlimited() {
        return capacity == null;
    }

    @Override
    public long gtlcore$computationCapacity() {
        if (!online.getAsBoolean()) return 0;
        return unlimited() ? Long.MAX_VALUE : project(capacity.get());
    }

    @Override
    public long gtlcore$availableComputation() {
        advance();
        if (!online.getAsBoolean()) return 0;
        return unlimited() ? Long.MAX_VALUE : project(capacity.get().subtract(delivered));
    }

    @Override
    public boolean gtlcore$canBridgeComputation() {
        return online.getAsBoolean();
    }

    @Override
    public Receipt gtlcore$withdrawComputation(long amount) {
        var receipt = withdrawComputation(BigInteger.valueOf(amount));
        return receipt == null ? null : new Receipt(amount, receipt.refund());
    }

    /** Direct wide supply; no int/long clipping and no invented BigInteger maximum. */
    public WideReceipt withdrawComputation(BigInteger amount) {
        advance();
        if (CreativeComputationPolicy.grant(amount, online.getAsBoolean()).signum() == 0) return null;
        if (!unlimited() && amount.compareTo(capacity.get().subtract(delivered)) > 0) return null;
        delivered = delivered.add(amount);
        long paidTick = epoch;
        return new WideReceipt(amount, new Runnable() {
            private boolean refunded;

            @Override
            public void run() {
                if (refunded) return;
                refunded = true;
                advance();
                if (epoch == paidTick) delivered = delivered.subtract(amount);
            }
        });
    }

    public BigInteger deliveredComputation() {
        advance();
        return delivered;
    }

    private void advance() {
        long now = tick.getAsLong();
        if (epoch == now) return;
        epoch = now;
        delivered = BigInteger.ZERO;
    }

    private static long project(BigInteger amount) {
        return amount.max(BigInteger.ZERO).min(BigInteger.valueOf(Long.MAX_VALUE)).longValueExact();
    }

    public record WideReceipt(BigInteger amount, Runnable refund) {}
}
