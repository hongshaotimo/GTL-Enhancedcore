package com.gtl.enhancedcore.common.machine.hatch;

import org.gtlcore.gtlcore.api.machine.computation.ComputationSource;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/** Per-owner grants from an unlimited source must not share a long-sized reservation counter. */
public final class CreativeComputationReservations {
    private final Map<Object, Grant> grants = new IdentityHashMap<>();
    private long epoch = Long.MIN_VALUE;

    public static CreativeComputationSource find(List<ComputationSource> sources) {
        for (var source : sources) {
            if (source instanceof CreativeComputationSource creative && creative.unlimited()
                    && creative.gtlcore$canBridgeComputation()) return creative;
        }
        return null;
    }

    public void advance(long tick) {
        if (epoch == tick) return;
        epoch = tick;
        grants.values().removeIf(grant -> tick < grant.tick || tick - grant.tick > 12);
    }

    public void offer(Object owner, CreativeComputationSource source, long maximum) {
        grants.put(owner, new Grant(source, maximum, epoch));
    }

    public boolean contains(Object owner) {
        return grants.containsKey(owner);
    }

    public ComputationSource.Receipt withdraw(Object owner, long amount) {
        Grant grant = grants.get(owner);
        if (grant == null || amount <= 0 || amount > grant.maximum) return null;
        grants.remove(owner);
        return grant.source.gtlcore$withdrawComputation(amount);
    }

    public void forget(Object owner) {
        grants.remove(owner);
    }

    public void invalidate() {
        grants.clear();
    }

    private record Grant(CreativeComputationSource source, long maximum, long tick) {}
}
