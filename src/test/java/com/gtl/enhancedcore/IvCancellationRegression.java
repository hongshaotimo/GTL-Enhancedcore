package com.gtl.enhancedcore;

import com.gtl.enhancedcore.common.recipe.iv.IvJob;
import java.math.BigInteger;
import java.util.Map;
import net.minecraft.nbt.CompoundTag;

/** Exercise the production order transition without bootstrapping Minecraft item registries. */
public final class IvCancellationRegression {
    private static int assertions;
    public static int run() {
        var running = job(100_000);
        running.remaining = 98_720; running.parallel = 640;
        running.completedOperations = 640; running.deliveredOperations = 640;
        // A null sentinel is sufficient for these in-memory stock maps; no AE key is serialized.
        running.inventory.put(null, 98_721L); // waiting material plus an unconsumed tool
        running.completion.put(null, 640L); // unfinished rolled output must be discarded
        running.duration = 200_000; running.elapsed = 5;
        running.eut = 32; running.energyTotal = BigInteger.valueOf(6_400_000);
        running.energyLeft = running.energyTotal.subtract(BigInteger.valueOf(160));
        running.nativeCapacity = 640; running.nativeWireless = true;
        running.nativeWirelessEUt = BigInteger.TEN; running.halted = true;
        running.cancel();
        check(!running.active() && running.remaining == 0 && running.completion.isEmpty(), "running and queued work stop immediately");
        check(running.cancelledOperations == 99_360 && running.deliveredOperations == 640, "past deliveries survive cancellation without counting unfinished output");
        check(running.refunds.get(null) == 98_721 && running.inventory.isEmpty(), "return all identifiable unused stock, including final-batch tools");
        check(running.energyLeft.signum() == 0 && running.eut == 0 && running.duration == 0 && running.nativeCapacity == 0
                && !running.nativeWireless && running.nativeWirelessEUt.signum() == 0 && !running.halted, "no unpaid EU, native reservation or halt keeps the cancelled batch alive");
        running.cancel();
        check(running.cancelledOperations == 99_360 && running.refunds.get(null) == 98_721, "repeated cancellation is idempotent");
        running.refunds.clear();
        check(running.done(), "order can retire as soon as recoverable stock drains");

        var completed = job(10);
        completed.remaining=0; completed.parallel=10;
        completed.inventory.put(null, 1L); completed.completion.put(null, 10L);
        completed.completeBatch();
        check(completed.completedOperations == 10 && completed.pendingOperations == 10 && completed.pending.get(null) == 11,
                "normal completion combines products and unused tools exactly once");
        completed.pending.put(null, 4L); // seven units were already inserted before cancellation
        completed.cancel();
        check(completed.pending.get(null) == 4 && completed.cancelledOperations == 0 && completed.pendingOperations == 10,
                "cancel preserves only undelivered remainder of completed output");

        var empty = job(1); empty.cancel();
        check(empty.done() && empty.cancelledOperations == 1, "no network is needed to cancel an empty queued order");
        var enormous = job(Long.MAX_VALUE);
        enormous.remaining = Long.MAX_VALUE - 640; enormous.parallel = 640;
        enormous.cancel(); enormous.cancel();
        check(enormous.cancelledOperations == Long.MAX_VALUE && enormous.done(), "large order counters remain exact");
        return assertions;
    }
    private static IvJob job(long operations) {
        return new IvJob(0, null, new CompoundTag(), Map.of(), Map.of(), operations);
    }
    private static void check(boolean value, String message) {
        assertions++;
        if (!value) throw new AssertionError(message);
    }
}
