package com.gtl.enhancedcore;

import com.google.gson.JsonParser;
import com.gtl.enhancedcore.common.config.ConfigFiles;
import com.gtl.enhancedcore.common.config.SingularityRecipeConfig;
import com.gtl.enhancedcore.common.structure.StructureData;
import com.gtl.enhancedcore.common.util.FairRecipeSelector;
import com.gtl.enhancedcore.common.recipe.FusionParallelPolicy;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.zip.GZIPOutputStream;

/** Dependency-light regressions run by Gradle check without starting Minecraft. */
public final class RegressionSuite {
    private static int assertions;

    public static void main(String[] args) throws Exception {
        configParsing();
        structureMigration();
        fairScheduling();
        atomicFiles();
        assertions += WeatherAnchorPowerRegression.run();
        assertions += CreativeComputationPolicyRegression.run();
        assertions += EquipmentRecipesRegression.run();
        assertions += PatternGeneratorRegression.run();
        assertions += BlackHoleFrameRegression.run();
        assertions += StellarForgeRegression.run();
        assertions += IvIsolationRegression.run();
        assertions += IvDisplayRegression.run();
        assertions += IvCancellationRegression.run();
        assertions += SuperBufferAutoNameRegression.run();
        assertions += SuperBufferJadeNameRegression.run();
        assertions += Audit20260925Regression.run();
        assertions += StructureUpgradeRegression.run();
        assertions += LucidDragonStructureRegression.run();
        assertions += MaintenanceUpgradeRegression.run();
        assertions += StructureCasingRegression.run();
        assertions += UpstreamPreviewContractRegression.run();
        assertions += IntegratedFactoryRegression.run();
        assertions += ThreadBudgetRegression.run();
        assertions += FlightStateConcurrencyRegression.run();
        assertions += CoilDebugToolRegression.run();
        assertions += RecipeRecoveryRegression.run();
        assertions += ChunkUnloadRegression.run();
        assertions += ModuleStabilityRegression.run();
        assertions += SuprachronalModuleRemovalRegression.run();
        assertions += IvNativeRecoveryRegression.run();
        assertions += TooltipPolicyRegression.run();
        assertions += ResonatorPlacementRegression.run();
        assertions += BlockReplacementTransactionRegression.run();
        check(FusionParallelPolicy.limit("gtceu", "luv_fusion_reactor") == 128, "MK1 parallel limit");
        check(FusionParallelPolicy.limit("gtceu", "zpm_fusion_reactor") == 128, "MK2 parallel limit");
        check(FusionParallelPolicy.limit("gtceu", "uev_fusion_reactor") == 512, "MK5 parallel limit");
        check(FusionParallelPolicy.limit("other", "luv_fusion_reactor") == 0, "other mods excluded");
        check(FusionParallelPolicy.limit("gtceu", "compressed_fusion_reactor") == 0, "compressed variant unchanged");
        stellarWirelessContract();
        System.out.println("Regression suite passed: " + assertions + " assertions");
    }

