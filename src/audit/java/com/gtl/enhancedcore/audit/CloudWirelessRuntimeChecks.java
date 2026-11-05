package com.gtl.enhancedcore.audit;

import com.gtl.enhancedcore.common.cloud.CloudMeConnection;
import com.gtl.enhancedcore.common.cloud.CloudPatternBufferMachine;
import com.gtl.enhancedcore.common.cloud.CloudPatternLibraryMachine;
import com.gtl.enhancedcore.common.cloud.store.CloudLedger;
import com.mojang.authlib.GameProfile;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import org.gtlcore.gtlcore.integration.ae2.wireless.GTLWirelessAeContent;
import org.gtlcore.gtlcore.integration.ae2.wireless.WirelessAeNetworkRuntime;
import org.gtlcore.gtlcore.integration.ae2.wireless.WirelessAeSavedData;
import org.gtlcore.gtlcore.integration.ae2.wireless.WirelessNetworkCoreBlockEntity;

/** Actual saved wireless membership and core-chunk unload/reload, with no node/readiness field edits. */
public final class CloudWirelessRuntimeChecks {
    // Keep the core and its physical bridge cell inside one disposable chunk, so the unload
    // probe does not depend on two neighboring chunks completing their unload independently.
    private static final BlockPos CORE = new BlockPos(520, 100, 520);
    private static ServerLevel level;
    private static CloudPatternLibraryMachine main;
    private static List<CloudPatternBufferMachine> children;
    private static FakePlayer owner;
    private static List<String> checks;
    private static UUID frequency;
    private static Map<UUID, List<CloudLedger.Lease>> leases;
    private static List<appeng.api.crafting.IPatternDetails> patterns;
    private static int ticks, stage, since;
    private CloudWirelessRuntimeChecks() {}

    public static void begin(ServerLevel world, CloudPatternLibraryMachine library,
                             List<CloudPatternBufferMachine> endpoints, FakePlayer actor, List<String> results) {
        level = world; main = library; children = endpoints; owner = actor; checks = results;
        ticks = 0; stage = 0; since = 0;
        System.setProperty("gtl.enhancedcore.cloudTicketTraceActive", "false");
        level.setChunkForced(CORE.getX() >> 4, CORE.getZ() >> 4, true);
        level.getChunk(CORE);
        require(level.isEmptyBlock(CORE), "wireless_core_uses_empty_disposable_world_position");
        level.setBlockAndUpdate(CORE, GTLWirelessAeContent.WIRELESS_NETWORK_CORE.get().defaultBlockState());
        var core = (WirelessNetworkCoreBlockEntity)level.getBlockEntity(CORE);
        require(new net.minecraft.world.level.ChunkPos(CORE).equals(new net.minecraft.world.level.ChunkPos(CORE.relative(core.getConnectionSide()))),
                "wireless_core_and_physical_bridge_cell_share_the_same_isolated_chunk");
        level.setBlockAndUpdate(CORE.relative(core.getConnectionSide()),
                appeng.core.definitions.AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState());
        core.setOwner(owner); frequency = core.getFrequency();
    }

