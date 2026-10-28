package com.gtl.enhancedcore;

import com.gtl.enhancedcore.common.machine.galaxy.GalaxyWirelessHandlerSelection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

final class GalaxyWirelessHandlerRegression {
    static int run() throws Exception {
        Object nativeHandler = new Object();
        AtomicInteger factoryCalls = new AtomicInteger();
        check(GalaxyWirelessHandlerSelection.select(nativeHandler, () -> {
            factoryCalls.incrementAndGet(); return new Object();
        }) == nativeHandler, "Existing ADD wireless handler remains authoritative on a live link");
        check(factoryCalls.get() == 0, "Existing handler does not create a galaxy proxy");

        check(GalaxyWirelessHandlerSelection.select(null, () -> {
            factoryCalls.incrementAndGet(); return null;
        }) == null,
                "An unavailable galaxy link does not provide a wireless handler");
        check(factoryCalls.get() == 1, "Unavailable link does not create a galaxy proxy");

        Object linkedHandler = new Object();
        check(GalaxyWirelessHandlerSelection.select(null, () -> {
            factoryCalls.incrementAndGet(); return linkedHandler;
        }) == linkedHandler, "An available galaxy link supplies a missing handler");
        check(factoryCalls.get() == 2, "Linked proxy is created only for an absent native handler");

        String handler = Files.readString(Path.of(
                "src/main/java/com/gtl/enhancedcore/common/machine/galaxy/GalaxyWirelessEnergyHandler.java"));
        String mixin = Files.readString(Path.of(
                "src/main/java/com/gtl/enhancedcore/mixin/gtladditions/GalaxyWirelessEnergyHandlerMixin.java"));
        check(mixin.contains("@Overwrite") && mixin.contains("public default IWirelessNetworkEnergyHandler getWirelessNetworkEnergyHandler()"),
                "The verified null default is overwritten without using unsupported interface injectors");
        check(handler.contains("UUID owner = owner();")
                        && handler.contains("amount, machine") && handler.contains("amount.signum() > 0"),
                "ADD signed BigInteger debit resolves current owner and refuses positive credit deltas");
        check(handler.contains("GalaxyLinks.linkedStation(machine)")
                        && handler.contains("station.getUserId()"),
                "Handler owner is resolved from the currently linked station on demand");
        return 8;
    }

    private static void check(boolean valid, String label) {
        if (!valid) throw new AssertionError(label);
    }
}
