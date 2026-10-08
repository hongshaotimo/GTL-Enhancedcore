package com.gtl.enhancedcore.common.recipe;

/** Only the four TieredParallelMachine controllers opt into this penalty. */
public final class IvMaintenancePolicy {
    private IvMaintenancePolicy() {}

    public static double durationMultiplier(double value) {
        return Double.isFinite(value) ? Math.max(1.0, value) : 1.0;
    }
}
