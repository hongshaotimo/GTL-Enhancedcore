package com.gtl.enhancedcore;

import com.gtl.enhancedcore.common.machine.hatch.CreativeComputationSource;
import com.gtl.enhancedcore.common.recipe.ComputationWaivers;
import org.gtlcore.gtlcore.api.machine.computation.ComputationAmounts;
import org.gtlcore.gtlcore.api.machine.computation.ComputationScheduler;
import java.util.List;
import java.util.Map;
import java.math.BigInteger;

/** Exercises the production source against the actual GTLCore scheduler, without a Forge world. */
final class CreativeComputationPolicyRegression {
    private static int checked;

    private CreativeComputationPolicyRegression() {}

    public static void main(String[] args) {
        System.out.println("Creative computation regression checks: " + run());
    }

    static int run() {
        checked = 0;
        unlimitedSupply();
        bigIntegerSupply();
        unlimitedLifecycle();
        sharedBudget();
        refundAndTicks();
        longAllocation();
        failedSourceRefund();
        waivers();
        return checked;
    }

    private static void sharedBudget() {
        long[] tick = {100};
        // A finite fixture still protects normal GTLCore scheduling semantics.
        var source = source(tick, 2_100_000_000L);
        var scheduler = new ComputationScheduler();
        scheduler.advance(tick[0]);
        var owner = new Object();
        var other = new Object();
        check(scheduler.offer(owner, List.of(source, source), 1_400_000_000L, 1_400_000_000L) == 1_400_000_000L,
                "duplicate direct, wired or cloud routes reserve one physical source");
        check(source.gtlcore$availableComputation() == 2_100_000_000L, "offering computation is non-consuming");
        check(scheduler.available(other, List.of(source)) == 700_000_000L,
                "other controllers cannot claim reserved computation");
        try (var payment = scheduler.withdraw(owner, 1_400_000_000L)) {
            check(payment != null && payment.amount() == 1_400_000_000L, "a reservation withdraws exactly once");
            payment.commit();
        }
        check(source.gtlcore$availableComputation() == 700_000_000L, "all routes share the same spent budget");
        check(scheduler.offer(other, List.of(source), 800_000_000L, 800_000_000L) == 0,
                "a second order cannot overspend the tick");
        check(scheduler.offer(other, List.of(source), 700_000_000L, 700_000_000L) == 700_000_000L,
                "a second order can use the exact remainder");
        try (var payment = scheduler.withdraw(other, 700_000_000L)) { payment.commit(); }
        check(source.gtlcore$availableComputation() == 0, "the physical tick budget is fully exhausted");
        check(source.gtlcore$withdrawComputation(1) == null, "legacy and recipe calls cannot spend beyond the ledger");
    }

    private static void refundAndTicks() {
        long[] tick = {1};
        long[] capacity = {100};
        boolean[] online = {true};
        var source = new CreativeComputationSource(() -> tick[0], () -> capacity[0], () -> online[0]);
        var scheduler = new ComputationScheduler();
        var owner = new Object();
        scheduler.advance(tick[0]);
        check(source.gtlcore$withdrawComputation(0) == null && source.gtlcore$withdrawComputation(-1) == null,
                "nonpositive requests never debit a source");
        check(scheduler.offer(owner, List.of(source), 60, 60) == 60, "reserve computation for a payable tick");
        var cancelled = scheduler.withdraw(owner, 60);
        check(source.gtlcore$availableComputation() == 40, "receipt records an uncommitted debit");
        cancelled.close();
        cancelled.close();
        check(source.gtlcore$availableComputation() == 100, "EU failure refunds CWU exactly once");
        scheduler.offer(owner, List.of(source), 60, 60);
        var committed = scheduler.withdraw(owner, 60);
        committed.commit();
        committed.close();
        check(source.gtlcore$availableComputation() == 40, "successful payment is never refunded on close");
        capacity[0] = 20;
        check(source.gtlcore$availableComputation() == 0, "lowering capacity cannot restore already spent work");
        tick[0]++;
        scheduler.advance(tick[0]);
        check(source.gtlcore$availableComputation() == 20, "a new world tick resets the physical budget");
        online[0] = false;
        check(source.gtlcore$computationCapacity() == 0 && source.gtlcore$availableComputation() == 0,
                "unformed or unloaded hatches expose no computation");
        check(!source.gtlcore$canBridgeComputation() && source.gtlcore$withdrawComputation(1) == null,
                "offline hatches reject bridge and withdrawal calls");
        online[0] = true;
        check(source.gtlcore$availableComputation() == 20, "restoring a route does not manufacture another tick budget");
        scheduler.forget(owner);
        check(scheduler.available(new Object(), List.of(source)) == 20, "invalidating an owner releases its reservation");
    }

