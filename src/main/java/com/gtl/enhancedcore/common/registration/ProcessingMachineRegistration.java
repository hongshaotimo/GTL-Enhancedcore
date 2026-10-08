package com.gtl.enhancedcore.common.registration;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.block.IMachineBlock;
import com.gregtechceu.gtceu.api.data.RotationState;
import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.machine.multiblock.PartAbility;
import com.gregtechceu.gtceu.api.pattern.BlockPattern;
import com.gregtechceu.gtceu.api.pattern.Predicates;
import com.gregtechceu.gtceu.api.pattern.TraceabilityPredicate;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gregtechceu.gtceu.api.recipe.OverclockingLogic;
import com.gregtechceu.gtceu.api.recipe.modifier.RecipeModifier;
import com.gregtechceu.gtceu.common.data.GTBlocks;
import com.gregtechceu.gtceu.common.data.GTMachines;
import com.gregtechceu.gtceu.common.data.GTRecipeModifiers;
import com.gregtechceu.gtceu.common.data.GTRecipeTypes;
import com.gtladd.gtladditions.api.machine.GTLAddPartAbility;
import com.gtl.enhancedcore.GTLEnhancedcore;
import java.util.function.Function;
import com.gtl.enhancedcore.common.data.machines.multiblock.LargeFurnaceStructure.LargeFurnaceStructure;
import com.gtl.enhancedcore.common.data.machines.multiblock.GTLStructures;
import com.gtl.enhancedcore.common.data.machines.multiblock.IVProcessingStructures;
import com.gtl.enhancedcore.common.util.MachineTooltips;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.Map;
import java.util.function.Supplier;
import org.gtlcore.gtlcore.api.pattern.GTLPredicates;
import org.gtlcore.gtlcore.common.data.GTLRecipeModifiers;
import org.gtlcore.gtlcore.common.data.GTLRecipeTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import com.gtl.enhancedcore.common.machine.*;
import static com.gtl.enhancedcore.common.registration.MachineRegistrationSupport.*;

/** Machine builders, invoked only during the GTCEu registration event. */
public final class ProcessingMachineRegistration {
    private ProcessingMachineRegistration() {}

    public static MultiblockMachineDefinition registerLargeFurnace() {
        return GTLEnhancedcore.REGISTRATE
                .multiblock("large_furnace", holder -> new LargeFurnaceMachine(holder))
                .langValue("大型熔炉")
                .rotationState(RotationState.ALL)
                .appearanceBlock((Supplier) GTBlocks.CASING_STEEL_SOLID)
                .recipeType(GTRecipeTypes.FURNACE_RECIPES)
                .pattern(definition -> LargeFurnaceStructure.create()
                        .where('A', Predicates.controller(Predicates.blocks(new IMachineBlock[]{definition.get()})))
                        .where('L', Predicates.blocks(new Block[]{(Block) GTBlocks.CASING_STEEL_SOLID.get()})
                                .or(Predicates.abilities(PartAbility.IMPORT_ITEMS).setPreviewCount(1))
                                .or(Predicates.abilities(PartAbility.EXPORT_ITEMS).setPreviewCount(1))
                                .or(Predicates.abilities(PartAbility.INPUT_ENERGY).setPreviewCount(1).setMaxGlobalLimited(2))
                                .or(Predicates.abilities(PartAbility.MAINTENANCE).setPreviewCount(1).setMaxGlobalLimited(1))
                                .or(Predicates.abilities(PartAbility.PARALLEL_HATCH).setPreviewCount(1).setMaxGlobalLimited(1))
                                .or(Predicates.abilities(GTLAddPartAbility.INSTANCE.getTHREAD_MODIFIER()).setPreviewCount(1).setMaxGlobalLimited(1)))
                        .where('J', requiredBlock("gtceu:steel_block"))
                        .where('E', requiredBlock("gtceu:firebricks"))
                        .where('D', requiredBlock("gtceu:coke_oven_bricks"))
                        .where('B', Predicates.blocks(new Block[]{Blocks.IRON_BARS}))
                        .where('C', Predicates.blocks(new Block[]{Blocks.IRON_BARS}))
                        .where('G', Predicates.blocks(new Block[]{Blocks.IRON_BARS}))
                        .where('K', Predicates.blocks(new Block[]{Blocks.IRON_BARS}))
                        .where('M', Predicates.blocks(new Block[]{Blocks.IRON_BARS}))
                        .where('H', Predicates.blocks(new Block[]{Blocks.LAVA}))
                        .where('I', Predicates.blocks(new Block[]{Blocks.LAVA}))
                        .where(' ', Predicates.any())
                        .build())
                .tooltipBuilder(MachineTooltips.create("large_furnace", Map.of(
                        "tooltip.gtl_enhancedcore.large_furnace.base_parallel", new Object[]{LargeFurnaceMachine.getBaseParallel()},
                        "tooltip.gtl_enhancedcore.large_furnace.base_threads", new Object[]{LargeFurnaceMachine.getBaseThreads()}),
                        GTRecipeTypes.FURNACE_RECIPES))
                .workableCasingRenderer(GTCEu.id("block/casings/solid/machine_casing_solid_steel"),
                        GTCEu.id("block/multiblock/electric_blast_furnace"))
                .register();
    }