    /**
     * 恒星约束聚变堆取电/并行模型对齐 {@code gtladditions:forge_of_the_antichrist}：
     * 必须用无线多配方基类，不能再挂回 GTCEu 原生聚变堆的预热修饰器与字段托管。
     */
    private static void stellarWirelessContract() throws Exception {
        String machine = Files.readString(Path.of(
                "src/main/java/com/gtl/enhancedcore/common/machine/StellarConfinementFusionReactorMachine.java"));
        check(machine.contains("extends GTLAddWirelessWorkableElectricMultipleRecipesMachine"),
                "stellar fusion must extend the wireless multiple-recipes base class");
        check(!machine.contains("FusionReactorMachine.MANAGED_FIELD_HOLDER"),
                "stellar fusion must not inherit the native fusion machine field holder");
        check(machine.contains("FIXED_DURATION_TICKS = 20"), "stellar fusion keeps the fixed 20-tick batch");
        check(machine.contains("setLimitedDuration(FIXED_DURATION_TICKS)"),
                "stellar fusion must force the fixed batch duration on formation");
        check(machine.contains("return Integer.MAX_VALUE;") && machine.contains("getMultipleThreads()"),
                "stellar fusion must remove the upstream 128-thread cap");
        String structures = Files.readString(Path.of(
                "src/main/java/com/gtl/enhancedcore/common/data/machines/multiblock/GTLStructures.java"));
        int from = structures.indexOf("public static BlockPattern stellarConfinementFusionReactor");
        int to = structures.indexOf("public static BlockPattern", from + 1);
        String stellar = structures.substring(from, to < 0 ? structures.length() : to);
        check(stellar.contains("PartAbility.IMPORT_FLUIDS") && stellar.contains("PartAbility.EXPORT_FLUIDS")
                        && stellar.contains("PartAbility.IMPORT_ITEMS") && stellar.contains("PartAbility.EXPORT_ITEMS"),
                "stellar fusion structure must accept every input/output hatch ability");
        check(!stellar.contains("PartAbility.MAINTENANCE") && !stellar.contains("PartAbility.PARALLEL_HATCH")
                        && !stellar.contains("THREAD_MODIFIER"),
                "stellar fusion structure must reject maintenance/parallel/thread hatches");
        String registration = Files.readString(Path.of(
                "src/main/java/com/gtl/enhancedcore/common/registration/SpecialMachineRegistration.java"));
        check(!registration.contains("FIXED_ONE_SECOND"),
                "stellar fusion must not register the removed fusion recipe modifier");
        String helper = Files.readString(Path.of(
                "src/main/java/com/gtl/enhancedcore/common/recipe/SingleRecipeParallel.java"));
        check(!helper.contains("StellarConfinementFusionReactorMachine"),
                "the same-recipe lane helper is no longer a stellar fusion code path");
    }

    private static void configParsing() {
        Set<String> items = Set.of("gtceu:bronze_ingot", "a_b:c", "a:b_c", "minecraft:water");
        Set<String> fluids = Set.of("minecraft:water", "gtceu:steam");
        var legacy = SingularityRecipeConfig.parse("\uFEFF# materials\n\"gtceu:bronze_ingot\"6400\r\n"
                + "\"fluid:minecraft:water\"64000 # fluid\n", items::contains, fluids::contains);
        check(legacy.errors().isEmpty(), "legacy syntax remains supported");
        check(legacy.entries().size() == 2, "both legacy entries parsed");
        check(legacy.entries().get(1).fluid(), "explicit fluid wins over item with same id");
        var json = SingularityRecipeConfig.parse("""
                {"recipes":[{"id":"gtceu:bronze_ingot","count":6400},
                {"id":"minecraft:water","type":"fluid","count":64000}]}
                """, items::contains, fluids::contains);
        check(json.equals(legacy), "JSON and legacy resolve to the same recipes");
        var invalid = SingularityRecipeConfig.parse("""
                "gtceu:bronze_ingot"0
                "gtceu:bronze_ingot"-2
                "gtceu:bronze_ingot"2147483648
                junk "gtceu:bronze_ingot"12
                "gtceu:bronze_ingot"12junk
                "unknown:material"4
                "UPPERCASE:id"2
                "item:gtceu:steam"2
                "gtceu:bronze_ingot"10
                "gtceu:bronze_ingot"20
                "a_b:c"1
                "a:b_c"1
                "gtceu:steam"1000
                """, items::contains, fluids::contains);
        check(invalid.errors().size() == 9, "invalid lines and duplicates are diagnosed");
        check(invalid.entries().size() == 4, "valid entries survive invalid neighbors");
        check(invalid.entries().get(0).count() == 10, "duplicate cannot replace the first recipe");
        check(!invalid.entries().get(1).recipePath().equals(invalid.entries().get(2).recipePath()), "namespace IDs cannot collide");
        check(invalid.entries().get(3).fluid(), "unprefixed known fluid resolves correctly");
        try {
            invalid.entries().clear();
            throw new AssertionError("mutable config snapshot");
        } catch (UnsupportedOperationException expected) { assertions++; }
        check(!SingularityRecipeConfig.parse("[broken", items::contains, fluids::contains).errors().isEmpty(), "bad JSON rejected");
        check(!SingularityRecipeConfig.parse("[{\"id\":\"gtceu:bronze_ingot\",\"count\":1.5}]", items::contains, fluids::contains).errors().isEmpty(), "fractional quantities rejected");
    }

