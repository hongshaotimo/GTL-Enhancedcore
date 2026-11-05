package com.gtl.enhancedcore.common.world.campus;

import com.gtl.enhancedcore.GTLEnhancedcore;
import com.gtl.enhancedcore.common.world.plot.PlotChunkGenerator;
import com.gtl.enhancedcore.common.world.plot.PlotPresetShare;
import com.gtl.enhancedcore.common.world.plot.PlotSettings;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityTeleportEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

/** Server-owned destination indexes; this channel cannot select a world or arbitrary coordinates. */
@Mod.EventBusSubscriber(modid = GTLEnhancedcore.MOD_ID)
public final class CampusTeleportNetwork {
    private static final String PROTOCOL = "5";
    private static final long SESSION_TICKS = 20 * 120, REQUEST_COOLDOWN = 20, OPEN_COOLDOWN = 10;
    private static final int SAFE_RADIUS = 8;
    private static final int MAX_LAYOUT_IDENTITY_LENGTH = 1024;
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(GTLEnhancedcore.MOD_ID, "campus_teleport"), () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);
    private static final Map<MinecraftServer, ServerSessions> SERVERS = new WeakHashMap<>();
    private static boolean initialized;
    public enum Result { SUCCESS, EXPIRED, INVALID_DEVICE, BLOCKED, INVALID_TARGET, COOLDOWN, UNSAFE, CANCELLED }
    private record Session(UUID id, ResourceKey<Level> dimension, BlockPos origin,
                           CentralTeleporterBlockEntity device, PlotChunkGenerator generator,
                           CampusPlanAccess.Plan plan, String layoutIdentity, long expiresAt) {}
    private static final class ServerSessions {
        final Map<UUID, Session> sessions = new HashMap<>();
        final Map<UUID, Long> attempts = new HashMap<>(), opens = new HashMap<>();
        void remove(UUID player) { sessions.remove(player); attempts.remove(player); opens.remove(player); }
    }
    private record Open(UUID id, BlockPos origin, ResourceLocation dimension, PlotSettings settings, String layoutIdentity) {
        static void encode(Open packet, FriendlyByteBuf buffer) {
            buffer.writeUUID(packet.id); buffer.writeBlockPos(packet.origin); buffer.writeResourceLocation(packet.dimension);
            buffer.writeUtf(PlotPresetShare.encode(packet.settings), PlotPresetShare.MAX_TEXT_LENGTH);
            buffer.writeUtf(packet.layoutIdentity, MAX_LAYOUT_IDENTITY_LENGTH);
        }
        static Open decode(FriendlyByteBuf buffer) {
            return new Open(buffer.readUUID(), buffer.readBlockPos(), buffer.readResourceLocation(),
                    PlotPresetShare.decode(buffer.readUtf(PlotPresetShare.MAX_TEXT_LENGTH)), buffer.readUtf(MAX_LAYOUT_IDENTITY_LENGTH));
        }
    }
    private record Travel(UUID id, int index) {}
    private record Close(UUID id) {}
    private record Reply(UUID id, Result result) {}
    private CampusTeleportNetwork() {}

    public static synchronized void register() {
        if (initialized) return;
        CHANNEL.registerMessage(0, Open.class, Open::encode, Open::decode, (p, supplier) -> {
            var ctx = supplier.get();
            ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                    {
                        if (!CampusPlanAccess.layoutIdentity(p.settings).equals(p.layoutIdentity)) {
                            close(p.id);
                            var player = net.minecraft.client.Minecraft.getInstance().player;
                            if (player != null) player.displayClientMessage(Component.translatable("gtl_enhancedcore.campus.teleport.result.layout_mismatch"), true);
                            return;
                        }
                        com.gtl.enhancedcore.client.plot.CampusPlanScreen.openTeleporter(p.settings, p.origin, p.dimension, p.id);
                    }));
            ctx.setPacketHandled(true);
        }, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(1, Travel.class, (p, b) -> { b.writeUUID(p.id); b.writeVarInt(p.index); },
                b -> new Travel(b.readUUID(), b.readVarInt()), CampusTeleportNetwork::travelPacket, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(2, Close.class, (p, b) -> b.writeUUID(p.id), b -> new Close(b.readUUID()),
                CampusTeleportNetwork::closePacket, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(3, Reply.class, (p, b) -> { b.writeUUID(p.id); b.writeEnum(p.result); },
                b -> new Reply(b.readUUID(), b.readEnum(Result.class)), (p, supplier) -> {
                    var ctx = supplier.get();
                    ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                            com.gtl.enhancedcore.client.plot.CampusPlanScreen.receiveTeleportResult(p.id, p.result)));
                    ctx.setPacketHandled(true);
                }, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        initialized = true;
    }

    /** Only called from a server-side use of the real, already-loaded device. */
    public static void open(ServerPlayer player, BlockPos origin) {
        var generator = validateDevice(player, origin, null);
        if (generator == null) { notice(player, Result.INVALID_DEVICE); return; }
        if (blocked(player)) { notice(player, Result.BLOCKED); return; }
        ServerSessions state = SERVERS.computeIfAbsent(player.server, server -> new ServerSessions());
        long now = now(player.server);
        if (now - state.opens.getOrDefault(player.getUUID(), -OPEN_COOLDOWN) < OPEN_COOLDOWN) return;
        state.opens.put(player.getUUID(), now);
        var device = (CentralTeleporterBlockEntity) player.serverLevel().getBlockEntity(origin);
        var plan = CampusPlanAccess.of(generator.settings());
        Session session = new Session(UUID.randomUUID(), player.level().dimension(), origin.immutable(), device, generator,
                plan, CampusPlanAccess.layoutIdentity(generator.settings()), now + SESSION_TICKS);
        state.sessions.put(player.getUUID(), session);
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new Open(session.id, session.origin, session.dimension.location(), generator.settings(), session.layoutIdentity));
    }
    public static void requestTeleport(UUID session, int targetIndex) { CHANNEL.sendToServer(new Travel(session, targetIndex)); }
    public static void close(UUID session) { CHANNEL.sendToServer(new Close(session)); }

    private static PlotChunkGenerator validateDevice(ServerPlayer player, BlockPos origin, Session expected) {
        ServerLevel level = player.serverLevel();
        if (!player.isAlive() || player.isSpectator() || player.distanceToSqr(origin.getX() + .5, origin.getY() + .5, origin.getZ() + .5) > 64
                || !level.hasChunkAt(origin) || !level.getBlockState(origin).is(CampusDeviceBlocks.CENTRAL_TELEPORTER.get())
                || !(level.getBlockEntity(origin) instanceof CentralTeleporterBlockEntity device)
                || !level.dimension().location().equals(device.boundDimension())
                || !(level.getChunkSource().getGenerator() instanceof PlotChunkGenerator generator) || !generator.settings().isCampus()) return null;
        if (expected != null && (!level.dimension().equals(expected.dimension) || device != expected.device || generator != expected.generator)) return null;
        return generator;
    }
    private static boolean blocked(ServerPlayer player) { return player.isPassenger() || player.isSleeping(); }
    private static long now(MinecraftServer server) { return server.overworld().getGameTime(); }
    private static void notice(ServerPlayer player, Result result) {
        player.displayClientMessage(Component.translatable("gtl_enhancedcore.campus.teleport.result." + result.name().toLowerCase(java.util.Locale.ROOT)), true);
    }
    private static void reply(ServerPlayer player, UUID id, Result result) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new Reply(id, result));
        notice(player, result);
    }
    private static void travelPacket(Travel packet, Supplier<NetworkEvent.Context> supplier) {
        var ctx = supplier.get(); var player = ctx.getSender();
        if (player != null) ctx.enqueueWork(() -> travel(player, packet));
        ctx.setPacketHandled(true);
    }
    private static void closePacket(Close packet, Supplier<NetworkEvent.Context> supplier) {
        var ctx = supplier.get(); var player = ctx.getSender();
        if (player != null) ctx.enqueueWork(() -> {
            ServerSessions state = SERVERS.get(player.server);
            if (state != null) {
                Session session = state.sessions.get(player.getUUID());
                if (session != null && session.id.equals(packet.id)) state.sessions.remove(player.getUUID());
            }
        });
        ctx.setPacketHandled(true);
    }
    private static void travel(ServerPlayer player, Travel packet) {
        ServerSessions state = SERVERS.get(player.server);
        Session session = state == null ? null : state.sessions.get(player.getUUID());
        long now = now(player.server);
        if (session == null || !session.id.equals(packet.id) || now >= session.expiresAt) {
            if (session != null && now >= session.expiresAt) state.sessions.remove(player.getUUID());
            reply(player, packet.id, Result.EXPIRED); return;
        }
        if (now - state.attempts.getOrDefault(player.getUUID(), -REQUEST_COOLDOWN) < REQUEST_COOLDOWN) { reply(player, packet.id, Result.COOLDOWN); return; }
        state.attempts.put(player.getUUID(), now);
        PlotChunkGenerator generator = validateDevice(player, session.origin, session);
        if (generator == null) { state.sessions.remove(player.getUUID()); reply(player, packet.id, Result.INVALID_DEVICE); return; }
        if (blocked(player)) { reply(player, packet.id, Result.BLOCKED); return; }
        CampusPlanAccess.Plan plan = session.plan;
        if (packet.index < 0 || packet.index >= plan.targets().size()) { reply(player, packet.id, Result.INVALID_TARGET); return; }
        CampusPlanAccess.Target target = plan.targets().get(packet.index);
        ServerLevel level = player.serverLevel();
        BlockPos feet = safePosition(level, player, generator.settings(), plan, target);
        if (feet == null) { reply(player, packet.id, Result.UNSAFE); return; }
        double x = feet.getX() + .5, y = feet.getY(), z = feet.getZ() + .5;
        // Same-dimension ServerPlayer.teleportTo does not post a Forge teleport event itself.
        var event = new EntityTeleportEvent.TeleportCommand(player, x, y, z);
        if (MinecraftForge.EVENT_BUS.post(event) || event.getTargetX() != x || event.getTargetY() != y || event.getTargetZ() != z) {
            reply(player, packet.id, Result.CANCELLED); return;
        }
        // A protection listener may have changed the device or destination while handling the event.
        if (validateDevice(player, session.origin, session) == null || !safeAt(level, player, plan, feet, target.arrivalX() >> 4, target.arrivalZ() >> 4)) {
            reply(player, packet.id, Result.UNSAFE); return;
        }
        player.teleportTo(level, x, y, z, player.getYRot(), player.getXRot());
        if (player.level() != level || player.distanceToSqr(x, y, z) > .01) { reply(player, packet.id, Result.CANCELLED); return; }
        player.fallDistance = 0; player.setDeltaMovement(0, 0, 0);
        state.sessions.remove(player.getUUID());
        reply(player, packet.id, Result.SUCCESS);
    }

    private static BlockPos safePosition(ServerLevel level, ServerPlayer player, PlotSettings settings,
                                         CampusPlanAccess.Plan plan, CampusPlanAccess.Target target) {
        int cx = target.arrivalX() >> 4, cz = target.arrivalZ() >> 4;
        // Load exactly the chosen forecourt chunk, never the full atlas or a heightmap over machines.
        level.getChunkSource().getChunk(cx, cz, ChunkStatus.FULL, true);
        for (int radius = 0; radius <= SAFE_RADIUS; radius++) for (int dx = -radius; dx <= radius; dx++) for (int dz = -radius; dz <= radius; dz++) {
            if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) continue;
            BlockPos feet = new BlockPos(target.arrivalX() + dx, settings.surfaceY() + 1, target.arrivalZ() + dz);
            if (safeAt(level, player, plan, feet, cx, cz)) return feet;
        }
        return null;
    }
    private static boolean safeAt(ServerLevel level, ServerPlayer player, CampusPlanAccess.Plan plan, BlockPos feet, int cx, int cz) {
        if (feet.getY() <= level.getMinBuildHeight() || feet.getY() > level.getMaxBuildHeight()
                || (feet.getX() >> 4) != cx || (feet.getZ() >> 4) != cz || !level.hasChunkAt(feet)
                || !plan.bounds().contains(feet.getX(), feet.getZ())) return false;
        AABB box = player.getDimensions(Pose.STANDING).makeBoundingBox(feet.getX() + .5, feet.getY(), feet.getZ() + .5);
        var border = level.getWorldBorder();
        if (box.minX < border.getMinX() || box.maxX > border.getMaxX() || box.minZ < border.getMinZ() || box.maxZ > border.getMaxZ()
                || box.minX < cx * 16 || box.maxX > cx * 16 + 16 || box.minZ < cz * 16 || box.maxZ > cz * 16 + 16) return false;
        for (int x = Mth.floor(box.minX); x <= Mth.floor(box.maxX - 1.0E-7); x++) for (int z = Mth.floor(box.minZ); z <= Mth.floor(box.maxZ - 1.0E-7); z++) {
            for (var plot : plan.plots()) if (plot.contains(x, z)) return false;
            BlockPos support = new BlockPos(x, feet.getY() - 1, z);
            BlockState floor = level.getBlockState(support);
            if (!floor.isFaceSturdy(level, support, Direction.UP) || !floor.getFluidState().isEmpty() || hazard(floor)) return false;
            for (int y = feet.getY(); y <= Mth.floor(box.maxY - 1.0E-7); y++) {
                BlockState state = level.getBlockState(new BlockPos(x, y, z));
                if (!state.getFluidState().isEmpty() || hazard(state)) return false;
            }
        }
        return level.noCollision(player, box);
    }
    private static boolean hazard(BlockState state) {
        return state.is(Blocks.MAGMA_BLOCK) || state.is(Blocks.CAMPFIRE) || state.is(Blocks.SOUL_CAMPFIRE)
                || state.is(Blocks.CACTUS) || state.is(Blocks.SWEET_BERRY_BUSH) || state.is(Blocks.WITHER_ROSE)
                || state.is(Blocks.POWDER_SNOW) || state.is(Blocks.FIRE) || state.is(Blocks.SOUL_FIRE)
                || state.is(Blocks.END_PORTAL) || state.is(Blocks.NETHER_PORTAL) || state.is(Blocks.END_GATEWAY);
    }

    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) { var state = SERVERS.get(player.server); if (state != null) state.remove(player.getUUID()); }
    }
    @SubscribeEvent public static void changedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) { var state = SERVERS.get(player.server); if (state != null) state.remove(player.getUUID()); }
    }
    @SubscribeEvent public static void unloadChunk(ChunkEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            var state = SERVERS.get(level.getServer()); var pos = event.getChunk().getPos();
            if (state != null) state.sessions.values().removeIf(s -> s.dimension.equals(level.dimension())
                    && (s.origin.getX() >> 4) == pos.x && (s.origin.getZ() >> 4) == pos.z);
        }
    }
    @SubscribeEvent public static void unloadLevel(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            var state = SERVERS.get(level.getServer());
            if (state != null) state.sessions.values().removeIf(s -> s.dimension.equals(level.dimension()));
        }
    }
    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.getServer().getTickCount() % 20 != 0) return;
        var state = SERVERS.get(event.getServer());
        if (state == null) return;
        long now = now(event.getServer());
        state.sessions.values().removeIf(s -> now >= s.expiresAt);
        state.attempts.values().removeIf(time -> now - time > SESSION_TICKS);
        state.opens.values().removeIf(time -> now - time > SESSION_TICKS);
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event) { SERVERS.remove(event.getServer()); }
}
