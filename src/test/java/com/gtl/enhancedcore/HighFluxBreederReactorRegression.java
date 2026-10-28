package com.gtl.enhancedcore;

import com.gtl.enhancedcore.common.machine.HighFluxBreederReactorMachine;
import com.gtl.enhancedcore.common.recipe.EquipmentRecipeCatalog;
import com.gtl.enhancedcore.common.structure.StructureData;
import com.gtl.enhancedcore.common.util.TooltipPolicy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Set;
import java.util.regex.Pattern;

/** 临界增殖反应堆：用户提供的结构、ADD 跨配方线程 512、六类仓室与 UHV 获取。 */
public final class HighFluxBreederReactorRegression {
    private static final Path JAVA = Path.of("src/main/java/com/gtl/enhancedcore");
    private static int assertions;

    public static int run() throws Exception {
        assertions = 0;
        geometry();
        acquisition();
        portsAndRegistration();
        threads();
        return assertions;
    }

    /** 结构与用户提供的 .schem 一致：47 x 55 x 48，控制器唯一，112 个仓室位。 */
    private static void geometry() throws Exception {
        var aisles = new ArrayList<String[]>();
        try (var input = Files.newInputStream(Path.of(
                "src/main/resources/data/gtl_enhancedcore/structures/high_flux_breeder_reactor.pattern.gz"))) {
            StructureData.read(input).forEachAisle(aisles::add);
        }
        int controllers = 0, ports = 0;
        for (int z = 0; z < aisles.size(); z++) {
            for (int y = 0; y < aisles.get(z).length; y++) {
                var row = aisles.get(z)[y];
                for (int x = 0; x < row.length(); x++) {
                    if (row.charAt(x) == 'C') controllers++;
                    else if (row.charAt(x) == 'X') ports++;
                }
            }
        }
        check(controllers == 1, "The structure has exactly one controller");
        check(ports == 112, "The reviewed blueprint marks exactly 112 port positions");
        check(aisles.size() == 48 && aisles.getFirst().length == 55 && aisles.getFirst()[0].length() == 47,
                "Current reviewed schematic dimensions");

        // 每个出现在结构里的字符都必须有谓词绑定，否则 FactoryBlockPattern.build() 抛异常。
        var used = new java.util.TreeSet<Character>();
        for (var aisle : aisles) for (var row : aisle) for (int x = 0; x < row.length(); x++) used.add(row.charAt(x));
        String structures = source("common/data/machines/multiblock/GTLStructures.java");
        int from = structures.indexOf("public static BlockPattern highFluxBreederReactor(");
        int to = structures.indexOf("public static BlockPattern", from + 1);
        String ports7 = structures.substring(from, to);
        var declared = new java.util.TreeSet<Character>();
        var matcher = Pattern.compile("\\.where\\('(.)'").matcher(ports7);
        while (matcher.find()) declared.add(matcher.group(1).charAt(0));
        var palette = com.google.gson.JsonParser.parseString(Files.readString(Path.of(
                "src/main/resources/data/gtl_enhancedcore/structures/high_flux_breeder_reactor.json"))).getAsJsonObject();
        for (String symbol : palette.getAsJsonObject("bindings").keySet()) declared.add(symbol.charAt(0));
        check(declared.containsAll(used), "Every symbol used by the geometry has a predicate: "
                + new java.util.TreeSet<>(used) { }.stream().filter(c -> !declared.contains(c)).toList());

        // 用户指定的仓室替换方块与主体材质。
        check(ports7.contains(".where('X', block(\"gtlcore:supercritical_turbine_casing\")"),
                "Empty ports use the requested supercritical turbine casing");
        check(palette.getAsJsonObject("bindings").getAsJsonObject("A").get("block").getAsString()
                        .equals("gtlcore:fission_reactor_casing"),
                "The structure body uses the requested fission reactor casing");
    }

