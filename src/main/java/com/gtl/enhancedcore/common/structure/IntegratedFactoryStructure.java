package com.gtl.enhancedcore.common.structure;

import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.machine.multiblock.PartAbility;
import com.gregtechceu.gtceu.api.pattern.BlockPattern;
import com.gregtechceu.gtceu.api.pattern.Predicates;
import com.gregtechceu.gtceu.api.pattern.TraceabilityPredicate;
import com.gregtechceu.gtceu.common.block.LampBlock;
import com.gtl.enhancedcore.integration.terminal.LampPlacement;
import com.gtl.enhancedcore.common.recipe.iv.IvBufferRegistry;
import org.gtlcore.gtlcore.integration.terminal.StableBlockCandidates;
import com.lowdragmc.lowdraglib.utils.BlockInfo;

import static com.gtl.enhancedcore.common.registration.MachineRegistrationSupport.*;

/** WorldEdit import: 49 x 31 x 49; only the central 11 x 13 front panel accepts hatches. */
public final class IntegratedFactoryStructure {
    private IntegratedFactoryStructure() {}

    public static BlockPattern create(MultiblockMachineDefinition definition) {
        var abilities = Predicates.abilities(PartAbility.MAINTENANCE).setMaxGlobalLimited(1).setPreviewCount(1)
                .or(Predicates.abilities(PartAbility.INPUT_ENERGY).setMinGlobalLimited(1)
                        .setMaxGlobalLimited(2).setPreviewCount(1))
                .or(IvBufferRegistry.materialPorts(30));
        var hatches = requiredBlock("gtlcore:multi_functional_casing").or(abilities);
        return StructurePatterns.start("/data/gtl_enhancedcore/structures/integrated_universal_factory.pattern.gz")
                .where('C', Predicates.controller(Predicates.blocks(definition.get())))
                .where('X', hatches)
                .where('Q', hatches)
                .where('A', requiredBlock("gtlcore:multi_functional_casing"))
                .where('B', requiredBlock("gtceu:mv_machine_casing"))
                .where('D', requiredBlock("gtceu:solid_machine_casing"))
                .where('E', requiredBlock("gtceu:steel_gearbox"))
                .where('F', requiredBlock("gtceu:steel_pipe_casing"))
                .where('G', requiredBlock("gtceu:cupronickel_coil_block"))
                .where('I', requiredBlock("ae2:quartz_glass"))
                .where('J', lamps())
                .where(' ', Predicates.any())
                .build();
    }

    private static TraceabilityPredicate lamps() {
        var block = requiredBlockBlock("gtceu:white_lamp");
        var state = block.defaultBlockState().setValue(LampBlock.INVERTED, true)
                .setValue(LampBlock.LIGHT, true).setValue(LampBlock.BLOOM, false);
        return new TraceabilityPredicate(world -> world.getBlockState().is(block),
                StableBlockCandidates.mark(() -> new BlockInfo[]{LampPlacement.info(state)}));
    }
}
