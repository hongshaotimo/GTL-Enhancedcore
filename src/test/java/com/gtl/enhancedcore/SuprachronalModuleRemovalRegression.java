package com.gtl.enhancedcore;

import com.gtl.enhancedcore.common.util.SuprachronalModuleRemoval;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

public final class SuprachronalModuleRemovalRegression {
    private static int assertions;

    private SuprachronalModuleRemovalRegression() {}

    public static int run() {
        return run(0x53555052414D4F44L);
    }

    private static int run(long seed) {
        assertions = 0;
        legacyConnections();
        reentrantDisconnects();
        emptyAndRepeatedCleanup();
        nullableLegacyRecords();
        repeatedRestores(new Random(seed));
        moduleDisplayKeys();
        previewBoundaries();
        return assertions;
    }

    public static void main(String[] args) {
        long seed = args.length == 0 ? 0x53555052414D4F44L : Long.parseLong(args[0]);
        System.out.println("SuprachronalModuleRemovalRegression passed: " + run(seed)
                + " assertions, seed=" + seed + " (dependency-light; Minecraft runtime not exercised)");
    }

    private static void legacyConnections() {
        LegacyModule first = new LegacyModule("suprachronal", "left", 17);
        LegacyModule second = new LegacyModule("suprachronal", "right", 29);
        LegacyModule unrelated = new LegacyModule("space_elevator", "upper", 31);
        Set<LegacyModule> modules = new LinkedHashSet<>(List.of(first, second));
        Set<LegacyModule> originalSet = modules;
        Set<LegacyModule> unrelatedModules = new LinkedHashSet<>(List.of(unrelated));

        SuprachronalModuleRemoval.detachLegacyModules(modules, module -> module.disconnect(modules));

        check(modules == originalSet && modules.isEmpty(), "native mutable module set is preserved and emptied");
        verifyDetached(first, 17);
        verifyDetached(second, 29);
        check(unrelatedModules.equals(Set.of(unrelated)), "another host retains its module membership");
        check("space_elevator".equals(unrelated.owner) && "upper".equals(unrelated.hostPosition),
                "another machine's saved module link remains unchanged");
        check(unrelated.disconnectCount == 0 && unrelated.inventory == 31,
                "another machine's module callback and inventory are untouched");
    }

    private static void reentrantDisconnects() {
        LegacyModule first = new LegacyModule("suprachronal", "left", 41);
        LegacyModule second = new LegacyModule("suprachronal", "right", 43);
        Set<LegacyModule> modules = new LinkedHashSet<>(List.of(first, second));
        AtomicInteger callbacks = new AtomicInteger();

        SuprachronalModuleRemoval.detachLegacyModules(modules, module -> {
            check(modules.isEmpty(), "module records are cleared before counterpart callbacks run");
            SuprachronalModuleRemoval.detachLegacyModules(modules,
                    nested -> { throw new AssertionError("reentrant native getter must not disconnect twice"); });
            module.disconnect(modules);
            callbacks.incrementAndGet();
        });

        check(callbacks.get() == 2, "snapshot visits every legacy module exactly once during reentrant removal");
        verifyDetached(first, 41);
        verifyDetached(second, 43);
    }

    private static void emptyAndRepeatedCleanup() {
        Set<LegacyModule> modules = new LinkedHashSet<>();
        AtomicInteger callbacks = new AtomicInteger();
        SuprachronalModuleRemoval.detachLegacyModules(modules, module -> callbacks.incrementAndGet());
        check(modules.isEmpty() && callbacks.get() == 0, "newly formed hosts have no legacy callbacks");

        LegacyModule module = new LegacyModule("suprachronal", "old", 47);
        modules.add(module);
        SuprachronalModuleRemoval.detachLegacyModules(modules, entry -> entry.disconnect(modules));
        for (int cycle = 0; cycle < 16; cycle++) {
            SuprachronalModuleRemoval.detachLegacyModules(modules, entry -> entry.disconnect(modules));
        }
        verifyDetached(module, 47);
    }

