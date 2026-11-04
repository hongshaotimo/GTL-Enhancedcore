package com.gtl.enhancedcore.client.cloud;

import com.gtl.enhancedcore.common.item.PatternGeneratorFilter;
import com.lowdragmc.lowdraglib.side.fluid.FluidStack;
import java.util.BitSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.locale.Language;
import net.minecraft.world.level.material.Fluids;

/** Client localized aliases; only registry-ID bitsets cross the network boundary. */
public final class CloudPatternNameSearch {
    public record Matches(BitSet items, BitSet fluids) {
        public Matches { items = (BitSet) items.clone(); fluids = (BitSet) fluids.clone(); }
        @Override public BitSet items() { return (BitSet) items.clone(); }
        @Override public BitSet fluids() { return (BitSet) fluids.clone(); }
    }
    private record Query(String text, BitSet items, BitSet fluids) {
        private Query { items = (BitSet) items.clone(); fluids = (BitSet) fluids.clone(); }
    }
    private static final Matches EMPTY = new Matches(new BitSet(), new BitSet());
    private static final Map<Query, Matches> QUERIES = new LinkedHashMap<>(16, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<Query, Matches> entry) {
            return size() > 32;
        }
    };
    // Each map is naturally bounded by its registry size; only visited authorized IDs acquire names.
    private static final Map<Integer, CloudSearchMatcher.SearchText> ITEM_NAMES = new HashMap<>();
    private static final Map<Integer, CloudSearchMatcher.SearchText> FLUID_NAMES = new HashMap<>();
    private static Language loadedLanguage;
    private static String languageCode = "";
    private static long cacheGeneration;

    private CloudPatternNameSearch() {}

    /** Compatibility entry point. Cloud terminals should use the authorized-candidate overload. */
    public static Matches find(String query) {
        Task task = start(query);
        while (!task.done()) task.advance(Integer.MAX_VALUE, Long.MAX_VALUE);
        return task.result();
    }

    /** Full semantic candidate set, scanned over several client frames by terminal callers. */
    public static Task start(String query) {
        var items = new BitSet();
        var fluids = new BitSet();
        items.set(0, BuiltInRegistries.ITEM.size());
        fluids.set(0, BuiltInRegistries.FLUID.size());
        return start(query, items, fluids);
    }

    public static Matches find(String query, BitSet candidateItems, BitSet candidateFluids) {
        Task task = start(query, candidateItems, candidateFluids);
        while (!task.done()) task.advance(Integer.MAX_VALUE, Long.MAX_VALUE);
        return task.result();
    }

    public static Task start(String query, BitSet candidateItems, BitSet candidateFluids) {
        String normalized = query == null ? "" : query.strip().toLowerCase(Locale.ROOT);
        // Opening an unfiltered terminal must not initialize names or the pinyin dictionary.
        if (normalized.isEmpty()) return new Task(EMPTY);
        var words = PatternGeneratorFilter.keywords(normalized);
        if (words.isEmpty()) return new Task(EMPTY);
        Language language = Language.getInstance();
        String code = Minecraft.getInstance().options.languageCode;
        if (loadedLanguage != language || !languageCode.equals(code)) {
            clearCache();
            loadedLanguage = language;
            languageCode = code;
        }
        // Clamp before key construction/iteration so out-of-range bits cannot grow a cache entry.
        Query key = new Query(normalized, bounded(candidateItems, BuiltInRegistries.ITEM.size()),
                bounded(candidateFluids, BuiltInRegistries.FLUID.size()));
        Matches cached = QUERIES.get(key);
        if (cached != null) return new Task(cached);
        return new Task(key, words.stream().map(CloudSearchMatcher::compile).toList());
    }

    /** Main-thread cursor: no registry, language or ItemStack work is sent to a worker. */
    public static final class Task {
        private final Query key;
        private final List<CloudSearchMatcher> matchers;
        private final long generation;
        private final BitSet items = new BitSet(), fluids = new BitSet();
        private int itemId, fluidId;
        private Matches result;
        private boolean cancelled;
        private Task(Matches result) {
            this.key = null; this.matchers = List.of(); this.generation = cacheGeneration;
            this.result = result;
        }
        private Task(Query key, List<CloudSearchMatcher> matchers) {
            this.key = key; this.matchers = matchers; this.generation = cacheGeneration;
            itemId = key.items.nextSetBit(0); fluidId = key.fluids.nextSetBit(0);
        }
        public boolean done() { return result != null; }
        public boolean cancelled() {
            if (key == null && result == EMPTY) return cancelled;
            return cancelled || generation != cacheGeneration
                    || key != null && (loadedLanguage != Language.getInstance()
                    || !languageCode.equals(Minecraft.getInstance().options.languageCode));
        }
        public void cancel() { cancelled = true; }
        public Matches result() {
            if (cancelled() || result == null) throw new IllegalStateException("Cloud search task is not complete/current");
            return result;
        }
        public void advance(int maximumVisits, long budgetNanos) {
            if (done() || cancelled()) return;
            long started = System.nanoTime();
            int visited = 0;
            while (visited < maximumVisits && (itemId >= 0 || fluidId >= 0)) {
                if (visited != 0 && System.nanoTime() - started >= budgetNanos) return;
                visited++;
                if (itemId >= 0) {
                    int id = itemId; itemId = key.items.nextSetBit(id + 1);
                    var name = ITEM_NAMES.computeIfAbsent(id, index -> {
                        var item = BuiltInRegistries.ITEM.byId(index);
                        return CloudSearchMatcher.prepare(item.getDescription().getString() + " "
                                + BuiltInRegistries.ITEM.getKey(item));
                    });
                    if (matches(matchers, name)) items.set(id);
                } else {
                    int id = fluidId; fluidId = key.fluids.nextSetBit(id + 1);
                    var fluid = BuiltInRegistries.FLUID.byId(id);
                    if (fluid == Fluids.EMPTY) continue;
                    var name = FLUID_NAMES.computeIfAbsent(id, index -> CloudSearchMatcher.prepare(
                            FluidStack.create(fluid, 1).getDisplayName().getString() + " "
                                    + BuiltInRegistries.FLUID.getKey(fluid)));
                    if (matches(matchers, name)) fluids.set(id);
                }
            }
            if (itemId < 0 && fluidId < 0) {
                result = new Matches(items, fluids);
                QUERIES.put(key, result);
            }
        }
    }

    private static BitSet bounded(BitSet candidates, int registrySize) {
        return candidates == null ? new BitSet() : candidates.get(0, registrySize);
    }

    private static boolean matches(List<CloudSearchMatcher> matchers, CloudSearchMatcher.SearchText name) {
        for (var matcher : matchers) if (matcher.matches(name)) return true;
        return false;
    }

    public static void clearCache() {
        cacheGeneration++;
        ITEM_NAMES.clear();
        FLUID_NAMES.clear();
        loadedLanguage = null;
        languageCode = "";
        QUERIES.clear();
        CloudSearchMatcher.clearCaches();
    }
}
