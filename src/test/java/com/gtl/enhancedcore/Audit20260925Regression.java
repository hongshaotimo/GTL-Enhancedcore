package com.gtl.enhancedcore;

import com.google.gson.JsonParser;
import com.gtl.enhancedcore.common.structure.StructureData;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

/** Resource and source-contract checks; Forge behavior is covered by the isolated server fixture. */
final class Audit20260925Regression {
    private static int assertions;
    private static final Path JAVA = Path.of("src/main/java/com/gtl/enhancedcore");

    static int run() throws Exception {
        var root = Path.of("src/main/resources/data/gtl_enhancedcore/structures/gtl");
        var fixtures = Path.of("src/test/resources/gtl-megastructures");
        var originals = JsonParser.parseString(Files.readString(fixtures.resolve("original/bindings.json"))).getAsJsonObject();
        var manifest = JsonParser.parseString(Files.readString(root.resolve("manifest.json"))).getAsJsonObject();
        check(manifest.size() == 12, "all supplied GTL megastructures imported");
        for (var entry : manifest.entrySet()) {
            var spec = entry.getValue().getAsJsonObject();
            var original = originals.getAsJsonObject(entry.getKey());
            List<String[]> oldAisles = new ArrayList<>();
            StructureData.read(Files.newInputStream(fixtures.resolve("original/" + entry.getKey() + ".pattern.gz")))
                    .forEachAisle(oldAisles::add);
            var size = spec.getAsJsonArray("size");
            List<String[]> aisles = new ArrayList<>();
            StructureData.read(Files.newInputStream(root.resolve(entry.getKey() + ".pattern.gz"))).forEachAisle(aisles::add);
            check(aisles.size() == size.get(2).getAsInt(), entry.getKey() + " depth");
            Map<String, Integer> counts = new HashMap<>();
            StringBuilder payload = new StringBuilder(size.get(0) + " " + size.get(1) + " " + size.get(2) + "\n");
            for (String[] aisle : aisles) {
                check(aisle.length == size.get(1).getAsInt(), entry.getKey() + " height");
                for (String row : aisle) {
                    check(row.length() == size.get(0).getAsInt(), entry.getKey() + " width");
                    payload.append(row).append('\n');
                    for (char symbol : row.toCharArray()) counts.merge(String.valueOf(symbol), 1, Integer::sum);
                }
            }
            check(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(payload.toString().getBytes(StandardCharsets.US_ASCII)))
                    .equals(spec.get("sha256").getAsString()), entry.getKey() + " reviewed payload");
            check(counts.size() == spec.getAsJsonObject("counts").size(), entry.getKey() + " symbol set");
            for (var count : counts.entrySet()) {
                check(spec.getAsJsonObject("counts").get(count.getKey()).getAsInt() == count.getValue(), entry.getKey() + " symbol counts");
                var binding = spec.getAsJsonObject("bindings").getAsJsonObject(count.getKey());
                if (binding.get("mode").getAsString().equals("any")) continue;
                var sample = binding.getAsJsonArray("sample");
                char oldSymbol = oldAisles.get(sample.get(0).getAsInt())[sample.get(1).getAsInt()]
                        .charAt(sample.get(2).getAsInt());
                var oldBinding = original.getAsJsonObject("predicates").getAsJsonObject(String.valueOf(oldSymbol));
                check(oldBinding != null && oldBinding.get("sample").equals(sample), entry.getKey() + " original predicate provenance");
                if (binding.has("block")) {
                    check(binding.get("block").equals(oldBinding.get("block")), entry.getKey() + " original block registry ID");
                }
            }
            check(counts.get(spec.get("controller").getAsString()) == 1, entry.getKey() + " unique controller");
            check(counts.get("#") == 24 && spec.get("diamondOreMarkers").getAsInt() == 24, entry.getKey() + " only marked hatch positions");
            var center = spec.getAsJsonArray("controllerPosition");
            check(aisles.get(center.get(2).getAsInt())[center.get(1).getAsInt()].charAt(center.get(0).getAsInt()) == '@',
                    entry.getKey() + " oriented controller anchor");
            check(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(Files.readAllBytes(fixtures.resolve(entry.getKey() + ".schem"))))
                    .equals(spec.get("sourceSha256").getAsString()), entry.getKey() + " exact WorldEdit source");
            check(!spec.get("sourceSize").equals(spec.get("originalSize")), entry.getKey() + " new geometry replaces old export");
        }
        check(source("common/recipe/iv/IvTaskLog.java").contains("\"gtl.enhancedcore.iv.traceTicks\", \"false\""), "tick tracing is opt-in");
        check(source("common/recipe/iv/IvBufferConfigurator.java").contains("Component.translatable(job.error)"), "job status localizes on display");
        check(source("network/MachinePacketAccess.java").contains("menu.getModularUI().holder"), "buttons require the sender's open machine UI");
        for (String machine : List.of("LargeFurnaceMachine", "BasicOreProcessingPlantMachine")) {
            check(source("common/machine/" + machine + ".java").contains("public void onPartUnload()"), machine + " clears unloaded thread part");
        }
        check(source("mixin/gtlcore/SpaceProbeRecipeNoCWUMixin.java").contains("new java.util.HashMap<>(this.tickInputs)"), "constructor does not mutate caller map");
        check(source("mixin/gtceu/MultiblockControllerLockFixMixin.java").contains("finally {\n                lock.unlock();"), "concrete GTL controller releases its lock");
        check(source("common/machine/CausalityTerminalMachine.java").contains("if (!this.isRecipeLogicAvailable()) return;"), "unloaded structure does not cancel paid causality work");
        check(source("common/machine/hatch/QuantumDataAccessHatchMachine.java").contains("this.cachedGeneration != recipeGeneration"), "research cache invalidates after datapack reload");
        return assertions;
    }

    private static String source(String path) throws Exception { return Files.readString(JAVA.resolve(path)); }
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        assertions++;
    }
}
