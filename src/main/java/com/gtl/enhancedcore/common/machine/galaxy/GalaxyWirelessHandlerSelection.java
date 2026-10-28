package com.gtl.enhancedcore.common.machine.galaxy;

import java.util.function.Supplier;

/** Keeps native wireless handlers authoritative and only fills a missing handler on a live link. */
public final class GalaxyWirelessHandlerSelection {
    private GalaxyWirelessHandlerSelection() {}

    public static <T> T select(T original, Supplier<? extends T> linkedHandler) {
        return original != null ? original : linkedHandler.get();
    }
}
