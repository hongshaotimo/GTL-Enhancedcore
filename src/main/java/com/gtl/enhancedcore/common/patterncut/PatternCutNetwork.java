package com.gtl.enhancedcore.common.patterncut;

import com.gtl.enhancedcore.GTLEnhancedcore;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.function.Supplier;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

@Mod.EventBusSubscriber(modid = GTLEnhancedcore.MOD_ID)
public final class PatternCutNetwork {
    private static final String PROTOCOL = "2";
    private static final ResourceLocation TOOL = new ResourceLocation("gtlcore", "me_pattern_buffer_cut");
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(GTLEnhancedcore.MOD_ID, "pattern_cut"), () -> PROTOCOL,
            PROTOCOL::equals, PROTOCOL::equals);
    private static final Map<ServerPlayer, Integer> LAST_REQUEST = new WeakHashMap<>();
    private static boolean registered;

    public record View(InteractionHand hand, UUID selected, boolean smart,
                       List<PatternCutStore.Entry> entries, Component message) {}

    private record Open(InteractionHand hand) {}
    private record Refresh(InteractionHand hand) {}
    private record Select(InteractionHand hand, UUID id) {}
    private record Mode(InteractionHand hand, boolean smart) {}
    private record Snapshot(View view, boolean open) {}

    private PatternCutNetwork() {}

    public static synchronized void register() {
        if (registered) return;
        CHANNEL.registerMessage(0, Open.class, (p, b) -> b.writeEnum(p.hand),
                b -> new Open(b.readEnum(InteractionHand.class)), PatternCutNetwork::openPacket,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(1, Refresh.class, (p, b) -> b.writeEnum(p.hand),
                b -> new Refresh(b.readEnum(InteractionHand.class)), PatternCutNetwork::refreshPacket,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(2, Select.class, (p, b) -> { b.writeEnum(p.hand); b.writeUUID(p.id); },
                b -> new Select(b.readEnum(InteractionHand.class), b.readUUID()), PatternCutNetwork::selectPacket,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(3, Mode.class, (p, b) -> { b.writeEnum(p.hand); b.writeBoolean(p.smart); },
                b -> new Mode(b.readEnum(InteractionHand.class), b.readBoolean()), PatternCutNetwork::modePacket,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(4, Snapshot.class, PatternCutNetwork::encode, PatternCutNetwork::decode,
                (packet, supplier) -> {
                    NetworkEvent.Context context = supplier.get();
                    context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                            () -> () -> com.gtl.enhancedcore.client.patterncut.PatternCutClient.accept(packet.view, packet.open)));
                    context.setPacketHandled(true);
                }, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        registered = true;
    }

    public static boolean isTool(ItemStack stack) {
        return !stack.isEmpty() && BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(TOOL);
    }

    public static void open(ServerPlayer player, InteractionHand hand) {
        if (!validHand(player, hand)) return;
        sendView(player, PatternCutService.open(player, hand), true);
    }

    public static void sendView(ServerPlayer player, View view, boolean open) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new Snapshot(view, open));
    }

    public static void requestOpen(InteractionHand hand) { CHANNEL.sendToServer(new Open(hand)); }
    public static void requestRefresh(InteractionHand hand) { CHANNEL.sendToServer(new Refresh(hand)); }
    public static void requestSelect(InteractionHand hand, UUID id) { CHANNEL.sendToServer(new Select(hand, id)); }
    public static void requestMode(InteractionHand hand, boolean smart) { CHANNEL.sendToServer(new Mode(hand, smart)); }

    private static boolean validHand(ServerPlayer player, InteractionHand hand) {
        return isTool(player.getItemInHand(hand));
    }

    private static boolean admit(ServerPlayer player) {
        int now = player.serverLevel().getServer().getTickCount();
        synchronized (LAST_REQUEST) {
            Integer previous = LAST_REQUEST.get(player);
            if (previous != null && now - previous < 2) return false;
            LAST_REQUEST.put(player, now);
            return true;
        }
    }

    private static void openPacket(Open packet, Supplier<NetworkEvent.Context> supplier) {
        var context = supplier.get(); var player = context.getSender();
        if (player != null) context.enqueueWork(() -> {
            if (validHand(player, packet.hand))
                sendView(player, PatternCutService.open(player, packet.hand), true);
        });
        context.setPacketHandled(true);
    }

    private static void refreshPacket(Refresh packet, Supplier<NetworkEvent.Context> supplier) {
        var context = supplier.get(); var player = context.getSender();
        if (player != null) context.enqueueWork(() -> {
            if (admit(player) && validHand(player, packet.hand))
                sendView(player, PatternCutService.view(player, packet.hand, null), false);
        });
        context.setPacketHandled(true);
    }

    private static void selectPacket(Select packet, Supplier<NetworkEvent.Context> supplier) {
        var context = supplier.get(); var player = context.getSender();
        if (player != null) context.enqueueWork(() -> {
            if (validHand(player, packet.hand))
                sendView(player, PatternCutService.select(player, packet.hand, packet.id), false);
        });
        context.setPacketHandled(true);
    }

    private static void modePacket(Mode packet, Supplier<NetworkEvent.Context> supplier) {
        var context = supplier.get(); var player = context.getSender();
        if (player != null) context.enqueueWork(() -> {
            if (validHand(player, packet.hand))
                sendView(player, PatternCutService.mode(player, packet.hand, packet.smart), false);
        });
        context.setPacketHandled(true);
    }

    private static void encode(Snapshot packet, FriendlyByteBuf b) {
        View v = packet.view;
        b.writeEnum(v.hand);
        b.writeBoolean(v.selected != null); if (v.selected != null) b.writeUUID(v.selected);
        b.writeBoolean(v.smart);
        b.writeVarInt(v.entries.size());
        for (PatternCutStore.Entry entry : v.entries) {
            b.writeUUID(entry.id()); b.writeUtf(clip(entry.name(), 256), 256); b.writeUtf(clip(entry.dimension(), 128), 128);
            b.writeBlockPos(entry.source()); b.writeVarInt(entry.patterns()); b.writeLong(entry.created());
        }
        b.writeComponent(v.message); b.writeBoolean(packet.open);
    }

    private static Snapshot decode(FriendlyByteBuf b) {
        InteractionHand hand = b.readEnum(InteractionHand.class);
        UUID selected = b.readBoolean() ? b.readUUID() : null;
        boolean smart = b.readBoolean();
        int entryCount = b.readVarInt();
        if (entryCount < 0 || entryCount > b.readableBytes() / 35)
            throw new IllegalArgumentException("invalid pattern cut entry count");
        List<PatternCutStore.Entry> entries = new ArrayList<>(entryCount);
        for (int i = 0; i < entryCount; i++) entries.add(new PatternCutStore.Entry(
                b.readUUID(), b.readUtf(256), b.readUtf(128), b.readBlockPos(), b.readVarInt(), b.readLong()));
        return new Snapshot(new View(hand, selected, smart, List.copyOf(entries), b.readComponent()), b.readBoolean());
    }

    private static String clip(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }

    @SubscribeEvent public static void stopped(ServerStoppedEvent event) {
        synchronized (LAST_REQUEST) { LAST_REQUEST.clear(); }
    }
}
