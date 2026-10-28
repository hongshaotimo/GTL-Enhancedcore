package com.gtl.enhancedcore.common.cloud;

import com.gtl.enhancedcore.common.cloud.store.CloudPatternStore;
import com.lowdragmc.lowdraglib.misc.ItemStackTransfer;
import com.lowdragmc.lowdraglib.misc.FluidTransferList;
import com.lowdragmc.lowdraglib.side.item.IItemTransfer;
import com.lowdragmc.lowdraglib.side.fluid.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** Menu-scoped views. Every server read resolves the current authority; every write rechecks the viewer. */
final class CloudLibraryInventories {
    private CloudLibraryInventories() {}
    private static boolean allowed(CloudPatternLibraryMachine machine, Player viewer) {
        return viewer instanceof ServerPlayer player && machine.menuAuthorized(player) && !machine.cloud$importQuarantined();
    }
    private static boolean readable(CloudPatternLibraryMachine machine, Player viewer) {
        return viewer instanceof ServerPlayer player && machine.viewAuthorized(player) && !machine.cloud$importQuarantined();
    }
    private static final class Snapshot {
        final CompoundTag original;
        CompoundTag expected;
        long revision;
        final long tick;
        boolean valid = true;
        Snapshot(CompoundTag state, long revision, long tick) {
            original = state.copy(); expected = state.copy(); this.revision = revision; this.tick = tick;
        }
        void observe(CompoundTag state, long revision) {
            if (this.revision != revision || !expected.equals(state)) valid = false;
        }
    }
    private static long revision(CloudPatternLibraryMachine machine) {
        return CloudPatternStore.get(machine.serverLevel()).library(machine.libraryId()).revision();
    }
    static IItemTransfer items(CloudPatternLibraryMachine machine, Player viewer, boolean inbox) {
        if (!(viewer instanceof ServerPlayer)) return new ItemStackTransfer(inbox ? 1 : 9) {
            @Override public int getSlotLimit(int slot) { return inbox ? 1 : super.getSlotLimit(slot); }
        };
        return new IItemTransfer() {
            private Snapshot active;
            private ItemStackTransfer mirror;
            private CompoundTag observed;
            private long observedRevision;
            private boolean mirrorWritable;
            private final ItemStack[] exposed = new ItemStack[inbox ? 1 : 9];
            private final java.util.Map<ItemStack, Long> issued = new java.util.WeakHashMap<>();
            private long mirrorEpoch;
            private ItemStackTransfer current() {
                var result = inbox ? machine.importInventory() : CloudLibraryService.itemContents(CloudPatternStore.get(machine.serverLevel()).library(machine.libraryId()).catalysts());
                if (active != null) active.observe(result.serializeNBT(), revision(machine));
                return result;
            }
            private ItemStackTransfer mirror() {
                var authority = current(); CompoundTag state = authority.serializeNBT(); long rev = revision(machine);
                boolean writable = allowed(machine, viewer);
                if (mirror == null || mirrorWritable != writable || observedRevision != rev || !state.equals(observed)) {
                    mirrorEpoch = Math.incrementExact(mirrorEpoch);
                    mirror = new ItemStackTransfer(getSlots()) {
                        @Override public int getSlotLimit(int slot) { return inbox ? 1 : super.getSlotLimit(slot); }
                    };
                    mirror.deserializeNBT(state.copy());
                    observed = state.copy(); observedRevision = rev; mirrorWritable = writable; java.util.Arrays.fill(exposed, null);
                }
                return mirror;
            }
            private void persist(ItemStackTransfer inventory) {
                if (active != null && active.revision != revision(machine)) active.valid = false;
                if (inbox) {
                    machine.importInventory().deserializeNBT(inventory.serializeNBT().copy());
                    machine.scheduleInboxUpload((ServerPlayer)viewer);
                    machine.markDirty(); updateSnapshot(inventory); remember(inventory); return;
                }
                var library = CloudPatternStore.get(machine.serverLevel()).library(machine.libraryId());
                CompoundTag state = library.catalysts(); state.put("items", inventory.serializeNBT());
                library.setCatalysts(viewer.getUUID(), state); updateSnapshot(inventory); remember(inventory);
            }
            private void remember(ItemStackTransfer inventory) {
                mirror = inventory; observed = inventory.serializeNBT().copy(); observedRevision = revision(machine);
            }
            private void updateSnapshot(ItemStackTransfer inventory) {
                if (active != null) { active.expected = inventory.serializeNBT().copy(); active.revision = revision(machine); }
            }
            public int getSlots() { return inbox ? 1 : 9; }
            public ItemStack getStackInSlot(int slot) {
                if (!readable(machine, viewer)) return ItemStack.EMPTY;
                var inventory = mirror(); ItemStack stack = inventory.getStackInSlot(slot); exposed[slot] = stack;
                if (!stack.isEmpty()) issued.put(stack, mirrorEpoch);
                return stack; // Mutable menu mirror only; authoritative SavedData/inbox is never exposed.
            }
            public void onContentsChanged() {
                if (!allowed(machine, viewer) || !mirrorWritable) { mirror = null; java.util.Arrays.fill(exposed, null); return; }
                if (mirror == null) return;
                CompoundTag actual = current().serializeNBT();
                if (observedRevision != revision(machine) || !actual.equals(observed)) {
                    mirror = null; java.util.Arrays.fill(exposed, null);
                    throw new IllegalStateException("Central inventory changed during menu transfer");
                }
                if (!mirror.serializeNBT().equals(observed)) persist(mirror);
            }
            public void setStackInSlot(int slot, ItemStack stack) {
                if (!allowed(machine, viewer)) return;
                if (stack.getCount() > Math.min(inbox ? 1 : 64, stack.getMaxStackSize())) throw new IllegalArgumentException("Oversized central inventory stack");
                Long issuedEpoch = issued.get(stack); var inventory = mirror();
                if (issuedEpoch != null && issuedEpoch.longValue() != mirrorEpoch)
                    throw new IllegalStateException("Stale central inventory reference");
                // LDLib getMaxStackSize temporarily clears a slot, then restores the exact object
                // returned by getItem. Preserve that local identity so subsequent grow/shrink is flushed.
                inventory.setStackInSlot(slot, stack == exposed[slot] ? stack : stack.copy()); persist(inventory);
            }
            public ItemStack insertItem(int slot, ItemStack stack, boolean simulate, boolean notify) {
                if (!allowed(machine, viewer) || !isItemValid(slot, stack)) return stack;
                var inventory = mirror(); ItemStack remainder = inventory.insertItem(slot, stack, simulate, notify);
                if (!simulate && remainder.getCount() != stack.getCount()) persist(inventory);
                return remainder;
            }
            public ItemStack extractItem(int slot, int amount, boolean simulate, boolean notify) {
                if (!allowed(machine, viewer)) return ItemStack.EMPTY;
                var inventory = mirror(); ItemStack actual = inventory.extractItem(slot, amount, simulate, notify);
                if (!simulate && !actual.isEmpty()) persist(inventory);
                return actual;
            }
            public int getSlotLimit(int slot) { return inbox ? 1 : 64; }
            public boolean isItemValid(int slot, ItemStack stack) {
                if (!allowed(machine, viewer)) return false;
                if (!inbox) return true;
                try {
                    var details = appeng.api.crafting.PatternDetailsHelper.decodePattern(stack, machine.getLevel());
                    return details != null && !(details instanceof appeng.crafting.pattern.AECraftingPattern)
                            && !CloudPatternStore.get(machine.serverLevel()).library(machine.libraryId()).containsOriginal(stack);
                } catch (RuntimeException invalid) { return false; }
            }
            public Object createSnapshot() {
                if (!allowed(machine, viewer)) return null;
                return active = new Snapshot(current().serializeNBT(), revision(machine), machine.serverLevel().getGameTime());
            }
            public void restoreFromSnapshot(Object snapshot) {
                if (!allowed(machine, viewer) || snapshot == null || snapshot != active) return;
                var inventory = current(); Snapshot saved = active;
                if (!saved.valid || saved.tick != machine.serverLevel().getGameTime()) return;
                active = null; inventory.deserializeNBT(saved.original.copy()); persist(inventory); java.util.Arrays.fill(exposed, null);
            }
        };
    }
    static IFluidTransfer fluids(CloudPatternLibraryMachine machine, Player viewer) {
        if (!(viewer instanceof ServerPlayer)) return CloudLibraryService.fluidContents(new CompoundTag());
        return new IFluidTransfer() {
            private Snapshot active;
            private FluidTransferList current() {
                var result = CloudLibraryService.fluidContents(CloudPatternStore.get(machine.serverLevel()).library(machine.libraryId()).catalysts());
                if (active != null) active.observe(result.serializeNBT(), revision(machine));
                return result;
            }
            private void persist(FluidTransferList fluids) {
                if (active != null && active.revision != revision(machine)) active.valid = false;
                var library = CloudPatternStore.get(machine.serverLevel()).library(machine.libraryId());
                CompoundTag state = library.catalysts(); state.put("fluids", fluids.serializeNBT()); library.setCatalysts(viewer.getUUID(), state);
                if (active != null) { active.expected = fluids.serializeNBT().copy(); active.revision = revision(machine); }
            }
            public int getTanks() { return 9; }
            public FluidStack getFluidInTank(int tank) { return readable(machine, viewer) ? current().getFluidInTank(tank).copy() : FluidStack.empty(); }
            public void setFluidInTank(int tank, FluidStack stack) {
                if (!allowed(machine, viewer)) return;
                if (stack.getAmount() < 0 || stack.getAmount() > CloudLibraryService.FLUID_CAPACITY) throw new IllegalArgumentException("Invalid catalyst amount");
                var fluids = current(); fluids.setFluidInTank(tank, stack.copy()); persist(fluids);
            }
            public long getTankCapacity(int tank) { return CloudLibraryService.FLUID_CAPACITY; }
            public boolean isFluidValid(int tank, FluidStack stack) { return allowed(machine, viewer); }
            public long fill(int tank, FluidStack stack, boolean simulate, boolean notify) {
                if (!allowed(machine, viewer)) return 0;
                var fluids = current(); long filled = fluids.fill(tank, stack, simulate, notify);
                if (!simulate && filled > 0) persist(fluids); return filled;
            }
            public FluidStack drain(int tank, FluidStack stack, boolean simulate, boolean notify) {
                if (!allowed(machine, viewer)) return FluidStack.empty();
                var fluids = current(); FluidStack actual = fluids.drain(tank, stack, simulate, notify);
                if (!simulate && !actual.isEmpty()) persist(fluids); return actual;
            }
            public boolean supportsFill(int tank) { return allowed(machine, viewer); }
            public boolean supportsDrain(int tank) { return allowed(machine, viewer); }
            public Object createSnapshot() {
                if (!allowed(machine, viewer)) return null;
                return active = new Snapshot(current().serializeNBT(), revision(machine), machine.serverLevel().getGameTime());
            }
            public void restoreFromSnapshot(Object snapshot) {
                if (!allowed(machine, viewer) || snapshot == null || snapshot != active) return;
                var fluids = current(); Snapshot saved = active;
                if (!saved.valid || saved.tick != machine.serverLevel().getGameTime()) return;
                active = null; fluids.deserializeNBT(saved.original.copy()); persist(fluids);
            }
        };
    }
}
