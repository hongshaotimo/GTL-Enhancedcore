package com.gtl.enhancedcore;

import com.gtl.enhancedcore.common.recipe.iv.FairBudget;
import com.gtl.enhancedcore.common.recipe.iv.LongAllocation;
import java.util.*;
import java.math.BigInteger;
import com.gtl.enhancedcore.common.recipe.iv.IvRemainingTime;

/** Independent brute-force oracle for the allocator used by real isolated tasks. */
public final class IvIsolationRegression {
    private static int assertions;
    public static int run() {
        check(LongAllocation.plan(List.of(supply("iron", 10)), List.of(need(Set.of("iron"), 6), need(Set.of("iron", "copper"), 6))) == null,
                "overlapping material cannot spend the same ten ingots twice");
        var split = LongAllocation.plan(List.of(supply("iron", 6), supply("copper", 6)),
                List.of(need(Set.of("iron", "copper"), 6), need(Set.of("iron"), 6)));
        check(split != null && split.equals(Map.of("iron", 6L, "copper", 6L)), "flow reroutes broad ingredient for specific ingredient");
        check(LongAllocation.plan(List.of(new LongAllocation.Supply<>("iron", 100L, false)), List.of(need(Set.of("iron"), 1))) == null,
                "virtual stock never supplies consumable ingredients");
        var reusable = LongAllocation.plan(List.of(supply("iron", 90_000_000L), new LongAllocation.Supply<>("circuit2", 1L, false)),
                List.of(need(Set.of("iron"), 90_000_000L), new LongAllocation.Need<String>("circuit2"::equals, 1L, false)));
        check(reusable.equals(Map.of("iron", 90_000_000L)), "whole smart-multiplied order with virtual circuit retained");
        check(LongAllocation.plan(List.of(supply("iron", 5), supply("acid", 0)), List.of(need(Set.of("iron"), 5), need(Set.of("acid"), 1))) == null,
                "missing fluid rejects the whole snapshot");
        var huge = LongAllocation.plan(List.of(supply("iron", Long.MAX_VALUE)), List.of(need(Set.of("iron"), Long.MAX_VALUE)));
        check(huge.get("iron") == Long.MAX_VALUE, "long quantities do not truncate to int");
        Random random = new Random(0x1_20260918L);
        for (int trial = 0; trial < 1200; trial++) {
            int[] counts = {random.nextInt(4), random.nextInt(4), random.nextInt(4)};
            int n = 1 + random.nextInt(3); int[] masks = new int[n], demands = new int[n];
            List<LongAllocation.Need<String>> needs = new ArrayList<>();
            for (int j = 0; j < n; j++) {
                masks[j] = 1 + random.nextInt(7); demands[j] = random.nextInt(4);
                Set<String> allowed = new HashSet<>();
                for (int k = 0; k < 3; k++) if ((masks[j] & (1 << k)) != 0) allowed.add("k" + k);
                needs.add(need(allowed, demands[j]));
            }
            var actual = LongAllocation.plan(List.of(supply("k0", counts[0]), supply("k1", counts[1]), supply("k2", counts[2])), needs);
            boolean expected = brute(counts.clone(), masks, demands.clone(), 0);
            check((actual != null) == expected, "allocation agrees with exhaustive search " + trial);
            if (actual != null) {
                long sum = actual.values().stream().mapToLong(Long::longValue).sum();
                check(sum == Arrays.stream(demands).sum(), "all accepted demand accounted for");
                for (int k = 0; k < 3; k++) check(actual.getOrDefault("k" + k, 0L) <= counts[k], "no stock overdraw");
            }
        }
        for (int trial = 0; trial < 1200; trial++) {
            long cap = random.nextInt(2000); long[] wants = new long[1 + random.nextInt(17)];
            for (int i = 0; i < wants.length; i++) wants[i] = random.nextInt(1000);
            long[] grants = FairBudget.divide(cap, wants, random.nextInt());
            check(Arrays.stream(grants).sum() == Math.min(cap, Arrays.stream(wants).sum()), "shared budget is neither duplicated nor lost");
            for (int i = 0; i < wants.length; i++) check(grants[i] >= 0 && grants[i] <= wants[i], "per-task grant bounded");
        }
        long[] maximal = FairBudget.divide(Long.MAX_VALUE, new long[]{Long.MAX_VALUE, Long.MAX_VALUE}, 1);
        check(Math.addExact(maximal[0], maximal[1]) == Long.MAX_VALUE, "aggregate requests may exceed long without overflow");
        nativeThreadBudget();
        var retimed = IvRemainingTime.calculate(BigInteger.valueOf(1000), 100, 2, 1);
        check(retimed.duration() == 12 && retimed.eut() == 100, "reformation preserves unpaid energy and shortens remaining time");
        var slower = IvRemainingTime.calculate(BigInteger.valueOf(1000), 50, 2, 1);
        check(slower.duration() == 22 && slower.eut() == 50, "lower reformed supply does not overpay energy");
        // Two orders each requested 100 EU/t but share a 100 EU/t machine.
        BigInteger unpaid = BigInteger.valueOf(10_000);
        int elapsed = 0, duration = 100;
        while (unpaid.signum() > 0) {
            long grant = Math.min(50, unpaid.longValueExact());
            duration = IvRemainingTime.estimatedDuration(unpaid, grant, elapsed, 1);
            unpaid = unpaid.subtract(BigInteger.valueOf(grant)); elapsed++;
            check(duration == 200, "shared supply reports actual 200 tick duration, not nominal 100 ticks");
            if (elapsed == 20) check(elapsed == 20 && duration - elapsed == 180,
                    "twenty paid processing ticks display one elapsed second even at half power");
        }
        check(elapsed == duration && elapsed == 200, "estimated finish agrees with fully paid energy");
        check(IvRemainingTime.estimatedDuration(BigInteger.valueOf(9_000), 100, 20, 1) == 110,
                "freed supply changes remaining estimate without changing past elapsed ticks");
        check(IvRemainingTime.estimatedDuration(BigInteger.ONE, 100, 0, 20) == 20,
                "cheap recipes retain their minimum processing time");
        check(IvRemainingTime.estimatedDuration(BigInteger.valueOf(Long.MAX_VALUE), 1, 20, 1) == Integer.MAX_VALUE,
                "huge remaining time saturates display without cancelling the energy debt");
        return assertions;
    }
    /**
     * 原生机器超级总成模式线程预算：新增 128，GTLCore 已有的 64 另行相加，再叠加 Ω。
     * 穷举驱动值边界，覆盖负数、0、整数溢出与 Integer.MAX_VALUE 饱和。
     */
    private static void nativeThreadBudget() {
        check(com.gtl.enhancedcore.common.recipe.iv.IvMachineScope.NATIVE_CROSS_RECIPE_THREADS == 128,
                "native isolation adds 128 cross-recipe threads");
        check(com.gtl.enhancedcore.common.recipe.iv.IvMachineScope.UPSTREAM_MULTIPLE_RECIPE_THREADS == 64,
                "GTLCore multiple-recipe machines retain their original 64 threads");
        // 无天球：恒为 128
        check(com.gtl.enhancedcore.common.recipe.iv.IvMachineScope.nativeThreads(0) == 128,
                "native machine without modifier keeps 128 threads");
        // 叠加而不是覆盖：不同 Ω 强度都必须线性加上去
        for (int modifier : new int[]{1, 2, 64, 128, 512, 1000, 65536}) {
            int expected = 128 + modifier;
            check(com.gtl.enhancedcore.common.recipe.iv.IvMachineScope.nativeThreads(modifier) == expected,
                    "modifier threads are added on top of the native baseline: " + modifier);
            check(com.gtl.enhancedcore.common.recipe.iv.IvMachineScope.nativeThreads(modifier)
                            > com.gtl.enhancedcore.common.recipe.iv.IvMachineScope.nativeThreads(0),
                    "adding the modifier never lowers the thread count: " + modifier);
        }
        // 负值不得削减基准
        check(com.gtl.enhancedcore.common.recipe.iv.IvMachineScope.nativeThreads(-1) == 128,
                "negative modifier cannot reduce the native baseline");
        check(com.gtl.enhancedcore.common.recipe.iv.IvMachineScope.nativeThreads(Integer.MIN_VALUE) == 128,
                "extreme negative modifier cannot reduce the native baseline");
        // 溢出饱和：不得回绕成负数或小值
        check(com.gtl.enhancedcore.common.recipe.iv.IvMachineScope.nativeThreads(Integer.MAX_VALUE) == Integer.MAX_VALUE,
                "huge modifier saturates instead of overflowing");
        check(com.gtl.enhancedcore.common.recipe.iv.IvMachineScope.nativeThreads(Integer.MAX_VALUE - 128) == Integer.MAX_VALUE,
                "modifier near the int ceiling still saturates safely");
        // 已有 64 条的机器不能被新增 128 条覆盖，也不能把 Ω 算成替代值。
        check(com.gtl.enhancedcore.common.recipe.iv.IvMachineScope.batchThreads(0) == 192,
                "original 64 threads and isolation's 128 threads are additive");
        check(com.gtl.enhancedcore.common.recipe.iv.IvMachineScope.batchThreads(64) == 256,
                "batching machines add the modifier on top of both baselines");
        check(com.gtl.enhancedcore.common.recipe.iv.IvMachineScope.batchThreads(-1) == 192,
                "negative modifier cannot reduce the combined baseline");
        check(com.gtl.enhancedcore.common.recipe.iv.IvMachineScope.batchThreads(Integer.MAX_VALUE) == Integer.MAX_VALUE,
                "combined baseline and huge modifier saturate safely");
        // 预算不溢出：128 线程要与并行相乘仍落在 long 内
        for (int parallel : new int[]{1, 64, 1024, 65536, Integer.MAX_VALUE}) {
            long budget = Math.multiplyExact((long)parallel,
                    com.gtl.enhancedcore.common.recipe.iv.IvMachineScope.nativeThreads(1024));
            check(budget > 0, "native budget stays positive: parallel=" + parallel);
        }
    }

    private static LongAllocation.Supply<String> supply(String key, long amount) { return new LongAllocation.Supply<>(key, amount, true); }    private static LongAllocation.Need<String> need(Set<String> keys, long amount) { return new LongAllocation.Need<>(keys::contains, amount, true); }
    private static boolean brute(int[] stock, int[] masks, int[] demands, int index) {
        if (index == demands.length) return true;
        if (demands[index] == 0) return brute(stock, masks, demands, index + 1);
        for (int k = 0; k < stock.length; k++) if (stock[k] > 0 && (masks[index] & (1 << k)) != 0) {
            stock[k]--; demands[index]--;
            boolean result = brute(stock, masks, demands, index);
            stock[k]++; demands[index]++;
            if (result) return true;
        }
        return false;
    }
    private static void check(boolean value, String message) {
        assertions++;
        if (!value) throw new AssertionError(message);
    }
}
