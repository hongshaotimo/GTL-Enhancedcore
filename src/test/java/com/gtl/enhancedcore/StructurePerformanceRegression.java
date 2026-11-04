package com.gtl.enhancedcore;

import com.gtl.enhancedcore.common.performance.structure.StructureChunkMembership;
import com.gtl.enhancedcore.common.performance.structure.StructureSearchBackoff;
import com.gtl.enhancedcore.common.performance.structure.StructureSearchWatches;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.Random;

/** Scheduling and indexing invariants, independent of Minecraft world initialization. */
public final class StructurePerformanceRegression {
    private static int assertions;
    private StructurePerformanceRegression() {}

    public static int run() {
        assertions = 0;
        retries();
        pendingAndInvalidation();
        interruptedFormationRecovery();
        wakeBoundsAndCapacity();
        reverseChunkMembership();
        return assertions;
    }

    public static void main(String[] args) { System.out.println("Structure performance checks: " + run()); }

    private static void retries() {
        StructureSearchBackoff budget = new StructureSearchBackoff();
        long now = 0;
        for (int seconds : new int[]{2, 4, 8, 10, 10}) {
            long token = budget.begin(now);
            check(token >= 0, "failed controller becomes eligible at its deadline");
            budget.failed(now, token);
            now += seconds * 1_000_000_000L;
            check(budget.begin(now - 1) == -1, "failure delay is not bypassed by scheduler polls");
        }
        check(budget.failures() == 4, "failure backoff saturates without counter overflow");
        budget.wake();
        check(budget.begin(now - 100) >= 0, "relevant change wakes before the old deadline");
    }

    private static void pendingAndInvalidation() {
        StructureSearchBackoff budget = new StructureSearchBackoff();
        long token = budget.begin(0);
        check(budget.begin(0) == -1, "duplicate asynchronous entries cannot enter one controller twice");
        check(budget.queue(token), "successful attempt reserves one main-thread completion");
        check(!budget.queue(token), "only one main-thread completion can be queued");
        check(budget.begin(1_000_000_000L) == -1, "queued formation prevents repeated full async scans");
        budget.wake();
        check(!budget.current(token), "block/part changes invalidate queued async result");
        budget.completed();
        long newer = budget.begin(0);
        check(newer > token, "woken state has a distinct generation");
        budget.wake();
        budget.failed(0, newer);
        check(budget.begin(0) > newer, "old failed scan cannot restore a delay after a change");
        budget.lockBusy();
        token = budget.begin(0);
        budget.unload();
        check(!budget.current(token) && budget.begin(Long.MAX_VALUE) == -1,
                "unloaded controller cannot apply or start a formation");
        budget.load();
        check(budget.begin(0) > token, "reload wakes with a fresh generation");
    }

    private static void interruptedFormationRecovery() {
        var budget = new StructureSearchBackoff();
        var watches = new StructureSearchWatches();
        budget.load();
        check(budget.requiresFormation(), "loaded persisted formed controller still requires capability binding");
        long token = budget.begin(0);
        watches.watch(budget, new StructureSearchWatches.Bounds(0, 0, 0, 47, 10, 0), token);
        check(budget.queue(token), "recovering controller reserves its successful async match");

        // A maximum repetition bound can include cells outside the current native structure cache.
        // Such a change wakes our watcher without a native callback rebuilding the capabilities.
        watches.blockChanged(32, 0, 0);
        check(!budget.current(token), "watched unused cell invalidates the queued formation token");
        check(budget.begin(0) == -1, "stale confirmation retains its reservation until completion");
        budget.completed();
        check(budget.requiresFormation(), "discarding a stale match cannot mark capabilities as rebound");
        long retry = budget.begin(0);
        check(retry > token, "interrupted formed recovery can retry after its stale confirmation completes");
        check(budget.queue(retry), "recovery retry can reserve a new confirmation");

        watches.chunkChanged(2, 0);
        check(!budget.current(retry), "watched chunk event also invalidates a queued recovery");
        budget.completed();
        check(budget.requiresFormation(), "chunk wake and callback cleanup preserve required binding");
        long manual = budget.begin(0);
        check(manual > retry && budget.queue(manual), "recovery remains eligible after repeated event invalidation");
        budget.formationCompleted();
        check(!budget.requiresFormation(), "native manual formation clears the outstanding binding requirement");
        check(!budget.current(manual), "native manual formation supersedes a queued asynchronous result");
        budget.completed();
        check(!budget.requiresFormation(), "stale callback cleanup cannot undo successful native formation");

        budget.unload();
        budget.load();
        check(budget.requiresFormation(), "a later load requires rebinding even after a prior successful formation");
        token = budget.begin(0);
        budget.failed(0, token);
        check(budget.requiresFormation(), "failed matching never satisfies the formation requirement");
    }

