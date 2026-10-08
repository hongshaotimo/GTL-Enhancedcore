package com.gtl.enhancedcore.common.data.machines.multiblock;

import com.gregtechceu.gtceu.api.block.IMachineBlock;
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.machine.multiblock.PartAbility;
import com.gregtechceu.gtceu.api.pattern.BlockPattern;
import com.gregtechceu.gtceu.api.pattern.Predicates;
import com.gtl.enhancedcore.common.structure.StructurePatterns;
import static com.gtl.enhancedcore.common.registration.MachineRegistrationSupport.requiredBlock;

/** Atmospheric receiver and MAX Taiji vessel, with their original functional IO. */
public final class FieldMachineStructures {
    private FieldMachineStructures() {}

    public static BlockPattern weather(MultiblockMachineDefinition definition) {
        return StructurePatterns.start("/data/gtl_enhancedcore/structures/weather_anchor.pattern.gz")
                .where('C', Predicates.controller(Predicates.blocks(new IMachineBlock[]{definition.get()})))
                .where('H', requiredBlock("gtceu:high_power_casing"))
                .where('B', requiredBlock("gtceu:laminated_glass"))
                .where('D', requiredBlock("gtceu:computer_casing"))
                .where('J', requiredBlock("gtceu:hssg_coil_block"))
                .where('L', requiredBlock("minecraft:sea_lantern"))
                .where('X', requiredBlock("gtceu:high_power_casing")
                        .or(Predicates.abilities(PartAbility.INPUT_ENERGY).setPreviewCount(2).setMaxGlobalLimited(2))
                        .or(Predicates.abilities(PartAbility.INPUT_LASER).setPreviewCount(0).setMaxGlobalLimited(1)))
                .where(' ', Predicates.any())
                .build();
    }

    public static BlockPattern causality(MultiblockMachineDefinition definition) {
        return StructurePatterns.start("/data/gtl_enhancedcore/structures/causality_terminal.pattern.gz")
                .where('C', Predicates.controller(Predicates.blocks(new IMachineBlock[]{definition.get()})))
                .where('H', requiredBlock("gtceu:high_power_casing"))
                .where('F', requiredBlock("gtlcore:dimension_connection_casing"))
                .where('J', requiredBlock("gtceu:superconducting_coil"))
                .where('S', requiredBlock("gtceu:spacetime_block"))
                .where('W', requiredBlock("minecraft:quartz_block"))
                .where('K', requiredBlock("minecraft:polished_blackstone_bricks"))
                .where('L', requiredBlock("minecraft:sea_lantern"))
                .where('X', requiredBlock("gtceu:high_power_casing")
                        .or(Predicates.abilities(PartAbility.MAINTENANCE).setPreviewCount(1).setMaxGlobalLimited(1))
                        .or(Predicates.abilities(PartAbility.INPUT_LASER).setPreviewCount(1).setMinGlobalLimited(1).setMaxGlobalLimited(2))
                        .or(Predicates.abilities(PartAbility.IMPORT_ITEMS).setPreviewCount(1).setMaxGlobalLimited(4))
                        .or(Predicates.abilities(PartAbility.EXPORT_ITEMS).setPreviewCount(1).setMaxGlobalLimited(4)))
                .where(' ', Predicates.any())
                .build();
    }
}
