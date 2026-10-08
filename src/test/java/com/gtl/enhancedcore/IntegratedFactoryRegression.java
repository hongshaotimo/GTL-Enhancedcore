package com.gtl.enhancedcore;

import com.google.gson.JsonParser;
import com.gtl.enhancedcore.common.machine.IntegratedUniversalFactoryMachine;
import com.gtl.enhancedcore.common.recipe.iv.FairBudget;
import com.gtl.enhancedcore.common.recipe.iv.SuperBufferAutoName;
import com.gtl.enhancedcore.common.structure.StructureData;
import com.gtl.enhancedcore.common.util.FairRecipeSelector;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Set;
import java.util.regex.Pattern;

/** Geometry, registration and isolated-order contracts; does not start Minecraft. */
final class IntegratedFactoryRegression {
    private static int assertions;
    private static final Path JAVA = Path.of("src/main/java/com/gtl/enhancedcore");
    private static final String NAME = "integrated_universal_factory";
    private static final String JOINT_ID = "universal_joint_factory";

    static int run() throws Exception {
        check(IntegratedUniversalFactoryMachine.PARALLEL == 64, "fixed 64 parallel");
        check(IntegratedUniversalFactoryMachine.THREADS == 10, "fixed ten threads");
        structure();
        recipeEngine();
        registration();
        return assertions;
    }

    private static void structure() throws Exception {
        var fixtures = Path.of("src/test/resources/integrated-factory");
        var manifest = JsonParser.parseString(Files.readString(fixtures.resolve("manifest.json"))).getAsJsonObject();
        check(hash(Files.readAllBytes(fixtures.resolve(NAME + ".schem")))
                .equals(manifest.get("sourceSha256").getAsString()), "original schematic retained");
        var aisles = new ArrayList<String[]>();
        StructureData.read(Files.newInputStream(Path.of(
                "src/main/resources/data/gtl_enhancedcore/structures/" + NAME + ".pattern.gz"))).forEachAisle(aisles::add);
        check(aisles.size() == 49, "49 deep");
        var payload = new StringBuilder("49 31 49\n");
        var counts = new HashMap<String, Integer>();
        for (int z = 0; z < aisles.size(); z++) {
            var rows = aisles.get(z);
            check(rows.length == 31, "31 high");
            for (int y = 0; y < rows.length; y++) {
                String row = rows[y];
                check(row.length() == 49, "49 wide");
                payload.append(row).append('\n');
                for (int x = 0; x < row.length(); x++) {
                    char c = row.charAt(x);
                    counts.merge(String.valueOf(c), 1, Integer::sum);
                    boolean panel = z == 48 && x >= 19 && x <= 29 && y >= 9 && y <= 21;
                    if (panel) check("CQX".indexOf(c) >= 0, "central panel accepts hatches except its controller");
                    else check("XQLC".indexOf(c) < 0, "no hatch/controller outside the central panel");
                }
            }
        }
        check(hash(payload.toString().getBytes(StandardCharsets.US_ASCII))
                .equals(manifest.get("sha256").getAsString()), "reviewed geometry hash");
        check(counts.size() == manifest.getAsJsonObject("counts").size(), "palette size");
        counts.forEach((symbol, count) -> check(count == manifest.getAsJsonObject("counts").get(symbol).getAsInt(),
                "reviewed symbol count " + symbol));
        check(counts.get("C") == 1 && aisles.get(48)[15].charAt(24) == 'C', "unique controller at the original height");
        check(counts.get("X") == 24 && counts.get("Q") == 118, "142 central panel hatch positions");
        check(counts.get("J") == 4 && !counts.containsKey("L"), "all four lamps retained as lamps only");
        String source = source("common/structure/IntegratedFactoryStructure.java");
        for (String ability : Set.of("MAINTENANCE", "INPUT_ENERGY")) check(source.contains("PartAbility." + ability), "allowed hatch " + ability);
        check(source.contains("IvBufferRegistry.materialPorts(30)"),
                "ordinary material ports or up to 30 pattern buffers can form the structure");
        for (String excluded : Set.of("PARALLEL_HATCH", "INPUT_LASER", "THREAD_MODIFIER", "IMPORT_ITEMS", "EXPORT_ITEMS",
                "IMPORT_FLUIDS", "EXPORT_FLUIDS")) {
            check(!source.contains(excluded), "no hatch outside the isolated-order contract: " + excluded);
        }
        check(source.contains(".where('Q', hatches)") && source.contains(".where('X', hatches)")
                && !source.contains("lamps().or(abilities)"), "panel predicates share global quotas; lamps reject hatches");
        check(source.contains("LampBlock.INVERTED, true") && source.contains("LampBlock.BLOOM, false")
                && source.contains("LampPlacement.info(state)"), "lamp options survive preview/terminal candidates");
    }

