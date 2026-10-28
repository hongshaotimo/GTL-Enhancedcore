package com.gtl.enhancedcore.audit;

import appeng.api.config.Actionable;
import appeng.api.networking.energy.IEnergySource;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.storage.MEStorage;
import appeng.api.storage.StorageHelper;
import com.gtl.enhancedcore.common.recipe.iv.IvOutputTransfer;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Standalone call of the pinned AE API with synthetic power and storage, without a world. */
public final class StellarOutputProbe {
    public static void main(String[] args) {
        long pending = 2_751_463_424_000L;
        for (double ae : new double[]{0, 160_000, Double.POSITIVE_INFINITY}) {
            Storage storage = new Storage(Long.MAX_VALUE);
            IEnergySource energy = (amount, mode, multiplier) -> Math.min(amount, ae);
            long inserted = StorageHelper.poweredInsert(energy, storage, new Key(125), pending, IActionSource.empty());
            long expected = Math.min(pending, (long)(ae * 125 + 0.9));
            if (inserted != expected || storage.stored != inserted)
                throw new AssertionError("Unexpected AE insertion: " + inserted);
            System.out.println("AE=" + ae + ", offered(mB)=" + pending + ", inserted(mB)=" + inserted
                    + ", remaining(mB)=" + (pending - inserted));
        }
        Storage full = new Storage(0);
        if (StorageHelper.poweredInsert((a, m, p) -> a, full, new Key(125), pending, IActionSource.empty()) != 0)
            throw new AssertionError("A full network must reject output even with unlimited power");
        System.out.println("PASS: actual AE poweredInsert; finite power throttles, unlimited power drains, full storage rejects.");
        Key fluid = new Key(125);
        expect(IvOutputTransfer.State.POWER, new Storage(Long.MAX_VALUE), 0, fluid, pending);
        expect(IvOutputTransfer.State.PROGRESS, new Storage(Long.MAX_VALUE), 160_000, fluid, pending);
        expect(IvOutputTransfer.State.COMPLETE, new Storage(Long.MAX_VALUE), Double.POSITIVE_INFINITY, fluid, pending);
        expect(IvOutputTransfer.State.STORAGE, new Storage(0), Double.POSITIVE_INFINITY, fluid, pending);
        Storage sized = new Storage(Long.MAX_VALUE); sized.maxRequest = Integer.MAX_VALUE;
        expect(IvOutputTransfer.State.REQUEST_LIMIT, sized, Double.POSITIVE_INFINITY, fluid, pending);
        Storage denied = new Storage(Long.MAX_VALUE); denied.rejectExecution = true;
        expect(IvOutputTransfer.State.REJECTED, denied, Double.POSITIVE_INFINITY, fluid, pending);
        expect(IvOutputTransfer.State.COMPLETE, new Storage(Long.MAX_VALUE), Double.POSITIVE_INFINITY, new Key(1), Long.MAX_VALUE);
        System.out.println("PASS: output diagnostics distinguish all 6 states and preserve the exact Long.MAX_VALUE transfer.");
    }

    private static void expect(IvOutputTransfer.State expected, Storage storage, double ae, Key key, long offered) {
        var result = IvOutputTransfer.transfer((a,m,p) -> Math.min(a,ae), storage, key, offered, IActionSource.empty());
        if (result.state() != expected || result.inserted() != storage.stored
                || Math.addExact(result.inserted(), result.remaining()) != offered)
            throw new AssertionError("Transfer state or conservation failure: " + result);
        System.out.println(result);
    }

    private static final class Storage implements MEStorage {
        private final long capacity;
        private long stored;
        private long maxRequest = Long.MAX_VALUE;
        private boolean rejectExecution;
        Storage(long capacity) { this.capacity = capacity; }
        @Override public Component getDescription() { return null; }
        @Override public long insert(AEKey key, long amount, Actionable mode, IActionSource source) {
            if (amount > maxRequest || rejectExecution && mode == Actionable.MODULATE) return 0;
            long accepted = Math.min(amount, capacity - stored);
            if (mode == Actionable.MODULATE) stored += accepted;
            return accepted;
        }
    }

    private static final class Key extends AEKey {
        private final AEKeyType type;
        Key(int amountPerOperation) {
            type = new AEKeyType(null, Key.class, null) {
                @Override public int getAmountPerOperation() { return amountPerOperation; }
                @Override public AEKey readFromPacket(FriendlyByteBuf packet) { throw new UnsupportedOperationException(); }
                @Override public AEKey loadKeyFromTag(CompoundTag tag) { throw new UnsupportedOperationException(); }
            };
        }
        @Override public AEKeyType getType() { return type; }
        @Override public AEKey dropSecondary() { return this; }
        @Override public CompoundTag toTag() { throw new UnsupportedOperationException(); }
        @Override public Object getPrimaryKey() { return this; }
        @Override public ResourceLocation getId() { return null; }
        @Override public void writeToPacket(FriendlyByteBuf packet) { throw new UnsupportedOperationException(); }
        @Override protected Component computeDisplayName() { return null; }
        @Override public void addDrops(long amount, List<ItemStack> drops, Level level, BlockPos pos) {
            throw new UnsupportedOperationException();
        }
    }
}
