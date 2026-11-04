package com.gtl.enhancedcore;

import com.gtl.enhancedcore.client.performance.PreciseAmountSortCache;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public final class PreciseAmountSortCacheRegression {
    private static final class Key {
        final int id;
        int unit;
        Key(int id, int unit) { this.id = id; this.unit = unit; }
    }
    private record Entry(Key key, long stored) {}
    public static void main(String[] args) { System.out.println("Exact sort assertions: " + run()); }
    public static int run() {
        int checks = 0;
        var cache = new PreciseAmountSortCache<Key>();
        var entries = new ArrayList<Entry>();
        var exact = new HashMap<Key, BigInteger>();
        var random = new Random(928361);
        int[] units = {1, 1000, 144, 81, 3};
        for (int i = 0; i < 64; i++) entries.add(new Entry(new Key(i, units[i % units.length]), i % 7));
        for (int step = 0; step < 200; step++) {
            int index = random.nextInt(entries.size());
            var old = entries.get(index);
            old.key.unit = units[random.nextInt(units.length)];
            long stored = random.nextBoolean() ? Long.MAX_VALUE : random.nextInt(10000);
            entries.set(index, new Entry(old.key, stored));
            if (random.nextBoolean()) exact.put(old.key, BigInteger.ONE.shiftLeft(80 + random.nextInt(200)));
            else exact.remove(old.key);
            for (boolean ascending : List.of(true, false)) {
                var actual = new ArrayList<>(entries);
                actual.sort(cache.comparator(entries, exact, Entry::key, Entry::stored, key -> key.unit, ascending));
                var expected = new ArrayList<>(entries);
                expected.sort((a, b) -> {
                    int result = amount(a, exact).multiply(BigInteger.valueOf(b.key.unit))
                            .compareTo(amount(b, exact).multiply(BigInteger.valueOf(a.key.unit)));
                    return ascending ? result : -result;
                });
                if (!actual.equals(expected)) throw new AssertionError("Exact normalized order differs at " + step);
                checks++;
            }
        }
        // Changing the distinct unit set must invalidate every retained normalized key.
        entries.removeIf(entry -> entry.key.unit != 1);
        entries.add(new Entry(new Key(90, 2), 7));
        entries.add(new Entry(new Key(91, 3), 10));
        var ordered = new ArrayList<>(entries);
        ordered.sort(cache.comparator(entries, exact, Entry::key, Entry::stored, key -> key.unit, true));
        for (int i = 1; i < ordered.size(); i++) {
            var a = ordered.get(i - 1); var b = ordered.get(i);
            if (amount(a, exact).multiply(BigInteger.valueOf(b.key.unit))
                    .compareTo(amount(b, exact).multiply(BigInteger.valueOf(a.key.unit))) > 0)
                throw new AssertionError("Removed/added unit retained stale scale");
            checks++;
        }
        // Same inputs reuse the actual BigInteger key object, rather than re-multiplying each sort.
        try {
            var field = PreciseAmountSortCache.class.getDeclaredField("values"); field.setAccessible(true);
            var values = (Map<?, ?>) field.get(cache);
            Object value = values.get(entries.get(0).key);
            var normalized = value.getClass().getDeclaredField("normalized"); normalized.setAccessible(true);
            Object prior = normalized.get(value);
            cache.comparator(entries, exact, Entry::key, Entry::stored, key -> key.unit, false);
            if (normalized.get(value) != prior) throw new AssertionError("Unchanged amount key was reallocated");
            checks++;
            cache.clear();
            if (!values.isEmpty()) throw new AssertionError("Closed Repo retains keys"); checks++;
        } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
        return checks;
    }
    private static BigInteger amount(Entry entry, Map<Key, BigInteger> exact) {
        return entry.stored == Long.MAX_VALUE ? exact.getOrDefault(entry.key, BigInteger.valueOf(entry.stored))
                : BigInteger.valueOf(entry.stored);
    }
}
