package com.gtl.enhancedcore.common.cloud.transfer;

import appeng.api.stacks.AEKey;
import com.gtl.enhancedcore.mixin.gtlcore.CloudPatternBufferAccessor;
import com.lowdragmc.lowdraglib.misc.FluidStorage;
import com.lowdragmc.lowdraglib.misc.FluidTransferList;
import com.lowdragmc.lowdraglib.misc.ItemStackTransfer;
import com.lowdragmc.lowdraglib.side.fluid.FluidStack;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import org.gtlcore.gtlcore.common.machine.multiblock.part.ae.MEPatternBufferPartMachine;

/** Server-only physical catalyst escrow. Inner-slot catalyst maps are derived caches, never another inventory. */
public final class CloudImportedCatalysts {
    private CloudImportedCatalysts() {}
    private record Inventory(String kind, ItemStackTransfer items, FluidTransferList fluids, ItemStackTransfer circuits) {}

    /** A partial debit owns escrow(); a partial restore still owns remaining(). Neither is safe to discard. */
    public static final class TransferFailure extends IllegalStateException {
        private final CompoundTag escrow;
        private TransferFailure(String message, CompoundTag escrow, Throwable cause) {
            super(message, cause); this.escrow = escrow.copy();
        }
        public CompoundTag escrow() { return escrow.copy(); }
        public CompoundTag remaining() { return escrow(); }
    }
    public static CompoundTag captureShared(MEPatternBufferPartMachine source) { return capture(shared(source)); }
    public static CompoundTag capturePattern(MEPatternBufferPartMachine source, int slot) { return capture(pattern(source, slot)); }

    /** Caller must durably WAL expected first. Success owns actual debited escrow, matching expected exactly. */
    public static CompoundTag drainShared(MEPatternBufferPartMachine source, CompoundTag expected) {
        return drain(source, shared(source), expected);
    }
    public static CompoundTag drainPattern(MEPatternBufferPartMachine source, int slot, CompoundTag expected) {
        return drain(source, pattern(source, slot), expected);
    }
    public static boolean canRestoreShared(MEPatternBufferPartMachine source, CompoundTag state) {
        try { return canRestore(shared(source), state); } catch (RuntimeException rejected) { return false; }
    }
    public static boolean canRestorePattern(MEPatternBufferPartMachine source, int slot, CompoundTag state) {
        try { return canRestore(pattern(source, slot), state); } catch (RuntimeException rejected) { return false; }
    }
    /** Only restores into completely empty original inventories after every slot passes a simulation. */
    public static void restoreShared(MEPatternBufferPartMachine source, CompoundTag state) { restore(source, shared(source), state); }
    public static void restorePattern(MEPatternBufferPartMachine source, int slot, CompoundTag state) { restore(source, pattern(source, slot), state); }

