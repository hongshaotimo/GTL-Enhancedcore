package com.gtl.enhancedcore.common.config;

import java.util.Set;

/** Explicit feature groups; package-wide filters would also remove shared UI and safety fixes. */
public final class ForeignMachineMixinPolicy {
    private static final String PREFIX = "com.gtl.enhancedcore.mixin.";
    private static final Set<String> PERSONAL_WORLD_LIFECYCLE_MIXINS = Set.of(
            "mc.PersonalPlotFilePackMixin", "mc.PersonalPlotStorageMixin");
    private static final Set<String> STRUCTURE_MIXINS = Set.of(
            "gtceu.GtlMegastructurePatternMixin", "gtceu.LucidEtchdreamerPreviewMixin",
            "gtlcore.client.LucidSparsePreviewShapeMixin",
            "gtlcore.client.EyeOfHarmonyEffectAnchorMixin",
            "gtlcore.client.CelestialEffectViewDistanceMixin",
            "gtladditions.client.ArcanicAstrographEffectAnchorMixin",
            "gtladditions.client.HeartOfTheUniverseEffectSizeMixin",
            "gtladditions.client.HeartOfTheUniverseEffectBoundsMixin",
            "gtladditions.client.HeartOfTheUniverseShaderMixin",
            "gtladditions.ArcanicAstrographCollectionAnchorMixin");
    private static final Set<String> ENHANCEMENT_MIXINS = Set.of(
            "gtlcore.PrimitiveVoidOreRecipeMixin", "gtlcore.PrimitiveVoidOreDimensionMixin",
            "gtlcore.PrimitiveVoidOreRecipeTypeMixin", "gtlcore.PrimitiveVoidOreStructureMixin",
            "gtlcore.PrimitiveVoidOreTooltipMixin",
            "gtlcore.HarmonyAddRecipesMixin", "gtladditions.ArcanicAddRecipesMixin",
            "jade.HarmonyAddParallelProviderMixin",
            "gtladditions.IvNativeForgeStateAccessor", "gtladditions.IvNativeForgeRecipeAccessor",
            "gtladditions.IvNativeAntientropyAccessor",
            "gtladditions.IvNativeTaixuAccessor", "gtladditions.IvNativeApocalypticAccessor",
            "gtladditions.SuperBufferWideInventoryMixin",
            "gtladditions.SuperBufferWideLimitInventoryMixin",
            "gtceu.IvNativeModifierMixin",
            "gtceu.IvNativeMutableAccessor",
            "gtlcore.IvNativeMultipleAccessor", "gtlcore.IvComputationRecipesMixin", "gtlcore.IvComputationScopeMixin",
            "gtlcore.IvNativeAddMultipleAccessor",
            "gtlcore.IvNativeBatchMixin", "gtlcore.IvDissolvingTankMixin",
            "gtladditions.IvNativeBiologicalInputGuardMixin", "gtladditions.IvNativeAstrographInputGuardMixin",
            "gtlcore.IvNativeRecipeThreadLimitMixin", "gtlcore.IvNativeGtlAddThreadLimitMixin",
            "gtceu.IvNativeMachineUiMixin", "gtceu.IvNativeTooltipMixin",
            "gtladditions.SuprachronalModuleHostRemovalMixin",
            "gtladditions.SuprachronalAssemblyLineModuleRemovalMixin",
            "gtladditions.SuprachronalModulePreviewRemovalMixin",
            "mc.PlantResearchRecipeMixin", "mc.MvCircuitAssemblerRecipeMixin", "gtceu.MvCircuitAssemblerTooltipMixin",
            "gtlcore.AutoConfigMaintenanceHatchMixin", "gtlcore.AutoConfigMaintenanceHatchAccessor",
            "gtlcore.BedrockDrillingRigNoBreakMixin", "gtlcore.AggregationDevicePatternBufferMixin",
            "gtlcore.SpaceProbeRecipeNoCWUMixin", "gtlcore.FissionReactorMachineMixin",
            "gtceu.FusionReactorParallelMixin", "gtceu.MachineDefinitionFusionParallelMixin",
            "gtceu.MachineDefinitionFissionTooltipMixin", "gtceu.MachineDefinitionFusionTooltipMixin",
            "gtceu.MachineDefinitionPatternBufferTooltipMixin", "gtceu.MachineDefinitionAssemblyLineParallelMixin",
            "gtceu.AssemblyLinePatternBufferMixin", "gtceu.EngravingLaserComputationMixin",
            "gtceu.EngravingLaserRecipeLogicMixin", "gtceu.MachineDefinitionEngravingLaserTooltipMixin",
            "gtceu.MachineDefinitionDimensionalFocusEngravingArrayMixin",
            "gtceu.MachineDefinitionDimensionalFocusEngravingArrayTooltipMixin",
            "gtceu.MachineDefinitionStarUltimateForgeParallelMixin",
            "gtceu.MachineDefinitionStarUltimateForgeTooltipMixin",
            "gtceu.MultiblockMachineDefinitionStarUltimateForgeDisplayMixin",
            "gtceu.client.StellarForgeRendererMixin");

    private ForeignMachineMixinPolicy() {}

    public static boolean shouldApply(MachineOverrideConfig.Settings settings, String mixinClassName) {
        String name = mixinClassName.startsWith(PREFIX) ? mixinClassName.substring(PREFIX.length()) : mixinClassName;
        if (PERSONAL_WORLD_LIFECYCLE_MIXINS.contains(name)) return true;
        if (name.equals("gtceu.GtlMegastructurePatternMixin"))
            return settings.replaceOriginalStructures() || settings.enhanceOriginalMachines();
        if (STRUCTURE_MIXINS.contains(name)) return settings.replaceOriginalStructures();
        if (ENHANCEMENT_MIXINS.contains(name)) return settings.enhanceOriginalMachines();
        // Includes buffer/CPU bridges used by our own IV machines, UI, performance and general fixes.
        return true;
    }
}
