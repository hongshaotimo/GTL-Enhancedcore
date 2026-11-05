package com.gtl.enhancedcore.audit;

import com.gregtechceu.gtceu.api.machine.multiblock.WorkableMultiblockMachine;
import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import java.util.Set;
import com.gtl.enhancedcore.common.recipe.iv.IvMachineScope;

/** Audit inventory of native execution overrides requiring focused evidence.
 * This report does not disable existing cloud execution or change machine capacity. */
public final class CloudNativeExecutionCoverage {
    private static final Set<String> EXECUTION_METHODS = Set.of("serverTick", "handleRecipeWorking",
            "handleTickRecipe", "onRecipeFinish", "checkMatchedRecipeAvailable", "checkRecipe",
            "getGTRecipe", "getRecipe", "calculateParallels", "calculateParallel",
            "buildFinalNormalRecipe", "buildFinalWirelessRecipe");
    private static final Set<String> SUPPORTED_OWNERS = Set.of(
            "com.gregtechceu.gtceu.api.machine.trait.RecipeLogic",
            "org.gtlcore.gtlcore.common.machine.trait.MultipleRecipesLogic",
            "com.gtladd.gtladditions.api.machine.logic.GTLAddMultipleRecipesLogic",
            "com.gtladd.gtladditions.api.machine.logic.GTLAddMultipleWirelessRecipesLogic",
            "com.gtladd.gtladditions.api.machine.logic.GTLAddMultipleTypeWirelessRecipesLogic",
            "com.gtladd.gtladditions.api.machine.logic.MutableRecipesLogic",
            "com.gtladd.gtladditions.api.machine.logic.AddMutableRecipesLogic",
            "com.gtladd.gtladditions.common.machine.multiblock.controller.AntientropyCondensationCenter$Companion$AntientropyCondensationCenterLogic",
            "com.gtladd.gtladditions.common.machine.multiblock.controller.MacroAtomicResonantFragmentStripper$Companion$MacroAtomicResonantFragmentStripperLogic",
            "com.gtladd.gtladditions.common.machine.multiblock.controller.SpaceInfinityIntegratedOreProcessor$Companion$InfinityIntegratedOreProcessorLogic",
            "com.gtladd.gtladditions.common.machine.multiblock.controller.OreProcessorMachine$Companion$OreProcessorRecipeLogic",
            "com.gtladd.gtladditions.common.machine.multiblock.controller.DimensionallyTranscendentChemicalPlant$Companion$DimensionallyTranscendentChemicalPlantLogic",
            "com.gtladd.gtladditions.common.machine.multiblock.controller.mutable.MutablePCBFactoryMachine$createRecipeLogic$1",
            "com.gtladd.gtladditions.common.modify.MutableMultiBlockModify$setOtherMutable$13$1$createRecipeLogic$1",
            "com.gtladd.gtladditions.common.modify.MutableMultiBlockModify$setOtherMutable$14$1$createRecipeLogic$1",
            "com.gtl.enhancedcore.common.recipe.RetryableRecipeLogic",
            "com.gtladd.gtladditions.common.machine.multiblock.controller.SpaceScalingInstrument$Companion$SpaceScalingInstrumentLogic",
            "com.gtladd.gtladditions.common.machine.multiblock.controller.ForgeOfTheAntichrist$Companion$ForgeOfTheAntichristLogic",
            "com.gtladd.gtladditions.common.machine.multiblock.controller.module.ForgeOfTheAntichristModuleBase$Companion$ForgeOfTheAntichristModuleBaseLogic",
            "com.gtladd.gtladditions.common.machine.multiblock.controller.module.HelioFusionExoticizer$Companion$HelioFusionExoticizerLogic");
    private static final ClassValue<String> UNSUPPORTED = new ClassValue<>() {
        @Override protected String computeValue(Class<?> logic) {
            for (Class<?> type = logic; type != null && RecipeLogic.class.isAssignableFrom(type); type = type.getSuperclass()) {
                if (SUPPORTED_OWNERS.contains(type.getName())) continue;
                for (var method : type.getDeclaredMethods())
                    if (!method.isBridge() && EXECUTION_METHODS.contains(method.getName()))
                        return type.getName() + "." + method.getName();
            }
            return "";
        }
    };

    private CloudNativeExecutionCoverage() {}

    /** Empty means no uncovered override; otherwise identifies the exact native method
     * still needing focused runtime evidence. Never use this audit result as admission. */
    public static String unsupported(WorkableMultiblockMachine machine) {
        if (IvMachineScope.nativeTarget(machine) || machine instanceof com.gtl.enhancedcore.common.machine.TieredParallelMachine)
            return "";
        return unsupported(machine.getRecipeLogic().getClass());
    }

    public static String unsupported(Class<?> logic) { return UNSUPPORTED.get(logic); }
}
