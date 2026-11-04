package com.gtl.enhancedcore.client.performance;

import java.math.BigInteger;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.ToIntFunction;
import java.util.function.ToLongFunction;

/** One Repo's exact amount sort keys. Retains only its current keys, never a world or menu. */
public final class PreciseAmountSortCache<K> {
    private static final class Unit {
        final BigInteger amount;
        BigInteger factor;
        long seen;
        Unit(int amount) { this.amount = BigInteger.valueOf(amount); }
    }
    private static final class Value {
        long stored, seen, scaleVersion = -1;
        int unit;
        BigInteger exact, raw, normalized;
    }
    private final Map<K, Value> values = new HashMap<>();
    private final Map<Integer, Unit> units = new HashMap<>();
    private long generation, scaleVersion;

    public <E> Comparator<E> comparator(Collection<E> entries, Map<K, BigInteger> exactAmounts,
            Function<E, K> keyOf, ToLongFunction<E> storedOf, ToIntFunction<K> unitOf, boolean ascending) {
        long visit = ++generation;
        boolean unitsChanged = false;
        for (E entry : entries) {
            K key = keyOf.apply(entry);
            long stored = storedOf.applyAsLong(entry);
            int amountPerUnit = unitOf.applyAsInt(key);
            Unit unit = units.get(amountPerUnit);
            if (unit == null) {
                unit = new Unit(amountPerUnit);
                units.put(amountPerUnit, unit);
                unitsChanged = true;
            }
            unit.seen = visit;
            BigInteger exact = stored == Long.MAX_VALUE ? exactAmounts.get(key) : null;
            Value value = values.get(key);
            if (value == null) {
                value = new Value();
                values.put(key, value);
            }
            if (value.raw == null || value.stored != stored || !Objects.equals(value.exact, exact)) {
                value.stored = stored;
                value.exact = exact;
                value.raw = exact == null ? BigInteger.valueOf(stored) : exact;
                value.scaleVersion = -1;
            }
            if (value.unit != amountPerUnit) value.scaleVersion = -1;
            value.unit = amountPerUnit;
            value.seen = visit;
        }
        values.values().removeIf(value -> value.seen != visit);
        if (units.values().removeIf(unit -> unit.seen != visit)) unitsChanged = true;
        if (unitsChanged) {
            BigInteger lcm = BigInteger.ONE;
            for (Unit unit : units.values()) lcm = lcm.divide(lcm.gcd(unit.amount)).multiply(unit.amount);
            for (Unit unit : units.values()) unit.factor = lcm.divide(unit.amount);
            scaleVersion++;
        }
        for (Value value : values.values()) {
            if (value.scaleVersion != scaleVersion) {
                value.normalized = value.raw.multiply(units.get(value.unit).factor);
                value.scaleVersion = scaleVersion;
            }
        }
        Comparator<E> result = Comparator.comparing(entry -> values.get(keyOf.apply(entry)).normalized);
        return ascending ? result : result.reversed();
    }

    public void clear() {
        values.clear();
        units.clear();
        generation = scaleVersion = 0;
    }
}
