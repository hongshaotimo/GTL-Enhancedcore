package com.gtl.enhancedcore;

import com.gtl.enhancedcore.common.config.ConfigFiles;
import com.gtl.enhancedcore.common.config.OwnedConfigEntries;
import com.gtl.enhancedcore.common.config.SingularityRecipeConfig;
import com.gtl.enhancedcore.common.machine.hatch.CellSlotScan;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

public final class ModuleStabilityRegression {
    private static int assertions;

    private ModuleStabilityRegression() {}

    public static int run() throws IOException {
        return run(0x4D4F44554C45L);
    }

    private static int run(long seed) throws IOException {
        assertions = 0;
        ownershipReplacement();
        ownershipBoundaries();
        repeatedOwnershipReloads(new Random(seed));
        slotBoundaries();
        sequentialSlotScans();
        randomSlotScans(new Random(seed ^ 0x534C4F54L));
        countBoundaries();
        atomicFileFailures();
        return assertions;
    }

    public static void main(String[] args) throws IOException {
        long seed = args.length == 0 ? 0x4D4F44554C45L : Long.parseLong(args[0]);
        System.out.println("ModuleStabilityRegression passed: " + run(seed)
                + " assertions, seed=" + seed + " (dependency-light; Minecraft runtime not exercised)");
    }

    private static void ownershipReplacement() {
        Set<String> legacyAdded = new HashSet<>();
        List<String> legacyEntries = new ArrayList<>(List.of("preset"));
        legacyReconcile(legacyEntries, Set.of("injected"), legacyAdded);
        List<String> legacyReloaded = new ArrayList<>(legacyEntries);
        legacyReconcile(legacyReloaded, Set.of(), legacyAdded);
        check(!legacyReloaded.contains("injected"), "baseline deletes an explicitly configured reload entry");

        OwnedConfigEntries<String> owned = new OwnedConfigEntries<>();
        List<String> entries = new ArrayList<>(List.of("preset"));
        owned.reconcile(entries, Set.of("injected"));
        List<String> reloaded = new ArrayList<>(entries);
        check(entries.equals(reloaded) && entries != reloaded, "reload replaces identity without changing contents");
        owned.reconcile(reloaded, Set.of());
        check(reloaded.equals(List.of("preset", "injected")), "new configuration owns its explicitly present entry");
        check(entries.equals(List.of("preset", "injected")), "retired list is not modified during reconciliation");
    }

    private static void ownershipBoundaries() {
        OwnedConfigEntries<String> owned = new OwnedConfigEntries<>();
        List<String> entries = new ArrayList<>(List.of("user", "user"));
        owned.reconcile(entries, Set.of("user", "added"));
        owned.reconcile(entries, Set.of("user", "added"));
        check(entries.equals(List.of("user", "user", "added")), "repeated injection neither duplicates nor rewrites user entries");
        owned.reconcile(entries, Set.of());
        check(entries.equals(List.of("user", "user")), "only owned contributions are removed in the same list");

        List<String> replaced = new ArrayList<>();
        owned.reconcile(replaced, Set.of("added"));
        owned.reconcile(replaced, Set.of());
        check(replaced.isEmpty(), "contributions added to a replacement list remain removable");
        Set<String> nullable = new HashSet<>();
        nullable.add(null);
        nullable.add("valid");
        owned.reconcile(replaced, nullable);
        check(replaced.equals(List.of("valid")), "missing registry values are not injected");

        OwnedConfigEntries<String> fluidOwned = new OwnedConfigEntries<>();
        List<String> fluidEntries = new ArrayList<>(List.of("valid"));
        fluidOwned.reconcile(fluidEntries, Set.of("valid"));
        owned.reconcile(replaced, Set.of());
        fluidOwned.reconcile(fluidEntries, Set.of());
        check(replaced.isEmpty() && fluidEntries.equals(List.of("valid")), "item and fluid ownership are independent");
    }