    public static boolean step() {
        if (ticks % 20 == 0) writeChunkSnapshot();
        if (++ticks > 900) throw new AssertionError("wireless:timeout stage=" + stage + " chunkLoaded="
                + (level.getChunkSource().getChunkNow(CORE.getX() >> 4, CORE.getZ() >> 4) != null));
        var saved = WirelessAeSavedData.get(level.getServer());
        switch (stage) {
            case 0 -> {
                var core = strictCore();
                var bridge = strictBridge(core);
                if (bridge == null || !bridge.isActive()) return false;
                require(saved.getCore(frequency).equals(GlobalPos.of(level.dimension(), CORE))
                                && owner.getUUID().equals(saved.getNetworkOwner(frequency)),
                        "real_loaded_wireless_core_has_saved_frequency_owner_and_active_bridge");
                var outsider = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "WirelessDenied"));
                require(WirelessAeNetworkRuntime.canAccessNetwork(owner, frequency)
                                && !WirelessAeNetworkRuntime.canAccessNetwork(outsider, frequency),
                        "real_wireless_network_allows_owner_and_denies_unrelated_actor");
                for (var member : members()) saved.addMember(frequency, member);
                restore(); since = ticks; stage = 1;
            }
            case 1 -> {
                restore();
                var core = strictCore();
                var bridge = strictBridge(core);
                if (ticks - since < 25 || bridge == null || main.serviceGrid() != bridge.getGrid()
                        || children.stream().anyMatch(child -> !child.cloudReady(true) || child.getGrid() != bridge.getGrid()
                                || child.getAvailablePatterns().isEmpty())) return false;
                require(members().stream().allMatch(member -> frequency.equals(saved.getExactMemberNetwork(member))),
                        "main_and_both_cloud_nodes_use_their_exact_saved_wireless_memberships");
                leases = children.stream().collect(java.util.stream.Collectors.toMap(
                        CloudPatternBufferMachine::cloudNodeId, child -> List.copyOf(child.cloudNode().leases())));
                patterns = children.stream().map(child -> child.getAvailablePatterns().getFirst()).toList();
                require(children.stream().allMatch(child -> !leases.get(child.cloudNodeId()).isEmpty()),
                        "both_wireless_cloud_nodes_hold_real_original_leases_before_offline_transition");
                level.setChunkForced(CORE.getX() >> 4, CORE.getZ() >> 4, false);
                System.setProperty("gtl.enhancedcore.cloudTicketTraceActive", "true");
                since = ticks; stage = 2;
            }
            case 2 -> {
                if (level.getChunkSource().getChunkNow(CORE.getX() >> 4, CORE.getZ() >> 4) != null) return false;
                require(strictCore() == null
                                && members().stream().allMatch(member -> frequency.equals(saved.getExactMemberNetwork(member))),
                        "actual_core_chunk_unload_preserves_exact_saved_selection_but_has_no_loaded_core");
                require(main.getMainNode().isActive() && main.getMainNode().getGrid() != null
                                && main.serviceGrid() == null && !main.cloudUiConnected()
                                && children.stream().noneMatch(child -> child.cloudReady(true)),
                        "unavailable_selected_wireless_core_denies_live_wired_grid_fallback_and_dispatch");
                require(java.util.stream.IntStream.range(0, children.size()).allMatch(index ->
                                children.get(index).getAvailablePatterns().isEmpty()
                                        && !children.get(index).gtlcore$canProduceGraphPattern(patterns.get(index))),
                        "both_selected_cloud_endpoints_withdraw_publications_and_reject_new_graph_dispatch_while_core_is_unloaded");
                require(unchangedLeases(), "wireless_core_offline_preserves_original_lease_ids_slots_and_generations");
                level.setChunkForced(CORE.getX() >> 4, CORE.getZ() >> 4, true);
                level.getChunk(CORE); since = ticks; stage = 3;
            }
            case 3 -> {
                restore();
                var core = strictCore();
                var bridge = strictBridge(core);
                if (ticks - since < 25 || bridge == null || main.serviceGrid() != bridge.getGrid()
                        || children.stream().anyMatch(child -> !child.cloudReady(true) || child.getAvailablePatterns().isEmpty())) return false;
                require(frequency.equals(core.getFrequency()) && unchangedLeases(),
                        "actual_core_chunk_reload_restores_same_frequency_two_cloud_routes_and_unchanged_leases");
                System.setProperty("gtl.enhancedcore.cloudTicketTraceActive", "false");
                return true;
            }
            default -> throw new AssertionError("wireless:unknown stage");
        }
        return false;
    }
    public static UUID frequency() { return frequency; }
    /** Audit observations never call upstream getLoadedCore/findBridgeNode, which can refresh UNKNOWN tickets. */
    private static WirelessNetworkCoreBlockEntity strictCore() {
        var pos = WirelessAeSavedData.get(level.getServer()).getCore(frequency);
        if (pos == null) return null;
        var coreLevel = level.getServer().getLevel(pos.dimension());
        if (coreLevel == null) return null;
        var chunk = coreLevel.getChunkSource().getChunkNow(pos.pos().getX() >> 4, pos.pos().getZ() >> 4);
        var entity = chunk == null ? null : chunk.getBlockEntity(pos.pos());
        return entity instanceof WirelessNetworkCoreBlockEntity core && !core.isRemoved() && frequency.equals(core.getFrequency()) ? core : null;
    }
    private static appeng.api.networking.IGridNode strictBridge(WirelessNetworkCoreBlockEntity core) {
        if (core == null || core.getActionableNode() == null) return null;
        var node = core.getActionableNode(); var connection = node.getInWorldConnections().get(core.getConnectionSide());
        return connection == null ? null : connection.getOtherSide(node);
    }
    private static void writeChunkSnapshot() {
        var snapshot = new java.util.LinkedHashMap<String, Object>();
        var pos = new net.minecraft.world.level.ChunkPos(CORE);
        snapshot.put("ticks", ticks); snapshot.put("stage", stage); snapshot.put("corePosition", CORE.toString());
        snapshot.put("forced", level.getForcedChunks().contains(pos.toLong()));
        snapshot.put("loaded", level.getChunkSource().getChunkNow(pos.x, pos.z) != null);
        snapshot.put("chunkDebugData", level.getChunkSource().getChunkDebugData(pos));
        var distance = net.minecraftforge.fml.util.ObfuscationReflectionHelper.getPrivateValue(
                net.minecraft.server.level.ServerChunkCache.class, level.getChunkSource(), "f_8327_");
        var allTickets = (it.unimi.dsi.fastutil.longs.Long2ObjectMap<?>)net.minecraftforge.fml.util.ObfuscationReflectionHelper.getPrivateValue(
                net.minecraft.server.level.DistanceManager.class, (net.minecraft.server.level.DistanceManager)distance, "f_140761_");
        var ticketClock = (Long)net.minecraftforge.fml.util.ObfuscationReflectionHelper.getPrivateValue(
                net.minecraft.server.level.DistanceManager.class, (net.minecraft.server.level.DistanceManager)distance, "f_140771_");
        snapshot.put("ticketClock", ticketClock);
        var ticketRows = new java.util.ArrayList<Map<String, Object>>();
        for (int x = pos.x - 1; x <= pos.x + 1; x++) for (int z = pos.z - 1; z <= pos.z + 1; z++) {
            var set = allTickets.get(net.minecraft.world.level.ChunkPos.asLong(x, z));
            if (!(set instanceof Iterable<?> iterable)) continue;
            for (var ticket : iterable) {
                var created = (Long)net.minecraftforge.fml.util.ObfuscationReflectionHelper.getPrivateValue(
                        net.minecraft.server.level.Ticket.class, (net.minecraft.server.level.Ticket<?>)ticket, "f_9423_");
                ticketRows.add(Map.of("chunk", x + "," + z, "ticket", ticket.toString(), "createdTick", created,
                        "age", ticketClock - created));
            }
        }
        snapshot.put("tickets", ticketRows);
        snapshot.put("realPlayers", level.players().stream().map(player -> player.getName().getString() + ":" + player.position()).toList());
        try {
            var directory = level.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
                    .toAbsolutePath().normalize().getParent().getParent();
            var gson = new com.google.gson.GsonBuilder().setPrettyPrinting().create();
            java.nio.file.Files.writeString(directory.resolve("cloud-compat-wireless-chunk.json"), gson.toJson(snapshot));
            java.nio.file.Files.writeString(directory.resolve("cloud-compat-wireless-chunk.jsonl"),
                    new com.google.gson.Gson().toJson(snapshot) + "\n", java.nio.file.StandardOpenOption.CREATE,
                    java.nio.file.StandardOpenOption.APPEND);
        } catch (java.io.IOException failure) { throw new IllegalStateException(failure); }
    }
    private static boolean unchangedLeases() {
        return children.stream().allMatch(child -> leases.get(child.cloudNodeId()).equals(child.cloudNode().leases()));
    }
    private static List<WirelessAeSavedData.MemberKey> members() {
        return List.of(member(main.getPos()), member(children.get(0).getPos()), member(children.get(1).getPos()));
    }
    private static WirelessAeSavedData.MemberKey member(BlockPos pos) {
        return new WirelessAeSavedData.MemberKey(GlobalPos.of(level.dimension(), pos), null);
    }
    private static void restore() {
        CloudMeConnection.restore(main); children.forEach(CloudMeConnection::restore);
    }
    private static void require(boolean value, String text) {
        if (!value) throw new AssertionError("wireless:" + text);
        checks.add("wireless:" + text);
    }
}
