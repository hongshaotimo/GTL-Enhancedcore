package com.gtl.enhancedcore.audit;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import com.gtl.enhancedcore.common.cloud.CloudPatternAllocator;
import com.gtl.enhancedcore.common.cloud.CloudPatternBufferMachine;
import com.gtl.enhancedcore.common.cloud.CloudPatternLibraryMachine;
import com.gtl.enhancedcore.common.cloud.store.CloudPatternStore;
import com.lowdragmc.lowdraglib.gui.modular.ModularUIContainer;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.util.FakePlayer;
import org.gtlcore.gtlcore.integration.ae2.pattern.PatternQuickUploadMetadata;

/** Small production-structure stage; never synthesizes a formed controller or calls private maintenance. */
public final class CloudPatternPassiveRefreshRuntimeChecks {
    private CloudPatternPassiveRefreshRuntimeChecks() {}
    private static ServerLevel level;
    private static CloudPatternLibraryMachine main;
    private static CloudPatternBufferMachine child;
    private static FakePlayer player;
    private static List<String> checks;
    private static Runnable reconnect;
    private static CloudPatternStore.Entry seed, event, afterReconnect;
    private static int stage, ticks, started, quiet;
    private static long epoch, revision;
    private static Object published, search;
    private static String baselineState;
    private static BlockPos cell;
    private static BlockState cellState;
    public static void begin(ServerLevel world, CloudPatternLibraryMachine library, CloudPatternBufferMachine buffer,
                             FakePlayer actor, List<String> results, Runnable connect) {
        level = world; main = library; child = buffer; player = actor; checks = results; reconnect = connect;
        stage = 0; ticks = 0; started = 0; quiet = 0;
        require(main.isFormed() && child.cloudReady(true), "actual_formed_controller_and_newly_attached_child_are_ready");
    }
    private static Object field(Object object, String name) throws Exception {
        Class<?> owner = object.getClass();
        while (owner != null) { try { var field = owner.getDeclaredField(name); field.setAccessible(true); return field.get(object); }
            catch (NoSuchFieldException absent) { owner = owner.getSuperclass(); } }
        throw new AssertionError("Missing production diagnostic field " + name);
    }
    private static void require(boolean value, String label) {
        if (!value) throw new AssertionError("passive_refresh:" + label); checks.add("passive_refresh:" + label);
    }
    private static CloudPatternStore.Entry upload(String marker) {
        ItemStack pattern = PatternDetailsHelper.encodeProcessingPattern(
                new GenericStack[]{new GenericStack(AEItemKey.of(Items.APPLE), 1)},
                new GenericStack[]{new GenericStack(AEItemKey.of(Items.GOLD_INGOT), 1)});
        pattern.getOrCreateTag().putString("passiveAudit", marker);
        PatternQuickUploadMetadata.writeRecipeTypeId(pattern, child.cloudMode());
        UUID operation = UUID.randomUUID(); var store = child.cloudStore();
        var entry = store.upload(child.cloudLibraryId(), player.getUUID(), pattern, new CompoundTag(), operation);
        store.acknowledge(child.cloudLibraryId(), player.getUUID(), operation); return entry;
    }
    private static boolean visible(CloudPatternStore.Entry entry) throws Exception {
        if (entry.lease() == null || !entry.lease().nodeId().equals(child.cloudNodeId())
                || (Boolean)field(child, "publicationDirty") || CloudPatternAllocator.isScanning(child)) return false;
        var patterns = child.getAvailablePatterns();
        if (patterns.isEmpty() || patterns.size() != child.cloudMatchedCount()) return false;
        return patterns.stream().anyMatch(pattern -> {
            CompoundTag definition = pattern.getDefinition().getTag();
            if (definition == null) return false;
            CompoundTag route = definition.getCompound(com.gtl.enhancedcore.common.recipe.iv.IvBuffers.ROUTE_KEY);
            return route.hasUUID("lease") && route.getUUID("lease").equals(entry.lease().id())
                    && route.getInt("slot") == entry.lease().stableSlot()
                    && route.getCompound("original").equals(entry.original().save(new CompoundTag()));
        });
    }
    private static void openPacket() throws Exception {
        player.setPos(child.getPos().getX() + .5, child.getPos().getY() + .5, child.getPos().getZ() - 1);
        var previous = player.containerMenu;
        var ui = child.createUI(player); ui.initWidgets(); var menu = new ModularUIContainer(ui, 81);
        try {
            // Real UIFactory serializes before installing the new native container.
            var snapshot = CloudPatternInteractionRuntimeChecks.serverInitialSnapshot(
                    ui.mainGroup, ui.getFirstWidgetById("cloud.buffer"));
            if (!snapshot.getBoolean("networkConnected") || snapshot.getInt("matched") != child.cloudMatchedCount()
                    || snapshot.getInt("matched") < 1) throw new AssertionError("Child first packet is offline or falsely empty: " + snapshot);
            player.containerMenu = menu;
            child.cloudUiSnapshot(player, "", "", 0, "");
        } finally { player.containerMenu = previous; }
    }
    private static void baseline() throws Exception {
        epoch = child.cloudAllocationEpoch(); revision = (Long)field(child, "uiRevision");
        published = field(child, "publishedSnapshot");
        baselineState = diagnostic();
        openPacket(); var searches = (java.util.Map<?, ?>)field(child, "searches");
        search = searches.values().stream().findFirst().orElse(null); quiet = 0;
    }
    private static void stable() throws Exception {
        if (CloudPatternAllocator.isScanning(child) || child.cloudAllocationEpoch() != epoch
                || (Long)field(child, "uiRevision") != revision || field(child, "publishedSnapshot") != published)
            throw new AssertionError("Unchanged child rescanned or republished in stable window tick=" + quiet
                    + " before=" + baselineState + " current=" + diagnostic());
        child.refreshPublished();
        if (field(child, "publishedSnapshot") != published) throw new AssertionError("No-op refresh rebuilt published patterns");
        if (quiet < 3) openPacket();
        var searches = (java.util.Map<?, ?>)field(child, "searches");
        if (search != null && !searches.containsValue(search)) throw new AssertionError("Reopening reset the stable search cursor");
    }
    private static String diagnostic() throws Exception {
        var queuesField = CloudPatternAllocator.class.getDeclaredField("QUEUES"); queuesField.setAccessible(true);
        var queues = (java.util.Map<?, ?>)queuesField.get(null); Object queue = queues.get(level.getServer());
        return "{epoch=" + child.cloudAllocationEpoch() + ",revision=" + field(child, "uiRevision")
                + ",authority=" + child.cloudStore().library(child.cloudLibraryId()).allocationRevision()
                + ",lastAuthority=" + field(child, "lastLibraryRevision") + ",eligible=" + field(child, "allocationEligible")
                + ",ready=" + child.cloudReady(true) + ",publicationDirty=" + field(child, "publicationDirty")
                + ",publication=" + System.identityHashCode(field(child, "publishedSnapshot"))
                + ",matched=" + child.cloudMatchedCount() + ",available=" + child.getAvailablePatterns().size()
                + ",scanning=" + CloudPatternAllocator.isScanning(child)
                + ",queueOrder=" + (queue == null ? 0 : ((java.util.Collection<?>)field(queue, "order")).size())
                + ",queueScans=" + (queue == null ? 0 : ((java.util.Map<?, ?>)field(queue, "scans")).size()) + "}";
    }
    /** Returns after two 100-tick stable windows and one actual AE disconnect/reconnect. */
    public static boolean step() throws Exception {
        ticks++; if (ticks > 1800) throw new AssertionError("Passive refresh timed out stage=" + stage + " status=" + child.cloudStatusLabel());
        switch (stage) {
            case 0 -> {
                if (CloudPatternAllocator.isScanning(child) || !child.cloudReady(true)) return false;
                if (++quiet < 10) return false;
                seed = upload("first-placement"); started = ticks; stage = 1;
            }
            case 1 -> {
                if (!visible(seed)) return false;
                require(ticks - started <= 100, "newly_placed_child_accepts_its_first_catalog_original");
                baseline(); stage = 2;
            }
            case 2 -> {
                stable(); if (++quiet < 100) return false;
                require(true, "stable_100_ticks_and_three_menu_opens_do_not_rescan_republish_or_reset_search");
                event = upload("event-passive"); started = ticks;
                require(CloudPatternAllocator.isScanning(child), "upload_event_queues_ready_child_before_next_maintenance_tick");
                stage = 3;
            }
            case 3 -> {
                if (!visible(event)) return false;
                require(ticks - started <= 40, "event_original_becomes_visible_without_five_second_poll_delay_ticks_" + (ticks - started));
                baseline(); stage = 4;
            }
            case 4 -> {
                stable(); if (++quiet < 100) return false;
                require(true, "event_result_stays_stable_for_100_ticks_without_recurring_scan");
                cell = child.getPos().north(); cellState = level.getBlockState(cell);
                require(level.getBlockEntity(cell) instanceof appeng.blockentity.networking.CreativeEnergyCellBlockEntity,
                        "disconnect_targets_only_fixture_owned_real_ae_cell");
                level.removeBlock(cell, false); started = ticks; stage = 5;
            }
            case 5 -> {
                if (child.getMainNode().isActive() && child.cloudReady(true)) return false;
                require(!child.cloudReady(true), "actual_ae_cell_removal_revokes_child_readiness");
                level.setBlock(cell, cellState, 3); started = ticks; stage = 6;
            }
            case 6 -> {
                reconnect.run();
                if (!child.cloudReady(true) || CloudPatternAllocator.isScanning(child)) return false;
                require(visible(seed) && visible(event), "actual_ae_reconnect_restores_existing_child_publications");
                afterReconnect = upload("after-reconnect"); started = ticks; stage = 7;
            }
            case 7 -> {
                reconnect.run(); if (!visible(afterReconnect)) return false;
                require(ticks - started <= 40, "reconnected_child_passively_accepts_new_original_ticks_" + (ticks - started));
                openPacket(); require(true, "reconnected_child_first_menu_packet_contains_live_nonzero_data");
                child.cloudStore().checkpoint(); return true;
            }
            default -> throw new AssertionError("Unexpected passive stage " + stage);
        }
        return false;
    }
}
