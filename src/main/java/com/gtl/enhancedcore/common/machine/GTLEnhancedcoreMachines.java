package com.gtl.enhancedcore.common.machine;

import com.gtl.enhancedcore.common.registration.*;
import java.util.ArrayList;
import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gtl.enhancedcore.GTLEnhancedcore;
import com.gtl.enhancedcore.common.data.GTLEnhancedcoreRecipeTypes;


import java.util.List;


/**
 * GTL-Enhancedcore 机器注册中心。
 *
 * 规则（见 API标准.md「注册时序」）：
 * - init() 由 GTCEu MachineDefinition 注册事件触发，必须幂等，不依赖静态字段副作用。
 * - 外部只能通过 getXxx() 访问已注册机器，禁止直接读公有字段。
 * - 每个 Builder 的结构 Pattern 通过 LargeFurnaceStructure.create() 每次新建。
 * - 固定文字全部走翻译键（zh_cn.json / en_us.json），Java 代码内禁止拼接 § 颜色码。
 */
public final class GTLEnhancedcoreMachines {

    private static volatile MachineDefinition galaxySourceHatch, galaxyTargetHatch;
    private static volatile MachineDefinition cloudPatternLibrary, cloudPatternBuffer;
    private static volatile MultiblockMachineDefinition stellarVortexStation;
    private static volatile MachineDefinition meDrive;
    private static volatile MachineDefinition claimReplacementTerminal;
    private static volatile MachineDefinition quantumDataAccessHatch;
    private static volatile MachineDefinition creativeComputationReceiverHatch;
    private static volatile MachineDefinition circuitEncoderHatch;
    private static volatile MachineDefinition adaptiveMaintenanceHatch;
    private static volatile MultiblockMachineDefinition largeFurnace;
    private static volatile MultiblockMachineDefinition steamPlatform;
    private static volatile MultiblockMachineDefinition plasmaMachineTool;
    private static volatile MultiblockMachineDefinition hadronRefinery;
    private static volatile MultiblockMachineDefinition quantumMassArray;
    private static volatile MultiblockMachineDefinition fusionAssembler;
    private static volatile MultiblockMachineDefinition weatherAnchor;
    private static volatile MultiblockMachineDefinition causalityTerminal;
    private static volatile MultiblockMachineDefinition infinitySingularityCompressor;
    private static volatile MultiblockMachineDefinition neutronControlFactory;
    private static volatile MultiblockMachineDefinition starCoreStripper;
    private static volatile MultiblockMachineDefinition highFluxBreederReactor;
    private static volatile MultiblockMachineDefinition hydrothermalLeachingComplex;
    private static volatile MultiblockMachineDefinition extremeReactor;
    private static volatile MultiblockMachineDefinition platinumRefiningMatrix;
    private static volatile MultiblockMachineDefinition voidConstrainedMiningField;
    private static volatile MultiblockMachineDefinition dragonFieldProliferationCore;
    private static volatile MultiblockMachineDefinition hyperstructuralChemicalDistorter;
    private static volatile MultiblockMachineDefinition stellarConfinementFusionReactor;
    private static volatile MachineDefinition[] crystalResonators;
    private static volatile MachineDefinition[] wirelessChargers;
    private static volatile MultiblockMachineDefinition basicOrePlant;
    private static volatile MultiblockMachineDefinition processingPlus;
    private static volatile MultiblockMachineDefinition assemblingPlus;
    private static volatile MultiblockMachineDefinition separatingPlus;
    private static volatile MultiblockMachineDefinition mixingPlus;
    private static volatile MultiblockMachineDefinition integratedUniversalFactory;
    private static volatile boolean initialized;

    private GTLEnhancedcoreMachines() {
    }