    private static void recipeEngine() throws Exception {
        String machine = source("common/machine/IntegratedUniversalFactoryMachine.java");
        String tiered = source("common/machine/TieredParallelMachine.java");
        String logic = source("common/recipe/iv/IvRecipeLogic.java");
        String buffers = source("common/recipe/iv/IvBuffers.java");
        String legacy = source("common/recipe/ThreadLimitedRecipeLogic.java");
        check(machine.contains("extends TieredParallelMachine") && !machine.contains("createRecipeLogic"),
                "same isolated-order scheduler as the four IV machines");
        check(tiered.contains("return new IvRecipeLogic(this);"), "IV scheduler owns every order");
        check(machine.contains("return THREADS;") && machine.contains("return PARALLEL;"), "fixed threads and parallel");
        check(machine.contains("ivMaintenancePenalty()") && machine.contains("return false;"), "no IV maintenance penalty");
        check(logic.contains("ivMaintenancePenalty()) return super.getEuMultiplier();"), "native maintenance multiplier kept");
        check(buffers.contains("\"gtl_enhancedcore:universal_joint_factory\""), "buffers isolated and named per type");
        check(!legacy.contains("allRecipeTypes"), "retired all-type search removed");
        check(!machine.contains("MachineModeFancyConfigurator"), "automatic cross-type processing has no mode switch");
        long budget = (long)IntegratedUniversalFactoryMachine.PARALLEL * IntegratedUniversalFactoryMachine.THREADS;
        long[] wants = new long[IntegratedUniversalFactoryMachine.THREADS];
        Arrays.fill(wants, budget);
        for (long granted : FairBudget.divide(budget, wants, 0)) check(granted == 64, "ten concurrent orders share 640 fairly");
        check(FairBudget.divide(budget, new long[]{budget}, 0)[0] == 640, "a lone order may use the whole budget");
        var keys = new ArrayList<String>();
        for (int i = 0; i < 24; i++) keys.add("gtceu.type" + i);
        var names = new HashSet<String>();
        for (int i = 0; i < 24; i++) names.add(SuperBufferAutoName.keyForIndex(i, keys));
        check(names.size() == 24 && !names.contains(""), "24 buffers get 24 different recipe-type names");
        check(SuperBufferAutoName.keyForIndex(24, keys).isEmpty(), "extra buffers stay unnamed");
        var selector = new FairRecipeSelector<String>();
        var candidates = new HashSet<String>();
        for (int i = 0; i < 24; i++) candidates.add("buffer" + i + "/order");
        var visited = new HashSet<String>();
        for (int i = 0; i < 3; i++) {
            var selected = selector.select(candidates, IntegratedUniversalFactoryMachine.THREADS, id -> id);
            check(selected.size() == 10, "at most ten orders start per cycle");
            visited.addAll(selected);
        }
        check(visited.equals(candidates), "all queued orders receive turns");
    }

