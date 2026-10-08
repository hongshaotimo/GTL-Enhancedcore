package com.gtl.enhancedcore;

import com.gtl.enhancedcore.common.recipe.iv.IvNativeRecoveryState;
import com.gtl.enhancedcore.common.recipe.iv.IvWorkBudget;
import com.gtl.enhancedcore.common.util.FairRecipeSelector;
import java.util.ArrayList;
import java.math.BigInteger;
import java.util.List;
import java.util.Random;
import java.util.UUID;

public final class IvNativeRecoveryRegression {
    private static int assertions;

    private IvNativeRecoveryRegression() {}

    public static int run() {
        assertions = 0;
        controlMigration();
        powerGate();
        paymentBoundaries();
        outputFairness();
        deterministicRecovery();
        randomizedRecovery();
        return assertions;
    }

    public static void main(String[] arguments) {
        System.out.println("Native recovery state tests passed: " + run() + " assertions");
    }

    private static void controlMigration() {
        check(IvNativeRecoveryState.enabled(false, false, false), "old idle saves remain enabled");
        check(!IvNativeRecoveryState.enabled(false, true, true), "old suspended saves remain disabled");
        for (boolean enabled : new boolean[]{false, true}) {
            for (boolean suspended : new boolean[]{false, true}) {
                check(IvNativeRecoveryState.enabled(true, enabled, suspended) == enabled,
                        "stored operator switch is independent of native recipe status");
            }
        }
        for (int reset = 0; reset < 32; reset++) {
            check(!IvNativeRecoveryState.enabled(true, false, false), "structure reset cannot resume a paused controller");
            check(IvNativeRecoveryState.enabled(true, true, true), "transient unavailable status cannot disable the controller");
        }
    }

    private static void powerGate() {
        long multiAmp = IvNativeRecoveryState.inputPower(List.of(new IvNativeRecoveryState.PowerInput(16, 4)));
        check(multiAmp == 64, "actual input amperage contributes to native shared power");
        check(IvNativeRecoveryState.canPay(BigInteger.valueOf(48), multiAmp, 48),
                "recipe above one amp starts when real throughput is sufficient");
        check(!IvNativeRecoveryState.canPay(BigInteger.valueOf(48), 16, 1000),
                "stored EU alone cannot bypass throughput");
        check(!IvNativeRecoveryState.canPay(BigInteger.valueOf(48), 64, 47),
                "underfunded first tick does not authorize input consumption");
        check(IvNativeRecoveryState.inputPower(List.of(new IvNativeRecoveryState.PowerInput(16, 2),
                new IvNativeRecoveryState.PowerInput(32, 1))) == 64, "separate hatches share their true wattage");
        check(IvNativeRecoveryState.inputPower(List.of(new IvNativeRecoveryState.PowerInput(Long.MAX_VALUE, Long.MAX_VALUE)))
                == Long.MAX_VALUE, "input wattage saturates without long multiplication overflow");
        check(IvNativeRecoveryState.inputPower(List.of()) == 0, "unavailable capability map supplies no power");
        check(IvNativeRecoveryState.canPay(BigInteger.ZERO, 0, 0), "zero-EU native recipes remain valid");
        check(!IvNativeRecoveryState.canPay(BigInteger.ONE, 0, 0), "empty hatches reject nonzero first payment");
        check(!IvNativeRecoveryState.canPay(BigInteger.ONE, -1, -1), "negative third-party readings cannot create energy");
        expectFailure(() -> IvNativeRecoveryState.inputPower(List.of(new IvNativeRecoveryState.PowerInput(-1, 1))),
                "negative input voltage rejects invalid capability data");
        expectFailure(() -> IvNativeRecoveryState.inputPower(List.of(new IvNativeRecoveryState.PowerInput(1, -1))),
                "negative input amperage rejects invalid capability data");
        expectFailure(() -> IvNativeRecoveryState.canPay(BigInteger.valueOf(-1), 1, 1), "negative debit is rejected");
    }

    private static void paymentBoundaries() {
        var zero = IvNativeRecoveryState.paid(BigInteger.ZERO, BigInteger.ZERO, 0, 1);
        check(zero.complete() && zero.elapsed() == 1 && zero.energyLeft().signum() == 0,
                "zero-EU batches still require their full duration");
        BigInteger large = BigInteger.ONE.shiftLeft(200).add(BigInteger.valueOf(77));
        BigInteger debit = large.divide(BigInteger.valueOf(20));
        BigInteger remaining = debit.multiply(BigInteger.valueOf(20));
        BigInteger paid = BigInteger.ZERO;
        for (int elapsed = 0; elapsed < 20; elapsed++) {
            var tick = IvNativeRecoveryState.paid(remaining, debit, elapsed, 20);
            remaining = tick.energyLeft(); paid = paid.add(debit);
            check(tick.complete() == (elapsed == 19), "wireless BigInteger ledger finishes only on its twentieth paid tick");
        }
        check(remaining.signum() == 0 && paid.equals(debit.multiply(BigInteger.valueOf(20))),
                "native wireless floor(total/20) contract stays unchanged");
        expectFailure(() -> IvNativeRecoveryState.paid(BigInteger.TEN, BigInteger.valueOf(11), 0, 2), "energy cannot be overdrawn");
        expectFailure(() -> IvNativeRecoveryState.paid(BigInteger.ONE, BigInteger.ZERO, 0, 1),
                "duration cannot finish with unpaid energy");
        expectFailure(() -> IvNativeRecoveryState.paid(BigInteger.ZERO, BigInteger.ZERO, 1, 1),
                "completed ledgers cannot advance twice");
        expectFailure(() -> IvNativeRecoveryState.paid(BigInteger.ZERO, BigInteger.ZERO, 0, 0), "zero duration is rejected");
    }