    private static void wakeBoundsAndCapacity() {
        var watches = new StructureSearchWatches();
        var budget = new StructureSearchBackoff();
        var bounds = new StructureSearchWatches.Bounds(-17, -64, -33, 31, 319, 15);
        watches.watch(budget, bounds);
        long token = budget.begin(0);
        budget.lockBusy();
        watches.blockChanged(32, 0, 0);
        watches.blockChanged(0, 320, 0);
        check(budget.current(token), "unrelated blocks including vertical neighbors do not wake search");
        watches.blockChanged(-17, -64, -33);
        check(!budget.current(token), "negative boundary position wakes matching search");
        token = budget.begin(0); budget.lockBusy();
        watches.chunkChanged(-2, -3);
        check(!budget.current(token), "chunk load/unload touching bounds wakes search");
        int references = watches.referenceCount();
        watches.watch(budget, bounds);
        check(watches.referenceCount() == references, "repeated scans do not accumulate watch memberships");
        watches.remove(budget);
        check(watches.referenceCount() == 0 && watches.searchCount() == 0, "formed/unloaded controller releases watches");
        budget.unload();
        watches.watch(budget, bounds, token);
        check(watches.searchCount() == 0, "late async attempt cannot recreate a watcher after controller unload");

        var huge = new StructureSearchBackoff();
        watches.watch(huge, new StructureSearchWatches.Bounds(-1_000_000, 0, -1_000_000, 1_000_000, 1, 1_000_000));
        check(watches.referenceCount() == 0, "huge bounds use one overflow record without enumerating chunks");
        token = huge.begin(0); huge.lockBusy();
        watches.blockChanged(900_000, 1, 900_000);
        check(!huge.current(token), "overflow bounds still wake exact relevant blocks");
        for (int i = 0; i < 10; i++) {
            watches.watch(new StructureSearchBackoff(), new StructureSearchWatches.Bounds(0, 0, 0, 511, 1, 511));
        }
        check(watches.referenceCount() == StructureSearchWatches.MAX_CHUNK_REFERENCES,
                "per-level accelerated memberships stop at the fixed capacity");
        watches.clear();
        check(watches.referenceCount() == 0 && watches.searchCount() == 0,
                "level unload releases both indexed and overflow records");
    }

    private static void reverseChunkMembership() {
        var index = new StructureChunkMembership<Object>();
        var first = new Object(); var second = new Object();
        var positions = new LongOpenHashSet(); var expected = new LongOpenHashSet();
        Random random = new Random(4909);
        for (int i = 0; i < 20_000; i++) {
            int x = random.nextInt(60_000_001) - 30_000_000;
            int y = random.nextInt(384) - 64;
            int z = random.nextInt(60_000_001) - 30_000_000;
            long position = ((x & 0x3ffffffL) << 38) | ((z & 0x3ffffffL) << 12) | (y & 0xfffL);
            long chunk = ((x >> 4) & 0xffffffffL) | (((z >> 4) & 0xffffffffL) << 32);
            check(StructureChunkMembership.chunkOfBlock(position) == chunk,
                    "packed block to chunk conversion agrees across negative coordinates and height");
            positions.add(position); expected.add(chunk);
        }
        check(index.add(first, positions.iterator()).equals(expected), "index contains each unique chunk once");
        index.add(second, positions.iterator());
        check(index.remove(first).equals(expected), "removal returns its precise prior chunk membership");
        check(index.remove(first) == null, "repeated removal is empty");
        check(index.remove(second).equals(expected), "removing one controller preserves the other's membership");
    }

    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }
}
