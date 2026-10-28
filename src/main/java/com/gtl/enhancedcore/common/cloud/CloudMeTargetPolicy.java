package com.gtl.enhancedcore.common.cloud;

/** The native GTLCore selector may address exactly these registered cloud machines. */
public final class CloudMeTargetPolicy {
    private CloudMeTargetPolicy() {}
    public static boolean registered(String namespace, String path) {
        return "gtl_enhancedcore".equals(namespace)
                && ("cloud_pattern_library".equals(path) || "cloud_pattern_buffer".equals(path));
    }
}
