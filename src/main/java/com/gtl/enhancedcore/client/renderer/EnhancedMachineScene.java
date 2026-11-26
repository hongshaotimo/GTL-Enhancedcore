package com.gtl.enhancedcore.client.renderer;

import com.gtl.enhancedcore.GTLEnhancedcore;
import com.gtladd.gtladditions.api.client.scene.PartKey;
import com.gtladd.gtladditions.api.client.scene.ProviderHandle;
import com.gtladd.gtladditions.api.client.scene.SceneRendering;
import net.minecraft.core.BlockPos;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod.EventBusSubscriber(modid = GTLEnhancedcore.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class EnhancedMachineScene {
    private static ProviderHandle provider;

    private EnhancedMachineScene() {}

    @SubscribeEvent
    public static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> { provider = SceneRendering.register(GTLEnhancedcore.id("machine_effects")); });
    }

    public static PartKey machine(BlockPos pos) { return PartKey.block(provider, pos, 0); }
    public static PartKey eye(BlockPos pos) { return PartKey.block(provider, pos, 1); }
}
