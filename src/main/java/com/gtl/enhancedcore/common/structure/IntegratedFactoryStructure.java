package com.gtl.enhancedcore.common.structure;

import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.pattern.BlockPattern;
import com.gregtechceu.gtceu.api.pattern.Predicates;
import com.gregtechceu.gtceu.api.pattern.TraceabilityPredicate;
import com.gregtechceu.gtceu.common.block.LampBlock;
import com.gtl.enhancedcore.integration.terminal.LampPlacement;
import com.lowdragmc.lowdraglib.utils.BlockInfo;
import net.minecraft.world.level.block.Block;
import org.gtlcore.gtlcore.integration.terminal.StableBlockCandidates;

import static com.gtl.enhancedcore.common.registration.MachineRegistrationSupport.*;

/** North console, four non-interchangeable workstations and a separate breaker cabinet. */
public final class IntegratedFactoryStructure {
    public static final String RESOURCE = "/data/gtl_enhancedcore/structures/joint_factory_four_stations_mv.pattern.gz";

    private IntegratedFactoryStructure() {}

    public static BlockPattern create(MultiblockMachineDefinition definition) {
        var itemIn = ordinary("input_bus");
        var fluidIn = ordinary("input_hatch");
        var fluidOut = ordinary("output_hatch");
        // Independent, closed MV flanges stay valid with no expansion installed.
        var blank = requiredBlock("gtceu:mv_machine_casing");
        return StructurePatterns.start(RESOURCE)
                .where('C', Predicates.controller(Predicates.blocks(definition.get())))
                .where('i', itemIn).where('a', blank.or(itemIn))
                .where('f', fluidIn).where('b', blank.or(fluidIn))
                .where('o', ordinary("output_bus"))
                .where('l', fluidOut).where('d', blank.or(fluidOut))
                .where('e', Predicates.blocks(requiredBlockBlock("gtceu:mv_energy_input_hatch"),
                        requiredBlockBlock("gtceu:lv_energy_input_hatch"), requiredBlockBlock("gtceu:ulv_energy_input_hatch")))
                .where('m', requiredBlock("gtceu:maintenance_hatch").or(blank))
                .where('A', requiredBlock("gtlcore:multi_functional_casing"))
                .where('B', blank).where('D', requiredBlock("gtceu:solid_machine_casing"))
                .where('E', requiredBlock("gtceu:steel_gearbox"))
                .where('F', requiredBlock("gtceu:steel_pipe_casing"))
                .where('G', requiredBlock("gtceu:cupronickel_coil_block"))
                .where('I', requiredBlock("ae2:quartz_glass")).where('J', lamps())
                .where('K', requiredBlock("minecraft:waxed_cut_copper"))
                .where('P', requiredBlock("minecraft:polished_andesite"))
                .where('R', requiredBlock("minecraft:iron_bars"))
                .where('S', requiredBlock("minecraft:polished_andesite_slab"))
                .where('Y', requiredBlock("minecraft:yellow_terracotta"))
                .where('#', Predicates.air()).build();
    }

    private static TraceabilityPredicate ordinary(String family) {
        return Predicates.blocks(new Block[]{requiredBlockBlock("gtceu:lv_" + family), requiredBlockBlock("gtceu:mv_" + family)});
    }

    private static TraceabilityPredicate lamps() {
        var block = requiredBlockBlock("gtceu:white_lamp");
        var state = block.defaultBlockState().setValue(LampBlock.INVERTED, true)
                .setValue(LampBlock.LIGHT, true).setValue(LampBlock.BLOOM, false);
        return new TraceabilityPredicate(world -> world.getBlockState().is(block),
                StableBlockCandidates.mark(() -> new BlockInfo[]{LampPlacement.info(state)}));
    }
}