    private static void repeatedOwnershipReloads(Random random) {
        OwnedConfigEntries<String> owned = new OwnedConfigEntries<>();
        for (int cycle = 0; cycle < 2048; cycle++) {
            Set<String> configured = randomMaterials(random);
            Set<String> wanted = randomMaterials(random);
            List<String> entries = new ArrayList<>(configured);
            owned.reconcile(entries, wanted);
            verifyMaterials(entries, configured, wanted, "initial reconciliation " + cycle);
            owned.reconcile(entries, wanted);
            verifyMaterials(entries, configured, wanted, "idempotent reconciliation " + cycle);

            Set<String> changedWanted = randomMaterials(random);
            owned.reconcile(entries, changedWanted);
            verifyMaterials(entries, configured, changedWanted, "same-list removal " + cycle);

            Set<String> reconfigured = randomMaterials(random);
            List<String> reloaded = new ArrayList<>(reconfigured);
            owned.reconcile(reloaded, changedWanted);
            verifyMaterials(reloaded, reconfigured, changedWanted, "identity replacement " + cycle);
            owned.reconcile(reloaded, Set.of());
            verifyMaterials(reloaded, reconfigured, Set.of(), "owned-only removal after reload " + cycle);
        }
    }

    private static Set<String> randomMaterials(Random random) {
        Set<String> entries = new HashSet<>();
        for (int index = 0; index < 24; index++) {
            if (random.nextBoolean()) entries.add("material:" + index);
        }
        return entries;
    }

    private static void verifyMaterials(List<String> entries, Set<String> configured,
                                        Set<String> wanted, String context) {
        Set<String> expected = new HashSet<>(configured);
        expected.addAll(wanted);
        check(new HashSet<>(entries).equals(expected), context + " preserves the correct material union");
        check(entries.size() == expected.size(), context + " never adds duplicate materials");
    }

    private static void legacyReconcile(List<String> entries, Set<String> wanted, Set<String> added) {
        entries.removeIf(entry -> added.contains(entry) && !wanted.contains(entry));
        added.retainAll(wanted);
        for (String entry : wanted) {
            if (!entries.contains(entry)) {
                entries.add(entry);
                added.add(entry);
            }
        }
    }

    private static void slotBoundaries() {
        AtomicInteger queries = new AtomicInteger();
        check(CellSlotScan.nextEmpty(0, 0, slot -> {
            queries.incrementAndGet();
            return true;
        }) == -1 && queries.get() == 0, "empty inventory has no slot reads");
        check(CellSlotScan.nextEmpty(630, 630, slot -> {
            queries.incrementAndGet();
            return true;
        }) == -1 && queries.get() == 0, "end cursor does not read beyond inventory");
        check(CellSlotScan.nextEmpty(630, Integer.MAX_VALUE, slot -> true) == -1, "oversized cursor is bounded");
        check(CellSlotScan.nextEmpty(1, -1, slot -> true) == 0, "negative cursor starts at the first slot");
        check(CellSlotScan.nextEmpty(630, 0, slot -> slot == 629) == 629, "last-page final slot is found");
        check(CellSlotScan.nextEmpty(630, 0, slot -> false) == -1, "full drive cannot select a target slot");
        check(CellSlotScan.nextEmpty(630, 315, slot -> slot == 314) == -1, "cursor does not revisit occupied prefixes");
        check(CellSlotScan.nextEmpty(630, 100, slot -> slot == 200) == 200, "rejected extraction can retain the same free slot");
        check(CellSlotScan.nextEmpty(630, 100, slot -> slot == 200) == 200, "retry does not advance before successful insertion");
    }

    private static void sequentialSlotScans() {
        boolean[] legacyOccupied = new boolean[630];
        int legacyReads = 0;
        for (int insertion = 0; insertion < legacyOccupied.length; insertion++) {
            for (int slot = 0; slot < legacyOccupied.length; slot++) {
                legacyReads++;
                if (!legacyOccupied[slot]) {
                    legacyOccupied[slot] = true;
                    break;
                }
            }
        }
        check(legacyReads == 198765, "baseline filling 630 cells repeatedly scans 198765 prefix slots");
        boolean[] occupied = new boolean[630];
        AtomicInteger queries = new AtomicInteger();
        int cursor = 0;
        for (int expectedSlot = 0; expectedSlot < occupied.length; expectedSlot++) {
            int targetSlot = CellSlotScan.nextEmpty(occupied.length, cursor, slot -> {
                queries.incrementAndGet();
                return !occupied[slot];
            });
            check(targetSlot == expectedSlot, "sequential fill selects each drive slot exactly once");
            occupied[targetSlot] = true;
            cursor = targetSlot + 1;
        }
        check(CellSlotScan.nextEmpty(occupied.length, cursor, slot -> {
            queries.incrementAndGet();
            return !occupied[slot];
        }) == -1, "filled drive ends without another candidate");
        check(queries.get() == 630, "630-cell fill uses 630 slot reads instead of 198765 prefix reads");
        queries.set(0);
        check(CellSlotScan.nextEmpty(occupied.length, 0, slot -> {
            queries.incrementAndGet();
            return !occupied[slot];
        }) == -1 && queries.get() == 630, "full-drive preflight is bounded to one inventory scan");
    }