    private static void structureMigration() throws Exception {
        var manifest = JsonParser.parseString(Files.readString(Path.of("src/test/resources/structure-design-baseline.json"))).getAsJsonObject();
        for (var entry : manifest.entrySet()) {
            Path resource = Path.of("src/main/resources/data/gtl_enhancedcore/structures", entry.getKey());
            StructureData data = StructureData.read(Files.newInputStream(resource));
            var expected = entry.getValue().getAsJsonObject();
            List<String[]> aisles = new ArrayList<>();
            data.forEachAisle(aisles::add);
            check(aisles.size() == expected.get("depth").getAsInt(), entry.getKey() + " aisle count");
            check(aisles.stream().allMatch(rows -> rows.length == expected.get("height").getAsInt()), entry.getKey() + " height");
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (String[] aisle : aisles) for (String row : aisle) digest.update((row + "\n").getBytes(StandardCharsets.US_ASCII));
            check(HexFormat.of().formatHex(digest.digest()).equals(expected.get("sha256").getAsString()), entry.getKey() + " reviewed geometry baseline");
            String original = aisles.get(0)[0];
            aisles.get(0)[0] = "mutated";
            List<String[]> secondRead = new ArrayList<>();
            data.forEachAisle(secondRead::add);
            check(secondRead.get(0)[0].equals(original), entry.getKey() + " immutable cache");
        }
        for (String bad : List.of("", "0 1 1\n", "9999999 1 1\n", "2 1 1\nx\n", "2 1 1\nxx\nextra\n", "2 2 2\nxx\n")) {
            try {
                StructureData.read(new ByteArrayInputStream(gzip(bad)));
                throw new AssertionError("invalid structure accepted: " + bad);
            } catch (IOException expected) { assertions++; }
        }
        ivHatchFace();
        try {
            StructureData.read(null);
            throw new AssertionError("missing structure accepted");
        } catch (IOException expected) { assertions++; }
    }