    private static void longAllocation() {
        long[] tick = {4};
        var first = source(tick, 2_100_000_000L);
        var second = source(tick, 2_100_000_000L);
        var scheduler = new ComputationScheduler();
        scheduler.advance(tick[0]);
        var owner = new Object();
        check(scheduler.available(owner, List.of(first, second, first)) == 4_200_000_000L,
                "multiple physical sources aggregate beyond the int range without duplication");
        check(scheduler.offer(owner, List.of(first, second), 4_000_000_000L, 4_000_000_000L) == 4_000_000_000L,
                "long recipe demand is reserved exactly");
        try (var transaction = scheduler.withdraw(owner, 4_000_000_000L)) {
            check(transaction != null && transaction.amount() == 4_000_000_000L, "long withdrawal remains exact");
            transaction.commit();
        }
        check(first.gtlcore$availableComputation() + second.gtlcore$availableComputation() == 200_000_000L,
                "the long debit equals the sum of source debits");
        check(ComputationAmounts.read("5000000000") == 5_000_000_000L
                        && ComputationAmounts.box(5_000_000_000L) instanceof Long,
                "new recipe quantities preserve their long value");
        check(ComputationAmounts.box(128) instanceof Integer, "small amounts retain the legacy boxed ABI");
    }

    private static void failedSourceRefund() {
        long[] tick = {9};
        boolean[] online = {true};
        var first = source(tick, 100);
        var second = new CreativeComputationSource(() -> tick[0], () -> 100, () -> online[0]);
        var scheduler = new ComputationScheduler();
        scheduler.advance(tick[0]);
        var owner = new Object();
        scheduler.offer(owner, List.of(first, second), 180, 180);
        online[0] = false;
        check(scheduler.withdraw(owner, 180) == null, "a source going offline rejects the entire allocation");
        check(first.gtlcore$availableComputation() == 100, "partial source withdrawal is refunded on failure");
        online[0] = true;
        check(scheduler.available(new Object(), List.of(first, second)) == 200,
                "failed allocation releases all reservations");
    }

    private static void unlimitedSupply() {
        long[] tick = {200};
        var source = new CreativeComputationSource(() -> tick[0], () -> true);
        check(source.unlimited(), "the actual creative source has no finite per-tick budget");
        check(source.gtlcore$computationCapacity() == Long.MAX_VALUE
                        && source.gtlcore$availableComputation() == Long.MAX_VALUE,
                "the long capability advertises its full representable demand, not 2.1 billion");
        var first = source.gtlcore$withdrawComputation(Long.MAX_VALUE);
        var second = source.gtlcore$withdrawComputation(Long.MAX_VALUE);
        check(first != null && first.amount() == Long.MAX_VALUE
                        && second != null && second.amount() == Long.MAX_VALUE,
                "one physical source supplies multiple full-long requests in the same tick");
        BigInteger twoLongs = BigInteger.valueOf(Long.MAX_VALUE).multiply(BigInteger.TWO);
        check(source.deliveredComputation().equals(twoLongs),
                "real supplied computation aggregates beyond Long.MAX_VALUE without saturation");
        check(source.gtlcore$availableComputation() == Long.MAX_VALUE,
                "completed requests never exhaust the infinite creative supply");
        first.refund().run();
        first.refund().run();
        check(source.deliveredComputation().equals(BigInteger.valueOf(Long.MAX_VALUE)),
                "a failed long transaction reverses its exact supplied amount at most once");
        second.refund().run();
        check(source.deliveredComputation().signum() == 0,
                "refunding all long requests restores the BigInteger supply accounting exactly");
    }

