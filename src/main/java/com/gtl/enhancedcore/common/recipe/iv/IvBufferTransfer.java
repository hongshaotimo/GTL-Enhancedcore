package com.gtl.enhancedcore.common.recipe.iv;

import appeng.api.networking.crafting.ICraftingProvider;
import net.minecraft.nbt.CompoundTag;
import org.gtlcore.gtlcore.common.machine.multiblock.part.ae.MEPatternBufferPartMachine;

/** Transfers pattern configuration through the upstream tool, never the isolated order ledger. */
public final class IvBufferTransfer {
    private IvBufferTransfer() {}

    public static boolean guarded(MEPatternBufferPartMachine buffer) {
        IvBufferState state = IvBuffers.state(buffer);
        return state != null && (state.dedicated() || !state.jobs.isEmpty() || IvBuffers.isolated(buffer));
    }

    public static boolean allowCut(MEPatternBufferPartMachine buffer) {
        if (!guarded(buffer)) return true;
        IvBufferState state = IvBuffers.state(buffer);
        if (!idle(buffer, state) || !buffer.getBoundProxyPositions().isEmpty() || emptySlotCatalysts(buffer)
                || extraCircuitStock(buffer))
            return reject(buffer, state, "CUT", "gtl_enhancedcore.diagnostic.iv_cut");
        return true;
    }

    public static boolean allowPaste(MEPatternBufferPartMachine buffer, CompoundTag payload) {
        if (!guarded(buffer)) return true;
        IvBufferState state = IvBuffers.state(buffer);
        // Upstream replaces the entire shared storage; never overwrite existing physical contents.
        if (!idle(buffer, state) || !buffer.getBoundProxyPositions().isEmpty()
                || payload.getLongArray("proxies").length != 0 || payload.contains(IvBuffers.SAVE_KEY)
                || sharedStock(buffer) || emptySlotCatalysts(buffer))
            return reject(buffer, state, "PASTE", "gtl_enhancedcore.diagnostic.iv_paste");
        return true;
    }

    private static boolean idle(MEPatternBufferPartMachine buffer, IvBufferState state) {
        if (!state.healthy() || !state.jobs.isEmpty() || !buffer.getBuffer().isEmpty()) return false;
        for (Object slot : buffer.getInternalInventory())
            if (!(slot instanceof IvSlotAccess access) || access.iv$hasStock()) return false;
        return true;
    }

    private static boolean sharedStock(MEPatternBufferPartMachine buffer) {
        var items = buffer.getSharedCatalystInventory();
        for (int i = 0; i < items.getSlots(); i++)
            if (!items.getStackInSlot(i).isEmpty()) return true;
        var tanks = buffer.getSharedCatalystTank();
        for (int i = 0; i < tanks.getTanks(); i++)
            if (!tanks.getFluidInTank(i).isEmpty()) return true;
        var circuits = buffer.getSharedCircuitInventory();
        for (int i = 0; i < circuits.getSlots(); i++)
            if (!circuits.getStackInSlot(i).isEmpty()) return true;
        return false;
    }

    private static boolean extraCircuitStock(MEPatternBufferPartMachine buffer) {
        // This upstream cutter clears only shared circuit slot 0 after serializing all its slots.
        var circuits = buffer.getSharedCircuitInventory();
        for (int i = 1; i < circuits.getSlots(); i++)
            if (!circuits.getStackInSlot(i).isEmpty()) return true;
        return false;
    }

    private static boolean emptySlotCatalysts(MEPatternBufferPartMachine buffer) {
        Object[] slots = buffer.getInternalInventory();
        for (int i = 0; i < slots.length; i++)
            if (buffer.getPatternInventory().getStackInSlot(i).isEmpty()
                    && !((IvSlotAccess) slots[i]).iv$virtualStock().isEmpty()) return true;
        return false;
    }

    private static boolean reject(MEPatternBufferPartMachine buffer, IvBufferState state,
                                  String operation, String reason) {
        state.message = reason;
        IvTaskLog.event(buffer, null, operation, "REJECTED", "reason", reason);
        buffer.markDirty();
        return false;
    }

    public static void cutCompleted(MEPatternBufferPartMachine buffer, CompoundTag toolTag) {
        if (!guarded(buffer) || toolTag.getCompound("cut").isEmpty()) return;
        // cutToTag uses setItemDirect and does not rebuild the provider's published pattern map.
        IvBufferMethods methods = (IvBufferMethods) buffer;
        for (int i = 0; i < buffer.getPatternInventory().getSlots(); i++) methods.iv$patternChanged(i);
        methods.iv$refreshPatterns();
        ICraftingProvider.requestUpdate(buffer.getMainNode());
        IvTaskLog.event(buffer, null, "CUT", "OK", "tasks", 0);
    }
}
