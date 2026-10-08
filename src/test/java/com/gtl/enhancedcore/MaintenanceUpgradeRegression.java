package com.gtl.enhancedcore;

import com.gtl.enhancedcore.common.structure.MegastructureMaintenancePolicy;

final class MaintenanceUpgradeRegression {
    static int run() {
        if (!MegastructureMaintenancePolicy.matches("gtceu", "qft")
                || !MegastructureMaintenancePolicy.matches("gtceu", "gravitation_shockburst"))
            throw new AssertionError("Missing optional maintenance target");
        for (String namespace : new String[]{"gtceu", "gtladditions", "other"}) {
            for (String path : new String[]{"qft", "gravitation_shockburst", "mega_canner", "cooling_tower", "QFT", ""}) {
                boolean expected = namespace.equals("gtceu") && (path.equals("qft") || path.equals("gravitation_shockburst"));
                if (MegastructureMaintenancePolicy.matches(namespace, path) != expected)
                    throw new AssertionError("Maintenance scope leaked: " + namespace + ":" + path);
            }
        }
        for (double multiplier : new double[]{-1, 0, 0.2, 0.4, 0.9, 1, Double.NaN, Double.POSITIVE_INFINITY}) {
            if (com.gtl.enhancedcore.common.recipe.IvMaintenancePolicy.durationMultiplier(multiplier) != 1)
                throw new AssertionError("IV maintenance discount or invalid multiplier admitted");
        }
        for (double multiplier : new double[]{1.01, 1.5, 2, 5}) {
            if (com.gtl.enhancedcore.common.recipe.IvMaintenancePolicy.durationMultiplier(multiplier) != multiplier)
                throw new AssertionError("IV maintenance slowdown removed");
        }
        return 32;
    }
}
