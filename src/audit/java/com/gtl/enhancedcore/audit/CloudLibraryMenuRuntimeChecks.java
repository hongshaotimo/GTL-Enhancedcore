package com.gtl.enhancedcore.audit;

import com.gtl.enhancedcore.common.cloud.CloudLibraryService;
import com.gtl.enhancedcore.common.cloud.CloudPatternLibraryMachine;
import com.gtl.enhancedcore.common.cloud.store.CloudPatternStore;
import com.lowdragmc.lowdraglib.gui.modular.ModularUIContainer;
import com.lowdragmc.lowdraglib.gui.widget.SlotWidget;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.side.item.IItemTransfer;
import java.util.List;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.util.FakePlayer;

/** Actual production ModularUI slots and native cursor/quickMove algorithms, with real structure lifecycle. */
public final class CloudLibraryMenuRuntimeChecks {
    private CloudLibraryMenuRuntimeChecks() {}
    private record Menu(ModularUIContainer menu, Slot central, IItemTransfer items) {}
    private static Menu menu(CloudPatternLibraryMachine main, FakePlayer player) {
        var ui = main.createUI(player);
        // Match UIFactory.openUI: initWidgets installs each real native slot; the
        // ModularUIContainer constructor only attaches slots already registered.
        ui.initWidgets();
        var menu = new ModularUIContainer(ui, 74); player.containerMenu = menu;
        Slot central = null; IItemTransfer items = null; int playerSlots = 0;
        StringBuilder layout = new StringBuilder();
        for (var row : ui.getSlotMap().entrySet()) {
            Slot slot = row.getKey(); SlotWidget widget = row.getValue();
            layout.append(" [menu=").append(slot.index).append(",local=").append(slot.getContainerSlot())
                    .append(",slot=").append(slot.getClass().getName()).append(",container=").append(slot.container.getClass().getName())
                    .append(",isPlayerInventory=").append(slot.container == player.getInventory()).append(",playerFlag=").append(widget.isPlayerContainer);
            if (slot instanceof SlotWidget.WidgetSlotItemTransfer transfer) layout.append(",handler=")
                    .append(transfer.getItemHandler().getClass().getName()).append(",slots=").append(transfer.getItemHandler().getSlots());
            layout.append(']');
            // Show the real inventory widgets without dispatching client-only tab events.
            for (Widget current = widget; current != null; current = current.getParent()) { current.setVisible(true); current.setActive(true); }
            if (slot.container == player.getInventory()) {
                if (!widget.isPlayerContainer) throw new AssertionError("Production player slot lacks native shift-click direction metadata");
                playerSlots++;
            } else if (slot instanceof SlotWidget.WidgetSlotItemTransfer transfer && transfer.getItemHandler().getSlots() == 9 && slot.getContainerSlot() == 0) {
                central = slot; items = transfer.getItemHandler();
            }
        }
        if (central == null || items == null || playerSlots != 36) throw new AssertionError("Actual production menu slot layout missing: central="
                + (central == null ? "null" : central.index) + ", items=" + (items == null ? "null" : items.getClass().getName())
                + ", playerSlots=" + playerSlots + ", slotMap=" + ui.getSlotMap().size() + ", menuSlots=" + menu.slots.size() + layout);
        return new Menu(menu, central, items);
    }
    public static void run(ServerLevel level, CloudPatternLibraryMachine main, FakePlayer player, List<String> checks) {
        if (!main.isFormed()) throw new AssertionError("Menu audit requires the actual formed production library");
        var library = CloudPatternStore.get(level).library(main.libraryId()); CompoundTag original = library.catalysts();
        ListTag inventory = player.getInventory().save(new ListTag()); var previousMenu = player.containerMenu;
        ItemStack previousCursor = previousMenu.getCarried().copy(); Menu opened = null;
        try {
            opened = menu(main, player); var current = opened;
            authority(level, main, 64);
            fullPlayer(player); player.getInventory().setItem(9, new ItemStack(Items.IRON_INGOT, 48));
            current.menu.quickMoveStack(player, current.central.index);
            require(amount(level, main) == 48 && player.getInventory().getItem(9).getCount() == 64,
                    "Actual partial native shift-out moves 16 items once and persists the remaining central 48", checks);

            authority(level, main, 56); fullPlayer(player); player.getInventory().setItem(9, new ItemStack(Items.IRON_INGOT, 32));
            Slot playerSlot = current.menu.slots.stream().filter(slot -> slot.container == player.getInventory() && slot.getContainerSlot() == 9).findFirst().orElseThrow();
            current.menu.quickMoveStack(player, playerSlot.index);
            require(amount(level, main) == 64 && player.getInventory().getItem(9).getCount() == 24,
                    "Actual partial native shift-in merges eight into the existing central stack without item loss", checks);

            authority(level, main, 10); current.menu.setCarried(ItemStack.EMPTY);
            current.menu.clicked(current.central.index, 0, ClickType.PICKUP, player);
            require(amount(level, main) == 0 && current.menu.getCarried().getCount() == 10,
                    "Actual native cursor pickup extracts exactly the persisted central stack", checks);
            current.menu.clicked(current.central.index, 1, ClickType.PICKUP, player);
            require(amount(level, main) == 1 && current.menu.getCarried().getCount() == 9,
                    "Actual native right-click cursor deposit persists one item", checks);
            current.menu.clicked(current.central.index, 0, ClickType.PICKUP, player);
            require(amount(level, main) == 10 && current.menu.getCarried().isEmpty(),
                    "Actual native left-click cursor merge returns the remaining nine without duplication", checks);

            ItemStack exposed = current.items.getStackInSlot(0); exposed.shrink(2);
            require(amount(level, main) == 10, "Mutable menu mirror cannot mutate authoritative SavedData before its commit callback", checks);
            current.items.onContentsChanged();
            require(amount(level, main) == 8, "Menu mutation callback commits the mirror exactly once", checks);
        } finally {
            if (opened != null) opened.menu.setCarried(ItemStack.EMPTY);
            library.setCatalysts(player.getUUID(), original); player.getInventory().load(inventory);
            player.containerMenu = previousMenu; previousMenu.setCarried(previousCursor);
        }
    }
    public static void unformed(ServerLevel level, CloudPatternLibraryMachine main, FakePlayer player, List<String> checks) {
        if (main.isFormed()) throw new AssertionError("Read-only audit requires an actually broken production shell");
        var library = CloudPatternStore.get(level).library(main.libraryId()); CompoundTag original = library.catalysts();
        ListTag inventory = player.getInventory().save(new ListTag()); var previousMenu = player.containerMenu;
        ItemStack previousCursor = previousMenu.getCarried().copy(); Menu opened = null;
        try {
            authority(level, main, 10); opened = menu(main, player); fullPlayer(player);
            require(opened.items.getStackInSlot(0).getCount() == 10, "Unformed production menu retains visible catalyst contents", checks);
            require(opened.items.extractItem(0, 1, false, true).isEmpty()
                            && opened.items.insertItem(0, new ItemStack(Items.IRON_INGOT), false, true).getCount() == 1,
                    "Unformed production inventory rejects direct extraction and insertion", checks);
            opened.items.setStackInSlot(0, ItemStack.EMPTY); opened.items.getStackInSlot(0).shrink(1); opened.items.onContentsChanged();
            opened.menu.quickMoveStack(player, opened.central.index);
            opened.menu.clicked(opened.central.index, 0, ClickType.PICKUP, player);
            require(amount(level, main) == 10 && opened.menu.getCarried().isEmpty(),
                    "Unformed read-only mirror, native shift-click and native cursor cannot clear or duplicate central items", checks);
        } finally {
            if (opened != null) opened.menu.setCarried(ItemStack.EMPTY);
            library.setCatalysts(player.getUUID(), original); player.getInventory().load(inventory);
            player.containerMenu = previousMenu; previousMenu.setCarried(previousCursor);
        }
    }
    private static void authority(ServerLevel level, CloudPatternLibraryMachine main, int iron) {
        var library = CloudPatternStore.get(level).library(main.libraryId()); CompoundTag state = library.catalysts();
        var items = CloudLibraryService.itemContents(state);
        for (int i = 0; i < 9; i++) items.setStackInSlot(i, new ItemStack(Items.COBBLESTONE, 64));
        items.setStackInSlot(0, iron == 0 ? ItemStack.EMPTY : new ItemStack(Items.IRON_INGOT, iron));
        state.put("items", items.serializeNBT()); library.setCatalysts(library.owner(), state);
    }
    private static int amount(ServerLevel level, CloudPatternLibraryMachine main) {
        return CloudLibraryService.itemContents(CloudPatternStore.get(level).library(main.libraryId()).catalysts()).getStackInSlot(0).getCount();
    }
    private static void fullPlayer(FakePlayer player) {
        for (int i = 0; i < 36; i++) player.getInventory().setItem(i, new ItemStack(Items.COBBLESTONE, 64));
    }
    private static void require(boolean value, String message, List<String> checks) { if (!value) throw new AssertionError(message); checks.add(message); }
}
