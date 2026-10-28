package com.gtl.enhancedcore.common.performance;

/** Conservative startup policy for the optional FTB Chunks map pixel worker. */
public final class MapRasterThreadBudget {
    private static final long SIX_GIB = 6L << 30;

    private MapRasterThreadBudget() {}

    public static int plan(int availableProcessors, long maxHeapBytes, int configured, boolean automatic) {
        if (configured < 0 || configured > 8) throw new IllegalArgumentException("mapRasterThreads must be 0..8");
        if (configured > 0) return configured;
        if (!automatic || maxHeapBytes <= SIX_GIB) return 1;
        int cpus = Math.max(1, availableProcessors);
        if (cpus < 8) return 1;
        return Math.min(4, Math.max(1, cpus / 8));
    }
}
