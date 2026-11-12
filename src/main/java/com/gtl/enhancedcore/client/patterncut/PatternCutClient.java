package com.gtl.enhancedcore.client.patterncut;

import com.gtl.enhancedcore.common.patterncut.PatternCutNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Metadata snapshots only; pattern inventory remains on the server. */
@Mod.EventBusSubscriber(modid = "gtl_enhancedcore", value = Dist.CLIENT)
public final class PatternCutClient {
    private static PatternCutNetwork.View view;
    private static ClientPacketListener connection;
    private static ResourceLocation dimension;
    private static String query = "";

    private PatternCutClient() {}
    public static PatternCutNetwork.View view() { return view; }
    static String query() { return query; }
    static void query(String value) { query = value; }

    public static void accept(PatternCutNetwork.View received, boolean open) {
        Minecraft mc = Minecraft.getInstance();
        synchronizeSession(mc);
        if (mc.player == null || mc.level == null
                || !PatternCutNetwork.isTool(mc.player.getItemInHand(received.hand()))) return;
        view = received;
        if (mc.screen instanceof PatternCutScreen screen) screen.refresh();
        else if (open) mc.setScreen(new PatternCutScreen());
    }

    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        synchronizeSession(mc);
        if (view != null && (mc.player == null
                || !PatternCutNetwork.isTool(mc.player.getItemInHand(view.hand())))) view = null;
    }

    private static void synchronizeSession(Minecraft mc) {
        ResourceLocation current = mc.level == null ? null : mc.level.dimension().location();
        if (connection != mc.getConnection() || !java.util.Objects.equals(dimension, current)) {
            connection = mc.getConnection(); dimension = current; view = null; query = "";
        }
    }
}
