package com.gtl.enhancedcore.client.assembly;

import com.gtl.enhancedcore.common.assembly.AssemblyMirrorNetwork;
import com.gtl.enhancedcore.common.item.AssemblyManagerItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Mirror snapshots stay separate from world-marker and teleport snapshots. */
@Mod.EventBusSubscriber(modid = "gtl_enhancedcore", value = Dist.CLIENT)
public final class AssemblyMirrorClient {
    private static AssemblyMirrorNetwork.View view;
    private static ClientPacketListener connection;
    private static final String[] queries = {"", ""};
    private static boolean mirrorsTab;

    private AssemblyMirrorClient() {}
    public static AssemblyMirrorNetwork.View view() { return view; }
    static String query(boolean mirrors) { return queries[mirrors ? 1 : 0]; }
    static void query(boolean mirrors, String value) { queries[mirrors ? 1 : 0] = value; }
    static boolean mirrorsTab() { return mirrorsTab; }
    static void mirrorsTab(boolean value) { mirrorsTab = value; }

    public static void accept(AssemblyMirrorNetwork.View received, boolean open) {
        Minecraft mc = Minecraft.getInstance();
        synchronizeSession(mc);
        if (mc.player == null || mc.level == null
                || !(mc.player.getItemInHand(received.hand()).getItem() instanceof AssemblyManagerItem)
                || !mc.level.dimension().location().equals(received.dimension())) return;
        view = received;
        if (mc.screen instanceof AssemblyManagerScreen screen) screen.refresh();
        else if (open) mc.setScreen(new AssemblyManagerScreen());
    }

    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        synchronizeSession(mc);
        if (view != null && (mc.player == null || mc.level == null
                || !(mc.player.getItemInHand(view.hand()).getItem() instanceof AssemblyManagerItem)
                || !mc.level.dimension().location().equals(view.dimension()))) view = null;
    }

    private static void synchronizeSession(Minecraft mc) {
        if (connection == mc.getConnection()) return;
        connection = mc.getConnection(); view = null;
        queries[0] = queries[1] = ""; mirrorsTab = false;
    }
}
