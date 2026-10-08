package com.gtl.enhancedcore.common.performance;

/** Hardware-based heuristic; revised defaults still need in-game A/B validation. */
public final class ThreadBudget {
    private static final long GIB = 1L << 30;

    private ThreadBudget() {}

    /**
     * Plans worker counts without reading runtime state or creating threads.
     * Nonpositive CPU counts become one; nonpositive heap sizes use the lowest memory tier.
     * Heap thresholds are inclusive byte counts, not rounded GiB values.
     */
    public static Plan plan(int availableProcessors, long maxHeapBytes) {
        int cpus = Math.max(1, availableProcessors);
        int reserve = Math.max(2, cpus / 4);
        int budget = Math.max(2, cpus - reserve);
        int builder = Math.min(8, Math.max(1, budget / 3));
        // Budget the pools independently; keep background throughput with bounded high-core scaling.
        int background = Math.max(1, Math.min(24, cpus - 3));

        if (maxHeapBytes <= 4 * GIB) {
            builder = Math.min(builder, 2);
            background = Math.min(background, 4);
        } else if (maxHeapBytes <= 6 * GIB) {
            builder = Math.min(builder, 3);
            background = Math.min(background, 6);
        }
        return new Plan(background, builder);
    }

    public record Plan(int backgroundThreads, int builderThreads) {}
}