    /**
     * 四个 IV 机器：主方块所在整个正立面都要能放仓室。
     *
     * <p>几何实证：正立面（最后一张切片）中央是 7 宽 × 5 高的仓室面板
     * —— 外圈 'Q'（26 格，可放仓室）+ 内层 'X'（8 格，原有 3x3 面板）+ 中心控制器 'C'，
     * 合计 35 格。比面板更宽/更高的机身肩部（仅 refinery 与 fusion assembler 存在）
     * 仍是纯外壳 'I'，不属于控制器正面面板，因此允许存在。
     */
    private static void ivHatchFace() throws Exception {
        for (String name : List.of("plasma_machine_tool", "hadron_catalytic_refinery",
                "quantum_mass_spectrum_array", "superconducting_fusion_assembler")) {
            Path resource = Path.of("src/main/resources/data/gtl_enhancedcore/structures", name + ".pattern.gz");
            StructureData data = StructureData.read(Files.newInputStream(resource));
            List<String[]> aisles = new ArrayList<>();
            data.forEachAisle(aisles::add);
            String[] front = aisles.get(aisles.size() - 1);

            int q = 0, x = 0, c = 0;
            for (String row : front) {
                for (char ch : row.toCharArray()) {
                    if (ch == 'Q') q++;
                    else if (ch == 'X') x++;
                    else if (ch == 'C') c++;
                }
            }
            // 面板 = Q(26) + X(8) + C(1)；Q 就是“整面可放仓室”的落地方式。
            check(q == 26, name + " entire controller face is hatch-capable (Q count)");
            check(x == 8, name + " inner 3x3 port panel preserved");
            check(c == 1, name + " exactly one controller on the face");
            // 面板必须是 7 宽 × 5 高、左右对称，且上下对称（围绕控制器中心）。
            String face = String.join("\n", front).trim();
            check(face.contains("QQQQQQQ"), name + " 7-wide hatch apron present");
            for (String row : front) {
                if (!row.isBlank()) {
                    check(new StringBuilder(row).reverse().toString().equals(row),
                            name + " face row mirrors across the controller column");
                }
            }
            // 该面上每一格 Q 都要有实心方块托底（不能悬空）。
            String[] behind = aisles.get(aisles.size() - 2);
            for (int row = 0; row < front.length; row++) {
                for (int col = 0; col < front[row].length(); col++) {
                    if (front[row].charAt(col) == 'Q') {
                        check(col < behind[row].length() && behind[row].charAt(col) != ' ',
                                name + " hatch face tile is backed at row " + row + " col " + col);
                    }
                }
            }
            // Java 侧必须把同一份能力谓词绑给 X 与 Q：四种能力都要在。
            String java = Files.readString(Path.of(
                    "src/main/java/com/gtl/enhancedcore/common/data/machines/multiblock/IVProcessingStructures.java"));
            check(java.contains(".where('Q', hatchFace)"), name + " Q predicate wired");
            check(java.contains(".where('X', hatchFace)"), name + " X predicate wired");
            check(java.contains("PartAbility.MAINTENANCE"), name + " maintenance hatch allowed");
            check(java.contains("PartAbility.INPUT_ENERGY"), name + " energy hatch allowed");
            check(java.contains("PartAbility.PARALLEL_HATCH"), name + " parallel hatch allowed");
            check(java.contains("IvBufferRegistry.materialPorts(-1)"),
                    name + " ordinary ports or pattern buffers allowed, with no cross-mode mixing");
        }
    }

    private static void fairScheduling() {
        FairRecipeSelector<String> selector = new FairRecipeSelector<>();
        Set<String> candidates = Set.of("third", "first", "second");
        Set<String> visited = new java.util.HashSet<>();
        for (int i = 0; i < 6; i++) {
            Set<String> selection = selector.select(candidates, 1, value -> value);
            check(selection.size() == 1, "single thread never schedules two recipes");
            visited.addAll(selection);
        }
        check(visited.equals(candidates), "permanent inputs do not starve any recipe");
        check(selector.select(Set.of("new"), 10, value -> value).equals(Set.of("new")), "candidate removal/addition is safe");
        check(selector.select(candidates, Integer.MAX_VALUE, value -> value).size() == 3, "large thread cap does not allocate huge buffers");
        check(selector.select(candidates, 0, value -> value).size() == 1, "nonpositive thread count is clamped");
        check(selector.select(null, 1, value -> value).isEmpty(), "empty lookup safe");
    }

    private static void atomicFiles() throws Exception {
        Path directory = Path.of("build/regression/config");
        Path target = directory.resolve("pins.json");
        ConfigFiles.writeAtomically(target, "旧数据");
        ConfigFiles.writeAtomically(target, "新数据\n");
        check(Files.readString(target).equals("新数据\n"), "atomic replacement preserves UTF-8");
        try (var files = Files.list(directory)) {
            check(files.noneMatch(path -> path.toString().endsWith(".tmp")), "atomic save leaves no temporary files");
        }
    }

    private static byte[] gzip(String text) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (var stream = new GZIPOutputStream(bytes)) { stream.write(text.getBytes(StandardCharsets.US_ASCII)); }
        return bytes.toByteArray();
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        assertions++;
    }
}
