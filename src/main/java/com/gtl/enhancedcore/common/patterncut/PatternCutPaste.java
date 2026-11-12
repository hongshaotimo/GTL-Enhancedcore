package com.gtl.enhancedcore.common.patterncut;

import com.lowdragmc.lowdraglib.misc.FluidStorage;
import com.lowdragmc.lowdraglib.misc.FluidTransferList;
import com.lowdragmc.lowdraglib.misc.ItemStackTransfer;
import com.lowdragmc.lowdraglib.side.fluid.FluidStack;
import com.lowdragmc.lowdraglib.side.fluid.IFluidTransfer;
import com.gtl.enhancedcore.mixin.gtlcore.PatternCutBufferAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import org.gtlcore.gtlcore.common.machine.multiblock.part.ae.MEPatternBufferPartMachine;

import java.util.ArrayList;
import java.util.List;

/** Plans a complete additive paste without changing the machine or its existing storage. */
public final class PatternCutPaste {
    private PatternCutPaste() {}

    public static final class Plan {
        private final String reason;
        private final Runnable commit;
        private boolean applied;

        private Plan(String reason, Runnable commit) { this.reason = reason; this.commit = commit; }
        public String reason() { return reason; }
        public boolean ready() { return reason == null; }

        /** Apply once, on the server thread immediately after claiming the source file. */
        public void apply() {
            if (!ready() || applied) throw new IllegalStateException("Paste plan is unavailable");
            applied = true;
            commit.run();
        }
    }

    private record Slot(int index, ItemStack pattern, byte cache, ItemStackTransfer items,
                        FluidTransferList fluids) {}

    public static Plan prepare(MEPatternBufferPartMachine buffer, CompoundTag payload) {
        return prepare(buffer, List.of(payload));
    }

    public static Plan prepare(MEPatternBufferPartMachine buffer, List<CompoundTag> payloads) {
        var access = (PatternCutBufferAccess) buffer;
        List<Slot> slots = new ArrayList<>();
        int next = 0;
        boolean hadPatterns = false;
        for (int i = 0; i < buffer.getPatternInventory().getSlots(); i++)
            hadPatterns |= !buffer.getPatternInventory().getStackInSlot(i).isEmpty();
        for (CompoundTag payload : payloads) {
            ListTag incoming = payload.getList("patterns", Tag.TAG_COMPOUND);
            for (int i = 0; i < incoming.size(); i++) {
                while (next < buffer.getPatternInventory().getSlots()
                        && (!buffer.getPatternInventory().getStackInSlot(next).isEmpty()
                        || hasItems(access.enhancedcore$cutItems()[next])
                        || hasFluids(access.enhancedcore$cutFluids()[next]))) next++;
                if (next >= buffer.getPatternInventory().getSlots()) return failed("no_capacity");
                CompoundTag entry = incoming.getCompound(i);
                ItemStack pattern = ItemStack.of(entry.getCompound("pattern"));
                if (pattern.isEmpty()) return failed("paste_failed");
                ItemStackTransfer items = itemCopy(access.enhancedcore$cutItems()[next]);
                FluidTransferList fluids = fluidCopy(access.enhancedcore$cutFluids()[next]);
                if (!mergeItems(items, entry.getCompound("catalystItems"))
                        || !mergeFluids(fluids, entry.getCompound("catalystFluids").getList("tanks", Tag.TAG_COMPOUND)))
                    return failed("merge_capacity");
                slots.add(new Slot(next++, pattern, entry.getByte("cacheCount"), items, fluids));
            }
        }
        var sharedItems = itemCopy(buffer.getSharedCatalystInventory().storage);
        var circuits = itemCopy(buffer.getSharedCircuitInventory().storage);
        var tanks = fluidCopy(buffer.getSharedCatalystTank());
        for (CompoundTag payload : payloads) {
            if (!mergeItems(sharedItems, payload.getCompound("sharedCatalystInventory"))
                    || !mergeItems(circuits, payload.getCompound("sharedCircuitInventory"))
                    || !mergeFluids(tanks, payload.getList("sharedCatalystTank", Tag.TAG_COMPOUND)))
                return failed("merge_capacity");
        }
        boolean inheritName = !hadPatterns && buffer.getCustomName().isBlank();
        String sourceName = payloads.isEmpty() ? "" : payloads.getFirst().getString("name");
        boolean keepByProduct = access.enhancedcore$keepByProduct()
                || payloads.stream().anyMatch(payload -> payload.getBoolean("keepByProduct"));
        return new Plan(null, () -> {
            for (Slot slot : slots) {
                buffer.getPatternInventory().setStackInSlot(slot.index(), slot.pattern().copy());
                copyItems(slot.items(), access.enhancedcore$cutItems()[slot.index()]);
                copyFluids(slot.fluids(), access.enhancedcore$cutFluids()[slot.index()]);
                access.enhancedcore$cutCacheCounts()[slot.index()] = slot.cache() > 0 ? slot.cache() : 1;
                access.enhancedcore$cutItems()[slot.index()].onContentsChanged();
                access.enhancedcore$cutFluids()[slot.index()].onContentsChanged();
            }
            copyItems(sharedItems, buffer.getSharedCatalystInventory().storage);
            copyItems(circuits, buffer.getSharedCircuitInventory().storage);
            copyFluids(tanks, buffer.getSharedCatalystTank());
            buffer.getSharedCatalystInventory().onContentsChanged();
            buffer.getSharedCircuitInventory().onContentsChanged();
            buffer.getSharedCatalystTank().onContentsChanged();
            if (inheritName) buffer.setCustomName(sourceName);
            access.enhancedcore$keepByProduct(keepByProduct);
            access.enhancedcore$refreshCutByProduct();
            buffer.markDirty();
        });
    }

