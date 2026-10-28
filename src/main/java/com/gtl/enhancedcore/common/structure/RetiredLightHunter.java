package com.gtl.enhancedcore.common.structure;

import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.pattern.BlockPattern;
import com.gregtechceu.gtceu.api.pattern.Predicates;
import com.gregtechceu.gtceu.api.pattern.TraceabilityPredicate;
import net.minecraft.resources.ResourceLocation;

/** Retired IDs remain registered, but their enormous pattern/preview suppliers are never called. */
public final class RetiredLightHunter {
    private RetiredLightHunter() {}
    public static boolean contains(ResourceLocation id) {
        if (id != null && com.gtl.enhancedcore.common.registration.RetiredMachines.contains(id.toString())) return true;
        return id != null && id.getNamespace().equals("gtladditions")
                && (id.getPath().equals("light_hunter_space_station")
                || id.getPath().matches("nexus_satellite_factory_mk[1-4]"));
    }
    public static BlockPattern pattern(MultiblockMachineDefinition definition) {
        return StructurePatterns.start("/data/gtl_enhancedcore/structures/retired_light_hunter.pattern.gz")
                .where('C', Predicates.controller(Predicates.blocks(definition.get())))
                .where('!', new TraceabilityPredicate(state -> false, () -> new com.lowdragmc.lowdraglib.utils.BlockInfo[0])).build();
    }
}