    public static GTRecipeType[] steamPlatformRecipeTypes() {
        return new GTRecipeType[]{
                GTLRecipeTypes.LAVA_FURNACE_RECIPES,
                GTRecipeTypes.FORGE_HAMMER_RECIPES,
                GTRecipeTypes.COMPRESSOR_RECIPES,
                GTRecipeTypes.ALLOY_SMELTER_RECIPES,
                GTRecipeTypes.MACERATOR_RECIPES,
                GTRecipeTypes.CIRCUIT_ASSEMBLER_RECIPES,
                GTRecipeTypes.MIXER_RECIPES,
                GTRecipeTypes.CENTRIFUGE_RECIPES,
                GTRecipeTypes.THERMAL_CENTRIFUGE_RECIPES,
                GTRecipeTypes.CHEMICAL_BATH_RECIPES,
                GTRecipeTypes.ORE_WASHER_RECIPES,
                GTRecipeTypes.FURNACE_RECIPES,
                GTRecipeTypes.EXTRACTOR_RECIPES
        };
    }

    public static TraceabilityPredicate steamPlatformHulls() {
        Int2ObjectOpenHashMap<Supplier<?>> hulls = new Int2ObjectOpenHashMap<>();
        for (int tier = GTValues.ULV; tier <= IndustrialSteamPlatformMachine.MAX_HULL_TIER; tier++) {
            MachineDefinition hull = GTMachines.HULL[tier];
            if (hull == null) {
                throw new IllegalStateException("GTCEu machine hull is not registered for tier " + GTValues.VN[tier]);
            }
            hulls.put(tier, (Supplier<?>) hull::getBlock);
        }
        return GTLPredicates.tierCasings(hulls, IndustrialSteamPlatformMachine.HULL_TIER_KEY)
                .setMinGlobalLimited(1);
    }

    public static MultiblockMachineDefinition registerSteamPlatform() {
        GTRecipeType[] recipeTypes = steamPlatformRecipeTypes();
        return GTLEnhancedcore.REGISTRATE
                .multiblock("industrial_steam_platform", holder -> new IndustrialSteamPlatformMachine(holder))
                .langValue("工业泛用蒸汽机")
                .rotationState(RotationState.NON_Y_AXIS)
                .appearanceBlock((Supplier) GTBlocks.CASING_BRONZE_BRICKS)
                .recipeTypes(recipeTypes)
                // 单配方并行修饰器（用户要求删跨配方线程）：并行/耗电由 GTLCore
                // RecipeModifierList 统一 applyParallel，电压由 getMaxVoltage/getOverclockVoltage 接管。
                .recipeModifier(IndustrialSteamPlatformMachine::recipeModifier, true)
                .pattern(definition -> GTLStructures.steamPlatform(definition, steamPlatformHulls()))
                .tooltipBuilder(MachineTooltips.create("steam_platform", Map.of(
                        "tooltip.gtl_enhancedcore.steam_platform.steam_input",
                        new Object[]{IndustrialSteamPlatformMachine.STEAM_CAPACITY_MB / 1000L},
                        "tooltip.gtl_enhancedcore.steam_platform.steam_cost",
                        new Object[]{IndustrialSteamPlatformMachine.STEAM_PER_RECIPE_MB / 1000L},
                        "tooltip.gtl_enhancedcore.steam_platform.parallel_fixed",
                        new Object[]{IndustrialSteamPlatformMachine.FIXED_PARALLEL}), recipeTypes))
                .workableCasingRenderer(GTCEu.id("block/casings/solid/machine_casing_bronze_plated_bricks"),
                        GTCEu.id("block/multiblock/steam_grinder"))
                .register();
    }