    private static void registration() throws Exception {
        String builder = source("common/registration/IntegratedFactoryRegistration.java");
        var types = new HashSet<String>();
        var matcher = Pattern.compile("(?:GTRecipeTypes|GTLRecipeTypes)\\.([A-Z_]+)").matcher(builder);
        int count = 0;
        while (matcher.find()) { types.add(matcher.group(1)); count++; }
        check(count == 24 && types.equals(Set.of("BENDER_RECIPES", "COMPRESSOR_RECIPES", "FORGE_HAMMER_RECIPES",
                "CUTTER_RECIPES", "EXTRUDER_RECIPES", "LATHE_RECIPES", "WIREMILL_RECIPES", "FORMING_PRESS_RECIPES",
                "POLARIZER_RECIPES", "LASER_ENGRAVER_RECIPES", "FLUID_SOLIDFICATION_RECIPES", "ASSEMBLER_RECIPES",
                "CIRCUIT_ASSEMBLER_RECIPES", "CENTRIFUGE_RECIPES", "THERMAL_CENTRIFUGE_RECIPES", "ELECTROLYZER_RECIPES",
                "SIFTER_RECIPES", "MACERATOR_RECIPES", "EXTRACTOR_RECIPES", "DEHYDRATOR_RECIPES", "CHEMICAL_RECIPES",
                "MIXER_RECIPES", "CHEMICAL_BATH_RECIPES", "ORE_WASHER_RECIPES")), "exact union of four retired factories");
        check(builder.contains("\"block/multi_functional_casing\"") && builder.contains("\"block/multiblock/processing_array\""),
                "processing-plus textures retained");
        String mappings = source("common/event/RetiredFactoryMappings.java");
        String facade = source("common/machine/GTLEnhancedcoreMachines.java");
        String recipes = Files.readString(Path.of("src/main/resources/data/gtl_enhancedcore/equipment_recipes.json"));
        for (String id : Set.of("processing_plus", "assembling_plus", "separating_plus", "mixing_plus")) {
            check(facade.contains("\"" + id + "\"") && recipes.contains("\"output\": \"" + id + "\""),
                    "restored Plus factory registered and craftable: " + id);
        }
        check(builder.contains("universal_joint_factory") && recipes.contains("\"output\": \"universal_joint_factory\""),
                "joint factory uses the replacement registration id");
        var catalog = com.gtl.enhancedcore.common.recipe.EquipmentRecipeCatalog.read(new java.io.StringReader(recipes));
        var byOutput = new HashMap<String, com.gtl.enhancedcore.common.recipe.EquipmentRecipeCatalog.Recipe>();
        catalog.forEach(entry -> byOutput.put(entry.output(), entry));
        var joint = byOutput.get(JOINT_ID);
        check(joint.tier() == com.gtl.enhancedcore.common.recipe.EquipmentRecipeCatalog.TIERS.indexOf("mv")
                && joint.method().equals("assembler"), "joint factory recipe stays MV assembler");
        check(inputs(joint).equals(Set.of("gtl_enhancedcore:processing_plus x1", "gtl_enhancedcore:assembling_plus x1",
                "gtl_enhancedcore:separating_plus x1", "gtl_enhancedcore:mixing_plus x1",
                "#gtceu:circuits/mv x8", "gtceu:phenolic_circuit_board x16")),
                "joint factory consumes the four plus factories plus circuit boards");
        check(joint.fluids().size() == 1 && joint.fluids().get(0).id().equals("gtceu:soldering_alloy")
                && joint.fluids().get(0).amount() == 576, "MV assembler solder retained");
        for (String id : Set.of("processing_plus", "assembling_plus", "separating_plus", "mixing_plus")) {
            check(!inputs(byOutput.get(id)).equals(inputs(joint)), "joint factory recipe is not a copy of " + id);
        }
        check(mappings.contains("integrated_universal_factory"), "old joint factory mapping retained");
        for (String registry : Set.of("ITEM", "BLOCK", "BLOCK_ENTITY_TYPE")) {
            check(mappings.contains("Registries." + registry + ","), "old save registry remapped: " + registry);
        }
        for (String language : Set.of("zh_cn", "en_us")) {
            var lang = JsonParser.parseString(Files.readString(Path.of(
                    "src/main/resources/assets/gtl_enhancedcore/lang/" + language + ".json"))).getAsJsonObject();
            check(lang.has("block.gtl_enhancedcore." + JOINT_ID), "localized machine name");
            if (language.equals("zh_cn")) check(lang.get("tooltip.gtl_enhancedcore." + JOINT_ID + ".intro").getAsString()
                    .equals("来自未来的跨时空级造物，继承了一些特别珍贵的属性，但因为跨越时空的时候产生了损伤，数值大幅度降低"),
                    "exact requested flavor text");
        }
    }

    private static Set<String> inputs(com.gtl.enhancedcore.common.recipe.EquipmentRecipeCatalog.Recipe recipe) {
        var inputs = new HashSet<String>();
        for (var ingredient : recipe.ingredients()) {
            inputs.add((ingredient.tag() ? "#" : "") + ingredient.id() + " x" + ingredient.count());
        }
        return inputs;
    }

    private static String source(String path) throws Exception { return Files.readString(JAVA.resolve(path)); }
    private static String hash(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        assertions++;
    }
}
