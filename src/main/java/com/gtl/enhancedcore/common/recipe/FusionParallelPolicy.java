package com.gtl.enhancedcore.common.recipe;

/** The modifier, GUI, Jade and tooltips must agree on the same fusion reactor limit. */
public final class FusionParallelPolicy {
    private FusionParallelPolicy() {}

    public static int limit(String namespace, String path) {
        if (!"gtceu".equals(namespace)) return 0;
        return switch (path) {
            case "luv_fusion_reactor", "zpm_fusion_reactor" -> 128;
            case "uv_fusion_reactor", "uhv_fusion_reactor", "uev_fusion_reactor" -> 512;
            default -> 0;
        };
    }
}