    public static GTRecipeType[] plasmaMachineToolRecipeTypes() {
        return new GTRecipeType[]{
                GTRecipeTypes.LATHE_RECIPES,
                GTRecipeTypes.BENDER_RECIPES,
                GTRecipeTypes.COMPRESSOR_RECIPES,
                GTRecipeTypes.FORGE_HAMMER_RECIPES,
                GTRecipeTypes.CUTTER_RECIPES,
                GTRecipeTypes.FORMING_PRESS_RECIPES,
                GTRecipeTypes.WIREMILL_RECIPES,
                GTRecipeTypes.EXTRUDER_RECIPES,
                GTRecipeTypes.POLARIZER_RECIPES
        };
    }

    public static MultiblockMachineDefinition registerTieredMultiblock(String id, String zhName, String tooltipKey,
                                                                        GTRecipeType[] recipeTypes,
                                                                        Function<MultiblockMachineDefinition, BlockPattern> structureFactory) {
        return GTLEnhancedcore.REGISTRATE
                .multiblock(id, holder -> new TieredParallelMachine(holder))
                .langValue(zhName)
                .rotationState(RotationState.NON_Y_AXIS)
                .appearanceBlock((Supplier) GTBlocks.CASING_TUNGSTENSTEEL_ROBUST)
                .recipeTypes(recipeTypes)
                .recipeModifiers(new RecipeModifier[]{
                        GTLRecipeModifiers.GCYM_REDUCTION,
                        GTRecipeModifiers.PARALLEL_HATCH,
                        GTRecipeModifiers.ELECTRIC_OVERCLOCK.apply(OverclockingLogic.PERFECT_OVERCLOCK_SUBTICK)
                })
                .pattern(structureFactory)
                .tooltipBuilder(MachineTooltips.create(tooltipKey, recipeTypes))
                .workableCasingRenderer(GTCEu.id("block/casings/solid/machine_casing_robust_tungstensteel"),
                        gtlEnhancedcoreVanillaOverlay(id))
                .register();
    }

    public static ResourceLocation gtlEnhancedcoreVanillaOverlay(String id) {
        return switch (id) {
            case "hadron_catalytic_refinery" -> GTCEu.id("block/multiblock/distillation_tower");
            case "quantum_mass_spectrum_array" -> GTCEu.id("block/multiblock/large_miner");
            case "superconducting_fusion_assembler" -> GTCEu.id("block/multiblock/fusion_reactor");
            default -> GTCEu.id("block/multiblock/multiblock_workable");
        };
    }

    public static MultiblockMachineDefinition registerPlasmaMachineTool() {
        return registerTieredMultiblock("plasma_machine_tool", "磁流体约束等离子机床",
                "plasma_machine_tool", plasmaMachineToolRecipeTypes(),
                IVProcessingStructures::plasmaMachine);
    }

