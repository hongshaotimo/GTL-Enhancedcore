package com.gtl.enhancedcore.common.cloud;

import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gtl.enhancedcore.common.cloud.store.CloudPatternStore;
import com.gtl.enhancedcore.common.cloud.store.CloudLedger;
import com.gtl.enhancedcore.common.cloud.transfer.CloudImportedCatalysts;
import com.gtl.enhancedcore.common.cloud.transfer.CloudNetworkPatternImport;
import com.lowdragmc.lowdraglib.gui.modular.ModularUIContainer;
import com.lowdragmc.lowdraglib.misc.ItemStackTransfer;
import com.lowdragmc.lowdraglib.misc.FluidTransferList;
import com.lowdragmc.lowdraglib.side.item.IItemTransfer;
import com.lowdragmc.lowdraglib.side.fluid.*;
import java.util.*;
import java.util.function.BooleanSupplier;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** Per-menu, per-original physical inventories. Closing or changing the selection revokes old handles. */
public final class CloudPatternInventories {
    private CloudPatternInventories() {}
    private record Selection(MetaMachine machine, UUID library, UUID entry, UUID group, int page, long epoch) {}
    private static final Map<ModularUIContainer, Selection> OPEN = new WeakHashMap<>();
    private static long nextEpoch;
    private static UUID sharedGroup(CloudPatternStore.Entry entry) {
        var config = entry.configuration();
        return config.hasUUID("importCatalystGroup") && !entry.importedSharedCatalysts().isEmpty()
                ? config.getUUID("importCatalystGroup") : null;
    }
    private static int pages(CompoundTag state) {
        return Math.max(1, Math.max((state.getInt("itemSlots") + 8) / 9,
                Math.max((state.getInt("fluidTanks") + 8) / 9, state.getInt("circuitSlots"))));
    }
    private static boolean permitted(MetaMachine machine, ServerPlayer player, UUID library, UUID entry) {
        if (library == null || !(player.containerMenu instanceof ModularUIContainer menu)
                || menu.getModularUI().holder != machine || !CloudLibraryService.authorized(player, library, machine)) return false;
        if (CloudNetworkPatternImport.entryLocked(library, entry)) return false;
        var status = CloudPatternStore.get(player.serverLevel()).library(library).entry(entry).status();
        if (status != CloudLedger.Status.AVAILABLE && status != CloudLedger.Status.LEASED && status != CloudLedger.Status.ARCHIVED && !(machine instanceof CloudPatternLibraryMachine && status == CloudLedger.Status.EXPORTED)) return false;
        if (machine instanceof CloudPatternLibraryMachine main) return main.menuAuthorized(player) && !main.cloud$importQuarantined();
        return machine instanceof CloudPatternBufferMachine child && child.cloudMayEditPattern(player, entry);
    }
    public static boolean action(MetaMachine machine, ServerPlayer player, UUID library, String action, CompoundTag payload) {
        boolean mode = action.equals("pattern_catalysts_shared") || action.equals("pattern_catalysts_private");
        boolean paging = action.equals("pattern_catalysts_previous") || action.equals("pattern_catalysts_next");
        if (!action.equals("pattern_config") && !action.equals("close_pattern_config") && !mode && !paging) return false;
        if (!(player.containerMenu instanceof ModularUIContainer menu) || menu.getModularUI().holder != machine) return true;
        if (action.equals("close_pattern_config")) { OPEN.remove(menu); return true; }
        Selection previous = OPEN.get(menu);
        if (!mode && !paging) OPEN.remove(menu);
        try {
            UUID entryId = UUID.fromString(payload.getString("selected"));
            if (!permitted(machine, player, library, entryId)) return true;
            var entry = CloudPatternStore.get(player.serverLevel()).library(library).entry(entryId);
            if (entry.status() != CloudLedger.Status.AVAILABLE && entry.status() != CloudLedger.Status.LEASED && entry.status() != CloudLedger.Status.ARCHIVED && !(machine instanceof CloudPatternLibraryMachine && entry.status() == CloudLedger.Status.EXPORTED)) return true;
            UUID group = null; int page = 0;
            if (mode || paging) {
                if (previous == null || previous.machine != machine || !previous.library.equals(library)
                        || !previous.entry.equals(entryId) || previous.epoch != payload.getLong("catalystAccessEpoch")) return true;
                group = previous.group;
                if (group != null && !group.equals(sharedGroup(entry))) { OPEN.remove(menu); return true; }
                if (mode) {
                    group = action.equals("pattern_catalysts_shared") ? sharedGroup(entry) : null;
                    if (action.equals("pattern_catalysts_shared") && group == null) return true;
                } else {
                    if (group == null) return true;
                    int count = pages(entry.importedSharedCatalysts());
                    page = Math.max(0, Math.min(count - 1, previous.page + (action.equals("pattern_catalysts_next") ? 1 : -1)));
                    if (page == previous.page) return true;
                }
            }
            OPEN.put(menu, new Selection(machine, library, entryId, group, page, nextEpoch = Math.incrementExact(nextEpoch)));
        } catch (RuntimeException invalid) { OPEN.remove(menu); }
        return true;
    }
    public static void decorate(MetaMachine machine, ServerPlayer player, CompoundTag snapshot) {
        snapshot.putBoolean("patternCatalystsAllowed", false); snapshot.putString("catalystEntry", "");
        snapshot.putBoolean("patternImportedSharedAvailable", false); snapshot.putBoolean("patternCatalystsShared", false);
        snapshot.putLong("catalystAccessEpoch", 0); snapshot.putInt("patternCatalystsPage", 0); snapshot.putInt("patternCatalystsPages", 1);
        snapshot.putInt("patternSharedItemSlots", 0); snapshot.putInt("patternSharedFluidTanks", 0); snapshot.putInt("patternSharedCircuitSlots", 0);
        snapshot.putIntArray("patternCatalystItemLimits", new int[0]); snapshot.putLongArray("patternCatalystFluidCapacities", new long[0]);
        snapshot.putInt("patternCatalystCircuitLimit", 0);
        if (!(player.containerMenu instanceof ModularUIContainer menu)) return;
        Selection selected = OPEN.get(menu);
        if (selected == null || selected.machine != machine) return;
        try {
            if (!permitted(machine, player, selected.library, selected.entry)) { OPEN.remove(menu); return; }
            var entry = CloudPatternStore.get(player.serverLevel()).library(selected.library).entry(selected.entry);
            UUID group = sharedGroup(entry);
            if (selected.group != null && !selected.group.equals(group)) { OPEN.remove(menu); return; }
            snapshot.putString("catalystEntry", selected.entry.toString()); snapshot.putBoolean("patternCatalystsAllowed", true);
            snapshot.putBoolean("patternImportedSharedAvailable", group != null);
            snapshot.putBoolean("patternCatalystsShared", selected.group != null); snapshot.putLong("catalystAccessEpoch", selected.epoch);
            if (group != null) {
                var shared = entry.importedSharedCatalysts();
                snapshot.putInt("patternSharedItemSlots", shared.getInt("itemSlots"));
                snapshot.putInt("patternSharedFluidTanks", shared.getInt("fluidTanks"));
                snapshot.putInt("patternSharedCircuitSlots", shared.getInt("circuitSlots"));
                snapshot.putInt("patternCatalystsPage", selected.group == null ? 0 : selected.page);
                snapshot.putInt("patternCatalystsPages", selected.group == null ? 1 : pages(shared));
            }
            CompoundTag contents = selected.group == null ? entry.catalysts() : entry.importedSharedCatalysts();
            boolean shaped = contents.getInt("schema") == 1 && (contents.getString("kind").equals("SHARED") || contents.getString("kind").equals("PATTERN"));
            var itemContents = shaped ? CloudImportedCatalysts.itemContents(contents) : CloudLibraryService.itemContents(contents);
            var fluidContents = shaped ? CloudImportedCatalysts.fluidContents(contents) : CloudLibraryService.fluidContents(contents);
            int base = selected.group == null ? 0 : selected.page * 9;
            int[] limits = new int[9], originalLimits = contents.getIntArray("itemLimits"); long[] capacities = new long[9];
            for (int i = 0; i < 9; i++) {
                if (base + i < itemContents.getSlots()) limits[i] = shaped ? originalLimits[base + i] : itemContents.getSlotLimit(base + i);
                if (base + i < fluidContents.getTanks()) capacities[i] = fluidContents.getTankCapacity(base + i);
            }
            snapshot.putIntArray("patternCatalystItemLimits", limits); snapshot.putLongArray("patternCatalystFluidCapacities", capacities);
            int[] circuitLimits = contents.getIntArray("circuitLimits");
            snapshot.putInt("patternCatalystCircuitLimit", selected.group != null && selected.page < circuitLimits.length ? circuitLimits[selected.page] : 0);
            snapshot.put("detail", CloudLibraryService.entry(entry, player.serverLevel()));
        } catch (RuntimeException invalid) {
            OPEN.remove(menu); snapshot.putBoolean("patternCatalystsAllowed", false); snapshot.putString("catalystEntry", "");
        }
    }
    private static final class Access {
        final MetaMachine machine; final Player viewer; final UUID library, entry;
        final BooleanSupplier writable, readable;
        final long requestedEpoch; final boolean circuit;
        ModularUIContainer owningMenu;
        Selection owningSelection;
        Access(MetaMachine machine, Player viewer, UUID library, String id, BooleanSupplier writable, BooleanSupplier readable) {
            this.machine = machine; this.viewer = viewer; this.library = library; this.writable = writable; this.readable = readable;
            UUID parsed; long epoch = -1; boolean circuits = false;
            try {
                String[] parts = id.split(":", -1); parsed = UUID.fromString(parts[0]);
                if (parts.length > 1) epoch = Long.parseLong(parts[1]);
                if (parts.length > 2) { if (!parts[2].equals("circuit")) throw new IllegalArgumentException("Unknown catalyst part"); circuits = true; }
                if (parts.length > 3) throw new IllegalArgumentException("Invalid catalyst handle");
            } catch (RuntimeException invalid) { parsed = null; }
            entry = parsed; requestedEpoch = epoch; circuit = circuits;
        }
        boolean allowed(boolean write) {
            if (!(viewer instanceof ServerPlayer player) || entry == null || library == null
                    || !(write ? writable : readable).getAsBoolean() || !(player.containerMenu instanceof ModularUIContainer menu)) return false;
            Selection selected = OPEN.get(menu);
            if (owningMenu != null && owningMenu != menu) return false;
            boolean allowed = selected != null && selected.machine == machine && selected.library.equals(library) && selected.entry.equals(entry)
                    && (requestedEpoch < 0 || requestedEpoch == selected.epoch) && (owningSelection == null || owningSelection == selected)
                    && (!circuit || selected.group != null)
                    && permitted(machine, player, library, entry);
            if (allowed && selected.group != null && !selected.group.equals(sharedGroup(entry()))) allowed = false;
            if (allowed && owningMenu == null) { owningMenu = menu; owningSelection = selected; }
            return allowed;
        }
        CloudPatternStore.Entry entry() { return CloudPatternStore.get(((ServerPlayer)viewer).serverLevel()).library(library).entry(entry); }
        CompoundTag state() {
            return owningSelection != null && owningSelection.group != null
                    ? CloudPatternStore.get(((ServerPlayer)viewer).serverLevel()).library(library).getImportedCatalysts(viewer.getUUID(), owningSelection.group)
                    : entry().catalysts();
        }
        boolean shaped(CompoundTag state) { return state.getInt("schema") == 1 && (state.getString("kind").equals("SHARED") || state.getString("kind").equals("PATTERN")); }
        ItemStackTransfer itemContents() {
            var state = state();
            return circuit ? CloudImportedCatalysts.circuitContents(state)
                    : shaped(state) ? CloudImportedCatalysts.itemContents(state) : CloudLibraryService.itemContents(state);
        }
        FluidTransferList fluidContents() { var state = state(); return shaped(state) ? CloudImportedCatalysts.fluidContents(state) : CloudLibraryService.fluidContents(state); }
        int slot(int visible) { return owningSelection != null && owningSelection.group != null ? owningSelection.page * (circuit ? 1 : 9) + visible : visible; }
        int limit(int slot) {
            var state = state(); int[] limits = state.getIntArray(circuit ? "circuitLimits" : "itemLimits");
            return shaped(state) ? (slot >= 0 && slot < limits.length ? limits[slot] : 0) : 64;
        }
        long revision() { return entry().catalystRevision(); }
        void persist(String part, CompoundTag value) {
            CompoundTag state = state(); state.put(part, value.copy());
            if (owningSelection.group != null) CloudPatternStore.get(((ServerPlayer)viewer).serverLevel()).library(library)
                    .setImportedCatalysts(viewer.getUUID(), owningSelection.group, state);
            else entry().setCatalysts(viewer.getUUID(), state);
        }
    }
    private static final class Snapshot {
        final CompoundTag original; CompoundTag expected; long revision; final long tick; boolean valid = true;
        Snapshot(CompoundTag state, long revision, Player viewer) {
            original = state.copy(); expected = state.copy(); this.revision = revision; tick = viewer.level().getGameTime();
        }
        void observe(CompoundTag state, long revision) { if (this.revision != revision || !expected.equals(state)) valid = false; }
    }
    public static IItemTransfer items(MetaMachine machine, Player viewer, UUID library, String id, BooleanSupplier writable, BooleanSupplier readable) {
        // Fixed menu slots are synchronized before the client receives selection metadata.
        int visibleSlots = id.endsWith(":circuit") ? 1 : 9;
        if (!(viewer instanceof ServerPlayer)) return new ItemStackTransfer(visibleSlots);
        Access access = new Access(machine, viewer, library, id, writable, readable);
        return new IItemTransfer() {
            ItemStackTransfer mirror; CompoundTag observed; long observedRevision; Snapshot active;
            ItemStack[] exposed = new ItemStack[0];
            final java.util.Map<ItemStack, Long> issued = new java.util.WeakHashMap<>();
            long mirrorEpoch;
            boolean valid(int slot, ItemStackTransfer inventory) { return slot >= 0 && slot < inventory.getSlots(); }
            ItemStackTransfer current() {
                var inventory = access.itemContents();
                if (active != null) active.observe(inventory.serializeNBT(), access.revision());
                return inventory;
            }
            ItemStackTransfer mirror() {
                var authority = current(); CompoundTag state = authority.serializeNBT(); long revision = access.revision();
                if (mirror == null || observedRevision != revision || !state.equals(observed)) {
                    mirrorEpoch = Math.incrementExact(mirrorEpoch);
                    mirror = authority; observed = state.copy(); observedRevision = revision;
                    exposed = new ItemStack[mirror.getSlots()];
                }
                return mirror;
            }
            void persist(ItemStackTransfer inventory) {
                for (int i = 0; i < inventory.getSlots(); i++) {
                    ItemStack value = inventory.getStackInSlot(i);
                    if (!value.isEmpty() && (value.getCount() < 0 || value.getCount() > Math.min(access.limit(i), value.getMaxStackSize())))
                        throw new IllegalArgumentException("Oversized catalyst stack");
                }
                access.persist(access.circuit ? "sharedCircuit" : "items", inventory.serializeNBT());
                mirror = inventory; observed = inventory.serializeNBT().copy(); observedRevision = access.revision();
                if (active != null) { active.expected = observed.copy(); active.revision = observedRevision; }
            }
            public int getSlots() { return visibleSlots; }
            public ItemStack getStackInSlot(int slot) {
                if (!access.allowed(false) || slot < 0 || slot >= visibleSlots) return ItemStack.EMPTY;
                int actual = access.slot(slot); var inventory = mirror();
                if (!valid(actual, inventory)) return ItemStack.EMPTY;
                var stack = inventory.getStackInSlot(actual);
                if (!stack.isEmpty()) issued.put(stack, mirrorEpoch);
                return exposed[actual] = stack;
            }
            public void setStackInSlot(int slot, ItemStack stack) {
                if (!access.allowed(true) || slot < 0 || slot >= visibleSlots) return;
                int actual = access.slot(slot); Long issuedEpoch = issued.get(stack); var inventory = mirror();
                if (!valid(actual, inventory)) return;
                if (!stack.isEmpty() && (stack.getCount() < 0 || stack.getCount() > Math.min(access.limit(actual), stack.getMaxStackSize())))
                    throw new IllegalArgumentException("Oversized catalyst stack");
                if (issuedEpoch != null && issuedEpoch.longValue() != mirrorEpoch)
                    throw new IllegalStateException("Stale pattern catalyst reference");
                inventory.setStackInSlot(actual, stack == exposed[actual] ? stack : stack.copy()); persist(inventory);
            }
            public void onContentsChanged() {
                if (!access.allowed(true)) { mirror = null; return; }
                if (mirror == null) return;
                if (access.revision() != observedRevision || !current().serializeNBT().equals(observed)) {
                    mirror = null; throw new IllegalStateException("Pattern catalysts changed during menu transfer");
                }
                if (!mirror.serializeNBT().equals(observed)) persist(mirror);
            }
            public ItemStack insertItem(int slot, ItemStack stack, boolean simulate, boolean notify) {
                if (!access.allowed(true) || stack.isEmpty() || slot < 0 || slot >= visibleSlots) return stack;
                int actual = access.slot(slot); var inventory = mirror();
                if (!valid(actual, inventory)) return stack;
                int room = Math.max(0, Math.min(access.limit(actual), stack.getMaxStackSize()) - inventory.getStackInSlot(actual).getCount());
                int offered = Math.min(room, stack.getCount()); if (offered <= 0) return stack;
                var rest = inventory.insertItem(actual, stack.copyWithCount(offered), simulate, notify);
                int inserted = offered - rest.getCount();
                if (!simulate && inserted > 0) persist(inventory);
                return inserted == 0 ? stack : stack.copyWithCount(stack.getCount() - inserted);
            }
            public ItemStack extractItem(int slot, int amount, boolean simulate, boolean notify) {
                if (!access.allowed(true) || slot < 0 || slot >= visibleSlots) return ItemStack.EMPTY;
                int actual = access.slot(slot); var inventory = mirror();
                if (!valid(actual, inventory)) return ItemStack.EMPTY;
                var value = inventory.extractItem(actual, amount, simulate, notify);
                if (!simulate && !value.isEmpty()) persist(inventory); return value;
            }
            public int getSlotLimit(int slot) {
                if (!access.allowed(false) || slot < 0 || slot >= visibleSlots) return 0;
                return access.limit(access.slot(slot));
            }
            public boolean isItemValid(int slot, ItemStack stack) {
                if (!access.allowed(true) || slot < 0 || slot >= visibleSlots) return false;
                int actual = access.slot(slot); var inventory = current();
                return valid(actual, inventory) && access.limit(actual) > 0 && inventory.isItemValid(actual, stack);
            }
            public Object createSnapshot() { return access.allowed(true) ? active = new Snapshot(current().serializeNBT(), access.revision(), viewer) : null; }
            public void restoreFromSnapshot(Object snapshot) {
                if (!access.allowed(true) || snapshot == null || snapshot != active) return;
                var inventory = current(); Snapshot saved = active;
                if (!saved.valid || saved.tick != viewer.level().getGameTime()) return;
                active = null; inventory.deserializeNBT(saved.original.copy()); persist(inventory);
                mirrorEpoch = Math.incrementExact(mirrorEpoch); Arrays.fill(exposed, null);
            }
        };
    }
    public static IFluidTransfer fluids(MetaMachine machine, Player viewer, UUID library, String id, BooleanSupplier writable, BooleanSupplier readable) {
        if (!(viewer instanceof ServerPlayer)) return CloudLibraryService.fluidContents(new CompoundTag());
        Access access = new Access(machine, viewer, library, id, writable, readable);
        return new IFluidTransfer() {
            Snapshot active;
            boolean valid(int tank, FluidTransferList inventory) { return tank >= 0 && tank < inventory.getTanks(); }
            FluidTransferList current() {
                var inventory = access.fluidContents();
                if (active != null) active.observe(inventory.serializeNBT(), access.revision()); return inventory;
            }
            void persist(FluidTransferList inventory) {
                access.persist("fluids", inventory.serializeNBT());
                if (active != null) { active.expected = inventory.serializeNBT().copy(); active.revision = access.revision(); }
            }
            public int getTanks() { return 9; }
            public FluidStack getFluidInTank(int tank) {
                if (!access.allowed(false) || tank < 0 || tank >= 9) return FluidStack.empty();
                int actual = access.slot(tank); var inventory = current();
                return valid(actual, inventory) ? inventory.getFluidInTank(actual).copy() : FluidStack.empty();
            }
            public void setFluidInTank(int tank, FluidStack stack) {
                if (!access.allowed(true) || tank < 0 || tank >= 9) return;
                int actual = access.slot(tank); var inventory = current(); if (!valid(actual, inventory)) return;
                if (stack.getAmount() < 0 || stack.getAmount() > inventory.getTankCapacity(actual)) throw new IllegalArgumentException("Invalid catalyst fluid amount");
                inventory.setFluidInTank(actual, stack.copy()); persist(inventory);
            }
            public long getTankCapacity(int tank) {
                if (!access.allowed(false) || tank < 0 || tank >= 9) return 0;
                int actual = access.slot(tank); var inventory = current(); return valid(actual, inventory) ? inventory.getTankCapacity(actual) : 0;
            }
            public boolean isFluidValid(int tank, FluidStack stack) {
                if (!access.allowed(true) || tank < 0 || tank >= 9) return false;
                int actual = access.slot(tank); var inventory = current(); return valid(actual, inventory) && inventory.isFluidValid(actual, stack);
            }
            public boolean supportsFill(int tank) {
                if (!access.allowed(true) || tank < 0 || tank >= 9) return false;
                int actual = access.slot(tank); var inventory = current(); return valid(actual, inventory) && inventory.supportsFill(actual);
            }
            public boolean supportsDrain(int tank) {
                if (!access.allowed(true) || tank < 0 || tank >= 9) return false;
                int actual = access.slot(tank); var inventory = current(); return valid(actual, inventory) && inventory.supportsDrain(actual);
            }
            public long fill(int tank, FluidStack stack, boolean simulate, boolean notify) {
                if (!access.allowed(true) || tank < 0 || tank >= 9) return 0;
                int actual = access.slot(tank); var inventory = current(); if (!valid(actual, inventory)) return 0;
                long amount = inventory.fill(actual, stack, simulate, notify);
                if (!simulate && amount > 0) persist(inventory); return amount;
            }
            public FluidStack drain(int tank, FluidStack stack, boolean simulate, boolean notify) {
                if (!access.allowed(true) || tank < 0 || tank >= 9) return FluidStack.empty();
                int actual = access.slot(tank); var inventory = current(); if (!valid(actual, inventory)) return FluidStack.empty();
                var value = inventory.drain(actual, stack, simulate, notify);
                if (!simulate && !value.isEmpty()) persist(inventory); return value;
            }
            public Object createSnapshot() { return access.allowed(true) ? active = new Snapshot(current().serializeNBT(), access.revision(), viewer) : null; }
            public void restoreFromSnapshot(Object snapshot) {
                if (!access.allowed(true) || snapshot == null || snapshot != active) return;
                var inventory = current(); Snapshot saved = active;
                if (!saved.valid || saved.tick != viewer.level().getGameTime()) return;
                active = null; inventory.deserializeNBT(saved.original.copy()); persist(inventory);
            }
        };
    }
}
