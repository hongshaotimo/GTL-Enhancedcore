package com.gtl.enhancedcore.common.structure;

import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.pattern.BlockPattern;
import com.gregtechceu.gtceu.api.pattern.Predicates;
import com.gregtechceu.gtceu.api.pattern.TraceabilityPredicate;
import com.gtl.enhancedcore.common.registration.RetiredMachines;
import net.minecraft.resources.ResourceLocation;

/** Supplies the inert pattern for the enhancement mod's retired controllers. */
public final class RetiredLightHunter {
    private RetiredLightHunter() {}
    public static boolean contains(ResourceLocation id) {
        return id != null && RetiredMachines.contains(id.toString());
    }
    public static BlockPattern pattern(MultiblockMachineDefinition definition) {
        return StructurePatterns.start("/data/gtl_enhancedcore/structures/retired_light_hunter.pattern.gz")
                .where('C', Predicates.controller(Predicates.blocks(definition.get())))
                .where('!', new TraceabilityPredicate(state -> false, () -> new com.lowdragmc.lowdraglib.utils.BlockInfo[0])).build();
    }
}