    public static GTRecipeType[] hadronRefineryRecipeTypes() {
        return new GTRecipeType[]{
                GTRecipeTypes.MIXER_RECIPES,
                GTRecipeTypes.EVAPORATION_RECIPES,
                GTRecipeTypes.AUTOCLAVE_RECIPES,
                GTRecipeTypes.EXTRACTOR_RECIPES,
                GTRecipeTypes.BREWING_RECIPES,
                GTRecipeTypes.FERMENTING_RECIPES,
                GTRecipeTypes.DISTILLERY_RECIPES,
                GTRecipeTypes.DISTILLATION_RECIPES,
                GTRecipeTypes.FLUID_HEATER_RECIPES,
                GTRecipeTypes.FLUID_SOLIDFICATION_RECIPES,
                GTRecipeTypes.CANNER_RECIPES
        };
    }

    public static GTRecipeType[] quantumMassArrayRecipeTypes() {
        return new GTRecipeType[]{
                GTRecipeTypes.ROCK_BREAKER_RECIPES,
                GTRecipeTypes.ORE_WASHER_RECIPES,
                GTRecipeTypes.CENTRIFUGE_RECIPES,
                GTRecipeTypes.ELECTROLYZER_RECIPES,
                GTRecipeTypes.SIFTER_RECIPES,
                GTRecipeTypes.MACERATOR_RECIPES,
                GTLRecipeTypes.DEHYDRATOR_RECIPES,
                GTRecipeTypes.THERMAL_CENTRIFUGE_RECIPES,
                GTRecipeTypes.ELECTROMAGNETIC_SEPARATOR_RECIPES,
                GTRecipeTypes.CHEMICAL_BATH_RECIPES,
                GTRecipeTypes.LASER_ENGRAVER_RECIPES
        };
    }

    public static GTRecipeType[] fusionAssemblerRecipeTypes() {
        return new GTRecipeType[]{
                GTRecipeTypes.ARC_FURNACE_RECIPES,
                GTLRecipeTypes.LIGHTNING_PROCESSOR_RECIPES,
                GTRecipeTypes.ASSEMBLER_RECIPES,
                GTLRecipeTypes.PRECISION_ASSEMBLER_RECIPES,
                GTRecipeTypes.CIRCUIT_ASSEMBLER_RECIPES
        };
    }

    public static MultiblockMachineDefinition registerBasicOreProcessingPlant() {
        return GTLEnhancedcore.REGISTRATE
                .multiblock("basic_ore_processing_plant", holder -> new BasicOreProcessingPlantMachine(holder))
                .langValue("基础矿石处理厂")
                .rotationState(RotationState.NON_Y_AXIS)
                .appearanceBlock(() -> requiredBlockBlock("gtceu:steam_machine_casing"))
                .recipeTypes(new GTRecipeType[]{GTLRecipeTypes.INTEGRATED_ORE_PROCESSOR})
                .pattern(definition -> GTLStructures.orePlant(definition))
                .tooltipBuilder(MachineTooltips.create("ore_plant", GTLRecipeTypes.INTEGRATED_ORE_PROCESSOR))
                // 蒸汽机械方块材质（2026-08-04）：appearanceBlock=steam_machine_casing（CTM 连接结构主体），
                // 正面 steam_grinder 原版双态 overlay（含 _active/_active_emissive 运行高亮，规则 24）。
                .workableCasingRenderer(GTCEu.id("block/casings/solid/machine_casing_bronze_plated_bricks"),
                        GTCEu.id("block/multiblock/steam_grinder"))
                .register();
    }

    public static MultiblockMachineDefinition registerHadronRefinery() {
        return registerTieredMultiblock("hadron_catalytic_refinery", "强子流态催化精炼塔",
                "hadron_refinery", hadronRefineryRecipeTypes(),
                IVProcessingStructures::hadronRefinery);
    }

    public static MultiblockMachineDefinition registerQuantumMassArray() {
        return registerTieredMultiblock("quantum_mass_spectrum_array", "量子场质谱解离阵列",
                "quantum_mass_array", quantumMassArrayRecipeTypes(),
                IVProcessingStructures::quantumArray);
    }

    public static MultiblockMachineDefinition registerFusionAssembler() {
        return registerTieredMultiblock("superconducting_fusion_assembler", "超导磁约束熔合组装器",
                "fusion_assembler", fusionAssemblerRecipeTypes(),
                IVProcessingStructures::fusionAssembler);
    }
}
