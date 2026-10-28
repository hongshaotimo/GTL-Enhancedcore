package com.gtl.enhancedcore.audit;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.helpers.IPatternTerminalLogicHost;
import appeng.parts.encoding.PatternEncodingLogic;
import com.gtl.enhancedcore.common.cloud.CloudPatternLibraryMachine;
import com.gtl.enhancedcore.common.cloud.store.CloudLedger;
import com.gtl.enhancedcore.common.cloud.store.CloudPatternStore;
import com.gtl.enhancedcore.common.cloud.transfer.CloudTerminalUpload;
import com.gtl.enhancedcore.common.cloud.transfer.CloudTerminalSource;
import com.lowdragmc.lowdraglib.gui.modular.ModularUIContainer;
import com.lowdragmc.lowdraglib.gui.widget.SlotWidget;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import io.netty.buffer.Unpooled;
import java.nio.file.Files;
import java.util.List;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.common.util.FakePlayer;

/** 3.9.4 opt-in production menu, SavedData and transformed AE checks. */
public final class CloudPatternInteractionRuntimeChecks {
    private CloudPatternInteractionRuntimeChecks() {}
    private record Menu(ModularUIContainer container, Widget panel, Widget root, Slot upload, Slot playerSlot) {}
    private static final class Host implements IPatternTerminalLogicHost {
        final ServerLevel level; final PatternEncodingLogic logic;
        Host(ServerLevel level) { this.level = level; logic = new PatternEncodingLogic(this); }
        public PatternEncodingLogic getLogic() { return logic; }
        public Level getLevel() { return level; }
        public void markForSave() {}
    }
    private static ItemStack pattern(String marker) {
        ItemStack result = PatternDetailsHelper.encodeProcessingPattern(
                new GenericStack[]{new GenericStack(AEItemKey.of(Items.APPLE), 1)},
                new GenericStack[]{new GenericStack(AEItemKey.of(Items.GOLD_INGOT), 1)});
        result.getOrCreateTag().putString("interactionAudit", marker); return result;
    }
    private static Menu open(CloudPatternLibraryMachine main, FakePlayer player) {
        var ui = main.createUI(player); ui.initWidgets();
        var container = new ModularUIContainer(ui, 79); player.containerMenu = container;
        Slot upload = ui.getSlotMap().keySet().stream().filter(slot -> slot instanceof SlotWidget.WidgetSlotItemTransfer transfer
                && transfer.getItemHandler().getSlots() == 1).findFirst().orElseThrow();
        Slot playerSlot = container.slots.stream().filter(slot -> slot.container == player.getInventory()
                && slot.getContainerSlot() == 9).findFirst().orElseThrow();
        return new Menu(container, ui.getFirstWidgetById("cloud.library"), ui.mainGroup, upload, playerSlot);
    }
    private static void check(boolean value, String label, List<String> checks) {
        if (!value) {
            String state = "";
            if (current != null && player != null && main != null && library != null) state = " phase=" + phase
                    + " source=" + player.getInventory().getItem(9).save(new CompoundTag())
                    + " carried=" + current.container.getCarried().save(new CompoundTag())
                    + " inbox=" + main.importInventory().serializeNBT() + " entries=" + library.entryCount()
                    + " base=" + count + " uploadActive=" + current.upload.isActive()
                    + " uploadAccepts=" + current.upload.mayPlace(pattern("diagnostic-probe"))
                    + " diagnostics=" + main.cloudUiSnapshot(player, "", "", 0, "");
            throw new AssertionError("pattern_interaction:" + label + state);
        }
        checks.add("pattern_interaction:" + label);
    }
    private static long unresolved(CloudPatternStore.Library library) {
        return library.receipts().stream().filter(receipt -> !receipt.status().equals("ACKNOWLEDGED")).count();
    }
    private static long journalFiles(ServerLevel level) throws Exception {
        return journalFiles(level, "gtl_cloud_import");
    }
    private static long journalFiles(ServerLevel level, String kind) throws Exception {
        var directory = level.getServer().getWorldPath(LevelResource.ROOT).resolve("data/" + kind);
        if (!Files.isDirectory(directory)) return 0;
        try (var files = Files.walk(directory)) { return files.filter(Files::isRegularFile).count(); }
    }
    private static CloudPatternStore.Entry exact(CloudPatternStore.Library library, ItemStack original) {
        return library.entries().stream().filter(entry -> ItemStack.isSameItemSameTags(entry.original(), original)
                && entry.status() != CloudLedger.Status.EXPORTED).findFirst().orElseThrow();
    }
    private static CompoundTag initial(Menu menu) throws Exception {
        var installed = player.containerMenu; player.containerMenu = previousMenu;
        try {
            return serverInitialSnapshot(menu.root, menu.panel);
        } finally { player.containerMenu = installed; }
    }
    /** Client decoding belongs to the client audit: LDLib strips client readers on a dedicated server. */
    static CompoundTag serverInitialSnapshot(Widget root, Widget panel) throws Exception {
        var packet = new CapturingInitialBuffer();
        try {
            root.writeInitialData(packet);
            Class<?> owner = panel.getClass(); java.lang.reflect.Field field = null;
            while (owner != null && field == null) { try { field = owner.getDeclaredField("snapshot"); } catch (NoSuchFieldException missing) { owner = owner.getSuperclass(); } }
            if (field == null) throw new AssertionError("Production snapshot missing");
            field.setAccessible(true); CompoundTag snapshot = ((CompoundTag)field.get(panel)).copy();
            if (snapshot.isEmpty()) throw new AssertionError("Initial root writer left an empty production snapshot");
            boolean found = false;
            for (var capture : packet.captures) {
                if (!snapshot.equals(capture.tag())) continue;
                var encoded = new FriendlyByteBuf(packet.retainedSlice(capture.start(), capture.end() - capture.start()));
                try {
                    if (!snapshot.equals(encoded.readNbt()) || encoded.readableBytes() != 0)
                        throw new AssertionError("Native initial snapshot NBT fails exact captured-span round trip");
                    found = true;
                } finally { encoded.release(); }
            }
            if (!found) throw new AssertionError("Initial root packet omits the production snapshot NBT: " + snapshot);
            return snapshot;
        } finally { packet.release(); }
    }
    private record NbtCapture(int start, int end, CompoundTag tag) {}
    private static final class CapturingInitialBuffer extends FriendlyByteBuf {
        final java.util.List<NbtCapture> captures = new java.util.ArrayList<>();
        CapturingInitialBuffer() { super(Unpooled.buffer()); }
        @Override public FriendlyByteBuf writeNbt(CompoundTag tag) {
            int start = writerIndex();
            super.writeNbt(tag);
            captures.add(new NbtCapture(start, writerIndex(), tag == null ? null : tag.copy()));
            return this;
        }
    }
    private static ServerLevel level;
    private static CloudPatternLibraryMachine main;
    private static FakePlayer player;
    private static List<String> checks;
    private static CloudPatternStore store;
    private static CloudPatternStore.Library library;
    private static net.minecraft.world.inventory.AbstractContainerMenu previousMenu;
    private static ListTag inventory;
    private static ItemStack cursor, a, b, c, race;
    private static Menu current, competing;
    private static int phase, count, receipts, shiftCount, shiftWait;
    private static long pending, files;
    private static CompoundTag firstSnapshot;
    private static CloudPatternStore.Entry withdrawing;
    private static java.util.Map<UUID, net.minecraft.server.level.ServerPlayer> onlineIndex;
    private static net.minecraft.server.level.ServerPlayer priorOnline;
    private static boolean onlineRegistered;
    @SuppressWarnings("unchecked")
    private static void registerFixtureActor() throws Exception {
        var list = level.getServer().getPlayerList();
        for (Class<?> owner = list.getClass(); owner != null && onlineIndex == null; owner = owner.getSuperclass())
                for (var candidate : owner.getDeclaredFields()) {
                    String type = candidate.getGenericType().getTypeName();
                    if (!java.util.Map.class.isAssignableFrom(candidate.getType()) || !type.contains("java.util.UUID")
                            || !type.contains(net.minecraft.server.level.ServerPlayer.class.getName())) continue;
                    candidate.setAccessible(true); onlineIndex = (java.util.Map<UUID, net.minecraft.server.level.ServerPlayer>)candidate.get(list); break;
                }
        if (onlineIndex == null) throw new AssertionError("Cannot locate the isolated server's player UUID index");
        priorOnline = onlineIndex.put(player.getUUID(), player); onlineRegistered = true;
        check(list.getPlayer(player.getUUID()) == player, "fixture_actor_is_resolved_by_native_online_lookup_for_deferred_upload", checks);
    }
    private static void identityStates() {
        UUID actor = UUID.randomUUID(); UUID id = store.defaultLibrary(actor); var target = store.library(id);
        ItemStack original = pattern("pending-identity"), first = original.copyWithCount(3); UUID operation = UUID.randomUUID();
        var entry = store.upload(id, actor, first, new CompoundTag(), operation);
        check(first.getCount() == 2 && entry.status() == CloudLedger.Status.EXTERNAL_PENDING, "first_upload_reserves_exact_original_while_external_pending", checks);
        int receipts = target.receipts().size(); ItemStack duplicate = original.copyWithCount(4); boolean rejected = false;
        CompoundTag otherConfig = new CompoundTag(); otherConfig.putInt("priority", 999);
        try { store.upload(id, actor, duplicate, otherConfig, UUID.randomUUID()); } catch (IllegalArgumentException expected) { rejected = true; }
        check(rejected && duplicate.getCount() == 4 && target.receipts().size() == receipts && target.entryCount() == 1,
                "pending_original_rejects_competing_upload_regardless_of_count_or_slot_configuration", checks);
        store.acknowledge(id, actor, operation); target.archive(actor, entry.id(), entry.revision(), true); rejected = false;
        try { store.upload(id, actor, duplicate, new CompoundTag(), UUID.randomUUID()); } catch (IllegalArgumentException expected) { rejected = true; }
        check(rejected && duplicate.getCount() == 4 && target.entryCount() == 1, "archived_original_remains_in_exact_duplicate_index", checks);
        target.archive(actor, entry.id(), entry.revision(), false); UUID export = UUID.randomUUID();
        store.extract(id, actor, entry.id(), entry.revision(), export); store.acknowledge(id, actor, export);
        UUID returning = UUID.randomUUID(); var replacement = store.upload(id, actor, duplicate, new CompoundTag(), returning); store.acknowledge(id, actor, returning);
        check(duplicate.getCount() == 3 && replacement.status() == CloudLedger.Status.AVAILABLE,
                "exported_original_releases_duplicate_index_for_legitimate_return", checks);
    }
    public static void begin(ServerLevel world, CloudPatternLibraryMachine machine, FakePlayer actor, List<String> results) throws Exception {
        if (!machine.isFormed()) throw new AssertionError("Interaction audit requires a formed production main");
        level = world; main = machine; player = actor; checks = results;
        store = CloudPatternStore.get(level); library = store.library(main.libraryId());
        previousMenu = player.containerMenu; inventory = player.getInventory().save(new ListTag()); cursor = previousMenu.getCarried().copy();
        count = library.entryCount(); pending = unresolved(library); phase = 0;
        a = pattern("cursor"); b = pattern("shift"); c = pattern("same-output-different-nbt"); race = pattern("two-menu-race");
        registerFixtureActor();
    }
    private static void action(String name, CloudPatternStore.Entry entry) {
        CompoundTag payload = new CompoundTag();
        if (entry != null) { payload.putString("selected", entry.id().toString()); payload.putLong("revision", entry.revision()); }
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try { buffer.writeUtf(name, 32); buffer.writeNbt(payload); current.panel.handleClientAction(211, buffer); } finally { buffer.release(); }
    }
    /** One stage per real server tick, permitting the production automatic inbox drain. */
    public static boolean step() throws Exception {
        switch (phase++) {
            case 0 -> {
                identityStates();
                player.getInventory().clearContent(); current = open(main, player);
                player.getInventory().setItem(9, a.copy()); current.container.clicked(current.playerSlot.index, 0, ClickType.PICKUP, player);
                action("import_cursor", null);
            }
            case 1 -> {
                check(current.container.getCarried().isEmpty() && main.importInventory().getStackInSlot(0).isEmpty() && library.entryCount() == count + 1,
                        "native_backpack_pickup_then_cursor_action_uploads_without_import_button", checks);
                shiftCount = b.getMaxStackSize() >= 2 ? 2 : 1;
                var upload = ((SlotWidget.WidgetSlotItemTransfer)current.upload).getItemHandler();
                check(upload.insertItem(0, b.copyWithCount(2), true, true).getCount() == 1,
                        "native_upload_capacity_probe_accepts_only_one_original_from_count_two", checks);
                player.getInventory().setItem(9, b.copyWithCount(shiftCount)); current.container.quickMoveStack(player, current.playerSlot.index);
                shiftWait = 0;
            }
            case 2 -> {
                if (library.entryCount() != count + 2 && shiftWait++ < 4) { phase--; return false; }
                check(player.getInventory().getItem(9).getCount() == shiftCount - 1 && main.importInventory().getStackInSlot(0).isEmpty() && library.entryCount() == count + 2,
                        "visible_main_page_player_shift_click_uploads_automatically", checks);
                if (shiftCount == 2) check(ItemStack.isSameItemSameTags(player.getInventory().getItem(9), b),
                        "native_shift_from_legal_two_stack_uploads_one_and_retains_one_exact_duplicate", checks);
                files = journalFiles(level); receipts = library.receipts().size();
                current.container.setCarried(a.copy()); action("import_cursor", null);
            }
            case 3 -> {
                check(ItemStack.matches(current.container.getCarried(), a) && library.entryCount() == count + 2, "duplicate_cursor_original_is_not_debited", checks);
                current.container.setCarried(ItemStack.EMPTY); player.getInventory().setItem(9, b.copy());
                current.container.quickMoveStack(player, current.playerSlot.index);
            }
            case 4 -> {
                check(ItemStack.matches(player.getInventory().getItem(9), b) && library.entryCount() == count + 2, "duplicate_shift_original_is_not_debited", checks);
                check(library.receipts().size() == receipts && journalFiles(level) == files && unresolved(library) == pending && !main.cloud$importQuarantined(),
                        "duplicate_local_upload_creates_no_receipt_intent_or_quarantine", checks);
                player.getInventory().setItem(9, c.copy()); current.container.quickMoveStack(player, current.playerSlot.index);
            }
            case 5 -> {
                check(player.getInventory().getItem(9).isEmpty() && library.entryCount() == count + 3, "same_output_different_nbt_is_distinct_original", checks);
                var terminal = new Host(level); terminal.logic.getEncodedPatternInv().setItemDirect(0, c.copy()); receipts = library.receipts().size();
                long terminalFiles = journalFiles(level, "gtl_cloud_terminal");
                try { CloudTerminalUpload.upload(player, main.libraryId(), terminal.logic); } catch (IllegalArgumentException | IllegalStateException expected) {}
                check(ItemStack.matches(terminal.logic.getEncodedPatternInv().getStackInSlot(0), c) && !CloudTerminalUpload.quarantined(terminal.logic)
                        && library.receipts().size() == receipts && library.entryCount() == count + 3 && unresolved(library) == pending,
                        "duplicate_encoded_terminal_keeps_original_without_pending_transaction", checks);
                check(!((CloudTerminalSource)terminal.logic).cloud$sourceState().hasUUID("identity")
                        && terminalFiles == journalFiles(level, "gtl_cloud_terminal"), "duplicate_terminal_preflight_writes_no_identity_or_intent", checks);
                current = open(main, player); competing = open(main, player);
                player.getInventory().setItem(9, race.copyWithCount(2)); competing.container.quickMoveStack(player, competing.playerSlot.index);
            }
            case 6 -> { player.containerMenu = current.container; current.container.quickMoveStack(player, current.playerSlot.index); }
            case 7 -> {
                check(library.entries().stream().filter(entry -> ItemStack.isSameItemSameTags(entry.original(), race)).count() == 1
                        && player.getInventory().getItem(9).getCount() == 1 && unresolved(library) == pending,
                        "two_open_menus_serialized_accept_exactly_one_original", checks);
                player.getInventory().clearContent(); withdrawing = exact(library, a); action("take_pattern", withdrawing);
                check(ItemStack.matches(current.container.getCarried(), a) && withdrawing.status() == CloudLedger.Status.EXPORTED, "take_pattern_returns_exact_original_to_cursor", checks);
                action("take_pattern", withdrawing);
                check(ItemStack.matches(current.container.getCarried(), a), "stale_take_pattern_does_not_duplicate_original", checks);
                current.container.setCarried(ItemStack.EMPTY); withdrawing = exact(library, b); action("withdraw", withdrawing);
                check(player.getInventory().items.stream().filter(stack -> ItemStack.isSameItemSameTags(stack, b)).mapToInt(ItemStack::getCount).sum() == 1
                        && withdrawing.status() == CloudLedger.Status.EXPORTED, "withdraw_returns_exact_original_to_inventory_once", checks);
                action("withdraw", withdrawing);
                check(player.getInventory().items.stream().filter(stack -> ItemStack.isSameItemSameTags(stack, b)).mapToInt(ItemStack::getCount).sum() == 1, "stale_withdraw_does_not_duplicate_original", checks);
            }
            case 8 -> {
                current = open(main, player); firstSnapshot = initial(current);
                check(firstSnapshot.getBoolean("networkConnected") && firstSnapshot.getInt("libraryTotal") >= count + 2
                        && !firstSnapshot.getString("status").endsWith("offline"), "first_open_packet_contains_live_network_and_nonzero_catalog", checks);
            }
            case 9, 10, 11 -> {
                current = open(main, player); CompoundTag reopened = initial(current);
                check(reopened.getBoolean("networkConnected") && reopened.getInt("libraryTotal") == firstSnapshot.getInt("libraryTotal"),
                        "reopen_" + (phase - 9) + "_preserves_live_first_packet", checks);
            }
            case 12 -> {
                CompoundTag reference = new CompoundTag(); reference.putUUID("library", main.libraryId()); reference.putUUID("actor", player.getUUID());
                reference.put("original", c.save(new CompoundTag())); reference.putInt("entries", library.entryCount());
                store.checkpoint();
                net.minecraft.nbt.NbtIo.writeCompressed(reference, level.getServer().getWorldPath(LevelResource.ROOT).resolve("data/cloud-interaction-reference.dat").toFile());
                cleanup(); return true;
            }
            default -> throw new AssertionError("Unknown interaction stage " + phase);
        }
        return false;
    }
    public static void cleanup() {
        if (player == null || previousMenu == null) return;
        if (current != null) current.container.setCarried(ItemStack.EMPTY);
        player.getInventory().load(inventory); player.containerMenu = previousMenu; previousMenu.setCarried(cursor);
        if (onlineRegistered) {
            if (priorOnline == null) onlineIndex.remove(player.getUUID(), player); else onlineIndex.put(player.getUUID(), priorOnline);
            onlineRegistered = false;
        }
    }
    /** Must execute in a new Forge process; never simulates persistence by reusing the runtime object. */
    public static void verifyReload(ServerLevel world, List<String> checks) throws Exception {
        CompoundTag reference = net.minecraft.nbt.NbtIo.readCompressed(world.getServer().getWorldPath(LevelResource.ROOT)
                .resolve("data/cloud-interaction-reference.dat").toFile());
        var loadedStore = CloudPatternStore.get(world); var loaded = loadedStore.library(reference.getUUID("library"));
        UUID actor = reference.getUUID("actor"); ItemStack original = ItemStack.of(reference.getCompound("original"));
        int count = loaded.entryCount(), receipts = loaded.receipts().size();
        check(count == reference.getInt("entries") && loaded.healthy() && loadedStore.healthy(), "independent_jvm_restores_exact_catalog_and_health", checks);
        ItemStack attempt = original.copyWithCount(3); boolean rejected = false;
        try { loadedStore.upload(loaded.id(), actor, attempt, new CompoundTag(), UUID.randomUUID()); }
        catch (IllegalArgumentException duplicate) { rejected = true; }
        check(rejected && attempt.getCount() == 3 && loaded.entryCount() == count && loaded.receipts().size() == receipts,
                "independent_jvm_rejects_exact_duplicate_without_debit_or_receipt", checks);
        ItemStack changed = original.copy(); changed.getOrCreateTag().putString("interactionAudit", "after-independent-restart");
        UUID operation = UUID.randomUUID(); var accepted = loadedStore.upload(loaded.id(), actor, changed, new CompoundTag(), operation);
        loadedStore.acknowledge(loaded.id(), actor, operation);
        check(changed.isEmpty() && loaded.entryCount() == count + 1 && accepted.status() == CloudLedger.Status.AVAILABLE,
                "independent_jvm_accepts_same_recipe_with_different_nbt", checks);
        loadedStore.checkpoint();
    }
}
