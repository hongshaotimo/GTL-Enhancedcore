package com.gtl.enhancedcore.common.config;

import java.util.Set;

/** Explicit feature groups; package-wide filters would also remove shared UI and safety fixes. */
public final class ForeignMachineMixinPolicy {
    private static final String PREFIX = "com.gtl.enhancedcore.mixin.";
    private static final Set<String> STRUCTURE_MIXINS = Set.of(
            "gtceu.GtlMegastructurePatternMixin", "gtceu.LucidEtchdreamerPreviewMixin",
            "gtlcore.client.LucidSparsePreviewShapeMixin");
    private static final Set<String> ENHANCEMENT_MIXINS = Set.of(
            "gtladditions.SuperBufferWideInventoryMixin",
            "gtladditions.SuperBufferWideLimitInventoryMixin",
            "gtceu.IvNativeRecipeLogicMixin", "gtceu.IvNativeModifierMixin",
            "gtceu.IvNativeMutableAccessor",
            "gtlcore.IvNativeMultipleAccessor", "gtlcore.IvComputationRecipesMixin", "gtlcore.IvComputationScopeMixin",
            "gtlcore.IvNativeAddMultipleAccessor",
            "gtlcore.IvNativeBatchMixin", "gtlcore.IvNativeMultipleGuardMixin", "gtlcore.IvDissolvingTankMixin",
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
        if (name.equals("gtceu.GtlMegastructurePatternMixin"))
            return settings.replaceOriginalStructures() || settings.enhanceOriginalMachines();
        if (STRUCTURE_MIXINS.contains(name)) return settings.replaceOriginalStructures();
        if (ENHANCEMENT_MIXINS.contains(name)) return settings.enhanceOriginalMachines();
        // Includes buffer/CPU bridges used by our own IV machines, UI, performance and general fixes.
        return true;
    }
}
