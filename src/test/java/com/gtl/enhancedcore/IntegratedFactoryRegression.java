package com.gtl.enhancedcore;

import com.google.gson.JsonParser;
import com.gtl.enhancedcore.common.machine.IntegratedUniversalFactoryMachine;
import com.gtl.enhancedcore.common.structure.StructureData;
import com.gtl.enhancedcore.common.structure.IntegratedFactoryStructure;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

/** Geometry, registration and isolated-order contracts; does not start Minecraft. */
final class IntegratedFactoryRegression {
    private static int assertions;
    private static final Path JAVA = Path.of("src/main/java/com/gtl/enhancedcore");
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
        var aisles = new ArrayList<String[]>();
        StructureData.read(IntegratedFactoryRegression.class.getResourceAsStream(IntegratedFactoryStructure.RESOURCE)).forEachAisle(aisles::add);
        check(aisles.size() == 31, "31 aisles");
        var counts = new HashMap<Character,Integer>();
        for (var rows : aisles) {
            check(rows.length == 18, "18 courses");
            for (var row : rows) {
                check(row.length() == 35, "35 blocks across");
                for (char c : row.toCharArray()) counts.merge(c, 1, Integer::sum);
            }
        }
        for (char c : "Cifolemad".toCharArray()) check(counts.get(c) == 1, "unique socket " + c);
        check(counts.get('b') == 2 && counts.get('J') == 4 && !counts.containsKey('Q') && !counts.containsKey('X'), "no generic hatch wall");
        check(aisles.get(25)[2].charAt(18) == 'C', "north-facing console in its own bay");
        check(aisles.get(23)[2].charAt(29) == 'i' && aisles.get(18)[2].charAt(2) == 'f'
                && aisles.get(5)[2].charAt(32) == 'o' && aisles.get(2)[2].charAt(5) == 'l', "four non-interchangeable station faces");
        check(aisles.get(26)[2].charAt(18) == '#' && aisles.get(14)[4].charAt(29) == '#', "console and moving heads retain clearance");
    }

    private static void recipeEngine() throws Exception {
        check(IntegratedUniversalFactoryMachine.lanesFor(1, 1, 1) == 1, "default bill: one lane");
        check(IntegratedUniversalFactoryMachine.lanesFor(1, 2, 1) == 2, "second liquid input counts");
        check(IntegratedUniversalFactoryMachine.lanesFor(2, 2, 2) == 8, "independent feed and return lanes");
        check(IntegratedUniversalFactoryMachine.lanesFor(2, 3, 2) == 10, "full complement is capped at ten");
        check(!source("common/recipe/iv/IvBuffers.java").contains("gtl_enhancedcore:universal_joint_factory"), "no isolated-order membership");
    }

    public static void main(String[] args) throws Exception { System.out.println("Joint factory: " + run() + " checks"); }

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
        check(!mappings.contains("neutron_control_factory_mk2"), "retired neutron factory MK2 is not remapped");
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
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        assertions++;
    }
}
