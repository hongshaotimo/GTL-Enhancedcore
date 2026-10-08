package com.gtl.enhancedcore.common.data.machines.multiblock;

import com.gregtechceu.gtceu.api.block.IMachineBlock;
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.machine.multiblock.PartAbility;
import com.gregtechceu.gtceu.api.pattern.BlockPattern;
import com.gregtechceu.gtceu.api.pattern.Predicates;
import com.gregtechceu.gtceu.api.pattern.TraceabilityPredicate;
import com.gtl.enhancedcore.common.structure.StructurePatterns;
import com.gtl.enhancedcore.common.recipe.iv.IvBufferRegistry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.registries.ForgeRegistries;

/** Independent IV layouts with the original materials and hatch capability contract. */
public final class IVProcessingStructures {
    private IVProcessingStructures() {}

    public static BlockPattern plasmaMachine(MultiblockMachineDefinition definition) {
        return create("plasma_machine_tool", definition);
    }

    public static BlockPattern hadronRefinery(MultiblockMachineDefinition definition) {
        return create("hadron_catalytic_refinery", definition);
    }

    public static BlockPattern quantumArray(MultiblockMachineDefinition definition) {
        return create("quantum_mass_spectrum_array", definition);
    }

    public static BlockPattern fusionAssembler(MultiblockMachineDefinition definition) {
        return create("superconducting_fusion_assembler", definition);
    }

    private static BlockPattern create(String name, MultiblockMachineDefinition definition) {
        // X = 主方块正中的 3x3 仓室面板；Q = 主方块所在整个正立面（同一方块，同样可放仓室）。
        // 两者共用同一份能力谓词，避免两处定义漂移。
        TraceabilityPredicate hatchFace = hatchCapable();
        return StructurePatterns.start("/data/gtl_enhancedcore/structures/" + name + ".pattern.gz")
                .where('C', Predicates.controller(Predicates.blocks(new IMachineBlock[]{definition.get()})))
                .where('X', hatchFace)
                .where('Q', hatchFace)
                .where('A', block("gtceu:large_scale_assembler_casing"))
                .where('B', block("gtceu:laminated_glass"))
                .where('D', block("minecraft:iron_block"))
                .where('G', block("gtceu:stress_proof_casing"))
                // I 仍是纯外壳：机身后部与内层不受影响，只有正立面（Q）可放仓室。
                .where('I', block("gtceu:robust_machine_casing"))
                .where('J', block("gtceu:molybdenum_disilicide_coil_block"))
                .where(' ', Predicates.any())
                .build();
    }

    /**
     * 仓室面谓词：鲁棒机械外壳、普通材料输入输出仓、合资格总成及原能源/维护/并行仓。
     * 同一实例同时绑给 'X' 与 'Q'，两处能力永远一致。
     */
    private static TraceabilityPredicate hatchCapable() {
        return block("gtceu:robust_machine_casing")
                .or(Predicates.abilities(PartAbility.MAINTENANCE).setPreviewCount(1).setMaxGlobalLimited(1))
                .or(Predicates.abilities(PartAbility.INPUT_ENERGY).setPreviewCount(2).setMaxGlobalLimited(2))
                .or(IvBufferRegistry.materialPorts(-1))
                .or(Predicates.abilities(PartAbility.PARALLEL_HATCH).setPreviewCount(1).setMaxGlobalLimited(1));
    }

    private static TraceabilityPredicate block(String id) {
        var block = ForgeRegistries.BLOCKS.getValue(new ResourceLocation(id));
        if (block == null || block == Blocks.AIR) {
            throw new IllegalStateException("Required IV structure block is not registered: " + id);
        }
        return Predicates.blocks(block);
    }
}
