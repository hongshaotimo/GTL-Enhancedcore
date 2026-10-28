package com.gtl.enhancedcore.audit;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import com.gtl.enhancedcore.common.cloud.CloudLibraryService;
import com.gtl.enhancedcore.common.cloud.CloudPatternLibraryMachine;
import com.gtl.enhancedcore.common.cloud.store.CloudPatternStore;
import com.lowdragmc.lowdraglib.gui.modular.ModularUIContainer;
import com.lowdragmc.lowdraglib.gui.widget.SlotWidget;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.side.item.IItemTransfer;
import io.netty.buffer.Unpooled;
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
import net.minecraftforge.common.util.FakePlayer;

/** Actual production menu/slot algorithms and server widget actions, no synthetic UI or transfer implementation. */
public final class CloudPatternCatalystRuntimeChecks {
    private CloudPatternCatalystRuntimeChecks() {}
    private record Menu(ModularUIContainer container, Widget panel, Slot catalyst, IItemTransfer transfer) {}
    private static Menu open(CloudPatternLibraryMachine main, FakePlayer player) {
        var ui = main.createUI(player); ui.initWidgets();
        var menu = new ModularUIContainer(ui, 76); player.containerMenu = menu;
        var widget = ui.getFirstWidgetById("cloud.catalyst.0");
        var slot = ui.getSlotMap().entrySet().stream().filter(e -> e.getValue() == widget).findFirst().orElseThrow().getKey();
        return new Menu(menu, ui.getFirstWidgetById("cloud.library"), slot,
                ((SlotWidget.WidgetSlotItemTransfer)slot).getItemHandler());
    }
    private static void action(Menu menu, String action, CloudPatternStore.Entry entry) {
        var payload = new CompoundTag(); payload.putString("selected", entry.id().toString()); payload.putLong("revision", entry.revision());
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try { buffer.writeUtf(action, 32); buffer.writeNbt(payload); menu.panel.handleClientAction(211, buffer); }
        finally { buffer.release(); }
    }
    private static CloudPatternStore.Entry entry(ServerLevel level, CloudPatternLibraryMachine main, FakePlayer player) {
        var pattern = PatternDetailsHelper.encodeProcessingPattern(
                new GenericStack[]{new GenericStack(AEItemKey.of(Items.APPLE), 1)},
                new GenericStack[]{new GenericStack(AEItemKey.of(Items.GOLD_INGOT), 1)});
        var store = CloudPatternStore.get(level); var operation = UUID.randomUUID();
        pattern.getOrCreateTag().putUUID("patternCatalystAudit", operation);
        var result = store.upload(main.libraryId(), player.getUUID(), pattern, new CompoundTag(), operation);
        store.acknowledge(main.libraryId(), player.getUUID(), operation); return result;
    }
    private static void amount(CloudPatternStore.Entry entry, UUID actor, int amount) {
        var state = entry.catalysts(); var items = CloudLibraryService.itemContents(state);
        for (int i = 0; i < 9; i++) items.setStackInSlot(i, new ItemStack(Items.COBBLESTONE, 64));
        items.setStackInSlot(0, amount == 0 ? ItemStack.EMPTY : new ItemStack(Items.IRON_INGOT, amount));
        state.put("items", items.serializeNBT()); entry.setCatalysts(actor, state);
    }
    private static int amount(CloudPatternStore.Entry entry) { return CloudLibraryService.itemContents(entry.catalysts()).getStackInSlot(0).getCount(); }
    private static void check(boolean value, String message, List<String> checks) {
        if (!value) throw new AssertionError("pattern_catalyst:" + message); checks.add("pattern_catalyst:" + message);
    }
    public static void run(ServerLevel level, CloudPatternLibraryMachine main, FakePlayer player, List<String> checks) {
        if (!main.isFormed()) throw new AssertionError("Catalyst audit requires the actual formed production library");
        var previousMenu = player.containerMenu; var previousInventory = player.getInventory().save(new ListTag());
        var previousCursor = previousMenu.getCarried().copy();
        var a = entry(level, main, player); var b = entry(level, main, player);
        Menu current = null;
        try {
            amount(a, player.getUUID(), 10); amount(b, player.getUUID(), 20);
            current = open(main, player);
            var playerSlot = current.container.slots.stream().filter(s -> s.container == player.getInventory() && s.getContainerSlot() == 9).findFirst().orElseThrow();
            player.getInventory().clearContent(); player.getInventory().setItem(9, new ItemStack(Items.IRON_INGOT, 8));
            current.container.quickMoveStack(player, playerSlot.index);
            check(amount(a) == 10 && amount(b) == 20 && player.getInventory().getItem(9).getCount() == 8,
                    "Hidden unselected catalyst slots reject native player shift-click", checks);
            action(current, "pattern_config", a);
            current.container.quickMoveStack(player, playerSlot.index);
            check(amount(a) == 18 && amount(b) == 20 && player.getInventory().getItem(9).isEmpty(),
                    "Actual native shift-in changes only the selected original and conserves items", checks);
            current.container.setCarried(ItemStack.EMPTY);
            current.container.clicked(current.catalyst.index, 0, ClickType.PICKUP, player);
            check(amount(a) == 0 && current.container.getCarried().getCount() == 18 && amount(b) == 20,
                    "Actual native cursor pickup extracts selected catalysts once", checks);
            current.container.clicked(current.catalyst.index, 0, ClickType.PICKUP, player);
            check(amount(a) == 18 && current.container.getCarried().isEmpty(), "Actual cursor merge persists the returned catalysts", checks);
            var oldA = main.cloudPatternItemCatalysts(player, a.id().toString()); oldA.getStackInSlot(0);
            action(current, "pattern_config", b);
            check(oldA.extractItem(0, 1, false, true).isEmpty() && amount(a) == 18 && amount(b) == 20,
                    "Changing selection revokes a previously exposed original handle", checks);
            action(current, "close_pattern_config", b);
            player.getInventory().setItem(9, new ItemStack(Items.IRON_INGOT, 7));
            current.container.quickMoveStack(player, playerSlot.index);
            check(amount(a) == 18 && amount(b) == 20 && player.getInventory().getItem(9).getCount() == 7,
                    "Closing catalyst details revokes hidden-slot native shift-click writes", checks);
            action(current, "pattern_config", a);
            oldA.getStackInSlot(0);
            current = open(main, player); action(current, "pattern_config", a);
            check(oldA.extractItem(0, 1, false, true).isEmpty() && amount(a) == 18,
                    "Reopening another production menu cannot reactivate a closed menu's handle", checks);
            boolean rejected = false;
            try { CloudPatternStore.get(level).extract(main.libraryId(), player.getUUID(), a.id(), a.revision(), UUID.randomUUID()); }
            catch (IllegalStateException expected) { rejected = true; }
            check(rejected && amount(a) == 18,
                    "Server export refuses to strand a physical per-pattern catalyst inventory", checks);
            amount(a, player.getUUID(), 10); action(current, "pattern_config", a);
            var stale = main.cloudPatternItemCatalysts(player, a.id().toString());
            var fresh = main.cloudPatternItemCatalysts(player, a.id().toString());
            ItemStack oldReference = stale.getStackInSlot(0);
            check(oldReference.getCount() == 10, "Old real-menu handler exposes ten authoritative catalysts before a competing extraction", checks);
            ItemStack taken = fresh.extractItem(0, 10, false, true);
            oldReference.shrink(1);
            try { stale.setStackInSlot(0, oldReference); } catch (IllegalStateException expectedStaleReference) {}
            check(taken.getCount() == 10 && amount(a) == 0 && amount(b) == 20,
                    "Competing real handler extraction prevents stale exposed setter from resurrecting catalysts", checks);
            amount(a, player.getUUID(), 10);
            stale = main.cloudPatternItemCatalysts(player, a.id().toString());
            fresh = main.cloudPatternItemCatalysts(player, a.id().toString());
            ItemStack referenceBeforeRefresh = stale.getStackInSlot(0);
            ItemStack takenBeforeRefresh = fresh.extractItem(0, 10, false, true);
            check(referenceBeforeRefresh.getCount() == 10 && takenBeforeRefresh.getCount() == 10 && amount(a) == 0,
                    "Second real-handler race starts with ten issued and ten actually extracted catalysts", checks);
            check(stale.getStackInSlot(0).isEmpty(), "Old real-menu handler observes the competing empty authority before setter retry", checks);
            referenceBeforeRefresh.shrink(1);
            try { stale.setStackInSlot(0, referenceBeforeRefresh); } catch (IllegalStateException expectedStaleReference) {}
            check(takenBeforeRefresh.getCount() == 10 && amount(a) == 0 && amount(b) == 20,
                    "Refreshing the handler mirror cannot legitimize an already issued stale ItemStack reference", checks);
        } finally {
            if (current != null) current.container.setCarried(ItemStack.EMPTY);
            if (a.status() != com.gtl.enhancedcore.common.cloud.store.CloudLedger.Status.EXPORTED) a.setCatalysts(player.getUUID(), new CompoundTag());
            if (b.status() != com.gtl.enhancedcore.common.cloud.store.CloudLedger.Status.EXPORTED) b.setCatalysts(player.getUUID(), new CompoundTag());
            player.getInventory().load(previousInventory); player.containerMenu = previousMenu; previousMenu.setCarried(previousCursor);
        }
    }
}
