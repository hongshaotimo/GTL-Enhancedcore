package com.gtl.enhancedcore.common.cloud.transfer;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.networking.crafting.ICraftingProvider;
import com.gtladd.gtladditions.api.machine.IWidePatternSlot;
import com.gtl.enhancedcore.common.recipe.iv.IvBufferAccess;
import com.gtl.enhancedcore.common.recipe.iv.IvBufferMethods;
import com.gtl.enhancedcore.common.recipe.iv.IvBuffers;
import com.gtl.enhancedcore.mixin.gtlcore.CloudPatternBufferAccessor;
import com.gtl.enhancedcore.mixin.gtlcore.CloudPatternSlotAccessor;
import java.util.Objects;
import java.util.UUID;
import java.util.function.BiPredicate;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.gtlcore.gtlcore.common.machine.multiblock.part.ae.MEPatternBufferPartMachine;

/** Server-thread, bounded import. A prepared receipt must survive any ambiguous extraction. */
public final class CloudBufferImport {
    private CloudBufferImport() {}
    public static final int MAX_SLOTS_PER_CALL = 64;

    public enum Rejection { NONE, NOT_LOADED, TOO_FAR, PERMISSION, UNSUPPORTED, UNHEALTHY, BUSY, PROXIES, MATERIALS, FOA }
    public record Inspection(Rejection reason) { public boolean allowed() { return reason == Rejection.NONE; } }
    public record Result(int nextSlot, int moved, int simulated, Rejection rejection, UUID unresolved) {}

    /** Store must persist PREPARED before returning, publish only on commit, and block uncertain receipts. */
    public interface ImportSink {
        default void beginBatch() {}
        default void endBatch() {}
        boolean canAccept(ItemStack pattern, CompoundTag configuration);
        UUID prepare(Source source, int slot, ItemStack expected, CompoundTag configuration);
        void commit(UUID transaction, ItemStack actuallyExtracted);
        void abort(UUID transaction);
        void uncertain(UUID transaction, RuntimeException failure);
    }
    public record Source(String dimension, long position, UUID identity) {}

    public static Inspection inspect(ServerPlayer player, MEPatternBufferPartMachine source,
                                     BiPredicate<ServerPlayer, MEPatternBufferPartMachine> permission) {
        return inspect(player, source, permission, true);
    }
    private static Inspection inspect(ServerPlayer player, MEPatternBufferPartMachine source,
                                      BiPredicate<ServerPlayer, MEPatternBufferPartMachine> permission, boolean nearby) {
        return inspect(player, source, permission, nearby, false);
    }
    /** Once per bounded network page: permit only physical catalysts/circuit, never materials or active work. */
    public static Inspection inspectNetworkWithCatalysts(ServerPlayer player, MEPatternBufferPartMachine source,
                                      BiPredicate<ServerPlayer, MEPatternBufferPartMachine> permission) {
        return inspect(player, source, permission, false, true);
    }
    private static Inspection inspect(ServerPlayer player, MEPatternBufferPartMachine source,
                                      BiPredicate<ServerPlayer, MEPatternBufferPartMachine> permission, boolean nearby, boolean allowCatalysts) {
        if (!(source.getLevel() instanceof ServerLevel level) || player.getServer() != level.getServer()
                || nearby && player.serverLevel() != level)
            return new Inspection(Rejection.NOT_LOADED);
        if (!level.getServer().isSameThread()) throw new IllegalStateException("Cloud import requires server thread");
        var pos = source.getPos();
        var chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
        if (chunk == null || chunk.getBlockEntity(pos) != source.getHolder().getSelf())
            return new Inspection(Rejection.NOT_LOADED);
        if (nearby && player.distanceToSqr(pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5) > 64)
            return new Inspection(Rejection.TOO_FAR);
        if (!level.mayInteract(player, pos) || !Objects.requireNonNull(permission).test(player, source))
            return new Inspection(Rejection.PERMISSION);
        var state = IvBuffers.state(source);
        if (source instanceof com.gtl.enhancedcore.common.cloud.CloudPatternBufferMachine)
            return new Inspection(Rejection.UNSUPPORTED);
        if (source instanceof CloudImportSourceAccess access && access.cloud$importQuarantined()) return new Inspection(Rejection.UNHEALTHY);
        if (state == null || !(source instanceof CloudPatternBufferAccessor)) return new Inspection(Rejection.UNSUPPORTED);
        if (!state.healthy()) return new Inspection(Rejection.UNHEALTHY);
        if (!state.jobs.isEmpty()) return new Inspection(Rejection.BUSY);
        if (((IvBufferAccess) source).iv$isFoaEnabled()) return new Inspection(Rejection.FOA);
        if (!source.getBoundProxyPositions().isEmpty() || !source.getProxies().isEmpty()) return new Inspection(Rejection.PROXIES);
        if (!source.getBuffer().isEmpty()) return new Inspection(Rejection.MATERIALS);
        if (allowCatalysts && source instanceof com.gtladd.gtladditions.api.machine.IWidePatternBuffer wide
                && !wide.widePending().isEmpty()) return new Inspection(Rejection.MATERIALS);
        if (!allowCatalysts) {
            var shared = source.getSharedCatalystInventory();
            for (int i = 0; i < shared.getSlots(); i++) if (!shared.getStackInSlot(i).isEmpty()) return new Inspection(Rejection.MATERIALS);
            var tanks = source.getSharedCatalystTank();
            for (int i = 0; i < tanks.getTanks(); i++) if (!tanks.getFluidInTank(i).isEmpty()) return new Inspection(Rejection.MATERIALS);
            var circuits = source.getSharedCircuitInventory();
            for (int i = 0; i < circuits.getSlots(); i++) if (!circuits.getStackInSlot(i).isEmpty()) return new Inspection(Rejection.MATERIALS);
            var access = (CloudPatternBufferAccessor) source;
            for (var items : access.cloud$catalystItems()) for (int i = 0; i < items.getSlots(); i++)
                if (!items.getStackInSlot(i).isEmpty()) return new Inspection(Rejection.MATERIALS);
            for (var fluids : access.cloud$catalystFluids()) for (int i = 0; i < fluids.getTanks(); i++)
                if (!fluids.getFluidInTank(i).isEmpty()) return new Inspection(Rejection.MATERIALS);
        }
        Object[] slots = source.getInternalInventory();
        for (Object raw : slots) {
            if (!(raw instanceof CloudPatternSlotAccessor slot)) return new Inspection(Rejection.UNSUPPORTED);
            if (!slot.cloud$items().isEmpty() || !slot.cloud$fluids().isEmpty()
                    || (!allowCatalysts && (!slot.cloud$itemCatalysts().isEmpty() || !slot.cloud$fluidCatalysts().isEmpty()))
                    || raw instanceof IWidePatternSlot wide && !wide.wideInputs().isEmpty())
                return new Inspection(Rejection.MATERIALS);
        }
        return new Inspection(Rejection.NONE);
    }

