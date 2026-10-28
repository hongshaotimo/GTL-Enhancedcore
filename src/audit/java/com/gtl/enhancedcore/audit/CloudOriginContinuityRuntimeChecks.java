package com.gtl.enhancedcore.audit;

import appeng.api.networking.*;
import appeng.api.storage.IStorageProvider;
import com.gtl.enhancedcore.common.cloud.CloudOriginAuthorization;
import com.gtl.enhancedcore.common.cloud.CloudGridRevision;
import com.gtl.enhancedcore.common.cloud.transfer.CloudAeOriginGuard;
import com.mojang.authlib.GameProfile;
import java.lang.reflect.Proxy;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.common.util.FakePlayerFactory;

/** Actual AE node lifecycle and graph merges; no player world or simulated network ownership. */
public final class CloudOriginContinuityRuntimeChecks {
    private CloudOriginContinuityRuntimeChecks() {}
    private static IManagedGridNode node(ServerLevel level, List<IManagedGridNode> owned, boolean storage) {
        var managed = GridHelper.createManagedNode(new Object(), new IGridNodeListener<Object>() {
            @Override public void onSaveChanges(Object owner, IGridNode node) {}
        });
        managed.setInWorldNode(false).setIdlePowerUsage(0);
        if (storage) managed.addService(IStorageProvider.class, mounts -> {});
        managed.create(level, new BlockPos(160, 80, 160)); owned.add(managed); return managed;
    }
    private static List<IManagedGridNode> group(ServerLevel level, List<IManagedGridNode> owned, int count, boolean storage) {
        var nodes = new ArrayList<IManagedGridNode>();
        for (int index = 0; index < count; index++) {
            var next = node(level, owned, index == 0 && storage); nodes.add(next);
            if (index > 0) GridHelper.createConnection(nodes.get(index - 1).getNode(), next.getNode());
        }
        return nodes;
    }
    private static CloudAeOriginGuard.Origin origin(ServerLevel level) {
        return new CloudAeOriginGuard.Origin(UUID.randomUUID(), level.dimension().location().toString(), new BlockPos(160, 80, 160).asLong(), 1);
    }
    private static void require(boolean value, String text, List<String> checks) {
        if (!value) throw new AssertionError("continuity:" + text); checks.add("continuity:" + text);
    }
    public static void run(ServerLevel level, List<String> checks) throws Exception {
        var player = FakePlayerFactory.get(level, new GameProfile(UUID.fromString("3bd4f472-fd91-48d9-9c84-3e11c7307510"), "ContinuityAudit"));
        level.getChunk(10, 10); var owned = new ArrayList<IManagedGridNode>();
        try {
            var original = group(level, owned, 3, true); var larger = group(level, owned, 7, false);
            var source = original.get(2); var evidence = origin(level); IGrid oldGrid = original.getFirst().getGrid();
            CloudOriginAuthorization.capture(level.getServer(), evidence, oldGrid, source.getNode());
            source.destroy();
            GridHelper.createConnection(original.getFirst().getNode(), larger.getFirst().getNode());
            IGrid target = larger.getFirst().getGrid();
            require(oldGrid != target && oldGrid.isEmpty(), "actual_AE_merge_migrates_original_node_identities", checks);
            require(CloudOriginAuthorization.authorize(player, evidence, target), "destroyed_source_and_continuous_storage_authorized", checks);
            require(CloudOriginAuthorization.authorizeAutomatic(level.getServer(), evidence, target),
                    "runtime_continuity_also_allows_automatic_reconnect", checks);
            require(!CloudOriginAuthorization.authorizeAutomatic(level.getServer(), origin(level), target),
                    "unknown_origin_cannot_automatically_reconnect_after_restart", checks);
            var alien = group(level, owned, 2, true);
            CloudOriginAuthorization.capture(level.getServer(), evidence, alien.getFirst().getGrid(), null);
            require(!CloudOriginAuthorization.authorize(player, evidence, alien.getFirst().getGrid()), "recapture_cannot_replace_original_evidence", checks);
            CloudOriginAuthorization.beginWork(level.getServer(), evidence, alien.getFirst().getGrid(), null);
            require(CloudOriginAuthorization.authorize(player, evidence, alien.getFirst().getGrid())
                    && !CloudOriginAuthorization.authorize(player, evidence, target), "empty_ledger_fresh_batch_renews_evidence", checks);

            var cachedGroup = group(level, owned, 6, true); IGrid cachedGrid = cachedGroup.getFirst().getGrid();
            require(cachedGrid instanceof CloudGridRevision, "actual_grid_exposes_membership_revision", checks);
            long initialRevision = ((CloudGridRevision)cachedGrid).cloud$topologyRevision(); int initialSize = cachedGrid.size();
            CloudOriginAuthorization.capture(level.getServer(), origin(level), cachedGrid, null);
            cachedGroup.getLast().destroy();
            var newLeaf = node(level, owned, false);
            var newLink = GridHelper.createConnection(cachedGroup.getFirst().getNode(), newLeaf.getNode());
            require(cachedGrid == cachedGroup.getFirst().getGrid() && cachedGrid.size() == initialSize
                    && ((CloudGridRevision)cachedGrid).cloud$topologyRevision() > initialRevision,
                    "same_count_actual_node_swap_changes_revision", checks);
            var refreshedEvidence = origin(level); CloudOriginAuthorization.capture(level.getServer(), refreshedEvidence, cachedGrid, null);
            newLink.destroy();
            require(!CloudOriginAuthorization.authorize(player, refreshedEvidence, cachedGroup.getFirst().getGrid()),
                    "shared_snapshot_refresh_includes_replacement_node", checks);

            var a = node(level, owned, true); var b = node(level, owned, false); var c = node(level, owned, true);
            GridHelper.createConnection(a.getNode(), b.getNode()); var severed = GridHelper.createConnection(b.getNode(), c.getNode());
            var splitEvidence = origin(level); CloudOriginAuthorization.capture(level.getServer(), splitEvidence, a.getGrid(), null);
            severed.destroy();
            require(a.getGrid() != c.getGrid() && !CloudOriginAuthorization.authorize(player, splitEvidence, a.getGrid())
                    && !CloudOriginAuthorization.authorize(player, splitEvidence, c.getGrid()), "split_rejects_both_descendant_grids", checks);

            var noStorage = group(level, owned, 2, false); var noStorageTarget = group(level, owned, 7, false);
            var noStorageEvidence = origin(level); IGrid noStorageOld = noStorage.getFirst().getGrid();
            CloudOriginAuthorization.capture(level.getServer(), noStorageEvidence, noStorageOld, null);
            GridHelper.createConnection(noStorage.getFirst().getNode(), noStorageTarget.getFirst().getNode());
            require(noStorageOld.isEmpty() && !CloudOriginAuthorization.authorize(player, noStorageEvidence, noStorageTarget.getFirst().getGrid()),
                    "migration_without_original_storage_rejected", checks);

            // Only the documented bound policy is proxied; all graph lifecycle cases above use real AE nodes.
            IGrid oversized = (IGrid)Proxy.newProxyInstance(IGrid.class.getClassLoader(), new Class[]{IGrid.class}, (proxy, method, args) -> switch (method.getName()) {
                case "size" -> CloudOriginAuthorization.MAX_CONTINUITY_NODES + 1;
                case "isEmpty" -> true;
                case "getNodes" -> throw new AssertionError("Oversized graph must not be scanned");
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == args[0];
                default -> throw new UnsupportedOperationException(method.getName());
            });
            var oversizedEvidence = origin(level); CloudOriginAuthorization.capture(level.getServer(), oversizedEvidence, oversized, null);
            require(CloudOriginAuthorization.authorize(player, oversizedEvidence, oversized)
                    && !CloudOriginAuthorization.authorize(player, oversizedEvidence, target), "oversized_identity_only_no_scan_or_migration", checks);
        } finally { for (var managed : owned) if (managed.getNode() != null) managed.destroy(); }
    }
}
