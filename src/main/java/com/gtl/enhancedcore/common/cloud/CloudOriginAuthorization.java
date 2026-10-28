package com.gtl.enhancedcore.common.cloud;

import appeng.api.networking.IGrid;
import appeng.api.networking.IInWorldGridNodeHost;
import appeng.api.networking.IGridNode;
import appeng.api.storage.IStorageProvider;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.integration.ae2.machine.feature.IGridConnectedMachine;
import com.gtl.enhancedcore.common.cloud.transfer.CloudAeOriginGuard;
import java.lang.ref.WeakReference;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** Server-validated origin evidence. A library address is never a refund destination credential. */
public final class CloudOriginAuthorization {
    public static final int MAX_CONTINUITY_NODES = 16_384;
    private record Anchor(WeakReference<IGridNode> node, boolean storage) {}
    private record Snapshot(int size, long revision, boolean complete, List<Anchor> anchors) {}
    private static final class Evidence extends WeakReference<IGrid> {
        final Snapshot snapshot;
        final Anchor excluded;
        Evidence(IGrid grid, Snapshot snapshot, IGridNode excluded) {
            super(grid); this.snapshot = snapshot;
            this.excluded = excluded == null ? null : snapshot.anchors.stream().filter(anchor -> anchor.node.get() == excluded).findFirst().orElse(null);
        }
    }
    private static final Map<MinecraftServer, Map<CloudAeOriginGuard.Origin, Evidence>> LIVE = new WeakHashMap<>();
    private static final Map<IGrid, Snapshot> SNAPSHOTS = new WeakHashMap<>();
    private CloudOriginAuthorization() {}
    public static void capture(MinecraftServer server, CloudAeOriginGuard.Origin origin, IGrid grid) {
        capture(server, origin, grid, null);
    }
    /** Source endpoint is replaceable; all other original nodes remain continuity witnesses. */
    public static void capture(MinecraftServer server, CloudAeOriginGuard.Origin origin, IGrid grid, IGridNode excluded) {
        if (grid == null || origin == null || !server.isSameThread()) return;
        var records = LIVE.computeIfAbsent(server, ignored -> new HashMap<>());
        if (records.containsKey(origin)) return; // Never silently bless a changed network for an outstanding origin.
        records.put(origin, new Evidence(grid, snapshot(grid), excluded));
    }
    /** Caller must prove the source order ledger is empty before beginning a new work batch. */
    public static void beginWork(MinecraftServer server, CloudAeOriginGuard.Origin origin, IGrid grid, IGridNode excluded) {
        if (grid == null || origin == null || !server.isSameThread()) return;
        LIVE.computeIfAbsent(server, ignored -> new HashMap<>()).put(origin, new Evidence(grid, snapshot(grid), excluded));
    }
    private static Snapshot snapshot(IGrid grid) {
        int size = grid.size(); var cached = SNAPSHOTS.get(grid);
        long revision = grid instanceof CloudGridRevision revised ? revised.cloud$topologyRevision() : -1;
        if (revision >= 0 && cached != null && cached.size == size && cached.revision == revision) return cached;
        var anchors = new ArrayList<Anchor>(); boolean complete = size >= 0 && size <= MAX_CONTINUITY_NODES;
        if (complete) {
            for (var node : grid.getNodes()) {
                if (anchors.size() >= MAX_CONTINUITY_NODES) { complete = false; break; }
                anchors.add(new Anchor(new WeakReference<>(node), node.getService(IStorageProvider.class) != null));
            }
            complete &= anchors.size() == size;
        }
        var result = new Snapshot(size, revision, complete, List.copyOf(anchors));
        if (revision >= 0) SNAPSHOTS.put(grid, result);
        return result;
    }
    private static boolean continuous(Evidence evidence, IGrid target) {
        IGrid original = evidence.get();
        if (original == null) return false;
        boolean migration = original != target;
        if (!evidence.snapshot.complete) return !migration;
        if (migration && !original.isEmpty()) return false;
        boolean storageSurvives = false;
        for (var anchor : evidence.snapshot.anchors) {
            if (anchor == evidence.excluded) continue;
            var node = anchor.node.get();
            if (node == null) return false; // Collected is not proof of a cleanly destroyed node.
            try {
                if (node.getGrid() != target) return false;
                storageSurvives |= anchor.storage;
            } catch (IllegalStateException destroyed) {
                // AE 15.4.10's concrete GridNode.getGrid has exactly this destroyed-node branch.
                if (node.getClass() != appeng.me.GridNode.class
                        || !"A node is being used after it has been destroyed.".equals(destroyed.getMessage())) return false;
            }
        }
        return !migration || storageSurvives;
    }
    /** Automatic recovery uses only complete runtime witnesses, never position evidence after restart. */
    public static boolean authorizeAutomatic(MinecraftServer server, CloudAeOriginGuard.Origin origin, IGrid target) {
        if (!server.isSameThread() || origin == null || target == null) return false;
        var known = LIVE.getOrDefault(server, Map.of()).get(origin);
        return known != null && continuous(known, target);
    }
    public static boolean authorize(ServerPlayer player, CloudAeOriginGuard.Origin origin, IGrid target) {
        if (origin == null || target == null) return false;
        var server = player.getServer();
        ResourceLocation dimension = ResourceLocation.tryParse(origin.dimension()); if (dimension == null) return false;
        var level = server.getLevel(ResourceKey.create(Registries.DIMENSION, dimension)); if (level == null) return false;
        BlockPos pos = BlockPos.of(origin.position());
        if (level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4) == null
                || !CloudLibraryService.claimAllows(player, level, pos)) return false;
        var known = LIVE.getOrDefault(server, Map.of()).get(origin);
        if (known != null) return continuous(known, target);
        // After restart there is no persistent AE grid UUID. Explicit reconnect therefore additionally
        // requires the original physical endpoint (or its directly adjacent cable) on the chosen grid.
        for (Direction side : Direction.values()) {
            for (BlockPos candidate : List.of(pos, pos.relative(side))) {
                var chunk = level.getChunkSource().getChunkNow(candidate.getX() >> 4, candidate.getZ() >> 4);
                if (chunk == null) continue;
                var holder = chunk.getBlockEntity(candidate);
                if (holder instanceof IMachineBlockEntity machine && machine.getMetaMachine() instanceof IGridConnectedMachine connected
                        && connected.getMainNode().getGrid() == target) { capture(server, origin, target); return true; }
                if (holder instanceof IInWorldGridNodeHost host) {
                    var node = host.getGridNode(side.getOpposite());
                    if (node != null && node.getGrid() == target) { capture(server, origin, target); return true; }
                }
            }
        }
        return false;
    }
}
