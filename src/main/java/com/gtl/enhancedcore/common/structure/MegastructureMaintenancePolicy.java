package com.gtl.enhancedcore.common.structure;

/** Only these two replacement structures gain an optional maintenance port. */
public final class MegastructureMaintenancePolicy {
    private MegastructureMaintenancePolicy() {}

    public static boolean matches(String namespace, String path) {
        return "gtceu".equals(namespace) && ("qft".equals(path) || "gravitation_shockburst".equals(path));
    }
}