    public static Result transfer(ServerPlayer player, MEPatternBufferPartMachine source,
                                  BiPredicate<ServerPlayer, MEPatternBufferPartMachine> permission,
                                  ImportSink sink, int startSlot, int maximum, boolean simulate) {
        return transfer(player, source, permission, sink, startSlot, maximum, simulate, true);
    }
    public static Result transferNetwork(ServerPlayer player, MEPatternBufferPartMachine source,
                                         BiPredicate<ServerPlayer, MEPatternBufferPartMachine> permission,
                                         ImportSink sink, int startSlot, int maximum, boolean simulate) {
        return transfer(player, source, permission, sink, startSlot, maximum, simulate, false);
    }
    private static Result transfer(ServerPlayer player, MEPatternBufferPartMachine source,
                                   BiPredicate<ServerPlayer, MEPatternBufferPartMachine> permission,
                                   ImportSink sink, int startSlot, int maximum, boolean simulate, boolean nearby) {
        Objects.requireNonNull(sink);
        Inspection check = inspect(player, source, permission, nearby);
        if (!check.allowed()) return new Result(startSlot, 0, 0, check.reason(), null);
        var inventory = source.getPatternInventory();
        if (startSlot < 0 || startSlot > inventory.getSlots() || maximum < 1 || maximum > MAX_SLOTS_PER_CALL)
            throw new IllegalArgumentException("Invalid import page");
        int end = Math.min(inventory.getSlots(), startSlot + maximum), moved = 0, offered = 0;
        if (!(source instanceof CloudImportSourceAccess markers)) return new Result(startSlot, 0, 0, Rejection.UNSUPPORTED, null);
        CloudImportInventoryGuard.register(inventory, markers);
        Source identity = new Source(source.getLevel().dimension().location().toString(), source.getPos().asLong(), markers.cloud$sourceIdentity());
        if (!simulate) sink.beginBatch();
        try {
        for (int i = startSlot; i < end; i++) {
            check = inspect(player, source, permission, nearby);
            if (!check.allowed()) return new Result(i, moved, offered, check.reason(), null);
            ItemStack expected = inventory.extractItem(i, Integer.MAX_VALUE, true, false).copy();
            if (expected.isEmpty()) continue;
            var details = PatternDetailsHelper.decodePattern(expected, source.getLevel());
            // Crafting-pattern dispatch belongs to molecular assemblers, not isolated processing.
            if (details == null || details instanceof appeng.crafting.pattern.AECraftingPattern) continue;
            CompoundTag config = CloudSlotConfiguration.capture(source, i);
            if (!sink.canAccept(expected.copy(), config.copy())) return new Result(i, moved, offered, Rejection.NONE, null);
            offered += expected.getCount();
            if (simulate) continue;
            UUID transaction = Objects.requireNonNull(sink.prepare(identity, i, expected.copy(), config.copy()));
            try {
                check = inspect(player, source, permission, nearby);
                if (!check.allowed()) { sink.abort(transaction); return new Result(i, moved, offered, check.reason(), null); }
                ItemStack current = inventory.getStackInSlot(i);
                if (!ItemStack.matches(current, expected)) { sink.abort(transaction); return new Result(i, moved, offered, Rejection.NONE, null); }
                ItemStack actual = inventory.extractItem(i, expected.getCount(), false, true);
                if (actual.isEmpty()) { sink.abort(transaction); continue; }
                markers.cloud$markProcessed(transaction);
                // Commit exactly what the source returned, never its earlier simulation.
                sink.commit(transaction, actual.copy());
                moved += actual.getCount();
                source.markDirty();
                ((IvBufferMethods) source).iv$patternChanged(i);
                ((IvBufferMethods) source).iv$refreshPatterns();
                ICraftingProvider.requestUpdate(source.getMainNode());
            } catch (RuntimeException failure) {
                markers.cloud$quarantineImport("Import transaction " + transaction + " requires review");
                try { sink.uncertain(transaction, failure); } catch (RuntimeException ignored) { failure.addSuppressed(ignored); }
                source.markDirty();
                return new Result(i, moved, offered, Rejection.NONE, transaction);
            }
        }
        return new Result(end, moved, offered, Rejection.NONE, null);
        } finally {
            if (!simulate) {
                try { sink.endBatch(); }
                catch (RuntimeException failure) {
                    markers.cloud$quarantineImport("Batch import durability requires review"); source.markDirty(); throw failure;
                }
            }
        }
    }
}
