package com.gtl.enhancedcore.common.performance.structure;

import com.gtl.enhancedcore.GTLEnhancedcore;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = GTLEnhancedcore.MOD_ID)
public final class StructureSearchEvents {
    private StructureSearchEvents() {}

    // Forge 6 names generated handlers with the event's simple name; Unload overloads collide.
    @SubscribeEvent public static void onChunkLoad(ChunkEvent.Load event) { chunk(event); }
    @SubscribeEvent public static void onChunkUnload(ChunkEvent.Unload event) { chunk(event); }

    private static void chunk(ChunkEvent event) {
        if (event.getLevel() instanceof ServerLevel level) {
            var pos = event.getChunk().getPos();
            StructureSearchScheduler.watches(level).chunkChanged(pos.x, pos.z);
        }
    }

    @SubscribeEvent public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) StructureSearchScheduler.watches(level).clear();
    }
}
