package com.gtl.enhancedcore.common.config;

import com.gtl.enhancedcore.GTLEnhancedcore;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * 数据包重载后重新注入 ExtendedAE 无限元件材料（创造物品栏）。
 * 此时所有物品/流体已注册，且 EPPConfig 的列表在每次配置加载后会被重建，需要重新追加。
 * <p>
 * 压缩器配方本身不在这里加载：配方走 GTCEu 附属标准链路
 * （GTLEnhancedcoreGTAddon.addRecipes → GTDynamicDataPack → RecipeManager），
 * GTRecipeLookup 由 GTCEu 自行填充。
 */
@Mod.EventBusSubscriber(modid = GTLEnhancedcore.MOD_ID)
public final class InfinityCellReloadListener {

    private InfinityCellReloadListener() {
    }

    @SubscribeEvent
    public static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new PreparableReloadListener() {
            @Override
            public CompletableFuture<Void> reload(PreparationBarrier stage, ResourceManager resourceManager,
                                                   ProfilerFiller preparationsProfiler, ProfilerFiller reloadProfiler,
                                                   Executor backgroundExecutor, Executor gameExecutor) {
                return stage.wait(null).thenRunAsync(InfinityCellConfigInjector::inject, gameExecutor);
            }
        });
        GTLEnhancedcore.LOGGER.debug("[InfinityCell] Registered infinity cell material reload listener");
    }
}
