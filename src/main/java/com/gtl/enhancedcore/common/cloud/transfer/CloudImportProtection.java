package com.gtl.enhancedcore.common.cloud.transfer;

import com.gregtechceu.gtceu.api.machine.MetaMachine;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.common.Mod;

/** A quarantined source is evidence, not a source of fresh item drops. */
@Mod.EventBusSubscriber(modid = "gtl_enhancedcore", bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class CloudImportProtection {
    private CloudImportProtection() {}
    @SubscribeEvent public static void tick(net.minecraftforge.event.TickEvent.ServerTickEvent event) {
        if (event.phase == net.minecraftforge.event.TickEvent.Phase.END) CloudNetworkPatternImport.tickAll(event.getServer());
    }
    @SubscribeEvent public static void stopping(net.minecraftforge.event.server.ServerStoppingEvent event) { CloudImportIo.drain(); }
    @SubscribeEvent public static void stopped(net.minecraftforge.event.server.ServerStoppedEvent event) { CloudImportIo.drain(); CloudNetworkPatternImport.stopped(event.getServer()); CloudImportJournal.closeCatalogs(); }
    private static boolean quarantined(Object machine) {
        return machine instanceof CloudImportSourceAccess source && (source.cloud$importQuarantined() || CloudNetworkPatternImport.sourceLocked(source));
    }
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void selectSource(net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) || !player.isShiftKeyDown()) return;
        var itemId = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(event.getItemStack().getItem());
        if (itemId == null || !(itemId.toString().equals("gtceu:data_stick") || itemId.toString().equals("gtmadvancedhatch:tool_net_data_stick"))) return;
        var machine = MetaMachine.getMachine(event.getLevel(), event.getPos());
        if (!(machine instanceof org.gtlcore.gtlcore.common.machine.multiblock.part.ae.MEPatternBufferPartMachine buffer)) return;
        boolean captured = CloudImportSourceAddress.capture(player, buffer, event.getItemStack());
        player.displayClientMessage(net.minecraft.network.chat.Component.translatable(captured ? "gtl_enhancedcore.cloud.source.captured" : "gtl_enhancedcore.cloud.status.denied"), true);
        event.setCanceled(true); event.setCancellationResult(captured ? net.minecraft.world.InteractionResult.SUCCESS : net.minecraft.world.InteractionResult.FAIL);
    }
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void breaking(BlockEvent.BreakEvent event) {
        if (!event.isCanceled() && quarantined(MetaMachine.getMachine(event.getLevel(), event.getPos()))) {
            event.setCanceled(true);
            event.getPlayer().displayClientMessage(net.minecraft.network.chat.Component.translatable("gtl_enhancedcore.cloud.status.review"), true);
        }
    }
    @SubscribeEvent public static void explosion(ExplosionEvent.Detonate event) {
        event.getAffectedBlocks().removeIf(pos -> quarantined(MetaMachine.getMachine(event.getLevel(), pos)));
    }
}
