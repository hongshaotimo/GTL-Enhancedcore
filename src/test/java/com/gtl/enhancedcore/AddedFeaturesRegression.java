package com.gtl.enhancedcore;

import com.gtl.enhancedcore.common.config.PreviewLayerSettings;
import java.nio.file.Files;
import java.nio.file.Path;

/** Contracts for the requested ADD threads, client preview option and MAX maintenance part. */
final class AddedFeaturesRegression {
    static int run() throws Exception {
        int checks = 0;
        check(PreviewLayerSettings.parse("# comment\npreviewLayers=true\n"), "True setting is parsed"); checks++;
        check(!PreviewLayerSettings.parse("previewLayers=false\n"), "False setting is parsed"); checks++;
        try {
            PreviewLayerSettings.parse("previewLayers=maybe\n");
            throw new AssertionError("Invalid preview layer setting must be rejected");
        } catch (IllegalArgumentException expected) { checks++; }

        String addRegistration = Files.readString(Path.of(
                "src/main/java/com/gtl/enhancedcore/common/registration/AddMutableMachineRegistration.java"));
        String mutable = Files.readString(Path.of(
                "src/main/java/com/gtl/enhancedcore/common/machine/WoodDistillationMutableMachine.java"));
        check(addRegistration.contains("MultiBlockMachineB.WOOD_DISTILLATION")
                        && addRegistration.contains("applyWoodDistillation()"),
                "Wood distillation is swapped to the ADD mutable machine supplier"); checks++;
        check(mutable.contains("CROSS_RECIPE_THREADS = 15") && mutable.contains("threadBaseline()"),
                "Wood distillation uses 15 ADD cross-recipe threads as its base"); checks++;
        check(mutable.contains("onStructureFormed()") && mutable.contains("setUseMultipleRecipes(true)"),
                "Wood distillation enables ADD multiple-recipe mode after formation"); checks++;
        String tooltipMixin = Files.readString(Path.of(
                "src/main/java/com/gtl/enhancedcore/mixin/gtceu/IvNativeTooltipMixin.java"));
        check(tooltipMixin.contains("id.getPath().equals(\"wood_distillation\")")
                        && tooltipMixin.contains("\"wood_distillation\""),
                "Wood distillation has an explicit ADD tooltip route"); checks++;
        check(com.gtl.enhancedcore.common.util.TooltipPolicy.page("wood_distillation").summary()
                        .contains("tooltip.gtl_enhancedcore.wood_distillation.threads"),
                "Wood distillation tips declare the 15-thread ADD capability"); checks++;
        check(!Files.readString(Path.of("src/main/java/com/gtl/enhancedcore/common/recipe/iv/IvMachineScope.java"))
                        .contains("wood_distillation"), "Wood distillation stays outside IV isolation"); checks++;

        String previewMixin = Files.readString(Path.of(
                "src/main/java/com/gtl/enhancedcore/mixin/gtceu/client/MultiblockPreviewLayerConfigMixin.java"));
        check(previewMixin.contains("!PreviewLayerConfig.enabled()") && previewMixin.contains("LAST_LAYER = -1"),
                "Disabled local option resets GTLCore's layer selector"); checks++;
        String resonator = Files.readString(Path.of(
                "src/main/java/com/gtl/enhancedcore/common/machine/EndCrystalResonatorMachine.java"));
        check(resonator.contains("GENERATION_MULTIPLIER = 2L")
                        && resonator.contains("amperageForTier(tier) * GENERATION_MULTIPLIER"),
                "Every end-crystal resonator generates twice its previous EU/t"); checks++;

        String maintenance = Files.readString(Path.of(
                "src/main/java/com/gtl/enhancedcore/common/machine/hatch/AdaptiveMaintenanceHatchMachine.java"));
        check(maintenance.contains("CleanroomType.CLEANROOM")
                        && maintenance.contains("CleanroomType.STERILE_CLEANROOM")
                        && maintenance.contains("GTLCleanroomType.LAW_CLEANROOM") && maintenance.contains("return true;"),
                "Adaptive MAX hatch provides clean, sterile and law cleanroom environments"); checks++;
        check(maintenance.contains("implements ICleanroomProvider, IGravityPartMachine")
                        && maintenance.contains("public int getCurrentGravity()"),
                "Adaptive MAX hatch exposes GTLCore gravity-part capability"); checks++;
        String gravityMixin = Files.readString(Path.of(
                "src/main/java/com/gtl/enhancedcore/mixin/gtlcore/AdaptiveMaintenanceGravityConditionMixin.java"));
        check(gravityMixin.contains("GravityCondition.class")
                        && gravityMixin.contains("AdaptiveMaintenanceHatchMachine.class::isInstance")
                        && gravityMixin.contains("cir.setReturnValue(true)"),
                "Adaptive MAX hatch satisfies both discrete GTLCore gravity conditions"); checks++;
        String tips = Files.readString(Path.of("src/main/java/com/gtl/enhancedcore/common/util/MachineTooltips.java"));
        // 2026-10-25 用户定稿配色：洁净/重力能力行保持绿色，其余行白色，署名为动态彩虹。
        check(tips.contains("ChatFormatting.RED") && tips.contains("ChatFormatting.GREEN")
                        && tips.contains("ChatFormatting.WHITE") && tips.contains("TooltipPolicy.CREDIT_KEY"),
                "Adaptive MAX hatch tooltip uses the cleanroom styling and the shared credit row"); checks++;
        return checks;
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
