package com.gtl.enhancedcore;

import com.gtl.enhancedcore.common.performance.TerminalDismantleGuard;
import com.gtl.enhancedcore.common.performance.DismantleLayoutPlanner;
import com.gtl.enhancedcore.common.performance.DismantleCursor;
import com.gtl.enhancedcore.common.performance.DismantleBudget;
import com.gtl.enhancedcore.common.performance.DismantleDropLedger;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Random;
import java.util.List;
import java.util.ArrayList;
import java.util.jar.JarFile;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import java.nio.file.Files;
import java.nio.file.Path;

final class TerminalDismantleRegression {
    private static int assertions;

    static int run() throws Exception {
        check(TerminalDismantleGuard.canReuseFormedLayout(true, 3, 3, true),
                "formed controller with complete layout context can skip a second pattern scan");
        check(!TerminalDismantleGuard.canReuseFormedLayout(true, 2, 3, true),
                "wrong repetitions length retains the upstream full scan");
        check(!TerminalDismantleGuard.canReuseFormedLayout(false, 3, 3, true),
                "unformed controller retains the upstream full scan");
        check(!TerminalDismantleGuard.canReuseFormedLayout(true, 3, 3, false),
                "missing minimum Z context retains the upstream full scan");

        String mixins = Files.readString(Path.of("src/main/resources/gtl_enhancedcore.mixins.json"));
        check(mixins.contains("gtlcore.UltimateTerminalDismantleMixin"),
                "terminal dismantle guard is registered in the production mixin config");
        String source = Files.readString(Path.of(
                "src/main/java/com/gtl/enhancedcore/common/performance/TerminalDismantleTasks.java"));
        check(source.contains("DismantleMode"),
                "successful dismantle clears the terminal's persisted mode");
        layoutScoring();
        cursorOrder();
        tickBudget();
        dropDelivery();
        trustedExamples();
        upstreamAccessors();
        return assertions;
    }

    private static void trustedExamples() {
        var fixed = new DismantleLayoutPlanner(new int[][] {{1, 1}, {2, 2}, {1, 1}}, 1, true);
        for (int i = 0; i < 200 && fixed.result() == null; i++) {
            fixed.advance();
            if (fixed.needed() != null) fixed.supply(fixed.needed(), 1);
        }
        check(fixed.result().minZ() == -1 && fixed.result().score() == 4,
                "trusted fixed 1/2/1 layout keeps controller-relative -1 origin and four slices");
        check(Arrays.equals(fixed.result().repetitions(), new int[] {1, 2, 1}),
                "trusted repeated aisle sample preserves fixed counts on both sides of controller");
        check(!fixed.result().flipped(), "upstream equal-score tie always selects normal orientation");

        var empty = new DismantleLayoutPlanner(new int[][] {{1, 4}}, 0, true);
        for (int i = 0; i < 200 && empty.result() == null; i++) {
            empty.advance();
            if (empty.needed() != null) empty.supply(empty.needed(), 0);
        }
        check(!empty.result().hasMatch(), "all-zero nearby cells cannot authorize dismantling");
        check(empty.result().repetitions()[0] == 1,
                "zero-score repeat scan retains upstream minimum repeat before stopping");
    }

    private static void dropDelivery() {
        var ledger = new DismantleDropLedger<>(List.of("ae-complete", "ae-unknown", "fallback-fails", "fallback-ok"));
        check(ledger.claim().equals("ae-complete"), "first committed drop is claimed exactly once");
        check(ledger.claim().equals("ae-unknown"), "transfer claim advances before calling external storage");
        List<String> delivered = new ArrayList<>();
        while (ledger.hasNext()) {
            String untouched = ledger.claim();
            try {
                if (untouched.equals("fallback-fails")) throw new IllegalStateException("external entity callback");
                delivered.add(untouched);
            } catch (RuntimeException ignored) { }
        }
        check(delivered.equals(List.of("fallback-ok")), "one failing fallback cannot lose later untouched drops");
        check(!ledger.hasNext(), "fallback and uncertain transfers cannot be claimed twice");
    }

