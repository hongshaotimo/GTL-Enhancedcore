package com.gtl.enhancedcore;

import com.gtl.enhancedcore.common.performance.ThreadBudget;
import com.gtl.enhancedcore.common.performance.ThreadBudget.Plan;
import java.util.Random;

final class ThreadBudgetRegression {
    private static final long GIB = 1L << 30;
    private static int assertions;

    static int run() {
        assertions = 0;
        int[] sampleCpus = {Integer.MIN_VALUE, -1, 0, 1, 2, 4, 8, 16, 32, 128, Integer.MAX_VALUE};
        int[] sampleBackground = {1, 1, 1, 1, 1, 1, 5, 13, 24, 24, 24};
        int[] sampleBuilder = {1, 1, 1, 1, 1, 1, 2, 4, 8, 8, 8};
        for (int i = 0; i < sampleCpus.length; i++) {
            expect(sampleCpus[i], 15 * GIB, sampleBackground[i], sampleBuilder[i]);
            expect(sampleCpus[i], Long.MAX_VALUE, sampleBackground[i], sampleBuilder[i]);
        }

        for (int cpus : new int[]{16, 32, 128, Integer.MAX_VALUE}) {
            for (long heap : new long[]{Long.MIN_VALUE, -1, 0, 1, 4 * GIB - 1, 4 * GIB}) {
                expect(cpus, heap, 4, 2);
            }
            for (long heap : new long[]{4 * GIB + 1, 6 * GIB - 1, 6 * GIB}) {
                expect(cpus, heap, 6, 3);
            }
            expect(cpus, 6 * GIB + 1, cpus == 16 ? 13 : 24, cpus == 16 ? 4 : 8);
        }
        expect(8, 4 * GIB, 4, 2);
        expect(8, 4 * GIB + 1, 5, 2);
        expect(26, Long.MAX_VALUE, 23, 6);
        expect(27, Long.MAX_VALUE, 24, 7);
        expect(28, Long.MAX_VALUE, 24, 7);
        expect(4, Long.MIN_VALUE, 1, 1);
        expect(2, Long.MIN_VALUE, 1, 1);

        int[] boundaryCpus = {Integer.MIN_VALUE, Integer.MIN_VALUE + 1, -128, -1, 0, 1, 2, 3,
                4, 5, 7, 8, 9, 15, 16, 17, 26, 27, 28, 31, 32, 33, 127, 128, 129,
                Integer.MAX_VALUE - 1, Integer.MAX_VALUE};
        long[] boundaryHeaps = {Long.MIN_VALUE, Long.MIN_VALUE + 1, -1, 0, 1, GIB,
                4 * GIB - 1, 4 * GIB, 4 * GIB + 1, 6 * GIB - 1, 6 * GIB, 6 * GIB + 1,
                15 * GIB, Long.MAX_VALUE - 1, Long.MAX_VALUE};
        for (int cpus : boundaryCpus) {
            for (long heap : boundaryHeaps) {
                verifyLimits(cpus, heap);
            }
        }

        Random random = new Random(0x544852454144L);
        for (int i = 0; i < 4096; i++) {
            verifyLimits(random.nextInt(), random.nextLong());
            int cpus = random.nextInt(273) - 16;
            long heap = (i % 2 == 0 ? 4 : 6) * GIB + random.nextInt(4097) - 2048;
            verifyLimits(cpus, heap);
        }
        return assertions;
    }

    private static void expect(int cpus, long heap, int background, int builder) {
        Plan actual = ThreadBudget.plan(cpus, heap);
        check(actual.equals(new Plan(background, builder)),
                "expected " + background + "+" + builder + ", got " + actual
                        + " for cpus=" + cpus + ", heap=" + heap);
    }

    private static void verifyLimits(int cpus, long heap) {
        Plan plan = ThreadBudget.plan(cpus, heap);
        String context = " for cpus=" + cpus + ", heap=" + heap + ": " + plan;
        check(plan.backgroundThreads() >= 1 && plan.backgroundThreads() <= 24,
                "background bounds" + context);
        check(plan.builderThreads() >= 1 && plan.builderThreads() <= 8,
                "builder bounds" + context);
        check(plan.equals(ThreadBudget.plan(cpus, heap)), "deterministic result" + context);
        if (cpus >= 4) {
            check(plan.backgroundThreads() <= cpus - 3,
                    "background reserves at least three logical CPUs" + context);
        }
        if (cpus <= 1) {
            check(plan.equals(new Plan(1, 1)), "nonpositive CPU fallback" + context);
        }
        if (heap <= 4 * GIB) {
            check(plan.backgroundThreads() <= 4, "low heap background cap" + context);
            check(plan.builderThreads() <= 2, "low heap builder cap" + context);
        } else if (heap <= 6 * GIB) {
            check(plan.backgroundThreads() <= 6, "medium heap background cap" + context);
            check(plan.builderThreads() <= 3, "medium heap builder cap" + context);
        }
        Plan unlimitedHeap = ThreadBudget.plan(cpus, Long.MAX_VALUE);
        check(plan.backgroundThreads() <= unlimitedHeap.backgroundThreads()
                        && plan.builderThreads() <= unlimitedHeap.builderThreads(),
                "memory caps never increase CPU allocations" + context);
        if (heap < Long.MAX_VALUE) {
            Plan nextHeap = ThreadBudget.plan(cpus, heap + 1);
            check(nextHeap.backgroundThreads() >= plan.backgroundThreads()
                            && nextHeap.builderThreads() >= plan.builderThreads(),
                    "more heap never reduces worker counts" + context);
        }
    }

    private static void check(boolean valid, String message) {
        if (!valid) throw new AssertionError(message);
        assertions++;
    }
}
