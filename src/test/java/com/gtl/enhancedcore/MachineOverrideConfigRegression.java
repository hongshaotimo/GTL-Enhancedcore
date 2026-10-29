package com.gtl.enhancedcore;

import com.google.gson.JsonParser;
import com.gtl.enhancedcore.common.config.ForeignMachineMixinPolicy;
import com.gtl.enhancedcore.common.config.MachineOverrideConfig;
import com.gtl.enhancedcore.common.config.MachineOverrideConfig.Settings;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Set;

/** Exercises startup file lifecycle and independent Mixin selection for all option combinations. */
final class MachineOverrideConfigRegression {
    private static int assertions;

    static int run() throws Exception {
        assertions = 0;
        Path directory = Files.createTempDirectory("enhancedcore-machine-switches-");
        try {
            Path file = directory.resolve("config/startup.properties");
            check(MachineOverrideConfig.read(file).equals(new Settings(true, true)), "First start enables both switches");
            check(Files.readString(file).contains("完整重启"), "Generated file has UTF-8 instructions");
            for (boolean structures : new boolean[]{false, true}) {
                for (boolean enhancements : new boolean[]{false, true}) {
                    Files.writeString(file, "# user comment\nreplaceOriginalStructures=" + structures
                            + "\nenhanceOriginalMachines=" + enhancements + "\nextra=preserved\n");
                    var settings = MachineOverrideConfig.read(file);
                    check(settings.replaceOriginalStructures() == structures, "Structure setting follows the file");
                    check(settings.enhanceOriginalMachines() == enhancements, "Enhancement is independent of structures");
                    check(Files.readString(file).contains("extra=preserved"), "Reading preserves unknown keys");
                    String saved = Files.readString(file);
                    check(MachineOverrideConfig.read(file).equals(settings), "Restart keeps effective settings");
                    check(Files.readString(file).equals(saved), "Unchanged settings do not rewrite user files");
                    selection(settings);
                }
            }
            Files.writeString(file, "replaceOriginalStructures=false\n");
            check(MachineOverrideConfig.read(file).equals(new Settings(false, true)), "Missing second key defaults on independently");
            Files.writeString(file, "enhanceOriginalMachines=false\n");
            check(MachineOverrideConfig.read(file).equals(new Settings(true, false)), "Missing structure key defaults to on");
            Files.writeString(file, "replaceOriginalStructures=perhaps\n");
            try {
                MachineOverrideConfig.read(file);
                throw new AssertionError("Invalid values must not silently enable enhancements");
            } catch (IllegalArgumentException expected) { assertions++; }
            check(Files.readString(file).contains("perhaps"), "Invalid input stays available for correction");
        } finally {
            try (var files = Files.walk(directory)) {
                for (Path path : files.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
            }
        }
        return assertions;
    }

    private static void selection(Settings settings) throws Exception {
        Set<String> structures = Set.of("gtceu.GtlMegastructurePatternMixin", "gtceu.LucidEtchdreamerPreviewMixin",
                "gtlcore.client.LucidSparsePreviewShapeMixin");
        Set<String> shared = Set.of("gtceu.IvNativeParallelMixin", "gtceu.GalaxyRecipeLogicMixin", "gtceu.GalaxyMachineDisplayMixin", "gtceu.GalaxyConnectionHatchUiMixin", "gtladditions.GalaxyMutableModeMixin", "gtceu.RetiredLightHunterDefinitionMixin",
                "gtlcore.CreativeComputationSchedulerMixin", "gtlcore.CreativeComputationTransactionAccessor",
                "gtladditions.GalaxyThreadCapacityMixin", "gtladditions.GalaxyMachineCapacityMixin", "gtladditions.GalaxySpecialBudgetMixin", "gtladditions.GalaxyAddParallelMixin",
                "gtladditions.GalaxyWirelessEnergyHandlerMixin",
                "gtlcore.GalaxyParallelCapacityMixin", "gtlcore.GalaxyChanceSimulationMixin", "mc.PersonalPlotServerAccessor", "gtceu.PersonalPlotDimensionConditionMixin",
                "gtlcore.client.BloomPendingPruneMixin",
                "gtlcore.client.PreviewFootprintInfoMixin",
                "travelanchors.TravelAnchorRangeMixin",
                "gtceu.BlockPatternAccessor", "gtceu.MultiblockMachineDefinitionAccessor",
                "gtceu.RecipeModifierListAccessor", "gtceu.IMultiControllerLockFixMixin",
                "gtceu.MultiblockControllerLockFixMixin", "gtceu.MultiblockControllerMachinePartsFixMixin",
                "gtceu.WorkableActiveBlocksSnapshotMixin",
                "gtceu.MultiblockPartControllerPositionsAccessor",
                "gtceu.client.RemoteMachineUiHolderMixin",
                "gtceu.DisableNaturalOreGenerationMixin", "gtceu.LampMaterialStackMixin",
                "gtceu.MultiblockAutoRenameSuperBufferMixin", "gtceu.MachineDiagnosticDisplayMixin",
                "gtlcore.GTLItemsDebugPatternToolMixin", "gtlcore.TerminalLampCandidatesMixin",
                "gtlcore.StructureDetectNormalModeMixin",
                "gtlcore.WirelessTargetNetworksRequestMixin",
                "gtlcore.UltimateTerminalDismantleMixin",
                "gtlcore.TerminalDismantlePatternAccessor",
                "gtlcore.AdaptiveMaintenanceGravityConditionMixin",
                "gtladditions.BiosphereModulePowerMixin",
                "gtladditions.BiosphereHostEnergyInvoker",
                "gtladditions.GridNetworkTerminalBigIntegerMixin",
                "gtmadvancedhatch.GridEnergyRecipeInputMixin", "gtceu.EnergyContainerListAccessor");
        var config = JsonParser.parseString(Files.readString(Path.of(
                "src/main/resources/gtl_enhancedcore.mixins.json"))).getAsJsonObject();
        for (String section : new String[]{"mixins", "client"}) {
            for (var entry : config.getAsJsonArray(section)) {
                String name = entry.getAsString();
                boolean retained = shared.contains(name) || name.startsWith("ae2.") || name.startsWith("ae2ct.")
                        || name.startsWith("gtceu.RetiredMachine") || name.equals("mc.RetiredMachineRecipesMixin")
                        || name.startsWith("gtceu.Galaxy") || name.equals("gtladditions.GalaxyWirelessPowerBudgetMixin")
                        || name.equals("gtlcore.client.ChamberPreviewWidgetMixin")
                        || name.equals("gtceu.CloudMachineIdentityMixin") || name.startsWith("gtlcore.Cloud")
                        || name.startsWith("extendedae.client.") || name.startsWith("ftblibrary.client.")
                        || name.startsWith("ftbquests.client.")
                        || name.startsWith("jei.") || name.startsWith("jer.") || name.startsWith("jade.")
                        || name.startsWith("ldlib.") || name.startsWith("performance.") || name.startsWith("ftbchunks.")
                        || name.startsWith("mc.client.") || name.equals("mc.ChunkBlockEntityRemovalMixin") || name.equals("mc.SingularityConfigRecipesMixin")
                        || name.equals("gtceu.client.KeyBindDisconnectedSendMixin")
                        || name.equals("gtlcore.client.ForgeClientEventListenerJeiExtractMixin")
                        || name.equals("gtceu.client.MultiblockPreviewLayerConfigMixin")
                        || name.equals("gtlcore.client.PreviewSharedMemoryMixin")
                        || name.equals("gtlcore.client.PreviewPatternMemoryMixin")
                        || name.startsWith("gtlcore.IvBuffer") || name.startsWith("gtlcore.IvPatternBuffer")
                        || name.equals("gtlcore.IvSuperBufferMixin") || name.equals("gtlcore.IvCompatibleBufferUiMixin")
                        || name.equals("gtlcore.IvTransfiniteCraftingMixin") || name.equals("gtlcore.IvSmartDispatchMixin")
                        || name.equals("gtlcore.PatternBufferTerminalNameMixin")
                        || name.equals("gtlcore.SuperBufferNamePersistenceMixin");
                boolean expected = name.equals("gtceu.GtlMegastructurePatternMixin")
                        ? settings.replaceOriginalStructures() || settings.enhanceOriginalMachines()
                        : structures.contains(name) ? settings.replaceOriginalStructures()
                        : retained || settings.enhanceOriginalMachines();
                check(ForeignMachineMixinPolicy.shouldApply(settings, "com.gtl.enhancedcore.mixin." + name) == expected,
                        "Mixin feature boundary: " + name + " / " + settings);
            }
        }
    }

    private static void check(boolean valid, String message) {
        if (!valid) throw new AssertionError(message);
        assertions++;
    }
}
