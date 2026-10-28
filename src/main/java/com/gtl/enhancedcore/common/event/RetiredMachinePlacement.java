package com.gtl.enhancedcore.common.event;

import com.gtl.enhancedcore.GTLEnhancedcore;
import com.gtl.enhancedcore.common.registration.RetiredMachines;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

/** Reject player and automation placement. Commands can only create the inert registered shell. */
@Mod.EventBusSubscriber(modid = GTLEnhancedcore.MOD_ID)
public final class RetiredMachinePlacement {
    private RetiredMachinePlacement() {}
    @SubscribeEvent
    public static void place(BlockEvent.EntityPlaceEvent event) {
        var id = ForgeRegistries.BLOCKS.getKey(event.getPlacedBlock().getBlock());
        if (id != null && RetiredMachines.contains(id.toString())) event.setCanceled(true);
    }
}
