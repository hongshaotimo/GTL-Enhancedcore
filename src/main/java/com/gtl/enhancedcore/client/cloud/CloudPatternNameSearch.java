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

    private CloudPatternNameSearch() {}

    /** Compatibility entry point. Cloud terminals should use the authorized-candidate overload. */
    public static Matches find(String query) {
        var items = new BitSet();
        var fluids = new BitSet();
        items.set(0, BuiltInRegistries.ITEM.size());
        fluids.set(0, BuiltInRegistries.FLUID.size());
        return find(query, items, fluids);
    }

    public static Matches find(String query, BitSet candidateItems, BitSet candidateFluids) {
        String normalized = query == null ? "" : query.strip().toLowerCase(Locale.ROOT);
        // Opening an unfiltered terminal must not initialize names or the pinyin dictionary.
        if (normalized.isEmpty()) return EMPTY;
        var words = PatternGeneratorFilter.keywords(normalized);
        if (words.isEmpty()) return EMPTY;
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
        if (cached != null) return cached;
        var matchers = words.stream().map(CloudSearchMatcher::compile).toList();
        var items = new BitSet();
        var fluids = new BitSet();
        for (int id = key.items.nextSetBit(0); id >= 0; id = key.items.nextSetBit(id + 1)) {
            var name = ITEM_NAMES.computeIfAbsent(id, index -> {
                var item = BuiltInRegistries.ITEM.byId(index);
                return CloudSearchMatcher.prepare(item.getDescription().getString() + " "
                        + BuiltInRegistries.ITEM.getKey(item));
            });
            if (matches(matchers, name)) items.set(id);
        }
        for (int id = key.fluids.nextSetBit(0); id >= 0; id = key.fluids.nextSetBit(id + 1)) {
            var fluid = BuiltInRegistries.FLUID.byId(id);
            if (fluid == Fluids.EMPTY) continue;
            var name = FLUID_NAMES.computeIfAbsent(id, index -> CloudSearchMatcher.prepare(
                    FluidStack.create(fluid, 1).getDisplayName().getString() + " "
                            + BuiltInRegistries.FLUID.getKey(fluid)));
            if (matches(matchers, name)) fluids.set(id);
        }
        Matches result = new Matches(items, fluids);
        QUERIES.put(key, result);
        return result;
    }

    private static BitSet bounded(BitSet candidates, int registrySize) {
        return candidates == null ? new BitSet() : candidates.get(0, registrySize);
    }

    private static boolean matches(List<CloudSearchMatcher> matchers, CloudSearchMatcher.SearchText name) {
        for (var matcher : matchers) if (matcher.matches(name)) return true;
        return false;
    }

    public static void clearCache() {
        ITEM_NAMES.clear();
        FLUID_NAMES.clear();
        loadedLanguage = null;
        languageCode = "";
        QUERIES.clear();
        CloudSearchMatcher.clearCaches();
    }
}
