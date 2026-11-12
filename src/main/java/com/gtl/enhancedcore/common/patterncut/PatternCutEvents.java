package com.gtl.enhancedcore.common.patterncut;

import com.gtl.enhancedcore.GTLEnhancedcore;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = GTLEnhancedcore.MOD_ID)
public final class PatternCutEvents {
    private PatternCutEvents() {}

    @SubscribeEvent public static void rightClickAir(PlayerInteractEvent.RightClickItem event) {
        if (!PatternCutNetwork.isTool(event.getItemStack())) return;
        if (event.getEntity() instanceof ServerPlayer player)
            PatternCutNetwork.open(player, event.getHand());
        event.setCancellationResult(InteractionResult.sidedSuccess(event.getLevel().isClientSide()));
        event.setCanceled(true);
    }
}
