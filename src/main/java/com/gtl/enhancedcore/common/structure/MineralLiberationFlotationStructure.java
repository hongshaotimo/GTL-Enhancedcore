package com.gtl.enhancedcore.common.structure;

import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.machine.multiblock.PartAbility;
import com.gregtechceu.gtceu.api.pattern.BlockPattern;
import com.gregtechceu.gtceu.api.pattern.Predicates;

import static com.gtl.enhancedcore.common.registration.MachineRegistrationSupport.requiredBlock;

/** The supplied 65 x 37 x 85 blueprint; only its 184 marked cells accept machine parts. */
public final class MineralLiberationFlotationStructure {
    private static final String NAME = "mineral_liberation_flotation_complex";

    private MineralLiberationFlotationStructure() {}

    public static BlockPattern create(MultiblockMachineDefinition definition) {
        return ReviewedStructurePalette.bind(StructurePatterns.start(
                        "/data/gtl_enhancedcore/structures/" + NAME + ".pattern.gz"), NAME)
                .where('C', Predicates.controller(Predicates.blocks(definition.get())))
                .where('X', requiredBlock("gtlcore:pikyonium_machine_casing")
                        .or(Predicates.abilities(PartAbility.INPUT_ENERGY).setMaxGlobalLimited(2).setPreviewCount(1))
                        .or(Predicates.abilities(PartAbility.INPUT_LASER).setMaxGlobalLimited(1).setPreviewCount(1))
                        .or(Predicates.abilities(PartAbility.MAINTENANCE).setMaxGlobalLimited(1).setPreviewCount(1))
                        .or(Predicates.abilities(PartAbility.PARALLEL_HATCH).setMaxGlobalLimited(1).setPreviewCount(1))
                        .or(Predicates.abilities(PartAbility.IMPORT_ITEMS).setPreviewCount(1))
                        .or(Predicates.abilities(PartAbility.EXPORT_ITEMS).setPreviewCount(1))
                        .or(Predicates.abilities(PartAbility.IMPORT_FLUIDS).setPreviewCount(1))
                        .or(Predicates.abilities(PartAbility.EXPORT_FLUIDS).setPreviewCount(1)))
                .where(' ', Predicates.any())
                .build();
    }
}
