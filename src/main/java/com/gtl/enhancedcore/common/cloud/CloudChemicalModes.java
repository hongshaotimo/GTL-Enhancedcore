package com.gtl.enhancedcore.common.cloud;

import java.util.*;

/** The small and large chemical recipe maps are one explicit cloud mode group. */
public final class CloudChemicalModes {
    private static final String CHEMICAL = "gtceu:chemical_reactor";
    private static final String LARGE_CHEMICAL = "gtceu:large_chemical_reactor";
    private CloudChemicalModes() {}

    public static boolean equivalent(String first, String second) {
        return first != null && second != null && (first.equals(second)
                || chemical(first) && chemical(second));
    }
    private static boolean chemical(String mode) {
        return CHEMICAL.equals(mode) || LARGE_CHEMICAL.equals(mode);
    }
    public static <T> boolean allows(Set<T> declared, T candidate) {
        return declared.isEmpty() || declared.stream().anyMatch(mode -> equivalent(mode.toString(), candidate.toString()));
    }
    /** Prefer the endpoint's own map when both maps contain the same recipe. */
    public static List<String> queryModes(String mode) {
        if (CHEMICAL.equals(mode)) return List.of(CHEMICAL, LARGE_CHEMICAL);
        if (LARGE_CHEMICAL.equals(mode)) return List.of(LARGE_CHEMICAL, CHEMICAL);
        return List.of(mode);
    }
    public static Set<String> catalogueTypes(Set<String> declared) {
        var result = new TreeSet<String>();
        declared.forEach(mode -> result.addAll(queryModes(mode)));
        return Set.copyOf(result);
    }
}
