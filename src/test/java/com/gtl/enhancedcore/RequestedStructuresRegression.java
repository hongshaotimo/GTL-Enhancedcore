package com.gtl.enhancedcore;

import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/** Current request contracts: distinct controller cosmetics and structural port fallbacks. */
final class RequestedStructuresRegression {
    private static int checks;
    static int run() throws Exception {
        Path javaRoot = Path.of("src/main/java/com/gtl/enhancedcore");
        String definitions = Files.readString(javaRoot.resolve("common/registration/SpecialMachineRegistration.java"));
        String dragon = method(definitions, "registerDragonFieldProliferationCore");
        check(dragon.contains("requiredBlockBlock(\"gtlcore:dragon_strength_tritanium_casing\")")
                        && dragon.contains("block/casings/extreme_strength_tritanium_casing"), "dragon native texture reference");
        String structures = Files.readString(javaRoot.resolve("common/data/machines/multiblock/GTLStructures.java"));
        check(structures.contains(".where('X', block(\"gtlcore:dragon_strength_tritanium_casing\")"), "dragon empty ports");
        String cloud = Files.readString(javaRoot.resolve("common/registration/CloudPatternRegistration.java"));
        String library = method(cloud, "library");
        check(library.contains("gtlcore:molecular_casing") && library.contains("block/molecular_casing"), "cloud appearance and renderer");
        check(!library.contains("PartAbility.") && !library.contains(".or("), "cloud has no hatch slots");
        String machine = Files.readString(javaRoot.resolve("common/machine/BasicOreProcessingPlantMachine.java"));
        check(machine.contains("BASE_THREADS = 1") && machine.contains("THREADS_PER_TIER = 4"), "ore plant gains four threads per tier");
        Path root = Path.of("src/main/resources/data/gtl_enhancedcore/structures");
        var hollow = JsonParser.parseString(Files.readString(root.resolve("hollow/manifest.json"))).getAsJsonObject().getAsJsonObject("resources");
        var manifests = JsonParser.parseString(Files.readString(root.resolve("gtl/manifest.json"))).getAsJsonObject();
        var upgrades = JsonParser.parseString(Files.readString(root.resolve("gtl/upgrades.json"))).getAsJsonObject();
        for (var target : Map.of("mega_extractor", 174, "advanced_vacuum_drying_furnace", 58,
                "antientropy_condensation_center", 196).entrySet()) {
            String name = target.getKey();
            var spec = (name.equals("antientropy_condensation_center") ? upgrades : manifests).getAsJsonObject(name);
            check(spec.get("diamondOreMarkers").getAsInt() == target.getValue(), name + " exact reviewed hatch count");
            check(!spec.has("appearance") && !spec.has("casingReplacements"), name + " retain native controller and source palette");
            String fallback = name.equals("mega_extractor") ? "gtlcore:hyper_mechanical_casing"
                    : name.equals("advanced_vacuum_drying_furnace") ? "gtceu:high_power_casing"
                    : "gtlcore:antifreeze_heatproof_machine_casing";
            check(spec.getAsJsonObject("bindings").getAsJsonObject("#").get("fallback").getAsString().equals(fallback),
                    name + " requested empty port casing");
            var review = hollow.getAsJsonObject("/data/gtl_enhancedcore/structures/gtl/" + name + ".pattern.gz");
            check(!review.has("optimizedResource") && review.get("removed").getAsInt() == 0, name + " cannot load stale deletion mask");
        }
        var skeleton = JsonParser.parseString(Files.readString(root.resolve("gtl/upgrades_20261013.json")))
                .getAsJsonObject().getAsJsonObject("skeleton_shift_rift_engine");
        check(skeleton.get("hatchFallback").getAsString().equals("gtlcore:hyper_mechanical_casing")
                && !skeleton.has("appearance"), "skeleton retains appearance and requested port fallback");
        var targets = Map.of("dragon_field_proliferation_core", new int[]{252,108,120,24},
                "high_flux_breeder_reactor", new int[]{47,55,48,112},
                "skeleton_shift_rift_engine", new int[]{31,31,48,80},
                "cloud_pattern_library", new int[]{49,33,45,0});
        for (var target : targets.entrySet()) {
            String name = target.getKey();
            var spec = name.equals("skeleton_shift_rift_engine") ? skeleton
                    : JsonParser.parseString(Files.readString(root.resolve(name + ".json"))).getAsJsonObject();
            int[] expected = target.getValue();
            for (int i=0;i<3;i++) check(spec.getAsJsonArray("size").get(i).getAsInt() == expected[i], name + " reviewed dimension");
            check(spec.get("diamondOreMarkers").getAsInt() == expected[3], name + " reviewed marker count");
            check(spec.get("sourceRevision").getAsString().equals("user-request-20261027"), name + " current source revision");
            var digest = java.security.MessageDigest.getInstance("SHA-256");
            check(java.util.HexFormat.of().formatHex(digest.digest(Files.readAllBytes(Path.of(spec.get("sourceSchematic").getAsString()))))
                    .equals(spec.get("sourceSha256").getAsString()), name + " exact source schematic");
            String resource = name.equals("skeleton_shift_rift_engine") ? "gtl/" + name
                    : name.equals("dragon_field_proliferation_core") ? "dragonfieldproliferationcorestructure" : name;
            byte[] payload;
            try (var input = new java.util.zip.GZIPInputStream(Files.newInputStream(root.resolve(resource + ".pattern.gz")))) { payload=input.readAllBytes(); }
            check(java.util.HexFormat.of().formatHex(digest.digest(payload)).equals(spec.get("sha256").getAsString()), name + " exact geometry hash");
            var counts = new java.util.HashMap<String,Integer>();
            String[] rows = new String(payload, java.nio.charset.StandardCharsets.US_ASCII).split("\n");
            for (int i=1;i<rows.length;i++) for (char symbol : rows[i].toCharArray()) counts.merge(String.valueOf(symbol),1,Integer::sum);
            check(counts.keySet().equals(spec.getAsJsonObject("bindings").keySet()), name + " complete used palette");
            for (var count : counts.entrySet()) check(spec.getAsJsonObject("counts").get(count.getKey()).getAsInt()==count.getValue(), name + " exact palette counts");
        }
        return checks;
    }
    private static String method(String source, String name) {
        int start = source.indexOf(" " + name + "(");
        int end = source.indexOf("public static ", start + 1);
        return source.substring(start, end < 0 ? source.length() : end);
    }
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        checks++;
    }
}