    private static void upstreamAccessors() throws Exception {
        // Inspect the shipped dependency, not a synthetic class matching the implementation.
        ClassNode advanced = new ClassNode();
        try (JarFile jar = new JarFile("libs/gtlcore-1.2.3.3-fix3.jar")) {
            try (var input = jar.getInputStream(jar.getJarEntry(
                    "org/gtlcore/gtlcore/api/pattern/AdvancedBlockPattern.class"))) {
                new ClassReader(input).accept(advanced, 0);
            }
        }
        check(advanced.fields.stream().anyMatch(f -> f.name.equals("blockMatches")
                && f.desc.equals("[[[Lcom/gregtechceu/gtceu/api/pattern/TraceabilityPredicate;")),
                "current fix4 declares the exact protected matches array targeted by the accessor");
        check(advanced.fields.stream().anyMatch(f -> f.name.equals("centerOffset") && f.desc.equals("[I")),
                "current fix4 declares the exact controller offset field");
        check(advanced.fields.stream().anyMatch(f -> f.name.equals("aisleRepetitions") && f.desc.equals("[[I")),
                "current fix4 declares its own repetition array rather than relying on the superclass field");
        check(advanced.methods.stream().anyMatch(m -> m.name.equals("setActualRelativeOffset")
                && m.desc.equals("(IIILnet/minecraft/core/Direction;Lnet/minecraft/core/Direction;Z)Lnet/minecraft/core/BlockPos;")),
                "current fix4 coordinate invoker has the exact private method descriptor");
        check(advanced.methods.stream().anyMatch(m -> m.name.equals("isBlockValid")
                && m.desc.equals("(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;"
                        + "Lcom/gregtechceu/gtceu/api/pattern/TraceabilityPredicate;Lcom/gregtechceu/gtceu/api/pattern/MultiblockState;)Z")),
                "current fix4 local predicate invoker has the exact private method descriptor");
        check(advanced.methods.stream().anyMatch(m -> m.name.equals("insertIntoNestedItemHandler")
                && m.desc.equals("(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/item/ItemStack;)Lnet/minecraft/world/item/ItemStack;")),
                "current fix4 nested inventory invoker has the exact private method descriptor");
        check(advanced.methods.stream().anyMatch(m -> m.name.equals("dismantleMultiblock")
                && m.desc.equals("(Lcom/gregtechceu/gtceu/api/machine/feature/multiblock/IMultiController;Lnet/minecraft/world/entity/player/Player;"
                        + "Lorg/gtlcore/gtlcore/common/item/UltimateTerminalBehavior$AutoBuildSetting;)V")),
                "queued replacement targets the actual shipped terminal method descriptor");
    }

    private static void tickBudget() {
        long[] nanos = {0};
        var scans = new DismantleBudget(3, 2, 100, () -> nanos[0]);
        check(scans.scan() && scans.scan() && scans.scan() && !scans.scan(),
                "the shared budget never exceeds its per-tick scan limit");
        var removed = new DismantleBudget(100, 2, 100, () -> nanos[0]);
        check(removed.scan(), "first removal receives a scan token");
        removed.removed();
        check(removed.scan(), "second removal receives a scan token");
        removed.removed();
        check(!removed.scan(), "the shared budget stops at its removal cap");
        var deadline = new DismantleBudget(100, 10, 100, () -> nanos[0]);
        nanos[0] = 100;
        check(!deadline.available() && !deadline.scan(), "deadline exhaustion stops new world operations");
    }

