package com.gtl.enhancedcore.common.world.personalplot;

import com.gtl.enhancedcore.GTLEnhancedcore;
import com.gtl.enhancedcore.common.world.plot.PlotPresetShare;
import com.gtl.enhancedcore.common.world.plot.PlotSettings;
import java.util.Optional;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

/** Separate, strictly directed channel. Settings are bounded and validated again on the server thread. */
public final class PersonalPlotNetwork {
    private static final String PROTOCOL = "3";
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(GTLEnhancedcore.MOD_ID, "personal_plot"), () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);
    private static boolean initialized;
    public enum Action { OPEN, CREATE, TEMPLATE, TRAVEL, RETURN }
    public record Snapshot(boolean dedicated, boolean canEditTemplate, boolean canCreatePersonal,
                           boolean canCustomizePersonal, boolean templateReady, boolean templatePending,
                           PlotSettings templateSettings, PlotSettings activeTemplateSettings,
                           PersonalPlotWorlds.State state, PlotSettings settings,
                           boolean inOwnWorld, boolean canReturn, String notice) {}
    private record Request(Action action, String settings) {}
    private record Environment(boolean dedicated) {}
    private record DimensionAdded(ResourceLocation dimension) {}
    private record DimensionRemoved(ResourceLocation dimension) {}
    public enum ManagementAction { LIST, PREPARE_DELETE, CONFIRM_DELETE, CREATE_FOR }
    public record Entry(UUID owner, String name, PersonalPlotWorlds.State state, boolean canCreate, boolean canDelete) {}
    public record ManagementSnapshot(boolean canManageAll, List<Entry> entries, int page, int pages,
                                     UUID deleteOwner, UUID deleteToken, String notice) {
        public ManagementSnapshot { entries = List.copyOf(entries); }
    }
    private record ManagementRequest(ManagementAction action, UUID owner, UUID token, int page) {}
    private PersonalPlotNetwork() {}
    public static synchronized void register() {
        if (initialized) return;
        CHANNEL.registerMessage(0, Request.class, (p, b) -> { b.writeEnum(p.action); b.writeUtf(p.settings, PlotPresetShare.MAX_TEXT_LENGTH); },
                b -> new Request(b.readEnum(Action.class), b.readUtf(PlotPresetShare.MAX_TEXT_LENGTH)), PersonalPlotNetwork::request,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(1, Snapshot.class, PersonalPlotNetwork::encode, PersonalPlotNetwork::decode, (packet, supplier) -> {
            NetworkEvent.Context ctx = supplier.get();
            ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                com.gtl.enhancedcore.common.item.PlotControllerEnvironment.accept(packet.dedicated);
                com.gtl.enhancedcore.client.plotinfo.PersonalPlotWorldScreen.receiveSnapshot(packet);
            })); ctx.setPacketHandled(true);
        }, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(2, Environment.class, (p, b) -> b.writeBoolean(p.dedicated), b -> new Environment(b.readBoolean()), (packet, supplier) -> {
            NetworkEvent.Context ctx = supplier.get(); ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                    () -> () -> com.gtl.enhancedcore.common.item.PlotControllerEnvironment.accept(packet.dedicated))); ctx.setPacketHandled(true);
        }, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(3, DimensionAdded.class, (p, b) -> b.writeResourceLocation(p.dimension), b -> new DimensionAdded(b.readResourceLocation()), (packet, supplier) -> {
            NetworkEvent.Context ctx = supplier.get(); ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                var connection = net.minecraft.client.Minecraft.getInstance().getConnection();
                if (connection != null) connection.levels().add(ResourceKey.create(Registries.DIMENSION, packet.dimension));
            })); ctx.setPacketHandled(true);
        }, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(4, ManagementRequest.class, (p, b) -> {
            b.writeEnum(p.action); writeUuid(b, p.owner); writeUuid(b, p.token); b.writeVarInt(p.page);
        }, b -> new ManagementRequest(b.readEnum(ManagementAction.class), readUuid(b), readUuid(b), b.readVarInt()),
                PersonalPlotNetwork::manage, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(5, ManagementSnapshot.class, PersonalPlotNetwork::encodeManagement,
                PersonalPlotNetwork::decodeManagement, (packet, supplier) -> {
                    NetworkEvent.Context ctx = supplier.get();
                    ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                            com.gtl.enhancedcore.client.plotinfo.PersonalPlotManagementScreen.receiveSnapshot(packet)));
                    ctx.setPacketHandled(true);
                }, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(6, DimensionRemoved.class, (p, b) -> b.writeResourceLocation(p.dimension),
                b -> new DimensionRemoved(b.readResourceLocation()), (packet, supplier) -> {
                    NetworkEvent.Context ctx = supplier.get();
                    ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                        var connection = net.minecraft.client.Minecraft.getInstance().getConnection();
                        if (connection != null) connection.levels().remove(ResourceKey.create(Registries.DIMENSION, packet.dimension));
                    })); ctx.setPacketHandled(true);
                }, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        initialized = true;
    }
    public static void open(ServerPlayer player) { send(player, ""); }
    public static void sendEnvironment(ServerPlayer player) { CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new Environment(player.server.isDedicatedServer())); }
    public static void broadcastDimension(ResourceKey<Level> dimension) { CHANNEL.send(PacketDistributor.ALL.noArg(), new DimensionAdded(dimension.location())); }
    public static void broadcastDimensionRemoval(ResourceKey<Level> dimension) { CHANNEL.send(PacketDistributor.ALL.noArg(), new DimensionRemoved(dimension.location())); }
    private static void send(ServerPlayer player, String notice) { CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), PersonalPlotWorlds.snapshot(player, notice)); }
    public static void requestOpen() { CHANNEL.sendToServer(new Request(Action.OPEN, "")); }
    public static void requestCreate(PlotSettings settings) { CHANNEL.sendToServer(new Request(Action.CREATE, settings == null ? "" : PlotPresetShare.encode(settings))); }
    public static void requestTemplate(PlotSettings settings) { CHANNEL.sendToServer(new Request(Action.TEMPLATE, PlotPresetShare.encode(settings))); }
    public static void requestTravel() { CHANNEL.sendToServer(new Request(Action.TRAVEL, "")); }
    public static void requestReturn() { CHANNEL.sendToServer(new Request(Action.RETURN, "")); }
    public static void requestManagement(int page) { sendManagement(ManagementAction.LIST, null, null, page); }
    public static void requestPrepareDelete(UUID owner, int page) { sendManagement(ManagementAction.PREPARE_DELETE, owner, null, page); }
    public static void requestConfirmDelete(UUID owner, UUID token, int page) { sendManagement(ManagementAction.CONFIRM_DELETE, owner, token, page); }
    public static void requestCreateFor(UUID owner, int page) { sendManagement(ManagementAction.CREATE_FOR, owner, null, page); }
    private static void sendManagement(ManagementAction action, UUID owner, UUID token, int page) {
        CHANNEL.sendToServer(new ManagementRequest(action, owner, token, page));
    }
    private static void manage(ManagementRequest packet, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context ctx = supplier.get(); ServerPlayer player = ctx.getSender();
        if (player != null) ctx.enqueueWork(() -> CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                PersonalPlotManagement.handle(player, packet.action, packet.owner, packet.token, packet.page)));
        ctx.setPacketHandled(true);
    }
    private static void request(Request packet, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context ctx = supplier.get(); ServerPlayer player = ctx.getSender();
        if (player != null) ctx.enqueueWork(() -> {
            PlotSettings settings = null;
            try {
                // A dedicated server never accepts player-controlled terrain for CREATE.
                if (packet.action == Action.TEMPLATE || packet.action == Action.CREATE && !player.server.isDedicatedServer())
                    settings = PlotPresetShare.decode(packet.settings);
                String notice = PersonalPlotWorlds.handle(player, packet.action, settings);
                if (packet.action == Action.CREATE && notice.startsWith("gtl_enhancedcore.personal_plot.created_"))
                    PersonalPlotManagement.invalidate(player.server, player.getUUID());
                send(player, notice);
            } catch (IllegalArgumentException invalid) { send(player, "gtl_enhancedcore.personal_plot.invalid_settings"); }
        }); ctx.setPacketHandled(true);
    }
    private static void writeSettings(FriendlyByteBuf b, PlotSettings settings) {
        b.writeBoolean(settings != null); if (settings != null) b.writeUtf(PlotPresetShare.encode(settings), PlotPresetShare.MAX_TEXT_LENGTH);
    }
    private static PlotSettings readSettings(FriendlyByteBuf b) { return b.readBoolean() ? PlotPresetShare.decode(b.readUtf(PlotPresetShare.MAX_TEXT_LENGTH)) : null; }
    private static void encode(Snapshot p, FriendlyByteBuf b) {
        b.writeBoolean(p.dedicated); b.writeBoolean(p.canEditTemplate); b.writeBoolean(p.canCreatePersonal); b.writeBoolean(p.canCustomizePersonal);
        b.writeBoolean(p.templateReady); b.writeBoolean(p.templatePending); writeSettings(b, p.templateSettings); writeSettings(b, p.activeTemplateSettings);
        b.writeEnum(p.state); writeSettings(b, p.settings); b.writeBoolean(p.inOwnWorld); b.writeBoolean(p.canReturn); b.writeUtf(p.notice, 192);
    }
    private static Snapshot decode(FriendlyByteBuf b) {
        return new Snapshot(b.readBoolean(), b.readBoolean(), b.readBoolean(), b.readBoolean(), b.readBoolean(), b.readBoolean(),
                readSettings(b), readSettings(b), b.readEnum(PersonalPlotWorlds.State.class), readSettings(b), b.readBoolean(), b.readBoolean(), b.readUtf(192));
    }
    private static void writeUuid(FriendlyByteBuf b, UUID value) { b.writeBoolean(value != null); if (value != null) b.writeUUID(value); }
    private static UUID readUuid(FriendlyByteBuf b) { return b.readBoolean() ? b.readUUID() : null; }
    private static void encodeManagement(ManagementSnapshot p, FriendlyByteBuf b) {
        b.writeBoolean(p.canManageAll); b.writeVarInt(p.entries.size());
        for (Entry entry : p.entries) {
            b.writeUUID(entry.owner); b.writeUtf(entry.name, 64); b.writeEnum(entry.state);
            b.writeBoolean(entry.canCreate); b.writeBoolean(entry.canDelete);
        }
        b.writeVarInt(p.page); b.writeVarInt(p.pages); writeUuid(b, p.deleteOwner); writeUuid(b, p.deleteToken); b.writeUtf(p.notice, 192);
    }
    private static ManagementSnapshot decodeManagement(FriendlyByteBuf b) {
        boolean all = b.readBoolean(); int count = b.readVarInt();
        if (count < 0 || count > PersonalPlotManagement.PAGE_SIZE) throw new IllegalArgumentException("Invalid personal plot management page");
        List<Entry> entries = new java.util.ArrayList<>(count);
        for (int i = 0; i < count; i++) entries.add(new Entry(b.readUUID(), b.readUtf(64), b.readEnum(PersonalPlotWorlds.State.class), b.readBoolean(), b.readBoolean()));
        return new ManagementSnapshot(all, entries, b.readVarInt(), b.readVarInt(), readUuid(b), readUuid(b), b.readUtf(192));
    }
}
