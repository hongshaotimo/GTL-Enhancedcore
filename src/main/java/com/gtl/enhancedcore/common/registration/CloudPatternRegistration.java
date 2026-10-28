package com.gtl.enhancedcore.common.registration;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.data.RotationState;
import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.machine.multiblock.PartAbility;
import com.gregtechceu.gtceu.api.pattern.Predicates;
import com.gregtechceu.gtceu.common.data.GTBlocks;
import com.gtl.enhancedcore.common.cloud.ui.CloudPatternMachineRenderer;
import com.gtl.enhancedcore.GTLEnhancedcore;
import com.gtl.enhancedcore.common.cloud.CloudPatternLibraryMachine;
import com.gtl.enhancedcore.common.cloud.CloudPatternBufferMachine;
import com.gtl.enhancedcore.common.util.MachineTooltips;
import com.gtl.enhancedcore.common.structure.StructurePatterns;

/** Definitions are registered once by GTLEnhancedcoreMachines and included in its creative-tab list. */
public final class CloudPatternRegistration {
    private CloudPatternRegistration() {}

    public static MultiblockMachineDefinition library() {
        return GTLEnhancedcore.REGISTRATE.multiblock("cloud_pattern_library", CloudPatternLibraryMachine::new)
                .langValue("Central Pattern Library Core").tier(GTValues.UV).rotationState(RotationState.NON_Y_AXIS)
                .appearanceBlock(() -> MachineRegistrationSupport.requiredBlockBlock("gtlcore:molecular_casing"))
                .pattern(definition -> com.gtl.enhancedcore.common.structure.ReviewedStructurePalette.bind(
                        StructurePatterns.start("/data/gtl_enhancedcore/structures/cloud_pattern_library.pattern.gz"), "cloud_pattern_library")
                        .where('C', Predicates.controller(Predicates.blocks(definition.get())))
                        .where(' ', Predicates.any())
                        .build())
                .renderer(() -> new CloudPatternMachineRenderer(new net.minecraft.resources.ResourceLocation("gtlcore", "block/molecular_casing"),
                        GTLEnhancedcore.id("block/machine/cloud/cloud_pattern_library"),
                        GTLEnhancedcore.id("block/machine/cloud/cloud_pattern_library_active")))
                .tooltipBuilder(MachineTooltips.create("cloud_library"))
                .register();
    }

    public static MachineDefinition buffer() {
        return GTLEnhancedcore.REGISTRATE.machine("cloud_pattern_buffer", CloudPatternBufferMachine::new)
                .langValue("Cloud Pattern Buffer").tier(GTValues.UV).rotationState(RotationState.ALL)
                // Match the upstream super pattern buffer's structural role. Independent
                // completion returns to AE; this is not an ordinary output-port position.
                .abilities(PartAbility.IMPORT_ITEMS, PartAbility.IMPORT_FLUIDS)
                .renderer(() -> new CloudPatternMachineRenderer(GTValues.UV,
                        GTLEnhancedcore.id("block/machine/cloud/adaptive_cloud_pattern_buffer"),
                        GTLEnhancedcore.id("block/machine/cloud/adaptive_cloud_pattern_buffer_active")))
                .tooltipBuilder(MachineTooltips.create("cloud_buffer"))
                .register();
    }
}