    private static void acquisition() throws Exception {
        try (var reader = Files.newBufferedReader(Path.of(
                "src/main/resources/data/gtl_enhancedcore/equipment_recipes.json"))) {
            var recipe = EquipmentRecipeCatalog.read(reader).stream()
                    .filter(value -> value.output().equals("high_flux_breeder_reactor")).findFirst().orElseThrow();
            check(recipe.tier() == EquipmentRecipeCatalog.TIERS.indexOf("uhv")
                            && recipe.method().equals("assembly_line"),
                    "Acquisition is gated at UHV on the assembly line");
            check(recipe.ingredients().stream().anyMatch(value ->
                            value.tag() && value.id().equals("gtceu:circuits/uhv") && value.count() == 16),
                    "Sixteen UHV circuits gate the acquisition stage");
            check(recipe.ingredients().stream().anyMatch(value ->
                            value.id().equals("gtlcore:fission_reactor_casing") && value.count() == 64),
                    "The recipe consumes the same casing the structure is built from");
            check(recipe.fluids().stream().anyMatch(value ->
                            value.id().equals("gtceu:soldering_alloy") && value.amount() == 18432),
                    "The assembly line consumes soldering alloy");
        }
        var page = TooltipPolicy.page("high_flux_breeder_reactor");
        for (String key : Set.of("high_flux_breeder_reactor.cross_recipe", "high_flux_breeder_reactor.threads",
                "high_flux_breeder_reactor.parallel", "high_flux_breeder_reactor.hatches")) {
            check(page.summary().contains("tooltip.gtl_enhancedcore." + key),
                    "The default tooltip exposes the ADD capability: " + key);
        }
        check(page.summary().stream().noneMatch(key -> key.contains("iv_")),
                "The reactor does not claim isolated-order features");
        // 中英两侧的语言键必须齐全，且线程键保留 %s 占位符。
        for (String lang : Set.of("zh_cn", "en_us")) {
            String json = Files.readString(Path.of(
                    "src/main/resources/assets/gtl_enhancedcore/lang/" + lang + ".json"));
            for (String key : Set.of("block.gtl_enhancedcore.high_flux_breeder_reactor",
                    "tooltip.gtl_enhancedcore.high_flux_breeder_reactor.threads",
                    "tooltip.gtl_enhancedcore.high_flux_breeder_reactor.intro",
                    "tooltip.gtl_enhancedcore.high_flux_breeder_reactor.flavor")) {
                check(json.contains("\"" + key + "\""), "Missing language key in " + lang + ": " + key);
            }
            check(json.contains("§f%s§r"), "The thread line keeps its numeric placeholder in " + lang);
        }
    }

    private static void portsAndRegistration() throws Exception {
        String structures = source("common/data/machines/multiblock/GTLStructures.java");
        int from = structures.indexOf("public static BlockPattern highFluxBreederReactor(");
        int to = structures.indexOf("public static BlockPattern", from + 1);
        String ports = structures.substring(from, to);
        var abilities = Pattern.compile("PartAbility\\.([A-Z_]+)").matcher(ports).results()
                .map(result -> result.group(1)).collect(java.util.stream.Collectors.toSet());
        check(abilities.equals(Set.of("MAINTENANCE", "INPUT_ENERGY", "INPUT_LASER", "PARALLEL_HATCH",
                        "IMPORT_ITEMS", "EXPORT_ITEMS", "IMPORT_FLUIDS", "EXPORT_FLUIDS")),
                "Exactly the six requested hatch families are accepted");

        String registration = source("common/registration/SpecialMachineRegistration.java");
        from = registration.indexOf("public static MultiblockMachineDefinition registerHighFluxBreederReactor(");
        to = registration.indexOf("public static MultiblockMachineDefinition", from + 1);
        String definition = registration.substring(from, to);
        check(definition.contains("HighFluxBreederReactorMachine::new")
                        && definition.contains("GTLRecipeTypes.FISSION_REACTOR_RECIPES"),
                "The fission reactor recipe type drives the machine");
        check(definition.contains(".recipeModifier(GTRecipeModifiers.PARALLEL_HATCH)"),
                "The parallel control hatch is settled by the functional modifier");
        // 只允许 PARALLEL_HATCH 一个修饰器：ADD 引擎内部已结算并行与超频，
        // 再挂电压超频修饰器会重复超频。注释不参与判定，仅看实际调用。
        var modifiers = Pattern.compile("\\.recipeModifiers?\\(").matcher(
                definition.replaceAll("//[^\\n]*", "").replaceAll("(?s)/\\*.*?\\*/", "")).results().count();
        check(modifiers == 1 && !definition.replaceAll("//[^\\n]*", "").contains("ELECTRIC_OVERCLOCK"),
                "No extra overclock is stacked on top of the ADD engine's own settlement");
        check(definition.contains("gtlcore:iridium_casing"),
                "The appearance block is the requested iridium casing");
        // 材质：主面用空间缩放仪，正面 overlay 用大型打包机。
        check(definition.contains("block/casings/iridium_casing")
                        && definition.contains("block/multiblock/gcym/large_packer"),
                "The iridium renderer retains the existing front overlay");
        check(!source("common/recipe/iv/IvMachineScope.java").contains("high_flux_breeder_reactor"),
                "The reactor stays outside the isolated native-order whitelist");
    }

    /** 用户指定 512 条跨配方线程，且保持 Ω 叠加语义。 */
    private static void threads() throws Exception {
        check(HighFluxBreederReactorMachine.CROSS_RECIPE_THREADS == 512,
                "The requested cross-recipe thread count is 512");
        String machine = source("common/machine/HighFluxBreederReactorMachine.java");
        check(machine.contains("extends GTLAddWorkableElectricParallelHatchMultipleRecipesMachine"),
                "The ADD parallel-hatch multiblock is the base class");
        check(machine.contains("extends GTLAddMultipleRecipesLogic"),
                "The ADD cross-recipe logic owns scheduling");
        check(machine.contains("CROSS_RECIPE_THREADS + Math.max(0, getMachine().getAdditionalThread())"),
                "Omega thread modifiers add on top instead of replacing the base count");
    }

    private static String source(String relative) throws Exception {
        return Files.readString(JAVA.resolve(relative));
    }

    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }
}
