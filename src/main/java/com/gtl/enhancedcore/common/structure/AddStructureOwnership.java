package com.gtl.enhancedcore.common.structure;

import java.util.Set;
import net.minecraft.resources.ResourceLocation;

/** ADD 3.6.2 owns its machine geometry and the upstream patterns it changes structurally. */
public final class AddStructureOwnership {
    private static final Set<String> LEGACY = Set.of(
            "atomic_transmutation_core", "apocalyptic_torsion_quantum_matrix",
            "dimensionally_transcendent_chemical_plant", "heart_of_the_universe");

    private AddStructureOwnership() {}

    public static boolean owns(ResourceLocation id) {
        if (id == null) return false;
        return switch (id.getNamespace()) {
            case "gtladditions" -> true;
            case "gtceu" -> switch (id.getPath()) {
                case "weather_control", "suprachronal_assembly_line", "research_station" -> true;
                default -> false;
            };
            default -> false;
        };
    }

    static boolean isLegacy(ResourceLocation id) {
        return id.getNamespace().equals("gtladditions") && LEGACY.contains(id.getPath());
    }
}
