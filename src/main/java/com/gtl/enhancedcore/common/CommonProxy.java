package com.gtl.enhancedcore.common;

import com.gtl.enhancedcore.network.GTLEnhancedcoreNetworkHandler;
import com.gtl.enhancedcore.integration.avaritia.FlightStateConcurrency;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;

/**
 * Common-side lifecycle wiring, without client classes or unused AE grid indexes.
 */
public final class CommonProxy {

    private CommonProxy() {}

    public static void register(IEventBus bus) {
        bus.addListener((FMLCommonSetupEvent event) -> event.enqueueWork(() -> {
            GTLEnhancedcoreNetworkHandler.init();
            FlightStateConcurrency.install();
        }));
    }
}
