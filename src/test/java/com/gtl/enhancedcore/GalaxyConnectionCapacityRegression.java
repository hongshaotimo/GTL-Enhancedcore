package com.gtl.enhancedcore;

import com.gtl.enhancedcore.common.machine.galaxy.GalaxyLinks;
import com.gtl.enhancedcore.common.config.GalaxyConnectionConfig;
import java.nio.file.Files;
import java.util.Set;

final class GalaxyConnectionCapacityRegression {
    static int run() throws Exception {
        int capacityChecks = 0;
        for (int arrays = 0; arrays <= 64; arrays++) {
            for (boolean active : new boolean[]{false, true}) {
                for (boolean modifier : new boolean[]{false, true}) {
                    boolean unlimited = active && !modifier && arrays == 64;
                    check(GalaxyLinks.connectionProvidesUnlimitedCapacity(active, modifier, arrays) == unlimited,
                            "only a full active station without a thread modifier grants unlimited capacity");
                    for (int base : new int[]{1, 64, 128, 192, Integer.MAX_VALUE - 5}) {
                        int expected = !active || modifier ? base : unlimited ? Integer.MAX_VALUE
                                : (int)Math.min(Integer.MAX_VALUE, (long)base + 10_000);
                        check(GalaxyLinks.connectionThreads(active, modifier, arrays, base) == expected,
                                "finite bonus preserves native threads, disconnect, precedence and saturation");
                        capacityChecks++;
                    }
                    capacityChecks++;
                }
            }
        }
        check(GalaxyLinks.ordinaryBatchBudget(17) == 170017, "ordinary finite budget adds exactly 10,000 lanes");
        check(GalaxyLinks.ordinaryBatchBudget(Long.MAX_VALUE) == Long.MAX_VALUE, "large budget cannot overflow");
        var ids = GalaxyConnectionConfig.parse("# allowlist\ngtceu:mage_assembler\ngtl_enhancedcore:large_furnace\n");
        check(ids.size() == 2 && ids.contains("gtceu:mage_assembler"), "allowlist keeps complete IDs");
        check(GalaxyConnectionConfig.parse("# deny all\n").isEmpty(), "empty allowlist denies every target");
        for (String invalid : new String[]{"mage_assembler", "*:machine", "GTCEU:mage_assembler", "gtceu:mage_assembler gtceu:qft"}) {
            boolean rejected = false;
            try { GalaxyConnectionConfig.parse(invalid); } catch (IllegalArgumentException expected) { rejected = true; }
            check(rejected, "incomplete, wildcard or combined IDs are rejected");
        }
        check(GalaxyConnectionConfig.defaults().contains("gtceu:mage_assembler")
                && !GalaxyConnectionConfig.defaults().contains("gtceu:primitive_blast_furnace"),
                "default scope follows enhanced support instead of every machine");
        check(GalaxyConnectionConfig.defaults().size() == 56, "complete explicit supported default scope");
        check(GalaxyConnectionConfig.defaults().containsAll(Set.of(
                "gtl_enhancedcore:industrial_steam_platform", "gtl_enhancedcore:causality_terminal",
                "gtl_enhancedcore:infinity_singularity_compressor", "gtl_enhancedcore:platinum_refining_matrix",
                "gtl_enhancedcore:void_constrained_mining_field", "gtl_enhancedcore:dragon_field_proliferation_core")),
                "previously omitted own electric controllers are explicitly configurable");
        check(GalaxyConnectionConfig.defaults().stream().noneMatch(id -> Set.of(
                "gtl_enhancedcore:weather_anchor", "gtl_enhancedcore:cloud_pattern_library",
                "gtl_enhancedcore:stellar_vortex_space_station", "gtl_enhancedcore:extreme_reactor",
                "gtl_enhancedcore:stellar_confinement_fusion_reactor").contains(id)),
                "no-input receivers, storage and source station are not targets");
        check(GalaxyConnectionConfig.parse(GalaxyConnectionConfig.defaultText()).equals(GalaxyConnectionConfig.defaults()),
                "generated template contains every supported default exactly");
        var file = Files.createTempDirectory("enhancedcore-galaxy-list-").resolve("galaxy_connections.txt");
        try {
            GalaxyConnectionConfig.ensureDefaultConfig(file);
            check(GalaxyConnectionConfig.parse(Files.readString(file)).equals(GalaxyConnectionConfig.defaults()),
                    "new file receives complete defaults");
            for (String custom : new String[]{"# user deliberately selected one\ncustom:my_machine\n",
                    "# deny all\n", "", "invalid user text"}) {
                Files.writeString(file, custom);
                GalaxyConnectionConfig.ensureDefaultConfig(file);
                check(Files.readString(file).equals(custom), "existing custom text is never replaced or unioned");
            }
        } finally { Files.deleteIfExists(file); Files.delete(file.getParent()); }
        return 18 + capacityChecks;
    }

    private static void check(boolean valid, String message) {
        if (!valid) throw new AssertionError(message);
    }
}
