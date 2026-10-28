package com.gtl.enhancedcore.common.structure;

import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController;
import com.gtl.enhancedcore.GTLEnhancedcore;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

/** Left-click scan follows the interaction convention of ShanhaiUltimateTerminalInteractionHandler
 * (permission granted; 9f2ad31188c0b7f91676f7d5e177b55783c80808). Original shift-right actions stay intact. */
@Mod.EventBusSubscriber(modid = GTLEnhancedcore.MOD_ID)
public final class TerminalChamberScan {
    private TerminalChamberScan() {}
    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.HIGHEST)
    public static void leftClick(PlayerInteractEvent.LeftClickBlock event) {
        var id = ForgeRegistries.ITEMS.getKey(event.getItemStack().getItem());
        if (id == null || !id.toString().equals("gtlcore:ultimate_terminal")) return;
        if (!(MetaMachine.getMachine(event.getLevel(),event.getPos()) instanceof IMultiController controller)) return;
        event.setCanceled(true);
        if (event.getAction() != PlayerInteractEvent.LeftClickBlock.Action.START
                || !(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.getCooldowns().isOnCooldown(event.getItemStack().getItem())) return;
        player.getCooldowns().addCooldown(event.getItemStack().getItem(),20);
        TerminalChamberScanTasks.enqueue(controller,player);
    }
}
