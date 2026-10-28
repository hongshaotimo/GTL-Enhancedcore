package com.gtl.enhancedcore.common.cloud;

import java.util.*;

/** Pure ownership/claim decisions; runtime callers still validate structure, grid and ACL. */
public final class CloudEndpointPolicy {
    private CloudEndpointPolicy() {}
    public static <T> List<T> preferredModes(List<T> supported, T selected) {
        var modes = new LinkedHashSet<T>();
        if (selected != null && supported.contains(selected)) modes.add(selected);
        modes.addAll(supported);
        return List.copyOf(modes);
    }
    public static boolean ownsJob(UUID assigned, UUID current, UUID original, boolean originalAddress) {
        return current != null && (assigned != null ? assigned.equals(current)
                : originalAddress && current.equals(original));
    }
}