    private static void deterministicRecovery() {
        var batch = new Batch(72, 8, 20, 32);
        batch.stored = 7;
        check(!batch.start() && batch.inventory == 72 && batch.committed == 0,
                "power starvation retains the whole uncommitted order");
        batch.stored = 32;
        check(batch.start() && batch.inventory == 0 && batch.committed == 72, "funding starts exactly one material commit");
        batch.enabled = false;
        var paused = batch.snapshot();
        for (int retry = 0; retry < 16; retry++) batch.tick();
        check(batch.snapshot().equals(paused), "operator pause retains elapsed time and paid EU");
        batch = batch.reload();
        batch.available = false;
        batch.enabled = true;
        var unloaded = batch.snapshot();
        for (int retry = 0; retry < 16; retry++) batch.tick();
        check(batch.snapshot().equals(unloaded), "capability unload never advances an accepted batch");
        batch.available = true;
        batch.conditions = false;
        var rejected = batch.snapshot();
        for (int retry = 0; retry < 16; retry++) batch.tick();
        check(batch.snapshot().equals(rejected), "condition failure retains the committed output roll");
        batch.conditions = true;
        batch.computation = false;
        var noComputation = batch.snapshot();
        for (int retry = 0; retry < 16; retry++) batch.tick();
        check(batch.snapshot().equals(noComputation), "CWU starvation does not credit progress or paid EU");
        batch.computation = true;
        while (!batch.completed) {
            batch.stored = 32;
            batch.tick();
            batch = batch.reload();
        }
        check(batch.paid.equals(BigInteger.valueOf(160)) && batch.elapsed == 20,
                "save/reload after every tick preserves exact total payment");
        check(batch.committed == 72 && batch.pending == 72 && batch.delivered == 0,
                "one accepted order becomes one retained output batch");
        for (int retry = 0; retry < 32; retry++) {
            batch.deliver(false);
            check(batch.pending == 72 && batch.delivered == 0, "blocked output never retires paid products");
            batch = batch.reload();
        }
        batch.deliver(true);
        batch.deliver(true);
        check(batch.pending == 0 && batch.delivered == 72 && batch.committed == 72,
                "output reconnection delivers once without a second input commit");
    }

    private static void outputFairness() {
        var selector = new FairRecipeSelector<Delivery>();
        var pending = new ArrayList<Delivery>();
        for (int index = 0; index < 4096; index++) pending.add(new Delivery(new UUID(0, index).toString()));
        int sweep = (pending.size() + IvWorkBudget.DELIVERY_JOBS_PER_TICK - 1) / IvWorkBudget.DELIVERY_JOBS_PER_TICK;
        for (int tick = 0; tick < sweep; tick++) {
            var selected = IvWorkBudget.deliveries(pending, selector, delivery -> delivery.id);
            check(selected.size() <= IvWorkBudget.DELIVERY_JOBS_PER_TICK, "native delivery visits a bounded number of orders");
            int attempts = 0;
            for (var delivery : selected) {
                delivery.visits++;
                attempts += IvWorkBudget.TRANSFERS_PER_JOB;
            }
            check(attempts <= 64, "4096 blocked native orders cannot exceed 64 AE transfer attempts in one tick");
        }
        for (var delivery : pending)
            check(delivery.visits == 1, "every blocked native order receives its transfer window without UUID starvation");
        long transferred = 0;
        for (int tick = 0; tick < sweep; tick++) {
            var selected = IvWorkBudget.deliveries(pending, selector, delivery -> delivery.id);
            int attempts = 0;
            for (var delivery : selected) {
                int supplied = Math.min(delivery.pending, IvWorkBudget.TRANSFERS_PER_JOB);
                delivery.pending -= supplied; attempts += supplied; transferred += supplied;
            }
            pending.removeIf(delivery -> delivery.pending == 0);
            check(attempts <= 64, "restored output remains bounded while all quantities are accounted for");
        }
        check(pending.isEmpty() && transferred == 16384, "delivery restoration transfers every retained native product exactly once");
    }

