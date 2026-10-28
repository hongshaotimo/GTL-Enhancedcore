package com.gtl.enhancedcore.common.cloud.ui;

import com.lowdragmc.lowdraglib.side.fluid.FluidStack;
import com.lowdragmc.lowdraglib.side.fluid.IFluidTransfer;
import com.lowdragmc.lowdraglib.side.item.IItemTransfer;
import java.util.function.Supplier;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** Fixed menu slots route only to a server-confirmed entry, mode, page and selection epoch. */
final class CloudPatternCatalystAccess {
    private final CloudPatternUiHost host;
    private final Player player;
    private final Supplier<String> selection;
    private final Supplier<CompoundTag> metadata;
    private String bound = "";
    private IItemTransfer itemDelegate = IItemTransfer.EMPTY, circuitDelegate = IItemTransfer.EMPTY;
    private IFluidTransfer fluidDelegate = IFluidTransfer.EMPTY;
    private record Saved(String selection, Object transfer, Object value) {}

    CloudPatternCatalystAccess(CloudPatternUiHost host, Player player, Supplier<String> selection, Supplier<CompoundTag> metadata) {
        this.host = host; this.player = player; this.selection = selection; this.metadata = metadata;
        if (!(player instanceof net.minecraft.server.level.ServerPlayer)) {
            itemDelegate = new com.lowdragmc.lowdraglib.misc.ItemStackTransfer(9);
            circuitDelegate = new com.lowdragmc.lowdraglib.misc.ItemStackTransfer(1);
            fluidDelegate = com.gtl.enhancedcore.common.cloud.CloudLibraryService.fluidContents(new CompoundTag());
        }
    }
    private void bind() {
        // Native slot synchronization may arrive before panel metadata; client slot identities stay fixed.
        if (!(player instanceof net.minecraft.server.level.ServerPlayer)) return;
        String next = selection.get();
        if (next.equals(bound)) return;
        bound = next;
        itemDelegate = next.isEmpty() ? IItemTransfer.EMPTY : host.cloudPatternItemCatalysts(player, next);
        circuitDelegate = next.isEmpty() ? IItemTransfer.EMPTY : host.cloudPatternItemCatalysts(player, next + ":circuit");
        fluidDelegate = next.isEmpty() ? IFluidTransfer.EMPTY : host.cloudPatternFluidCatalysts(player, next);
        if (itemDelegate == null) itemDelegate = IItemTransfer.EMPTY;
        if (circuitDelegate == null) circuitDelegate = IItemTransfer.EMPTY;
        if (fluidDelegate == null) fluidDelegate = IFluidTransfer.EMPTY;
    }
    private IItemTransfer item(boolean circuit) { bind(); return circuit ? circuitDelegate : itemDelegate; }
    private IFluidTransfer fluid() { bind(); return fluidDelegate; }
    IItemTransfer items() { return items; }
    IItemTransfer circuits() { return circuits; }
    IFluidTransfer fluids() { return fluids; }
    private final IItemTransfer items = itemView(false);
    private final IItemTransfer circuits = itemView(true);
    private IItemTransfer itemView(boolean circuit) {
        return new IItemTransfer() {
            private IItemTransfer at(int slot) { var value = item(circuit); return slot >= 0 && slot < value.getSlots() ? value : IItemTransfer.EMPTY; }
            public int getSlots() { return circuit ? 1 : 9; }
            public ItemStack getStackInSlot(int slot) { var d = at(slot); return d == IItemTransfer.EMPTY ? ItemStack.EMPTY : d.getStackInSlot(slot); }
            public void setStackInSlot(int slot, ItemStack stack) { var d = at(slot); if (d != IItemTransfer.EMPTY) d.setStackInSlot(slot, stack); }
            public ItemStack insertItem(int slot, ItemStack stack, boolean simulate, boolean notify) { var d = at(slot); return d == IItemTransfer.EMPTY ? stack : d.insertItem(slot, stack, simulate, notify); }
            public ItemStack extractItem(int slot, int amount, boolean simulate, boolean notify) { var d = at(slot); return d == IItemTransfer.EMPTY ? ItemStack.EMPTY : d.extractItem(slot, amount, simulate, notify); }
            public int getSlotLimit(int slot) {
                if (!(player instanceof net.minecraft.server.level.ServerPlayer)) {
                    if (circuit && metadata.get().contains("patternCatalystCircuitLimit")) return metadata.get().getInt("patternCatalystCircuitLimit");
                    int[] limits = metadata.get().getIntArray("patternCatalystItemLimits");
                    if (!circuit && slot >= 0 && slot < limits.length) return limits[slot];
                }
                var d = at(slot); return d == IItemTransfer.EMPTY ? 0 : d.getSlotLimit(slot);
            }
            public boolean isItemValid(int slot, ItemStack stack) { var d = at(slot); return d != IItemTransfer.EMPTY && d.isItemValid(slot, stack); }
            public void onContentsChanged() { item(circuit).onContentsChanged(); }
            public Object createSnapshot() { var d = item(circuit); return new Saved(bound, d, d.createSnapshot()); }
            public void restoreFromSnapshot(Object value) { var d = item(circuit); if (value instanceof Saved saved && bound.equals(saved.selection()) && d == saved.transfer()) d.restoreFromSnapshot(saved.value()); }
        };
    }
    private final IFluidTransfer fluids = new IFluidTransfer() {
        private IFluidTransfer at(int tank) { var value = fluid(); return tank >= 0 && tank < value.getTanks() ? value : IFluidTransfer.EMPTY; }
        public int getTanks() { return 9; }
        public FluidStack getFluidInTank(int tank) { var d = at(tank); return d == IFluidTransfer.EMPTY ? FluidStack.empty() : d.getFluidInTank(tank); }
        public void setFluidInTank(int tank, FluidStack stack) { var d = at(tank); if (d != IFluidTransfer.EMPTY) d.setFluidInTank(tank, stack); }
        public long getTankCapacity(int tank) {
            if (!(player instanceof net.minecraft.server.level.ServerPlayer)) {
                long[] capacities = metadata.get().getLongArray("patternCatalystFluidCapacities");
                if (tank >= 0 && tank < capacities.length) return capacities[tank];
            }
            var d = at(tank); return d == IFluidTransfer.EMPTY ? 0 : d.getTankCapacity(tank);
        }
        public boolean isFluidValid(int tank, FluidStack stack) { var d = at(tank); return d != IFluidTransfer.EMPTY && d.isFluidValid(tank, stack); }
        public long fill(int tank, FluidStack stack, boolean simulate, boolean notify) { var d = at(tank); return d == IFluidTransfer.EMPTY ? 0 : d.fill(tank, stack, simulate, notify); }
        public FluidStack drain(int tank, FluidStack stack, boolean simulate, boolean notify) { var d = at(tank); return d == IFluidTransfer.EMPTY ? FluidStack.empty() : d.drain(tank, stack, simulate, notify); }
        public boolean supportsFill(int tank) { var d = at(tank); return d != IFluidTransfer.EMPTY && d.supportsFill(tank); }
        public boolean supportsDrain(int tank) { var d = at(tank); return d != IFluidTransfer.EMPTY && d.supportsDrain(tank); }
        public void onContentsChanged() { fluid().onContentsChanged(); }
        public Object createSnapshot() { var d = fluid(); return new Saved(bound, d, d.createSnapshot()); }
        public void restoreFromSnapshot(Object value) { var d = fluid(); if (value instanceof Saved saved && bound.equals(saved.selection()) && d == saved.transfer()) d.restoreFromSnapshot(saved.value()); }
    };
}
