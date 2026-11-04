package com.gtl.enhancedcore.common.structure;

import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.machine.multiblock.PartAbility;
import com.gregtechceu.gtceu.api.pattern.BlockPattern;
import com.gregtechceu.gtceu.api.pattern.Predicates;
import static com.gtl.enhancedcore.common.registration.MachineRegistrationSupport.requiredBlock;

/** The supplied 57 x 45 x 71 design; only the 64 marked cells accept hatches. */
public final class LanthanideRefiningHubStructure {
    private LanthanideRefiningHubStructure() {}

    public static BlockPattern create(MultiblockMachineDefinition definition) {
        return ReviewedStructurePalette.bind(StructurePatterns.start(
                        "/data/gtl_enhancedcore/structures/lanthanide_refining_hub.pattern.gz"), "lanthanide_refining_hub")
                .where('C', Predicates.controller(Predicates.blocks(definition.get())))
                .where('X', requiredBlock("gtceu:computer_casing")
                        .or(Predicates.abilities(PartAbility.INPUT_ENERGY).setMaxGlobalLimited(2).setPreviewCount(1))
                        .or(Predicates.abilities(PartAbility.MAINTENANCE).setMaxGlobalLimited(1).setPreviewCount(1))
                        .or(Predicates.abilities(PartAbility.PARALLEL_HATCH).setMaxGlobalLimited(1).setPreviewCount(1))
                        .or(Predicates.abilities(PartAbility.IMPORT_ITEMS).setPreviewCount(1))
                        .or(Predicates.abilities(PartAbility.EXPORT_ITEMS).setPreviewCount(1))
                        .or(Predicates.abilities(PartAbility.IMPORT_FLUIDS).setPreviewCount(1)))
                .where(' ', Predicates.any())
                .build();
    }
}