    /** Detached decoders preserve every captured slot/tank; only per-pattern state happens to be 9+9. */
    public static ItemStackTransfer itemContents(CompoundTag state) { return decode(state).items; }
    public static FluidTransferList fluidContents(CompoundTag state) { return decode(state).fluids; }
    public static ItemStackTransfer circuitContents(CompoundTag state) { return decode(state).circuits; }
    public static Map<AEKey, Long> snapshot(CompoundTag state) {
        if (state.isEmpty()) return Map.of();
        Inventory saved = decode(state);
        Map<AEKey, Long> result = new LinkedHashMap<>(CloudCatalystSource.snapshot(saved.items, saved.fluids));
        CloudCatalystSource.snapshot(saved.circuits, new FluidTransferList(new FluidStorage[0])).forEach((key, amount) ->
                result.merge(key, amount, CloudImportedCatalysts::sum));
        return Map.copyOf(result);
    }
    public static boolean hasContents(CompoundTag state) { return !snapshot(state).isEmpty(); }
    private static long sum(long a, long b) { return a > Long.MAX_VALUE - b ? Long.MAX_VALUE : a + b; }
    private static void server(MEPatternBufferPartMachine source) {
        if (!(source.getLevel() instanceof ServerLevel level) || !level.getServer().isSameThread())
            throw new IllegalStateException("Catalyst escrow requires server thread");
    }
    private static Inventory shared(MEPatternBufferPartMachine source) {
        server(source);
        // capability IO is NONE; operate on actual owned storages rather than an external capability wrapper.
        return new Inventory("SHARED", source.getSharedCatalystInventory().storage,
                new FluidTransferList(source.getSharedCatalystTank().getStorages()), source.getSharedCircuitInventory().storage);
    }
    private static Inventory pattern(MEPatternBufferPartMachine source, int slot) {
        server(source);
        if (!(source instanceof CloudPatternBufferAccessor access)) throw new IllegalStateException("No physical catalyst accessor");
        ItemStackTransfer[] items = access.cloud$catalystItems(); FluidTransferList[] fluids = access.cloud$catalystFluids();
        if (slot < 0 || slot >= items.length || slot >= fluids.length) throw new IllegalArgumentException("Invalid catalyst slot");
        return new Inventory("PATTERN", items[slot], fluids[slot], new ItemStackTransfer(0));
    }
    private static CompoundTag capture(Inventory inventory) {
        CompoundTag result = new CompoundTag(); result.putInt("schema", 1); result.putString("kind", inventory.kind);
        result.putInt("itemSlots", inventory.items.getSlots());
        result.putIntArray("itemLimits", limits(inventory.items));
        result.putInt("fluidTanks", inventory.fluids.getTanks());
        long[] capacities = new long[inventory.fluids.getTanks()];
        for (int i = 0; i < capacities.length; i++) capacities[i] = inventory.fluids.getTankCapacity(i);
        result.putLongArray("fluidCapacities", capacities);
        result.putInt("circuitSlots", inventory.circuits.getSlots()); result.putIntArray("circuitLimits", limits(inventory.circuits));
        return contents(result, inventory);
    }
    private static int[] limits(ItemStackTransfer items) {
        int[] result = new int[items.getSlots()]; for (int i = 0; i < result.length; i++) result[i] = items.getSlotLimit(i); return result;
    }
    private static CompoundTag contents(CompoundTag shape, Inventory inventory) {
        CompoundTag result = shape.copy(); result.put("items", inventory.items.serializeNBT().copy());
        result.put("fluids", inventory.fluids.serializeNBT().copy()); result.put("sharedCircuit", inventory.circuits.serializeNBT().copy()); return result;
    }
    private static Inventory decode(CompoundTag state) {
        Objects.requireNonNull(state); String kind = state.getString("kind");
        if (state.getInt("schema") != 1 || !kind.equals("SHARED") && !kind.equals("PATTERN"))
            throw new IllegalArgumentException("Unknown catalyst escrow format");
        int itemSlots = state.getInt("itemSlots"), circuitSlots = state.getInt("circuitSlots");
        long[] capacities = state.getLongArray("fluidCapacities");
        if (itemSlots < 0 || circuitSlots < 0 || capacities.length != state.getInt("fluidTanks")
                || state.getIntArray("itemLimits").length != itemSlots || state.getIntArray("circuitLimits").length != circuitSlots)
            throw new IllegalArgumentException("Invalid catalyst inventory shape");
        var items = new ItemStackTransfer(itemSlots); items.deserializeNBT(state.getCompound("items"));
        var circuits = new ItemStackTransfer(circuitSlots); circuits.deserializeNBT(state.getCompound("sharedCircuit"));
        FluidStorage[] tanks = new FluidStorage[capacities.length];
        for (int i = 0; i < tanks.length; i++) {
            if (capacities[i] < 0) throw new IllegalArgumentException("Negative catalyst tank capacity");
            tanks[i] = new FluidStorage(capacities[i]);
        }
        var fluids = new FluidTransferList(tanks); fluids.deserializeNBT(state.getCompound("fluids"));
        if (items.getSlots() != itemSlots || circuits.getSlots() != circuitSlots || fluids.getTanks() != capacities.length)
            throw new IllegalArgumentException("Catalyst serialization changed inventory shape");
        return new Inventory(kind, items, fluids, circuits);
    }
    private static Inventory empty(CompoundTag state) {
        Inventory result = decode(state);
        for (int i = 0; i < result.items.getSlots(); i++) result.items.setStackInSlot(i, ItemStack.EMPTY);
        for (int i = 0; i < result.fluids.getTanks(); i++) result.fluids.setFluidInTank(i, FluidStack.empty());
        for (int i = 0; i < result.circuits.getSlots(); i++) result.circuits.setStackInSlot(i, ItemStack.EMPTY);
        return result;
    }
    private static boolean empty(Inventory inventory) {
        for (int i = 0; i < inventory.items.getSlots(); i++) if (!inventory.items.getStackInSlot(i).isEmpty()) return false;
        for (int i = 0; i < inventory.fluids.getTanks(); i++) if (!inventory.fluids.getFluidInTank(i).isEmpty()) return false;
        for (int i = 0; i < inventory.circuits.getSlots(); i++) if (!inventory.circuits.getStackInSlot(i).isEmpty()) return false;
        return true;
    }
    private static boolean sameShape(Inventory actual, CompoundTag expected) {
        return actual.kind.equals(expected.getString("kind")) && actual.items.getSlots() == expected.getInt("itemSlots")
                && actual.circuits.getSlots() == expected.getInt("circuitSlots") && actual.fluids.getTanks() == expected.getInt("fluidTanks")
                && Arrays.equals(limits(actual.items), expected.getIntArray("itemLimits"))
                && Arrays.equals(limits(actual.circuits), expected.getIntArray("circuitLimits"))
                && Arrays.equals(capture(actual).getLongArray("fluidCapacities"), expected.getLongArray("fluidCapacities"));
    }
    private static void simulateDebit(Inventory source, Inventory expected) {
        for (int i = 0; i < source.items.getSlots(); i++) {
            ItemStack stack = expected.items.getStackInSlot(i);
            if (!stack.isEmpty() && !ItemStack.matches(stack, source.items.extractItem(i, stack.getCount(), true, false)))
                throw new IllegalStateException("Catalyst item extraction simulation differed");
        }
        for (int i = 0; i < source.circuits.getSlots(); i++) {
            ItemStack stack = expected.circuits.getStackInSlot(i);
            if (!stack.isEmpty() && !ItemStack.matches(stack, source.circuits.extractItem(i, stack.getCount(), true, false)))
                throw new IllegalStateException("Circuit extraction simulation differed");
        }
        for (int i = 0; i < source.fluids.getTanks(); i++) {
            FluidStack fluid = expected.fluids.getFluidInTank(i);
            if (!fluid.isEmpty() && !fluid.isFluidStackEqual(source.fluids.drain(i, fluid.copy(), true, false)))
                throw new IllegalStateException("Catalyst fluid extraction simulation differed");
        }
    }
    private static CompoundTag drain(MEPatternBufferPartMachine machine, Inventory source, CompoundTag state) {
        Inventory expected = decode(state), escrow = empty(state); RuntimeException failure = null; boolean touched = false;
        try {
            if (!sameShape(source, state) || !capture(source).equals(state)) throw new IllegalStateException("Catalyst source changed after WAL snapshot");
            simulateDebit(source, expected); touched = true;
            debitItems(source.items, expected.items, escrow.items); debitItems(source.circuits, expected.circuits, escrow.circuits);
            for (int i = 0; i < source.fluids.getTanks(); i++) {
                FluidStack wanted = expected.fluids.getFluidInTank(i); if (wanted.isEmpty()) continue;
                FluidStack actual = source.fluids.drain(i, wanted.copy(), false, false);
                escrow.fluids.setFluidInTank(i, actual.copy());
                if (!wanted.isFluidStackEqual(actual)) throw new IllegalStateException("Catalyst fluid debit differed");
            }
            if (!empty(source)) throw new IllegalStateException("Catalyst source changed during debit");
        } catch (RuntimeException rejected) { failure = rejected; }
        if (touched) failure = notifyChanged(machine, source, failure);
        CompoundTag actual = contents(state, escrow);
        if (failure != null) throw new TransferFailure("Catalyst debit requires escrow review", actual, failure);
        return actual;
    }
    private static void debitItems(ItemStackTransfer source, ItemStackTransfer expected, ItemStackTransfer escrow) {
        for (int i = 0; i < source.getSlots(); i++) {
            ItemStack wanted = expected.getStackInSlot(i); if (wanted.isEmpty()) continue;
            ItemStack actual = source.extractItem(i, wanted.getCount(), false, false); escrow.setStackInSlot(i, actual.copy());
            if (!ItemStack.matches(wanted, actual)) throw new IllegalStateException("Catalyst item debit differed");
        }
    }
    private static boolean canRestore(Inventory target, CompoundTag state) {
        Inventory saved = decode(state);
        if (!sameShape(target, state) || !empty(target)) return false;
        for (int i = 0; i < target.items.getSlots(); i++) {
            ItemStack stack = saved.items.getStackInSlot(i);
            if (!stack.isEmpty() && !target.items.insertItem(i, stack.copy(), true, false).isEmpty()) return false;
        }
        for (int i = 0; i < target.circuits.getSlots(); i++) {
            ItemStack stack = saved.circuits.getStackInSlot(i);
            if (!stack.isEmpty() && !target.circuits.insertItem(i, stack.copy(), true, false).isEmpty()) return false;
        }
        for (int i = 0; i < target.fluids.getTanks(); i++) {
            FluidStack fluid = saved.fluids.getFluidInTank(i);
            if (!fluid.isEmpty() && target.fluids.fill(i, fluid.copy(), true, false) != fluid.getAmount()) return false;
        }
        return true;
    }
    private static void restore(MEPatternBufferPartMachine machine, Inventory target, CompoundTag state) {
        Inventory remaining = decode(state); RuntimeException failure = null; boolean touched = false;
        try {
            if (!canRestore(target, state)) throw new IllegalStateException("Original catalyst slots changed or cannot contain escrow");
            touched = true; restoreItems(target.items, remaining.items); restoreItems(target.circuits, remaining.circuits);
            for (int i = 0; i < target.fluids.getTanks(); i++) {
                FluidStack wanted = remaining.fluids.getFluidInTank(i).copy(); if (wanted.isEmpty()) continue;
                long amount = target.fluids.fill(i, wanted.copy(), false, false);
                if (amount < 0 || amount > wanted.getAmount()) throw new IllegalStateException("Invalid catalyst fluid restoration amount");
                remaining.fluids.setFluidInTank(i, wanted.copy(wanted.getAmount() - amount));
                if (amount != wanted.getAmount()) throw new IllegalStateException("Catalyst fluid restoration differed from simulation");
            }
        } catch (RuntimeException rejected) { failure = rejected; }
        if (touched) failure = notifyChanged(machine, target, failure);
        if (failure == null && !capture(target).equals(state)) failure = new IllegalStateException("Catalyst restoration did not preserve complete source state");
        if (failure != null) throw new TransferFailure("Catalyst restoration requires escrow review", contents(state, remaining), failure);
    }
    private static void restoreItems(ItemStackTransfer target, ItemStackTransfer remaining) {
        for (int i = 0; i < target.getSlots(); i++) {
            ItemStack stack = remaining.getStackInSlot(i); if (stack.isEmpty()) continue;
            ItemStack remainder = target.insertItem(i, stack.copy(), false, false); remaining.setStackInSlot(i, remainder.copy());
            if (!remainder.isEmpty()) throw new IllegalStateException("Catalyst item restoration differed from simulation");
        }
    }
    private static RuntimeException notifyChanged(MEPatternBufferPartMachine machine, Inventory inventory, RuntimeException failure) {
        for (Runnable changed : new Runnable[]{machine::markDirty, inventory.items::onContentsChanged, inventory.fluids::onContentsChanged, inventory.circuits::onContentsChanged}) {
            try { changed.run(); } catch (RuntimeException notificationFailure) {
                if (failure == null) failure = notificationFailure; else failure.addSuppressed(notificationFailure);
            }
        }
        return failure;
    }
}
