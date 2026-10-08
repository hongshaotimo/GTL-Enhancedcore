package com.gtl.enhancedcore;

import com.google.gson.JsonParser;
import com.gtl.enhancedcore.common.structure.StructureData;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Set;

final class StructureUpgradeRegression {
    private static int checks;
    private StructureUpgradeRegression() {}

    static int run() throws Exception {
        runGroup("upgrades.json", "gtl-structure-upgrades", Set.of("mega_fluid_heater", "advanced_sps_crafting",
                "dimensionally_transcendent_mixer", "star_ultimate_material_forge_factory", "qft", "nano_core",
                "gravitation_shockburst", "advanced_neutron_activator",
                "dimensionally_transcendent_chemical_plant", "antientropy_condensation_center"));
        runGroup("upgrades_20260927.json", "gtl-structure-upgrades-20260927",
                Set.of("advanced_integrated_ore_processor", "atomic_energy_excitation_plant",
                        "component_assembly_line", "mage_assembler", "super_blast_smelter", "superconducting_electromagnetism"));
        return checks;
    }

    private static void runGroup(String file, String fixtureDirectory, Set<String> names) throws Exception {
        var root = Path.of("src/main/resources/data/gtl_enhancedcore/structures/gtl");
        var fixtures = Path.of("src/test/resources", fixtureDirectory);
        var manifest = JsonParser.parseString(Files.readString(root.resolve(file))).getAsJsonObject();
        var original = JsonParser.parseString(Files.readString(fixtures.resolve("original-bindings.json"))).getAsJsonObject();
        check(manifest.keySet().equals(names), "exact structure import scope");
        for (var entry : manifest.entrySet()) {
            String name = entry.getKey();
            var spec = entry.getValue().getAsJsonObject();
            var size = spec.getAsJsonArray("size");
            int w = size.get(0).getAsInt(), h = size.get(1).getAsInt(), d = size.get(2).getAsInt();
            var hash = MessageDigest.getInstance("SHA-256");
            hash.update((w + " " + h + " " + d + "\n").getBytes(StandardCharsets.US_ASCII));
            int[] counts = new int[128], aisles = {0}, lamps = {0};
            var core = spec.getAsJsonArray("controllerPosition");
            int coreX = core.get(0).getAsInt(), coreY = core.get(1).getAsInt(), coreZ = core.get(2).getAsInt();
            var bindings = spec.getAsJsonObject("bindings");
            StructureData.read(Files.newInputStream(root.resolve(name + ".pattern.gz"))).forEachAisle(rows -> {
                check(rows.length == h, name + " height");
                for (int y = 0; y < rows.length; y++) {
                    String row = rows[y];
                    check(row.length() == w, name + " width");
                    hash.update((row + "\n").getBytes(StandardCharsets.US_ASCII));
                    for (char c : row.toCharArray()) counts[c]++;
                    if (aisles[0] == coreZ && y == coreY)
                        check(row.charAt(coreX) == '@', name + " controller anchor");
                    if (name.equals("superconducting_electromagnetism") && aisles[0] == coreZ
                            && (y >= coreY - 3 && y <= coreY + 1 || y == coreY + 3)) {
                        var binding = bindings.getAsJsonObject(String.valueOf(row.charAt(coreX - 7)));
                        check(binding != null && binding.has("block")
                                && binding.get("block").getAsString().equals("gtlcore:lafium_mechanical_casing")
                                && binding.get("mode").getAsString().equals("casing"),
                                name + " controller-left casing at y=" + y);
                    }
                }
                aisles[0]++;
            });
            check(aisles[0] == d, name + " depth");
            check(HexFormat.of().formatHex(hash.digest()).equals(spec.get("sha256").getAsString()), name + " payload hash");
            check(counts['@'] == 1 && counts['#'] == 24, name + " marker count");
            if (name.equals("superconducting_electromagnetism")) {
                check(counts['J'] == 180 && counts['K'] == 0, name + " five UEV casings and one lamp replaced");
                check(!bindings.has("K"), name + " unused white lamp binding removed");
            }
            for (var count : spec.getAsJsonObject("counts").entrySet())
                check(counts[count.getKey().charAt(0)] == count.getValue().getAsInt(), name + " symbol count");
            for (var bindingEntry : bindings.entrySet()) {
                var binding = bindingEntry.getValue().getAsJsonObject();
                String mode = binding.get("mode").getAsString();
                check(counts[bindingEntry.getKey().charAt(0)] > 0, name + " used binding");
                if (mode.equals("any") || mode.equals("literal")) {
                    if (binding.has("block") && binding.get("block").getAsString().endsWith("_lamp")) {
                        check(binding.getAsJsonObject("properties").get("inverted").getAsBoolean(), name + " inverted source lamp");
                        check(binding.getAsJsonObject("properties").get("lit").getAsBoolean(), name + " lit source lamp");
                        lamps[0]++;
                    }
                    continue;
                }
                boolean found = false;
                for (var old : original.getAsJsonObject(name).getAsJsonArray("predicates"))
                    found |= old.getAsJsonObject().get("sample").equals(binding.get("sample"));
                check(found, name + " original predicate provenance");
                check(!mode.equals("hatch") || bindingEntry.getKey().equals("#"), name + " hatch panel only");
            }
            check(lamps[0] > 0, name + " lamp states imported");
            check(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(Files.readAllBytes(fixtures.resolve(name + ".schem"))))
                    .equals(spec.get("sourceSha256").getAsString()), name + " exact source schematic");
            check((spec.get("namespace").getAsString() + ":" + name)
                    .equals(original.getAsJsonObject(name).get("id").getAsString()), name + " exact registered namespace");
        }
    }

    private static void check(boolean valid, String message) {
        if (!valid) throw new AssertionError(message);
        checks++;
    }
}
