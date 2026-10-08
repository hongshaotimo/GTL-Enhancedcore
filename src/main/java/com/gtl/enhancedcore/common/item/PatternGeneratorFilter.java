package com.gtl.enhancedcore.common.item;

import java.util.Arrays;
import java.util.BitSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Small, dependency-free predicates shared by the recipe preview and generation queue. */
public final class PatternGeneratorFilter {
    public static final int ANY_CIRCUIT = -1;

    public record Material(String id, boolean fluid, int registryIndex) {}

    private PatternGeneratorFilter() {}

    public static List<String> keywords(String value) {
        if (value == null || value.isBlank()) return List.of();
        return Arrays.stream(value.toLowerCase(Locale.ROOT).replace("\"", "")
                .replace("\u201c", "").replace("\u201d", "").strip().split("\\s+"))
                .filter(token -> !token.isBlank()).distinct().toList();
    }

    public static boolean matchesCircuit(int selection, int actual) {
        return selection == ANY_CIRCUIT || selection == actual;
    }

    public static boolean containsKeyword(List<String> keywords, String value) {
        String normalized = value.toLowerCase(Locale.ROOT);
        return keywords.stream().anyMatch(normalized::contains);
    }

    public static boolean allows(List<Material> materials, List<String> words,
                                 BitSet namedItems, BitSet namedFluids, Set<String> excluded) {
        // The item/fluid prefix prevents identical registry IDs from excluding each other.
        if (materials.stream().anyMatch(material -> excluded.contains(key(material.id(), material.fluid())))) return false;
        return words.isEmpty() || materials.stream().anyMatch(material ->
                containsKeyword(words, material.id()) || (material.registryIndex() >= 0 &&
                        (material.fluid() ? namedFluids : namedItems).get(material.registryIndex())));
    }

    public static String key(String id, boolean fluid) {
        return (fluid ? "fluid:" : "item:") + id;
    }
}
