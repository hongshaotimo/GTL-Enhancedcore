package com.gtl.enhancedcore.client.cloud;

import com.gtl.enhancedcore.GTLEnhancedcore;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.loading.FMLPaths;

@Mod.EventBusSubscriber(modid = GTLEnhancedcore.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class CloudSearchClientResources {
    private CloudSearchClientResources() {}
    @SubscribeEvent public static void setup(FMLClientSetupEvent event) {
        CloudTerminalViewState.preload(FMLPaths.CONFIGDIR.get().resolve("GTL-Enhancedcore/cloud-terminal-views.json"));
    }
    @SubscribeEvent public static void reload(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((ResourceManagerReloadListener) manager -> CloudPatternNameSearch.clearCache());
    }
}