    private static void bigIntegerSupply() {
        long[] tick = {300};
        var source = new CreativeComputationSource(() -> tick[0], () -> true);
        BigInteger requested = BigInteger.TEN.pow(100);
        var first = source.withdrawComputation(requested);
        var second = source.withdrawComputation(requested.add(BigInteger.ONE));
        check(first != null && first.amount().equals(requested)
                        && second != null && second.amount().equals(requested.add(BigInteger.ONE)),
                "the real BigInteger withdrawal path supplies 10^100 CWU without a long projection");
        check(source.deliveredComputation().equals(requested.multiply(BigInteger.TWO).add(BigInteger.ONE)),
                "arbitrary-precision requested amounts remain exact after real withdrawal");
        first.refund().run();
        first.refund().run();
        check(source.deliveredComputation().equals(requested.add(BigInteger.ONE)),
                "a BigInteger receipt refunds its own amount exactly once");
        second.refund().run();
        check(source.deliveredComputation().signum() == 0,
                "BigInteger receipts return the exact net supply accounting to zero");
        check(source.withdrawComputation(BigInteger.ZERO) == null
                        && source.withdrawComputation(BigInteger.ONE.negate()) == null,
                "zero and negative BigInteger requests never manufacture computation");
        var finite = new CreativeComputationSource(() -> tick[0], () -> BigInteger.valueOf(100), () -> true);
        check(!finite.unlimited() && finite.withdrawComputation(requested) == null,
                "ordinary finite-source fixtures cannot use the creative unbounded path");
    }

    private static void unlimitedLifecycle() {
        long[] tick = {400};
        boolean[] online = {true};
        var source = new CreativeComputationSource(() -> tick[0], () -> online[0]);
        BigInteger huge = BigInteger.TEN.pow(100);
        var previousTick = source.withdrawComputation(huge);
        check(previousTick != null && source.deliveredComputation().equals(huge),
                "online creative supply records the actual current-tick BigInteger amount");
        tick[0]++;
        check(source.deliveredComputation().signum() == 0,
                "a new world tick resets only delivered-computation statistics");
        var currentTick = source.withdrawComputation(BigInteger.valueOf(75));
        previousTick.refund().run();
        check(source.deliveredComputation().equals(BigInteger.valueOf(75)),
                "an old receipt cannot reduce the next tick's supplied computation");
        online[0] = false;
        check(source.gtlcore$computationCapacity() == 0
                        && source.gtlcore$availableComputation() == 0
                        && !source.gtlcore$canBridgeComputation(),
                "an unformed or unloaded creative source exposes neither supply nor a route");
        check(source.withdrawComputation(huge) == null && source.gtlcore$withdrawComputation(1) == null,
                "both BigInteger and long consumers reject offline creative supply");
        currentTick.refund().run();
        check(source.deliveredComputation().signum() == 0,
                "going offline still allows an already-issued receipt to refund its supplied work");
        online[0] = true;
        check(source.withdrawComputation(huge) != null
                        && source.gtlcore$availableComputation() == Long.MAX_VALUE,
                "a restored online source immediately supplies arbitrary-precision work again");
    }

    private static void waivers() {
        check(ComputationWaivers.engravingLaserPlant("gtceu:engraving_laser_plant", true),
                "the formed engraving plant retains its consumer-specific waiver");
        check(!ComputationWaivers.engravingLaserPlant("gtceu:engraving_laser_plant", false)
                        && !ComputationWaivers.engravingLaserPlant("gtceu:dimensional_focus_engraving_array", true),
                "a shared hatch never grants other machines the waiver");
        Map<String, Long> recipe = Map.of("CWU", 4_000_000_000L, "EU", 128L, "fluid", 1000L);
        var free = ComputationWaivers.withoutInput(recipe, "CWU");
        check(recipe.containsKey("CWU") && !free.containsKey("CWU") && free.get("EU") == 128L
                        && free.get("fluid") == 1000L,
                "filtering immutable recipe inputs preserves the original and all other requirements");
        check(ComputationWaivers.withoutInput(free, "CWU") == free, "waiver does not copy maps unnecessarily");
    }

    private static CreativeComputationSource source(long[] tick, long capacity) {
        return new CreativeComputationSource(() -> tick[0], () -> capacity, () -> true);
    }

    private static void check(boolean condition, String message) {
        checked++;
        if (!condition) throw new AssertionError(message);
    }
}