    private static void randomizedRecovery() {
        var random = new Random(Long.getLong("gtl.nativeRecovery.seed", 0x20260930L));
        for (int trial = 0; trial < 256; trial++) {
            long operations = 1 + random.nextInt(1024);
            long eut = random.nextInt(10000);
            int duration = 1 + random.nextInt(120);
            var batch = new Batch(operations, eut, duration, Math.max(1, eut * 4));
            batch.stored = batch.inputPower;
            check(batch.start(), "generated native order starts with sufficient power");
            for (int transition = 0; transition < 512 && !batch.completed; transition++) {
                batch.enabled = random.nextInt(5) != 0;
                batch.available = random.nextInt(5) != 0;
                batch.conditions = random.nextInt(7) != 0;
                batch.computation = random.nextInt(7) != 0;
                batch.stored = random.nextBoolean() ? batch.inputPower : 0;
                var before = batch.snapshot();
                boolean permitted = batch.enabled && batch.available && batch.conditions && batch.computation
                        && (eut == 0 || batch.stored >= eut);
                batch.tick();
                if (!permitted) check(batch.snapshot().equals(before), "every rejected state transition is non-consuming");
                check(batch.paid.add(batch.energyLeft).equals(batch.totalEnergy), "payment conservation across random recovery events");
                check(batch.committed == operations && batch.inventory == 0, "random recovery never recommits materials");
                check(batch.elapsed <= duration, "random recovery cannot overrun duration");
                if (random.nextInt(4) == 0) batch = batch.reload();
            }
            batch.enabled = true; batch.available = true; batch.conditions = true; batch.computation = true;
            while (!batch.completed) {
                batch.stored = batch.inputPower;
                batch.tick();
            }
            check(batch.paid.equals(batch.totalEnergy), "restored resources always finish the original ledger");
            batch.deliver(true);
            check(batch.delivered == operations && batch.pending == 0, "restored output receives the exact accepted quantity");
        }
    }

    private static void check(boolean success, String message) {
        assertions++;
        if (!success) throw new AssertionError(message);
    }

    private static void expectFailure(Runnable action, String message) {
        boolean rejected = false;
        try { action.run(); }
        catch (IllegalArgumentException | IllegalStateException expected) { rejected = true; }
        check(rejected, message);
    }

    private record Snapshot(long inventory, long committed, long pending, long delivered, int elapsed,
                            BigInteger energyLeft, BigInteger paid, boolean completed) {}

    private static final class Delivery {
        private final String id;
        private int pending = 4, visits;
        private Delivery(String id) { this.id = id; }
    }

    private static final class Batch {
        private final long operations, eut, inputPower;
        private final int duration;
        private final BigInteger totalEnergy;
        private long inventory, committed, pending, delivered, stored;
        private int elapsed;
        private BigInteger energyLeft, paid = BigInteger.ZERO;
        private boolean enabled = true, available = true, conditions = true, computation = true, completed;

        private Batch(long operations, long eut, int duration, long inputPower) {
            this.operations = operations; this.eut = eut; this.duration = duration; this.inputPower = inputPower;
            inventory = operations; totalEnergy = BigInteger.valueOf(eut).multiply(BigInteger.valueOf(duration));
            energyLeft = totalEnergy;
        }

        private boolean start() {
            if (committed != 0 || !IvNativeRecoveryState.canPay(BigInteger.valueOf(eut), inputPower, stored)) return false;
            inventory -= operations; committed = operations;
            return true;
        }

        private void tick() {
            if (!IvNativeRecoveryState.enabled(true, enabled, false) || !available || !conditions || !computation || completed) return;
            BigInteger payment = BigInteger.valueOf(eut).min(energyLeft);
            if (!IvNativeRecoveryState.canPay(payment, inputPower, stored)) return;
            var next = IvNativeRecoveryState.paid(energyLeft, payment, elapsed, duration);
            stored -= payment.longValueExact(); paid = paid.add(payment);
            energyLeft = next.energyLeft(); elapsed = next.elapsed(); completed = next.complete();
            if (completed) pending = committed;
        }

        private void deliver(boolean connected) {
            if (!connected) return;
            delivered += pending; pending = 0;
        }

        private Snapshot snapshot() {
            return new Snapshot(inventory, committed, pending, delivered, elapsed, energyLeft, paid, completed);
        }

        private Batch reload() {
            var restored = new Batch(operations, eut, duration, inputPower);
            restored.inventory = inventory; restored.committed = committed; restored.pending = pending; restored.delivered = delivered;
            restored.elapsed = elapsed; restored.energyLeft = new BigInteger(energyLeft.toString());
            restored.paid = new BigInteger(paid.toString()); restored.completed = completed;
            restored.enabled = enabled; restored.available = available; restored.conditions = conditions;
            restored.computation = computation; restored.stored = stored;
            check(restored.snapshot().equals(snapshot()), "serialized ledger numbers preserve the runtime state");
            return restored;
        }
    }
}