    private static void nullableLegacyRecords() {
        Set<LegacyModule> modules = new LinkedHashSet<>();
        modules.add(null);
        AtomicInteger callbacks = new AtomicInteger();
        SuprachronalModuleRemoval.detachLegacyModules(modules, module -> callbacks.incrementAndGet());
        check(modules.isEmpty() && callbacks.get() == 0, "a stale null record cannot become an installed module");
    }

    private static void repeatedRestores(Random random) {
        for (int cycle = 0; cycle < 256; cycle++) {
            int moduleCount = random.nextInt(7);
            Set<LegacyModule> modules = new LinkedHashSet<>();
            List<LegacyModule> originals = new ArrayList<>();
            for (int moduleIndex = 0; moduleIndex < moduleCount; moduleIndex++) {
                LegacyModule module = new LegacyModule("suprachronal", "saved-" + moduleIndex,
                        cycle * 8 + moduleIndex);
                modules.add(module);
                originals.add(module);
            }
            AtomicInteger callbacks = new AtomicInteger();
            SuprachronalModuleRemoval.detachLegacyModules(modules, module -> {
                module.disconnect(modules);
                callbacks.incrementAndGet();
            });
            check(modules.isEmpty() && callbacks.get() == moduleCount,
                    "restored module counts, including over-limit legacy sets, are fully disconnected");
            for (int moduleIndex = 0; moduleIndex < moduleCount; moduleIndex++) {
                verifyDetached(originals.get(moduleIndex), cycle * 8 + moduleIndex);
            }
            SuprachronalModuleRemoval.detachLegacyModules(modules, module -> callbacks.incrementAndGet());
            check(callbacks.get() == moduleCount, "later getter reads cannot reconnect or redisconnect old modules");
        }
    }

    private static void moduleDisplayKeys() {
        check(SuprachronalModuleRemoval.isInstalledModuleCount("tooltip.gtlcore.installed_module_count"),
                "the removed GUI row uses the exact native translation key");
        for (String key : List.of("gtceu.multiblock.parallel", "tooltip.gtlcore.module_not_installed",
                "gtceu.multiblock.data_access", "gtladditions.item.suprachronal_data_module.tooltips.0",
                "tooltip.gtlcore.installed_module_count.extra", "gtl_enhancedcore.iv.order")) {
            check(!SuprachronalModuleRemoval.isInstalledModuleCount(key),
                    "parallel, research, order and unrelated module labels are retained: " + key);
        }
        check(!SuprachronalModuleRemoval.isInstalledModuleCount(null), "non-translatable rows are not removed");
    }

    private static void previewBoundaries() {
        check(!SuprachronalModuleRemoval.previewHasModules(true, true),
                "suprachronal previews no longer expose the native module toggle");
        check(!SuprachronalModuleRemoval.previewHasModules(false, true),
                "an already absent suprachronal toggle stays absent");
        check(SuprachronalModuleRemoval.previewHasModules(true, false),
                "other modular machines retain their native preview toggle");
        check(!SuprachronalModuleRemoval.previewHasModules(false, false),
                "non-modular machines do not gain a module toggle");
    }

    private static void verifyDetached(LegacyModule module, int inventory) {
        check(module.owner == null && module.hostPosition == null, "both sides of the saved host link are cleared");
        check(module.disconnectCount == 1, "a legacy module is disconnected exactly once");
        check(module.inventory == inventory, "disconnecting a legacy module does not delete its inventory");
    }

    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }

    private static final class LegacyModule {
        private String owner;
        private String hostPosition;
        private final int inventory;
        private int disconnectCount;

        private LegacyModule(String owner, String hostPosition, int inventory) {
            this.owner = owner;
            this.hostPosition = hostPosition;
            this.inventory = inventory;
        }

        private void disconnect(Set<LegacyModule> modules) {
            owner = null;
            hostPosition = null;
            modules.remove(this);
            disconnectCount++;
        }
    }
}
