package com.gtl.enhancedcore.audit;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.helpers.IPatternTerminalLogicHost;
import appeng.parts.encoding.PatternEncodingLogic;
import com.gtl.enhancedcore.common.cloud.*;
import com.gtl.enhancedcore.common.cloud.store.CloudPatternStore;
import com.gtl.enhancedcore.common.cloud.transfer.*;
import java.nio.file.Files;
import java.util.List;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.common.util.FakePlayer;

/** Real AE serialization and faulted filesystem commits in the marked audit world only. */
public final class CloudInstantRuntimeChecks {
    private static org.gtlcore.gtlcore.integration.ae2.wireless.WirelessNetworkCoreBlockEntity wirelessCore;
    public static void prepareWireless(ServerLevel level, FakePlayer owner) {
        var pos = new net.minecraft.core.BlockPos(384, 100, 384);
        level.setChunkForced(pos.getX() >> 4, pos.getZ() >> 4, true);
        level.getChunk(pos);
        level.setBlock(pos, org.gtlcore.gtlcore.integration.ae2.wireless.GTLWirelessAeContent.WIRELESS_NETWORK_CORE.get().defaultBlockState(), 3);
        wirelessCore = (org.gtlcore.gtlcore.integration.ae2.wireless.WirelessNetworkCoreBlockEntity)level.getBlockEntity(pos);
        level.setBlock(pos.relative(wirelessCore.getConnectionSide()),
                appeng.core.definitions.AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState(), 3);
        wirelessCore.setOwner(owner);
    }
    private static final class Host implements IPatternTerminalLogicHost {
        final ServerLevel level;
        final PatternEncodingLogic logic;
        Host(ServerLevel level) { this.level = level; logic = new PatternEncodingLogic(this); }
        public PatternEncodingLogic getLogic() { return logic; }
        public Level getLevel() { return level; }
        public void markForSave() {}
    }
    private static void check(boolean result, String label, List<String> checks) {
        if (!result) throw new AssertionError("instant_upload:" + label);
        checks.add("instant_upload:" + label);
    }
    private static ItemStack pattern(String marker) {
        var pattern = PatternDetailsHelper.encodeProcessingPattern(new GenericStack[]{new GenericStack(AEItemKey.of(Items.APPLE), 1)},
                new GenericStack[]{new GenericStack(AEItemKey.of(Items.GOLD_INGOT), 1)});
        pattern.getOrCreateTag().putString("instantAudit", marker); return pattern;
    }
    public static void run(ServerLevel level, CloudPatternLibraryMachine main, CloudPatternBufferMachine child,
                           FakePlayer player, List<String> checks) throws Exception {
        var store = CloudPatternStore.get(level);
        var libraryId = store.defaultLibrary(player.getUUID());
        // A fresh authority isolates the injected failed payload from the fixture's running machines.
        var actor = net.minecraftforge.common.util.FakePlayerFactory.get(level,
                new com.mojang.authlib.GameProfile(UUID.randomUUID(), "CommitRetryAudit"));
        libraryId = store.defaultLibrary(actor.getUUID());
        var library = store.library(libraryId);
        var data = level.getServer().getWorldPath(LevelResource.ROOT).resolve("data");
        var blocked = data.resolve("gtl_cloud_originals_" + libraryId + "_0.dat");
        Files.createDirectories(blocked);
        var host = new Host(level); host.logic.getEncodedPatternInv().setItemDirect(0, pattern("io-failure"));
        boolean failed = false;
        try { CloudTerminalUpload.upload(actor, libraryId, host.logic); }
        catch (RuntimeException expected) { failed = true; }
        check(failed && CloudTerminalUpload.quarantined(host.logic), "actual_snapshot_failure_enters_review", checks);
        var failedSource = new CompoundTag(); host.logic.writeToNBT(failedSource);
        check(failedSource.getCompound("gtlCloudSource").getLong("processedGeneration") == 1
                && host.logic.getEncodedPatternInv().isEmpty(), "failed_commit_keeps_durable_debit_and_empty_source", checks);
        Files.delete(blocked);
        var reloaded = new Host(level); reloaded.logic.readFromNBT(failedSource);
        check(!CloudTerminalUpload.quarantined(reloaded.logic) && library.healthy() && library.entryCount() == 1
                && reloaded.logic.getEncodedPatternInv().isEmpty(), "reopen_retries_io_and_clears_review_exactly_once", checks);
        CloudTerminalUpload.reconcile(level, reloaded.logic);
        check(library.entryCount() == 1 && reloaded.logic.getEncodedPatternInv().isEmpty(), "repeated_reconcile_is_idempotent", checks);
        reloaded.logic.getEncodedPatternInv().setItemDirect(0, pattern("second-upload"));
        CloudTerminalUpload.upload(actor, libraryId, reloaded.logic);
        check(library.entryCount() == 2 && !CloudTerminalUpload.quarantined(reloaded.logic), "new_upload_succeeds_after_recovery", checks);

        var current = new CompoundTag(); reloaded.logic.writeToNBT(current);
        var source = current.getCompound("gtlCloudSource"); source.putBoolean("quarantined", true);
        source.putString("failure", "Cloud snapshot write failed");
        source.put("escrow", pattern("new-unrelated-original").save(new CompoundTag()));
        var escrow = new Host(level); escrow.logic.readFromNBT(current);
        check(!CloudTerminalUpload.quarantined(escrow.logic) && library.entryCount() == 2
                && ItemStack.matches(escrow.logic.getEncodedPatternInv().getStackInSlot(0), pattern("new-unrelated-original")),
                "acknowledged_commit_preserves_new_escrow_original", checks);
        var stale = new Host(level); stale.logic.readFromNBT(failedSource);
        check(CloudTerminalUpload.quarantined(stale.logic) && library.entryCount() == 2,
                "different_generation_is_not_guessed_or_cleared", checks);

        // Save enough originals to span payload shards, then discard all derived indexes and reload authority from disk.
        for (int i = 2; i < 270; i++) {
            UUID operation = UUID.randomUUID(); var entry = store.upload(libraryId, actor.getUUID(), pattern("catalog-" + i), new CompoundTag(), operation);
            CloudLibraryService.identify(entry, actor.getUUID()); store.acknowledge(libraryId, actor.getUUID(), operation);
        }
        CloudLibraryService.prepareOpeningCatalog(level, libraryId);
        check(!library.hasOpeningCatalog(), "pending_catalog_does_not_persist_empty_first_page", checks);
        var pending = CloudLibraryService.page(level, libraryId, "", "", 0, "");
        check(pending.getBoolean("indexing"), "missing_summary_queues_bounded_catalog_build", checks);
        long token = CloudLibraryService.catalogUiRevision(library);
        awaitCatalog(level, libraryId);
        check(token != CloudLibraryService.catalogUiRevision(library), "completed_catalog_invalidates_open_menu_without_poll_delay", checks);
        var first = CloudLibraryService.page(level, libraryId, "", "", 0, "");
        store.checkpointLibrary(libraryId);
        var saved = NbtIo.readCompressed(data.resolve("gtl_cloud_library_" + libraryId + ".dat").toFile());
        check(saved.getCompound("data").getCompound("data").getCompound("openingCatalog").getInt("total") == 270,
                "first_page_directory_is_in_world_saveddata", checks);
        var catalogs = CloudLibraryService.class.getDeclaredField("CATALOGS"); catalogs.setAccessible(true);
        ((java.util.Map<?,?>)catalogs.get(null)).clear();
        var libraries = CloudPatternStore.class.getDeclaredField("libraries"); libraries.setAccessible(true);
        ((java.util.Map<?,?>)libraries.get(store)).remove(libraryId);
        for (var cache : net.minecraft.world.level.storage.DimensionDataStorage.class.getDeclaredFields()) {
            if (!java.util.Map.class.isAssignableFrom(cache.getType())) continue;
            cache.setAccessible(true);
            ((java.util.Map<?,?>)cache.get(level.getServer().overworld().getDataStorage())).remove("gtl_cloud_library_" + libraryId);
        }
        var cold = CloudLibraryService.page(level, libraryId, "", "", 0, "");
        check(cold.equals(first) && ((java.util.Map<?,?>)catalogs.get(null)).isEmpty(),
                "cold_reopen_uses_saved_first_page_without_full_catalog_rebuild", checks);
        var restored = store.library(libraryId);
        var shards = restored.getClass().getDeclaredField("shards"); shards.setAccessible(true);
        check(((java.util.Map<?,?>)shards.get(restored)).size() == 1, "cold_first_page_loads_only_its_payload_shard", checks);
        var oldCount = cold.getInt("total");
        UUID operation = UUID.randomUUID(); var added = store.upload(libraryId, actor.getUUID(), pattern("invalidate"), new CompoundTag(), operation);
        CloudLibraryService.identify(added, actor.getUUID()); store.acknowledge(libraryId, actor.getUUID(), operation);
        check(!restored.hasOpeningCatalog(), "mutation_invalidates_saved_directory_immediately", checks);
        CloudLibraryService.prepareOpeningCatalog(level, libraryId);
        check(!restored.hasOpeningCatalog(), "mutation_cannot_stamp_stale_summary_with_new_revision", checks);
        awaitCatalog(level, libraryId);
        check(CloudLibraryService.page(level, libraryId, "", "", 0, "").getInt("total") == oldCount + 1,
                "completed_mutation_publishes_new_persistent_count", checks);

        player.setPos(child.getPos().getX() + .5, child.getPos().getY() + .5, child.getPos().getZ() + 1);
        var before = child.cloudUiInitialSnapshot(player);
        child.onUnload(); child.onLoad();
        var after = child.cloudUiInitialSnapshot(player);
        check(before.getList("entries", 10).equals(after.getList("entries", 10)) && after.getInt("total") > 0,
                "buffer_reload_has_saved_lease_page_before_maintenance", checks);

        var wireless = org.gtlcore.gtlcore.integration.ae2.wireless.WirelessAeSavedData.get(level.getServer());
        var frequency = wirelessCore.getFrequency();
        check(wirelessCore.getActionableNode() != null
                && org.gtlcore.gtlcore.integration.ae2.wireless.WirelessAeNetworkRuntime.findBridgeNode(wirelessCore) != null,
                "loaded_wireless_core_has_real_connection_side_bridge", checks);
        var member = new org.gtlcore.gtlcore.integration.ae2.wireless.WirelessAeSavedData.MemberKey(
                net.minecraft.core.GlobalPos.of(level.dimension(), main.getPos()), null);
        wireless.addMember(frequency, member);
        var savedWireless = wireless.save(new CompoundTag());
        check(frequency.equals(org.gtlcore.gtlcore.integration.ae2.wireless.WirelessAeSavedData.load(savedWireless).getExactMemberNetwork(member)),
                "wireless_membership_survives_saveddata_roundtrip", checks);
        CloudMeConnection.restore(main);
        check(main.getMainNode().getGrid() == wirelessCore.getActionableNode().getGrid(),
                "core_saved_membership_reconnects_immediately_without_scheduler_scan", checks);
        CloudMeConnection.restore(main);
        check(main.serviceGrid() == main.getMainNode().getGrid(), "wireless_core_is_live_anchor_after_idempotent_reconnect", checks);
        wireless.removeMember(frequency, member);
        org.gtlcore.gtlcore.integration.ae2.wireless.WirelessAeNetworkRuntime.disconnectMember(frequency, member);
        CloudMeConnection.restore(main);
        check(wireless.getExactMemberNetwork(member) == null
                && !org.gtlcore.gtlcore.integration.ae2.wireless.WirelessAeNetworkRuntime.hasWirelessConnection(frequency, member),
                "explicit_unbind_is_not_undone_by_fast_restore", checks);
        var childMember = new org.gtlcore.gtlcore.integration.ae2.wireless.WirelessAeSavedData.MemberKey(
                net.minecraft.core.GlobalPos.of(level.dimension(), child.getPos()), null);
        wireless.addMember(frequency, childMember);
        // onLoad scheduled the new AE node; its same-tick target may still be null. Membership is already durable.
        var restart = new CompoundTag(); restart.putUUID("library", libraryId); restart.putUUID("actor", actor.getUUID());
        restart.put("source", current); restart.putUUID("frequency", frequency);
        restart.putLong("child", child.getPos().asLong()); restart.putLong("main", main.getPos().asLong());
        NbtIo.writeCompressed(restart, level.getServer().getWorldPath(LevelResource.ROOT).resolve("instant-reload.dat").toFile());
        store.checkpointLibrary(libraryId);
    }
    static void awaitCatalog(ServerLevel level, UUID libraryId) {
        var library = CloudPatternStore.get(level).library(libraryId);
        for (int tick = 0; tick < 2000; tick++) {
            if (CloudLibraryService.catalogReady(library, level)) return;
            CloudLibraryService.tickCatalogs(new net.minecraftforge.event.TickEvent.ServerTickEvent(
                    net.minecraftforge.event.TickEvent.Phase.END, () -> true, level.getServer()));
        }
        throw new AssertionError("Bounded catalog did not complete");
    }
    public static void reload(net.minecraft.server.MinecraftServer server) {
        var checks = new java.util.ArrayList<String>(); var report = new java.util.LinkedHashMap<String,Object>();
        try {
            var level = server.overworld();
            var restart = NbtIo.readCompressed(server.getWorldPath(LevelResource.ROOT).resolve("instant-reload.dat").toFile());
            var libraryId = restart.getUUID("library");
            var before = CloudLibraryService.page(level, libraryId, "", "", 0, "");
            check(before.getInt("total") == 271 && before.getList("entries", 10).size() == 36,
                    "new_jvm_reads_complete_first_page_from_saved_world", checks);
            var host = new Host(level); host.logic.readFromNBT(restart.getCompound("source"));
            check(!CloudTerminalUpload.quarantined(host.logic)
                    && ItemStack.matches(host.logic.getEncodedPatternInv().getStackInSlot(0), pattern("new-unrelated-original")),
                    "new_jvm_acknowledged_io_failure_recovers_with_original_preserved", checks);
            var actor = net.minecraftforge.common.util.FakePlayerFactory.get(level,
                    new com.mojang.authlib.GameProfile(restart.getUUID("actor"), "CommitRetryAudit"));
            CloudTerminalUpload.upload(actor, libraryId, host.logic);
            check(CloudPatternStore.get(level).library(libraryId).entryCount() == 272 && host.logic.getEncodedPatternInv().isEmpty(),
                    "new_jvm_can_upload_again_after_recovery", checks);
            var wireless = org.gtlcore.gtlcore.integration.ae2.wireless.WirelessAeSavedData.get(server);
            check(restart.getUUID("frequency").equals(wireless.getMemberNetwork(net.minecraft.core.GlobalPos.of(level.dimension(),
                    net.minecraft.core.BlockPos.of(restart.getLong("child")))))
                    && wireless.getMemberNetwork(net.minecraft.core.GlobalPos.of(level.dimension(), net.minecraft.core.BlockPos.of(restart.getLong("main")))) == null,
                    "new_jvm_preserves_selected_child_network_and_explicit_main_unbind", checks);
            report.put("status", "PASS");
        } catch (Throwable failure) { report.put("status", "FAIL"); report.put("error", failure.toString()); failure.printStackTrace(); }
        report.put("checks", checks);
        try { Files.writeString(server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize().getParent().resolve("cloud-instant-reload.json"),
                new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(report)); }
        catch (Exception failure) { throw new IllegalStateException(failure); }
        server.halt(false);
    }
}
