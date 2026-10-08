package com.gtl.enhancedcore.common.data.machines.multiblock.QdysStructure;

import com.gtl.enhancedcore.common.structure.StructurePatterns;
import com.gregtechceu.gtceu.api.block.IMachineBlock;
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.machine.multiblock.PartAbility;
import com.gregtechceu.gtceu.api.pattern.BlockPattern;
import com.gregtechceu.gtceu.api.pattern.Predicates;
import com.gregtechceu.gtceu.api.pattern.TraceabilityPredicate;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.registries.ForgeRegistries;
import java.util.Objects;

/**
 * 奇点压缩器结构（qdys.schem，2026-09-09 用户提供，替换无限奇点压缩器默认 3×3×3）。
 * 283 aisles × 283 宽 × 116 高；控制器 ~ 在结构中部；X 位（钻石矿标记）仓室位，成型方块 ae2:mysterious_cube。
 */
public final class QdysStructure {
    private QdysStructure() {}

    public static BlockPattern create(MultiblockMachineDefinition def) {
        return StructurePatterns.start("/data/gtl_enhancedcore/structures/qdys.pattern.gz")
                .where('~', controller(def))
                .where(' ', Predicates.any())
                .where('X', block("ae2:mysterious_cube")
                        .or(Predicates.abilities(PartAbility.MAINTENANCE).setPreviewCount(1).setMaxGlobalLimited(1))
                        .or(Predicates.abilities(PartAbility.INPUT_ENERGY).setPreviewCount(1).setMaxGlobalLimited(2))
                        .or(Predicates.abilities(PartAbility.INPUT_LASER).setPreviewCount(1).setMaxGlobalLimited(2))
                        .or(Predicates.abilities(PartAbility.IMPORT_ITEMS).setPreviewCount(1).setMaxGlobalLimited(4))
                        .or(Predicates.abilities(PartAbility.EXPORT_ITEMS).setPreviewCount(1).setMaxGlobalLimited(4))
                        .or(Predicates.abilities(PartAbility.IMPORT_FLUIDS).setPreviewCount(1).setMaxGlobalLimited(4)))
                .where('A', block("gtceu:spacetime_block"))
                .where('B', block("ae2:mysterious_cube"))
                .where('C', block("gtceu:superconducting_coil"))
                .where('D', block("minecraft:sea_lantern"))
                .where('E', block("minecraft:glowstone"))
                .where('F', block("gtlcore:dimension_connection_casing"))
                .where('G', block("kubejs:eternity_coil_block"))
                .where('H', block("gtlcore:enhance_hyper_mechanical_casing"))
                .where('I', block("gtceu:iv_machine_casing"))
                .where('J', block("minecraft:chain"))
                .where('K', block("gtlcore:lafium_mechanical_casing"))
                .where('L', block("gtlcore:fusion_casing_mk5"))
                .where('M', block("minecraft:shroomlight"))
                .where('N', block("kubejs:magic_core"))
                .where('O', block("minecraft:gold_block"))
                .build();
    }

    private static TraceabilityPredicate block(String id) {
        ResourceLocation location = ResourceLocation.tryParse(id);
        Objects.requireNonNull(location, "Invalid block id: " + id);
        Block b = ForgeRegistries.BLOCKS.getValue(location);
        if (b == null || b == Blocks.AIR) {
            throw new IllegalStateException("Required block is not registered: " + id);
        }
        return Predicates.blocks(new Block[]{b});
    }

    private static TraceabilityPredicate controller(MultiblockMachineDefinition def) {
        return Predicates.controller(Predicates.blocks(new IMachineBlock[]{def.get()}));
    }
}