    private static void randomSlotScans(Random random) {
        for (int cycle = 0; cycle < 4096; cycle++) {
            boolean[] empty = new boolean[random.nextInt(631)];
            for (int slot = 0; slot < empty.length; slot++) empty[slot] = random.nextInt(5) == 0;
            int startSlot = random.nextInt(empty.length + 5) - 2;
            int expectedSlot = -1;
            int expectedReads = 0;
            for (int slot = Math.max(0, startSlot); slot < empty.length; slot++) {
                expectedReads++;
                if (empty[slot]) {
                    expectedSlot = slot;
                    break;
                }
            }
            AtomicInteger reads = new AtomicInteger();
            int actualSlot = CellSlotScan.nextEmpty(empty.length, startSlot, slot -> {
                reads.incrementAndGet();
                return empty[slot];
            });
            check(actualSlot == expectedSlot, "random slot selection " + cycle);
            check(reads.get() == expectedReads, "random bounded slot reads " + cycle);
        }
    }

    private static void countBoundaries() {
        for (String count : List.of("1", "2147483647")) {
            var legacy = SingularityRecipeConfig.parse("\"test:item\"" + count, "test:item"::equals, id -> false);
            var json = SingularityRecipeConfig.parse("[{\"id\":\"test:item\",\"count\":" + count + "}]",
                    "test:item"::equals, id -> false);
            check(legacy.errors().isEmpty() && legacy.entries().getFirst().count() == Integer.parseInt(count), "legacy count boundary " + count);
            check(json.errors().isEmpty() && json.entries().getFirst().count() == Integer.parseInt(count), "JSON count boundary " + count);
        }
        for (String count : List.of("0", "-1", "2147483648", "9223372036854775807", "1.5")) {
            var parsed = SingularityRecipeConfig.parse("[{\"id\":\"test:item\",\"count\":" + count + "},"
                    + "{\"id\":\"test:valid\",\"count\":1}]", id -> true, id -> false);
            check(parsed.errors().size() == 1 && parsed.entries().size() == 1
                    && parsed.entries().getFirst().id().equals("test:valid"), "invalid amount never suppresses its valid neighbor " + count);
        }
    }

    private static void atomicFileFailures() throws IOException {
        Path directory = Files.createTempDirectory("enhancedcore-module-regression-");
        Path target = directory.resolve("config.json");
        Path blocked = directory.resolve("blocked.json");
        Path preserved = blocked.resolve("preserved.txt");
        try {
            ConfigFiles.writeAtomically(target, "原配置");
            ConfigFiles.writeAtomically(target, "新配置\n第二行");
            check(Files.readString(target).equals("新配置\n第二行"), "atomic replacement preserves UTF-8 text");
            Files.createDirectories(blocked);
            Files.writeString(preserved, "keep");
            try {
                ConfigFiles.writeAtomically(blocked, "must fail");
                throw new AssertionError("nonempty directory was overwritten");
            } catch (IOException expected) {
                assertions++;
            }
            check(Files.readString(preserved).equals("keep"), "failed replacement preserves existing contents");
            try (var files = Files.list(directory)) {
                check(files.noneMatch(path -> path.getFileName().toString().endsWith(".tmp")), "failed writes leave no temporary files");
            }
        } finally {
            Files.deleteIfExists(target);
            Files.deleteIfExists(preserved);
            Files.deleteIfExists(blocked);
            Files.deleteIfExists(directory);
        }
    }

    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }
}