    public static synchronized void init() {
        if (initialized) {
            return;
        }
        GTLEnhancedcoreRecipeTypes.init();
        galaxySourceHatch = GalaxyMachineRegistration.hatch(true);
        galaxyTargetHatch = GalaxyMachineRegistration.hatch(false);
        stellarVortexStation = GalaxyMachineRegistration.station();
        cloudPatternLibrary = CloudPatternRegistration.library();
        cloudPatternBuffer = CloudPatternRegistration.buffer();
        meDrive = PartMachineRegistration.registerMEDrive();
        circuitEncoderHatch = PartMachineRegistration.registerCircuitEncoderHatch();
        adaptiveMaintenanceHatch = PartMachineRegistration.registerAdaptiveMaintenanceHatch();
        claimReplacementTerminal = PartMachineRegistration.registerClaimReplacementTerminal();
        quantumDataAccessHatch = PartMachineRegistration.registerQuantumDataAccessHatch();
        creativeComputationReceiverHatch = PartMachineRegistration.registerCreativeComputationReceiverHatch();
        largeFurnace = ProcessingMachineRegistration.registerLargeFurnace();
        steamPlatform = ProcessingMachineRegistration.registerSteamPlatform();
        plasmaMachineTool = ProcessingMachineRegistration.registerPlasmaMachineTool();
        hadronRefinery = ProcessingMachineRegistration.registerHadronRefinery();
        quantumMassArray = ProcessingMachineRegistration.registerQuantumMassArray();
        fusionAssembler = ProcessingMachineRegistration.registerFusionAssembler();
        weatherAnchor = SpecialMachineRegistration.registerWeatherAnchor();
        causalityTerminal = SpecialMachineRegistration.registerCausalityTerminal();
        infinitySingularityCompressor = SpecialMachineRegistration.registerInfinitySingularityCompressor();
        neutronControlFactory = SpecialMachineRegistration.registerNeutronControlFactory();
        starCoreStripper = SpecialMachineRegistration.registerStarCoreStripper();
        highFluxBreederReactor = SpecialMachineRegistration.registerHighFluxBreederReactor();
        hydrothermalLeachingComplex = com.gtl.enhancedcore.common.registration.HydrothermalLeachingRegistration.register();
        extremeReactor = SpecialMachineRegistration.registerExtremeReactor();
        platinumRefiningMatrix = SpecialMachineRegistration.registerPlatinumRefiningMatrix();
        voidConstrainedMiningField = SpecialMachineRegistration.registerVoidConstrainedMiningField();
        dragonFieldProliferationCore = SpecialMachineRegistration.registerDragonFieldProliferationCore();
        hyperstructuralChemicalDistorter = SpecialMachineRegistration.registerHyperstructuralChemicalDistorter();
        stellarConfinementFusionReactor = SpecialMachineRegistration.registerStellarConfinementFusionReactor();
        crystalResonators = WirelessMachineRegistration.registerCrystalResonators();
        wirelessChargers = WirelessMachineRegistration.registerWirelessChargers();
        basicOrePlant = ProcessingMachineRegistration.registerBasicOreProcessingPlant();
        processingPlus = PlusFactoryRegistration.registerPlusFactory("processing_plus", "通用加工厂-plus", "processing_plus",
                PlusFactoryRegistration.processingPlusRecipeTypes(), "gtceu:bronze_gearbox");
        assemblingPlus = PlusFactoryRegistration.registerPlusFactory("assembling_plus", "通用组装厂-plus", "assembling_plus",
                PlusFactoryRegistration.assemblingPlusRecipeTypes(), "gtceu:stainless_steel_frame");
        separatingPlus = PlusFactoryRegistration.registerPlusFactory("separating_plus", "通用分离厂-plus", "separating_plus",
                PlusFactoryRegistration.separatingPlusRecipeTypes(), "gtceu:bronze_pipe_casing");
        mixingPlus = PlusFactoryRegistration.registerPlusFactory("mixing_plus", "通用混合厂-plus", "mixing_plus",
                PlusFactoryRegistration.mixingPlusRecipeTypes(), "gtceu:steel_pipe_casing");
        integratedUniversalFactory = IntegratedFactoryRegistration.register();
        initialized = true;
        var definitions = all();
        long multiblocks = definitions.stream().filter(MultiblockMachineDefinition.class::isInstance).count();
        long resonators = java.util.Arrays.stream(crystalResonators).filter(java.util.Objects::nonNull).count();
        long chargers = java.util.Arrays.stream(wirelessChargers).filter(java.util.Objects::nonNull).count();
        GTLEnhancedcore.LOGGER.info(
                "GTL-Enhancedcore machines registered: {} single-block/part + {} multiblock + {} crystal resonators + {} wireless chargers",
                definitions.size() - multiblocks - resonators, multiblocks - chargers, resonators, chargers);
    }

    // ==================== 访问器（外部唯一入口） ====================


    public static MachineDefinition getGalaxySourceHatch() { return galaxySourceHatch; }
    public static MachineDefinition getGalaxyTargetHatch() { return galaxyTargetHatch; }
    public static MultiblockMachineDefinition getStellarVortexStation() { return stellarVortexStation; }
    public static MachineDefinition getCloudPatternLibrary() { return cloudPatternLibrary; }
    public static MachineDefinition getCloudPatternBuffer() { return cloudPatternBuffer; }

    public static MachineDefinition getMEDrive() {
        return meDrive;
    }

    public static MachineDefinition getCircuitEncoderHatch() {
        return circuitEncoderHatch;
    }

    public static MachineDefinition getClaimReplacementTerminal() {
        return claimReplacementTerminal;
    }

