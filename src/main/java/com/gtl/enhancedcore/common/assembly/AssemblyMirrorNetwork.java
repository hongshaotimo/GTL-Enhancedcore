package com.gtl.enhancedcore.common.assembly;

import com.gtl.enhancedcore.GTLEnhancedcore;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public final class AssemblyMirrorNetwork {
    public static final int SELECT_SOURCE = 0, UNBIND = 1, REBIND = 2, CLEAR_NETWORK = 3;
    private static final String PROTOCOL = "1";
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(GTLEnhancedcore.MOD_ID, "assembly_mirror"), () -> PROTOCOL,
            PROTOCOL::equals, PROTOCOL::equals);
    private static boolean registered;

    public record Source(BlockPos pos, String name, boolean superBuffer, int mirrors, String blockedReason) {}
    public record Mirror(BlockPos pos, BlockPos source, String sourceName, boolean superBuffer, boolean loaded) {}
    public record View(InteractionHand hand, ResourceLocation dimension, BlockPos anchor, String networkName,
                       boolean online, BlockPos selectedSource, String selectedName, List<Source> sources,
                       List<Mirror> mirrors, Component message) {}
    private record Open(InteractionHand hand) {}
    private record Refresh(InteractionHand hand) {}
    private record Action(InteractionHand hand, int action, BlockPos pos) {}
    private record Snapshot(View view, boolean open) {}

    private AssemblyMirrorNetwork() {}

    public static synchronized void register() {
        if (registered) return;
        CHANNEL.registerMessage(0, Open.class, (p, b) -> b.writeEnum(p.hand), b -> new Open(b.readEnum(InteractionHand.class)), AssemblyMirrorNetwork::openPacket, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(1, Refresh.class, (p, b) -> b.writeEnum(p.hand), b -> new Refresh(b.readEnum(InteractionHand.class)), AssemblyMirrorNetwork::refreshPacket, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(2, Action.class, (p, b) -> { b.writeEnum(p.hand); b.writeVarInt(p.action); b.writeBlockPos(p.pos); },
                b -> new Action(b.readEnum(InteractionHand.class), b.readVarInt(), b.readBlockPos()), AssemblyMirrorNetwork::actionPacket, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(3, Snapshot.class, (p, b) -> encode(p, b), AssemblyMirrorNetwork::decode, (p, ctx) -> {
            var context = ctx.get(); context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                    () -> () -> com.gtl.enhancedcore.client.assembly.AssemblyMirrorClient.accept(p.view, p.open)));
            context.setPacketHandled(true);
        }, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        registered = true;
    }

    private static void encode(Snapshot packet, net.minecraft.network.FriendlyByteBuf b) {
        View v = packet.view;
        b.writeEnum(v.hand); b.writeResourceLocation(v.dimension); b.writeBoolean(v.anchor != null); if (v.anchor != null) b.writeBlockPos(v.anchor);
        b.writeUtf(v.networkName); b.writeBoolean(v.online); b.writeBoolean(v.selectedSource != null); if (v.selectedSource != null) b.writeBlockPos(v.selectedSource);
        b.writeUtf(v.selectedName); b.writeVarInt(v.sources.size());
        for (Source s : v.sources) { b.writeBlockPos(s.pos); b.writeUtf(s.name); b.writeBoolean(s.superBuffer); b.writeVarInt(s.mirrors); b.writeUtf(s.blockedReason); }
        b.writeVarInt(v.mirrors.size());
        for (Mirror m : v.mirrors) { b.writeBlockPos(m.pos); b.writeBlockPos(m.source); b.writeUtf(m.sourceName); b.writeBoolean(m.superBuffer); b.writeBoolean(m.loaded); }
        b.writeComponent(v.message); b.writeBoolean(packet.open);
    }

    private static Snapshot decode(net.minecraft.network.FriendlyByteBuf b) {
        InteractionHand hand = b.readEnum(InteractionHand.class); ResourceLocation dimension = b.readResourceLocation();
        BlockPos anchor = b.readBoolean() ? b.readBlockPos() : null; String networkName = b.readUtf(); boolean online = b.readBoolean();
        BlockPos selected = b.readBoolean() ? b.readBlockPos() : null; String selectedName = b.readUtf();
        int sourceCount = b.readVarInt(); if (sourceCount < 0 || sourceCount > b.readableBytes() / 12) throw new IllegalArgumentException("invalid mirror source count");
        List<Source> sources = new ArrayList<>(sourceCount);
        for (int i = 0; i < sourceCount; i++) sources.add(new Source(b.readBlockPos(), b.readUtf(), b.readBoolean(), b.readVarInt(), b.readUtf()));
        int mirrorCount = b.readVarInt(); if (mirrorCount < 0 || mirrorCount > b.readableBytes() / 19) throw new IllegalArgumentException("invalid mirror count");
        List<Mirror> mirrors = new ArrayList<>(mirrorCount);
        for (int i = 0; i < mirrorCount; i++) mirrors.add(new Mirror(b.readBlockPos(), b.readBlockPos(), b.readUtf(), b.readBoolean(), b.readBoolean()));
        View view = new View(hand, dimension, anchor, networkName, online, selected, selectedName, List.copyOf(sources), List.copyOf(mirrors), b.readComponent());
        return new Snapshot(view, b.readBoolean());
    }

    public static void open(ServerPlayer player, InteractionHand hand) {
        if (validHand(player, hand)) send(player, hand, true, null);
    }
    public static void requestOpen(InteractionHand hand) { CHANNEL.sendToServer(new Open(hand)); }
    public static void requestRefresh(InteractionHand hand) { CHANNEL.sendToServer(new Refresh(hand)); }
    public static void requestAction(InteractionHand hand, int action, BlockPos pos) { CHANNEL.sendToServer(new Action(hand, action, pos)); }

    private static boolean validHand(ServerPlayer player, InteractionHand hand) {
        return AssemblyMirrorService.holding(player, hand);
    }
    private static void openPacket(Open packet, Supplier<NetworkEvent.Context> supplier) {
        var ctx = supplier.get(); var player = ctx.getSender(); if (player != null) ctx.enqueueWork(() -> { if (validHand(player, packet.hand)) send(player, packet.hand, true, null); }); ctx.setPacketHandled(true);
    }
    private static void refreshPacket(Refresh packet, Supplier<NetworkEvent.Context> supplier) {
        var ctx = supplier.get(); var player = ctx.getSender(); if (player != null) ctx.enqueueWork(() -> { if (validHand(player, packet.hand)) send(player, packet.hand, false, null); }); ctx.setPacketHandled(true);
    }
    private static void actionPacket(Action packet, Supplier<NetworkEvent.Context> supplier) {
        var ctx = supplier.get(); var player = ctx.getSender();
        if (player != null) ctx.enqueueWork(() -> {
            if (!validHand(player, packet.hand) || packet.action < SELECT_SOURCE || packet.action > CLEAR_NETWORK) return;
            Component message = AssemblyMirrorService.action(player, packet.hand, packet.action, packet.pos);
            send(player, packet.hand, false, message);
        }); ctx.setPacketHandled(true);
    }

    public static Component bindNetwork(ServerPlayer player, InteractionHand hand, BlockPos pos, Direction face) {
        if (!validHand(player, hand)) return AssemblyMirrorService.text("blocked");
        Component message = AssemblyMirrorService.bindNetwork(player, hand, pos, face);
        send(player, hand, true, message); return message;
    }
    public static boolean useMirror(ServerPlayer player, InteractionHand hand, BlockPos pos) {
        if (!validHand(player, hand) || !AssemblyMirrorService.isMirror(player, pos)) return false;
        Component message = AssemblyMirrorService.bindMirror(player, hand, pos, false);
        send(player, hand, false, message); player.displayClientMessage(message, true); return true;
    }
    private static void send(ServerPlayer player, InteractionHand hand, boolean open, Component message) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new Snapshot(AssemblyMirrorService.view(player, hand, message), open));
    }
}