    private static Plan failed(String reason) {
        return new Plan("gtl_enhancedcore.pattern_cut." + reason, null);
    }

    private static boolean hasItems(ItemStackTransfer items) {
        for (int i = 0; i < items.getSlots(); i++) if (!items.getStackInSlot(i).isEmpty()) return true;
        return false;
    }

    private static boolean hasFluids(IFluidTransfer fluids) {
        for (int i = 0; i < fluids.getTanks(); i++) if (!fluids.getFluidInTank(i).isEmpty()) return true;
        return false;
    }

    private static ItemStackTransfer itemCopy(ItemStackTransfer original) {
        ItemStackTransfer copy = new ItemStackTransfer(original.getSlots()) {
            @Override public int getSlotLimit(int slot) { return original.getSlotLimit(slot); }
            @Override public boolean isItemValid(int slot, ItemStack stack) { return original.isItemValid(slot, stack); }
        };
        copy.deserializeNBT(original.serializeNBT());
        return copy;
    }

    private static FluidTransferList fluidCopy(IFluidTransfer original) {
        List<IFluidTransfer> copies = new ArrayList<>();
        for (int i = 0; i < original.getTanks(); i++) {
            int tank = i;
            FluidStorage storage = new FluidStorage(original.getTankCapacity(i), fluid -> original.isFluidValid(tank, fluid));
            storage.setFluid(original.getFluidInTank(i).copy());
            copies.add(storage);
        }
        return new FluidTransferList(copies);
    }

    private static boolean mergeItems(ItemStackTransfer target, CompoundTag tag) {
        ListTag items = tag.getList("Items", Tag.TAG_COMPOUND);
        for (int i = 0; i < items.size(); i++) {
            ItemStack remainder = ItemStack.of(items.getCompound(i));
            // Fill compatible stacks before using empty slots.
            for (int pass = 0; pass < 2 && !remainder.isEmpty(); pass++)
                for (int slot = 0; slot < target.getSlots() && !remainder.isEmpty(); slot++)
                    if (target.getStackInSlot(slot).isEmpty() == (pass == 1))
                        remainder = target.insertItem(slot, remainder, false, false);
            if (!remainder.isEmpty()) return false;
        }
        return true;
    }

    private static boolean mergeFluids(FluidTransferList target, ListTag tanks) {
        for (int i = 0; i < tanks.size(); i++) {
            FluidStack remainder = FluidStack.loadFromTag(tanks.getCompound(i));
            for (int pass = 0; pass < 2 && !remainder.isEmpty(); pass++)
                for (int tank = 0; tank < target.getTanks() && !remainder.isEmpty(); tank++)
                    if (target.getFluidInTank(tank).isEmpty() == (pass == 1)) {
                        long inserted = target.fill(tank, remainder, false, false);
                        remainder = remainder.copy(remainder.getAmount() - inserted);
                    }
            if (!remainder.isEmpty()) return false;
        }
        return true;
    }

    private static void copyItems(ItemStackTransfer source, ItemStackTransfer target) {
        for (int i = 0; i < source.getSlots(); i++) target.setStackInSlot(i, source.getStackInSlot(i).copy());
    }

    private static void copyFluids(IFluidTransfer source, IFluidTransfer target) {
        for (int i = 0; i < source.getTanks(); i++) target.setFluidInTank(i, source.getFluidInTank(i).copy());
    }
}
