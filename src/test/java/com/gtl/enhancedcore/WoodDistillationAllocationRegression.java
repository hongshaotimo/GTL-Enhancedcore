package com.gtl.enhancedcore;

import com.gtl.enhancedcore.common.recipe.WoodDistillationAllocation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Shared log/fluid admission; executes the same two-pass allocator used by the mutable machine. */
final class WoodDistillationAllocationRegression {
    private static int checks;
    private static final Process NITROGEN = new Process("nitrogen", 1000, 8);
    private static final Process STEAM = new Process("steam", 1000, 4);
    private static final Process WATER = new Process("water", 6000, 6);

    static int run() {
        checks = 0;
        var processes = List.of(NITROGEN, STEAM, WATER);
        var oldGreedy = new Stock(48, 3);
        long oldRemaining = 15;
        for (Process process : processes) oldRemaining -= oldGreedy.admit(process, oldRemaining);
        check(oldGreedy.operations.size() == 1 && oldGreedy.operations.get("nitrogen") == 3,
                "The original ADD greedy allocation reproduces one-process starvation with shared logs");
        for (var order : permutations(processes)) {
            // Both the no-Ω budget and a larger Ω budget must start all three processes with
            // exactly three sets of logs. A greedy first candidate consumes all three instead.
            for (long budget : new long[]{15, 15 + 128, 15L * 64}) {
                var scarce = new Stock(48, 3);
                long admitted = WoodDistillationAllocation.allocate(order, budget, scarce::admit);
                check(admitted == 3 && scarce.logs == 0, "Three shared-log operations are paid exactly once");
                for (Process process : processes)
                    check(scarce.operations.getOrDefault(process.name, 0L) == 1,
                            "Every supplied process enters the same batch before bulk allocation");
                scarce.verifyConservation();

                var abundant = new Stock(16 * budget, budget);
                long used = WoodDistillationAllocation.allocate(order, budget, abundant::admit);
                check(used == budget && abundant.logs == 0, "All ADD budget remains usable");
                for (Process process : processes)
                    check(abundant.operations.getOrDefault(process.name, 0L) >= 1,
                            "Omega and parallel hatches cannot starve a supplied process");
                abundant.verifyConservation();
            }
        }

        var missingSteam = new Stock(48, 3);
        missingSteam.fluids.put("steam", 0L);
        missingSteam.originalFluids.put("steam", 0L);
        check(WoodDistillationAllocation.allocate(processes, 15, missingSteam::admit) == 3,
                "Failed candidates leave their allowance for supplied candidates");
        check(!missingSteam.operations.containsKey("steam"), "A dry fluid port cannot create output");
        missingSteam.verifyConservation();

        var locked = new Stock(240, 15);
        check(WoodDistillationAllocation.allocate(List.of(WATER), 15, locked::admit) == 15,
                "ADD's singleton recipe-lock candidate preserves full throughput");
        check(locked.operations.size() == 1 && locked.operations.get("water") == 15,
                "Locked batches never admit a second recipe");
        locked.verifyConservation();

        var tooLittle = new Stock(15, 100);
        check(WoodDistillationAllocation.allocate(processes, 15, tooLittle::admit) == 0,
                "Partial shared input cannot create an operation");
        check(tooLittle.logs == 15 && tooLittle.operations.isEmpty(), "Failed admission consumes nothing");
        int[] calls = {0};
        long huge = (long) Integer.MAX_VALUE * Integer.MAX_VALUE;
        check(WoodDistillationAllocation.allocate(processes, huge, (process, limit) -> {
            calls[0]++;
            return 0;
        }) == 0 && calls[0] == 6, "Maximal Omega/hatch budget still needs at most two visits per candidate");
        check(WoodDistillationAllocation.allocate(processes, 0, (process, limit) -> {
            throw new AssertionError("Empty budget must not visit inputs");
        }) == 0, "Zero budget cannot touch stock");
        return checks;
    }

    private record Process(String name, long fluidPerOperation, long outputPerOperation) {}

    private static final class Stock {
        long logs;
        final long originalLogs;
        final Map<String, Long> fluids = new HashMap<>();
        final Map<String, Long> originalFluids = new HashMap<>();
        final Map<String, Long> operations = new HashMap<>();
        final Map<String, Long> outputs = new HashMap<>();

        Stock(long logs, long fluidOperations) {
            this.logs = originalLogs = logs;
            for (Process process : List.of(NITROGEN, STEAM, WATER))
                fluids.put(process.name, process.fluidPerOperation * fluidOperations);
            originalFluids.putAll(fluids);
        }

        long admit(Process process, Long allowance) {
            long parallel = Math.min(allowance, Math.min(logs / 16,
                    fluids.get(process.name) / process.fluidPerOperation));
            if (parallel == 0) return 0;
            logs -= 16 * parallel;
            fluids.merge(process.name, -process.fluidPerOperation * parallel, Long::sum);
            operations.merge(process.name, parallel, Long::sum);
            outputs.merge(process.name, process.outputPerOperation * parallel, Long::sum);
            return parallel;
        }

        void verifyConservation() {
            long total = operations.values().stream().mapToLong(Long::longValue).sum();
            check(originalLogs - logs == 16 * total, "Shared log debit matches every admitted operation");
            for (Process process : List.of(NITROGEN, STEAM, WATER)) {
                long count = operations.getOrDefault(process.name, 0L);
                check(originalFluids.get(process.name) - fluids.get(process.name)
                                == process.fluidPerOperation * count,
                        "Fluid debit matches only its own admitted process");
                check(outputs.getOrDefault(process.name, 0L) == process.outputPerOperation * count,
                        "Outputs correspond exactly to paid inputs");
            }
        }
    }

    private static List<List<Process>> permutations(List<Process> processes) {
        var result = new ArrayList<List<Process>>();
        for (Process a : processes) for (Process b : processes) for (Process c : processes)
            if (a != b && a != c && b != c) result.add(List.of(a, b, c));
        return result;
    }

    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
        checks++;
    }
}