    public static MachineDefinition getQuantumDataAccessHatch() {
        return quantumDataAccessHatch;
    }

    public static MachineDefinition getCreativeComputationReceiverHatch() {
        return creativeComputationReceiverHatch;
    }

    public static MultiblockMachineDefinition getLargeFurnace() {
        return largeFurnace;
    }

    public static MultiblockMachineDefinition getSteamPlatform() {
        return steamPlatform;
    }

    public static MultiblockMachineDefinition getPlasmaMachineTool() {
        return plasmaMachineTool;
    }

    public static MultiblockMachineDefinition getHadronRefinery() {
        return hadronRefinery;
    }

    public static MultiblockMachineDefinition getQuantumMassArray() {
        return quantumMassArray;
    }

    public static MultiblockMachineDefinition getFusionAssembler() {
        return fusionAssembler;
    }

    public static MultiblockMachineDefinition getWeatherAnchor() {
        return weatherAnchor;
    }

    public static MultiblockMachineDefinition getCausalityTerminal() {
        return causalityTerminal;
    }

    public static MultiblockMachineDefinition getInfinitySingularityCompressor() {
        return infinitySingularityCompressor;
    }

    public static MultiblockMachineDefinition getNeutronControlFactory() {
        return neutronControlFactory;
    }

    public static MultiblockMachineDefinition getStarCoreStripper() {
        return starCoreStripper;
    }

    public static MultiblockMachineDefinition getHighFluxBreederReactor() {
        return highFluxBreederReactor;
    }

    public static MultiblockMachineDefinition getHydrothermalLeachingComplex() {
        return hydrothermalLeachingComplex;
    }

    public static MultiblockMachineDefinition getExtremeReactor() {
        return extremeReactor;
    }

    public static MultiblockMachineDefinition getPlatinumRefiningMatrix() {
        return platinumRefiningMatrix;
    }

    public static MultiblockMachineDefinition getVoidConstrainedMiningField() {
        return voidConstrainedMiningField;
    }

    public static MultiblockMachineDefinition getDragonFieldProliferationCore() {
        return dragonFieldProliferationCore;
    }

    public static MultiblockMachineDefinition getHyperstructuralChemicalDistorter() {
        return hyperstructuralChemicalDistorter;
    }

    public static MultiblockMachineDefinition getStellarConfinementFusionReactor() {
        return stellarConfinementFusionReactor;
    }

    public static MachineDefinition[] getResonators() {
        return crystalResonators == null ? new MachineDefinition[0] : crystalResonators.clone();
    }

    public static MachineDefinition[] getWirelessChargers() {
        return wirelessChargers == null ? new MachineDefinition[0] : wirelessChargers.clone();
    }

    public static MultiblockMachineDefinition getBasicOrePlant() {
        return basicOrePlant;
    }

    public static MultiblockMachineDefinition getProcessingPlus() { return processingPlus; }

    public static MultiblockMachineDefinition getAssemblingPlus() { return assemblingPlus; }

    public static MultiblockMachineDefinition getSeparatingPlus() { return separatingPlus; }

    public static MultiblockMachineDefinition getMixingPlus() { return mixingPlus; }

    public static MultiblockMachineDefinition getIntegratedUniversalFactory() {
        return integratedUniversalFactory;
    }


    /** Stable registration order shared by creative tabs and diagnostics. */
    public static List<MachineDefinition> all() {
        if (!initialized) return List.of();
        List<MachineDefinition> result = new ArrayList<>(List.of(
                galaxySourceHatch, galaxyTargetHatch, stellarVortexStation,
                cloudPatternLibrary, cloudPatternBuffer,
                meDrive, circuitEncoderHatch, adaptiveMaintenanceHatch, claimReplacementTerminal, quantumDataAccessHatch,
                creativeComputationReceiverHatch,
                largeFurnace, steamPlatform, plasmaMachineTool, hadronRefinery, quantumMassArray,
                fusionAssembler, weatherAnchor, causalityTerminal, infinitySingularityCompressor,
                neutronControlFactory, starCoreStripper, highFluxBreederReactor, hydrothermalLeachingComplex,
                extremeReactor, platinumRefiningMatrix,
                voidConstrainedMiningField, dragonFieldProliferationCore,
                hyperstructuralChemicalDistorter, stellarConfinementFusionReactor,
                basicOrePlant, processingPlus, assemblingPlus, separatingPlus, mixingPlus, integratedUniversalFactory));
        for (MachineDefinition definition : crystalResonators) if (definition != null) result.add(definition);
        for (MachineDefinition definition : wirelessChargers) if (definition != null) result.add(definition);
        return List.copyOf(result);
    }
}