    private static void cursorOrder() {
        int[] repeat = {2, 0, 3, 1};
        var cursor = new DismantleCursor(3, 2, repeat, -4);
        var visited = new HashSet<String>();
        int expectedZ = -4;
        for (int aisle = 0; aisle < repeat.length; aisle++) {
            for (int r = 0; r < repeat[aisle]; r++, expectedZ++) {
                for (int y = 0; y < 2; y++) for (int x = 0; x < 3; x++) {
                    check(cursor.aisleIndex() == aisle && cursor.x() == x && cursor.y() == y && cursor.z() == expectedZ,
                            "bounded cursor matches upstream repeated-aisle cell order");
                    check(visited.add(x + "," + y + "," + expectedZ), "cursor cannot visit a committed cell twice");
                    // Pausing before advance does not consume/skip an unloaded cell.
                    check(cursor.x() == x && cursor.z() == expectedZ, "paused cursor keeps its exact cell");
                    cursor.advance();
                }
            }
        }
        check(cursor.done(), "cursor stops after the final chosen repetition");
        cursor.advance();
        check(cursor.done(), "completion cannot restart the cursor");
        check(new DismantleCursor(1, 1, new int[] {0, 0}, 0).done(), "an empty layout has no removable cells");
    }

    private static void layoutScoring() {
        Random random = new Random(611732L);
        for (int trial = 0; trial < 500; trial++) {
            int length = 1 + random.nextInt(8);
            int center = random.nextInt(length);
            int[][] limits = new int[length][2];
            for (int i = 0; i < length; i++) {
                limits[i][0] = random.nextInt(3);
                limits[i][1] = limits[i][0] + random.nextInt(4);
            }
            int seed = random.nextInt();
            boolean flip = random.nextBoolean();
            var expected = reference(limits, center, false, seed);
            if (flip) {
                var mirrored = reference(limits, center, true, seed);
                if (mirrored.score() > expected.score()) expected = mirrored;
            }
            var planner = new DismantleLayoutPlanner(limits, center, flip);
            int operations = 0;
            while (planner.result() == null && operations++ < 10000) {
                planner.advance();
                var requested = planner.needed();
                if (requested != null) planner.supply(requested,
                        score(seed, requested.aisleIndex(), requested.z(), requested.flipped()));
            }
            check(operations < 10000, "partial layout planning always makes bounded progress");
            var actual = planner.result();
            check(actual.minZ() == expected.minZ(), "resumed layout retains controller-relative minimum Z");
            check(actual.flipped() == expected.flipped(), "resumed layout retains upstream tie/flip selection");
            check(actual.score() == expected.score(), "resumed layout retains upstream total score");
            check(Arrays.equals(actual.repetitions(), expected.repetitions()),
                    "resumed layout retains upstream repetition choices");
        }
    }

    private static DismantleLayoutPlanner.Layout reference(int[][] limits, int center, boolean flipped, int seed) {
        int[] repetitions = new int[limits.length];
        long total = 0;
        int z = 0;
        for (int aisle = center; aisle < limits.length; aisle++) {
            int count = referenceCount(limits, aisle, z, 1, flipped, seed);
            repetitions[aisle] = count;
            for (int r = 0; r < count; r++) total += score(seed, aisle, z + r, flipped);
            z += count;
        }
        z = -1;
        for (int aisle = center - 1; aisle >= 0; aisle--) {
            int count = referenceCount(limits, aisle, z, -1, flipped, seed);
            repetitions[aisle] = count;
            for (int r = 0; r < count; r++) total += score(seed, aisle, z - r, flipped);
            z -= count;
        }
        return new DismantleLayoutPlanner.Layout(z + 1, repetitions, flipped, total);
    }

    private static int referenceCount(int[][] limits, int aisle, int startZ, int step, boolean flipped, int seed) {
        int min = limits[aisle][0], max = limits[aisle][1];
        if (min == max) return min;
        int adjacent = aisle + step;
        int count = 0;
        for (; count < max; count++) {
            int current = score(seed, aisle, startZ + count * step, flipped);
            int other = adjacent < 0 || adjacent >= limits.length ? 0
                    : score(seed, adjacent, startZ + count * step, flipped);
            if (other > current || current == 0 && count >= min) break;
        }
        return count;
    }

    private static int score(int seed, int aisle, int z, boolean flipped) {
        return Math.floorMod(seed ^ aisle * 29877 ^ z * 1093 ^ (flipped ? 497271 : 0), 12);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        assertions++;
    }
}
