package com.gtl.enhancedcore.common.world.personalplot;

import com.gtl.enhancedcore.GTLEnhancedcore;
import com.gtl.enhancedcore.common.item.PlotWorldControllerItem;
import com.gtl.enhancedcore.common.world.plot.PlotChunkGenerator;
import com.gtl.enhancedcore.common.world.plot.PlotSettings;
import com.gtl.enhancedcore.mixin.mc.PersonalPlotServerAccessor;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.progress.ChunkProgressListener;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.dimension.BuiltinDimensionTypes;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.DerivedLevelData;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Server-authoritative personal dimension lifecycle. No existing level or generator is replaced. */
@Mod.EventBusSubscriber(modid = GTLEnhancedcore.MOD_ID)
public final class PersonalPlotWorlds {
    public enum State { NONE, PENDING, READY, UNAVAILABLE, DELETING }
    private static final Map<MinecraftServer, Session> SESSIONS = new WeakHashMap<>();
    private static final String RETURN_TAG = "gtlEnhancedcorePersonalPlotReturn";
    private static final String PREFIX = "gtl_enhancedcore.personal_plot.";
    private static final ChunkProgressListener SILENT_PROGRESS = new ChunkProgressListener() {
        public void updateSpawnPos(ChunkPos position) {} public void onStatusChange(ChunkPos position, ChunkStatus status) {}
        public void start() {} public void stop() {}
    };
    private static final class Session {
        final Path path;
        final PlotSettings activeTemplate;
        final java.util.Set<UUID> startupOwners;
        final Map<ResourceKey<Level>, PlotSettings> definitions = new java.util.HashMap<>();
        boolean templateSavedThisSession;
        PersonalPlotStore.Data data;
        final Map<UUID, Long> lastAction = new java.util.HashMap<>();
        Session(MinecraftServer server) throws IOException {
            path = server.getWorldPath(LevelResource.ROOT);
            data = PersonalPlotStore.read(path); startupOwners = new java.util.HashSet<>(data.worlds().keySet());
            data.worlds().forEach((owner, settings) -> definitions.put(key(owner), settings));
            activeTemplate = data.template() != null && data.template().isAvailableForCreation() ? data.template() : null;
        }
    }
    private PersonalPlotWorlds() {}
    private static Session session(MinecraftServer server) throws IOException {
        Session session = SESSIONS.get(server);
        if (session == null) { session = new Session(server); SESSIONS.put(server, session); }
        return session;
    }
    @SubscribeEvent public static void started(ServerStartedEvent event) {
        try {
            Session s = session(event.getServer());
            PersonalPlotDeletion.resume(event.getServer(), s.path);
        }
        catch (IOException error) { GTLEnhancedcore.LOGGER.error("Personal plot archive unavailable; no changes will be made", error); }
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event) { SESSIONS.remove(event.getServer()); PersonalPlotDeletion.stopped(event.getServer()); }
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) PersonalPlotNetwork.sendEnvironment(player);
    }
    public static ResourceKey<Level> key(UUID owner) {
        return ResourceKey.create(Registries.DIMENSION, new ResourceLocation(GTLEnhancedcore.MOD_ID, PersonalPlotStore.dimensionPath(owner)));
    }
    public static boolean isRegisteredPersonalLevel(Level level) {
        if (!(level instanceof ServerLevel serverLevel) || !level.dimensionTypeId().equals(BuiltinDimensionTypes.OVERWORLD)) return false;
        Session s = SESSIONS.get(serverLevel.getServer());
        if (s == null) return false;
        PlotSettings expected = s.definitions.get(level.dimension());
        return expected != null && serverLevel.getChunkSource().getGenerator() instanceof PlotChunkGenerator plot && plot.settings().equals(expected);
    }
    public static PersonalPlotNetwork.Snapshot snapshot(ServerPlayer player, String notice) {
        MinecraftServer server = player.server;
        try {
            Session s = session(server); UUID id = player.getUUID(); PlotSettings settings = s.data.worlds().get(id);
            boolean dedicated = server.isDedicatedServer(), owner = server.isSingleplayerOwner(player.getGameProfile());
            State state = state(server, s, id, settings);
            boolean inside = player.level().dimension().equals(key(id));
            return new PersonalPlotNetwork.Snapshot(dedicated,
                    PersonalPlotPolicy.editTemplate(dedicated, player.hasPermissions(2)),
                    state != State.DELETING && PersonalPlotPolicy.create(dedicated, owner, s.activeTemplate != null, settings != null),
                    PersonalPlotPolicy.customize(dedicated, owner), s.activeTemplate != null,
                    s.templateSavedThisSession && !java.util.Objects.equals(s.data.template(), s.activeTemplate), s.data.template(), s.activeTemplate,
                    state, settings, inside, inside, notice);
        } catch (IOException error) {
            return new PersonalPlotNetwork.Snapshot(server.isDedicatedServer(), false, false, false, false, false,
                    null, null, State.UNAVAILABLE, null, false, false, PREFIX + "storage_error");
        }
    }
    private static State state(MinecraftServer server, Session session, UUID id, PlotSettings settings) {
        if (PersonalPlotDeletion.isDeleting(server, id)) return State.DELETING;
        if (settings == null) return State.NONE;
        ServerLevel level = server.getLevel(key(id));
        if (level == null) return session.startupOwners.contains(id) ? State.UNAVAILABLE : State.PENDING;
        return level.getChunkSource().getGenerator() instanceof PlotChunkGenerator plot && plot.settings().equals(settings)
                && level.dimensionTypeId().equals(BuiltinDimensionTypes.OVERWORLD) ? State.READY : State.UNAVAILABLE;
    }
    public static String handle(ServerPlayer player, PersonalPlotNetwork.Action action, PlotSettings proposed) {
        try {
            Session s = session(player.server);
            String denied = authorizeAction(player, s); if (denied != null) return denied;
            return switch (action) {
                case OPEN -> "";
                case CREATE -> create(player, s, proposed);
                case TEMPLATE -> template(player, s, proposed);
                case TRAVEL -> travel(player, s);
                case RETURN -> returnFrom(player, s);
            };
        } catch (IOException | RuntimeException failure) {
            GTLEnhancedcore.LOGGER.error("Personal plot action {} failed for {}", action, player.getUUID(), failure);
            return PREFIX + "storage_error";
        }
    }
    private static String authorizeAction(ServerPlayer actor, Session s) {
        if (!(actor.getMainHandItem().getItem() instanceof PlotWorldControllerItem)
                && !(actor.getOffhandItem().getItem() instanceof PlotWorldControllerItem)) return PREFIX + "controller_required";
        long now = actor.server.getTickCount(); Long before = s.lastAction.put(actor.getUUID(), now);
        return before != null && now - before < 10 ? PREFIX + "cooldown" : null;
    }
    public static PersonalPlotStore.Data data(MinecraftServer server) throws IOException { return session(server).data; }
    public static State ownerState(MinecraftServer server, UUID owner) {
        try { Session s = session(server); return state(server, s, owner, s.data.worlds().get(owner)); }
        catch (IOException failure) { return State.UNAVAILABLE; }
    }
    public static String createFor(ServerPlayer actor, UUID owner) {
        try {
            Session s = session(actor.server); String denied = authorizeAction(actor, s); if (denied != null) return denied;
            if (!PersonalPlotPolicy.manageOwner(actor.server.isDedicatedServer(), actor.server.isSingleplayerOwner(actor.getGameProfile()),
                    actor.hasPermissions(2), actor.getUUID().equals(owner))) return PREFIX + "management_denied";
            if (!actor.server.isDedicatedServer()) return PREFIX + "invalid_settings";
            return createOwner(actor, s, owner, null);
        } catch (IOException | RuntimeException failure) {
            GTLEnhancedcore.LOGGER.error("Personal plot managed creation failed for {}", owner, failure); return PREFIX + "storage_error";
        }
    }
    public static String deleteFor(ServerPlayer actor, UUID owner) {
        try {
            Session s = session(actor.server); String denied = authorizeAction(actor, s); if (denied != null) return denied;
            if (!PersonalPlotPolicy.manageOwner(actor.server.isDedicatedServer(), actor.server.isSingleplayerOwner(actor.getGameProfile()),
                    actor.hasPermissions(2), actor.getUUID().equals(owner))) return PREFIX + "management_denied";
            if (PersonalPlotDeletion.isDeleting(actor.server, owner)) return PREFIX + "deleting";
            if (!s.data.worlds().containsKey(owner)) return PREFIX + "not_created";
            ServerLevel level = actor.server.getLevel(key(owner));
            if (level != null && !isRegisteredPersonalLevel(level)) return PREFIX + "dimension_conflict";
            if (level != null && !evacuate(level)) return PREFIX + "travel_cancelled";
            PersonalPlotStore.beginDeletion(s.path, owner);
            s.data = s.data.without(owner); s.definitions.remove(key(owner));
            PersonalPlotManagement.invalidate(actor.server, owner);
            PersonalPlotDeletion.begin(actor.server, s.path, owner, level);
            return PREFIX + "delete_started";
        } catch (IOException | RuntimeException failure) {
            GTLEnhancedcore.LOGGER.error("Personal plot deletion could not begin for {}", owner, failure); return PREFIX + "delete_failed";
        }
    }
    private static boolean evacuate(ServerLevel level) {
        if (level.players().isEmpty()) return true;
        ServerLevel target = level.getServer().overworld(); BlockPos safe = safePosition(target, target.getSharedSpawnPos());
        if (safe == null) return false;
        for (ServerPlayer visitor : List.copyOf(level.players())) {
            visitor.stopRiding(); if (visitor.isSleeping()) visitor.stopSleepInBed(true, true);
            visitor.teleportTo(target, safe.getX() + .5, safe.getY(), safe.getZ() + .5, visitor.getYRot(), visitor.getXRot());
            if (visitor.level() != target) return false;
            visitor.fallDistance = 0; visitor.getPersistentData().remove(RETURN_TAG);
        }
        return level.players().isEmpty();
    }
    private static String template(ServerPlayer player, Session s, PlotSettings settings) throws IOException {
        if (!PersonalPlotPolicy.editTemplate(player.server.isDedicatedServer(), player.hasPermissions(2))) return PREFIX + "permission_denied";
        if (settings == null) return PREFIX + "invalid_settings";
        if (!settings.isAvailableForCreation()) return PREFIX + "retired_preset";
        PersonalPlotStore.Data next = s.data.withTemplate(settings);
        PersonalPlotStore.write(s.path, next); s.data = next; s.templateSavedThisSession = true;
        return PREFIX + "template_saved";
    }
    private static String create(ServerPlayer player, Session s, PlotSettings proposed) throws IOException {
        return createOwner(player, s, player.getUUID(), proposed);
    }
    private static String createOwner(ServerPlayer player, Session s, UUID owner, PlotSettings proposed) throws IOException {
        boolean dedicated = player.server.isDedicatedServer();
        if (PersonalPlotDeletion.isDeleting(player.server, owner)) return PREFIX + "deleting";
        if (s.data.worlds().containsKey(owner)) return PREFIX + "already_owned";
        if (!PersonalPlotPolicy.create(dedicated, player.server.isSingleplayerOwner(player.getGameProfile()), s.activeTemplate != null, false))
            return PREFIX + (dedicated ? "template_required" : "permission_denied");
        PlotSettings settings = dedicated ? s.activeTemplate : proposed;
        if (settings == null) return PREFIX + "invalid_settings";
        if (!settings.isAvailableForCreation()) return PREFIX + "retired_preset";
        if (player.server.getLevel(key(owner)) != null) return PREFIX + "dimension_conflict";
        PersonalPlotStore.Data next = s.data.create(owner, settings);
        PersonalPlotStore.write(s.path, next); s.data = next; s.definitions.put(key(owner), settings);
        s.startupOwners.remove(owner);
        PersonalPlotManagement.invalidate(player.server, owner);
        if (!dedicated) return PREFIX + "created_restart_client";
        try { createLiveLevel(player.server, owner, settings); return PREFIX + "created_ready"; }
        catch (RuntimeException error) {
            // The committed definition is retained exactly once. Next startup can load it normally.
            GTLEnhancedcore.LOGGER.error("Personal plot committed but live creation failed; restart required for {}", owner, error);
            return PREFIX + "created_restart_server";
        }
    }
    @SuppressWarnings("deprecation")
    private static void createLiveLevel(MinecraftServer server, UUID owner, PlotSettings settings) {
        ResourceKey<Level> key = key(owner); ServerLevel level = null;
        try {
            var access = server.registryAccess();
            var stem = new LevelStem(access.registryOrThrow(Registries.DIMENSION_TYPE).getHolderOrThrow(BuiltinDimensionTypes.OVERWORLD),
                    new PlotChunkGenerator(access.registryOrThrow(Registries.BIOME).getHolderOrThrow(Biomes.PLAINS), settings));
            var data = new DerivedLevelData(server.getWorldData(), server.getWorldData().overworldData());
            level = new ServerLevel(server, Util.backgroundExecutor(),
                    ((PersonalPlotServerAccessor) server).enhancedcore$personalPlotStorage(), data, key, stem,
                    SILENT_PROGRESS, false, BiomeManager.obfuscateSeed(server.getWorldData().worldGenOptions().seed()),
                    List.of(), false, server.overworld().getRandomSequences());
            level.getWorldBorder().applySettings(server.overworld().getWorldBorder().createSettings());
            server.forgeGetWorldMap().put(key, level); server.markWorldsDirty();
            MinecraftForge.EVENT_BUS.post(new LevelEvent.Load(level));
            PersonalPlotNetwork.broadcastDimension(key);
        } catch (RuntimeException failure) {
            if (level != null) {
                server.forgeGetWorldMap().remove(key, level); server.markWorldsDirty();
                try { MinecraftForge.EVENT_BUS.post(new LevelEvent.Unload(level)); } catch (RuntimeException cleanup) { failure.addSuppressed(cleanup); }
                try { level.close(); } catch (IOException cleanup) { failure.addSuppressed(cleanup); }
            }
            throw failure;
        }
    }
    private static String travel(ServerPlayer player, Session s) {
        PlotSettings settings = s.data.worlds().get(player.getUUID());
        State state = state(player.server, s, player.getUUID(), settings);
        if (state != State.READY) return PREFIX + (state == State.NONE ? "not_created" : state == State.UNAVAILABLE ? "dimension_unavailable" : "restart_required");
        ServerLevel target = player.server.getLevel(key(player.getUUID()));
        if (player.level() == target) return "";
        if (player.isPassenger() || player.isSleeping()) return PREFIX + "travel_blocked";
        int spawn = settings.isCampus() ? 0 : settings.plotBlocks() / 2;
        BlockPos destination = safePosition(target, new BlockPos(spawn, settings.surfaceY() + 1, spawn));
        if (destination == null) return PREFIX + "unsafe_destination";
        CompoundTag back = new CompoundTag(); back.putString("dimension", player.level().dimension().location().toString());
        back.putDouble("x", player.getX()); back.putDouble("y", player.getY()); back.putDouble("z", player.getZ());
        back.putFloat("yaw", player.getYRot()); back.putFloat("pitch", player.getXRot());
        player.teleportTo(target, destination.getX() + .5, destination.getY(), destination.getZ() + .5, player.getYRot(), player.getXRot());
        if (player.level() != target) return PREFIX + "travel_cancelled";
        player.getPersistentData().put(RETURN_TAG, back);
        player.fallDistance = 0; return PREFIX + "travelled";
    }
    private static String returnFrom(ServerPlayer player, Session s) {
        if (!player.level().dimension().equals(key(player.getUUID())) || !s.data.worlds().containsKey(player.getUUID())) return PREFIX + "not_in_personal";
        if (player.isPassenger() || player.isSleeping()) return PREFIX + "travel_blocked";
        CompoundTag back = player.getPersistentData().getCompound(RETURN_TAG);
        ResourceLocation id = ResourceLocation.tryParse(back.getString("dimension"));
        ServerLevel target = id == null ? null : player.server.getLevel(ResourceKey.create(Registries.DIMENSION, id));
        BlockPos desired;
        if (target == null || target == player.level() || !Double.isFinite(back.getDouble("x")) || !Double.isFinite(back.getDouble("y")) || !Double.isFinite(back.getDouble("z"))) {
            target = player.server.overworld(); desired = target.getSharedSpawnPos();
        } else desired = BlockPos.containing(back.getDouble("x"), back.getDouble("y"), back.getDouble("z"));
        BlockPos safe = safePosition(target, desired);
        if (safe == null) return PREFIX + "unsafe_destination";
        player.teleportTo(target, safe.getX() + .5, safe.getY(), safe.getZ() + .5, back.getFloat("yaw"), back.getFloat("pitch"));
        if (player.level() != target) return PREFIX + "travel_cancelled";
        player.fallDistance = 0; player.getPersistentData().remove(RETURN_TAG); return PREFIX + "returned";
    }
    private static BlockPos safePosition(ServerLevel level, BlockPos wanted) {
        for (int radius = 0; radius <= 4; radius++) for (int x = -radius; x <= radius; x++) for (int z = -radius; z <= radius; z++) {
            if (Math.max(Math.abs(x), Math.abs(z)) != radius) continue;
            int px = wanted.getX() + x, pz = wanted.getZ() + z;
            if (Math.abs((long) px) > 29_999_980 || Math.abs((long) pz) > 29_999_980) continue;
            int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, px, pz);
            for (int y : new int[] { wanted.getY(), top }) {
                BlockPos feet = new BlockPos(px, y, pz);
                // Entity positions may sit above the build limit: a surface at Y=319 is still valid.
                if (y <= level.getMinBuildHeight() || y > level.getMaxBuildHeight() + 16 || !level.getWorldBorder().isWithinBounds(feet)) continue;
                if (!level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
                        || !level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty()
                        || !level.getFluidState(feet).isEmpty() || !level.getFluidState(feet.above()).isEmpty()) continue;
                if (hazard(level.getBlockState(feet)) || hazard(level.getBlockState(feet.above())) || hazard(level.getBlockState(feet.below()))) continue;
                if (level.getBlockState(feet.below()).isFaceSturdy(level, feet.below(), net.minecraft.core.Direction.UP)) return feet;
            }
        }
        return null;
    }
    private static boolean hazard(BlockState state) {
        return state.is(Blocks.MAGMA_BLOCK) || state.is(Blocks.CAMPFIRE) || state.is(Blocks.SOUL_CAMPFIRE)
                || state.is(Blocks.CACTUS) || state.is(Blocks.SWEET_BERRY_BUSH) || state.is(Blocks.WITHER_ROSE)
                || state.is(Blocks.POWDER_SNOW) || state.is(Blocks.FIRE) || state.is(Blocks.SOUL_FIRE);
    }
}
