package com.gtl.enhancedcore;

import com.google.gson.JsonParser;
import com.gtl.enhancedcore.common.structure.StructureData;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.zip.GZIPInputStream;

/** Locks the imported schematic and the requested hatch panel without starting Minecraft. */
final class LucidDragonStructureRegression {
    private static final Path STRUCTURES = Path.of("src/main/resources/data/gtl_enhancedcore/structures");
    private static int checks;

    static int run() throws Exception {
        var root = STRUCTURES.resolve("gtl");
        var spec = JsonParser.parseString(Files.readString(root.resolve("lucid_etchdreamer.json")))
                .getAsJsonObject().getAsJsonObject("lucid_etchdreamer");
        check(spec.get("namespace").getAsString().equals("gtladditions"), "lucid exact upstream ID");
        check(spec.getAsJsonArray("originalSize").toString().equals("[21,21,22]"), "lucid upstream shape guard");
        check(spec.get("hatchFallback").getAsString().equals("gtladditions:lucid_etchdreamer"),
                "lucid controller hatch replacement");
        check(spec.getAsJsonObject("bindings").getAsJsonObject("X").get("mode").getAsString().equals("hatch"),
                "lucid retains upstream hatch abilities");
        check(spec.getAsJsonObject("bindings").getAsJsonObject("X").get("fallback").getAsString()
                        .equals("gtladditions:lucid_etchdreamer"), "lucid hatch fallback binding");
        check(spec.getAsJsonObject("bindings").getAsJsonObject("X").get("retainCasing").getAsBoolean()
                        && spec.getAsJsonObject("bindings").getAsJsonObject("X").get("block").getAsString()
                        .equals("gtlcore:iridium_casing"), "lucid empty hatch slots accept original casing");
        check(spec.getAsJsonObject("bindings").getAsJsonObject("P").get("mode").getAsString()
                        .equals("heating_coils"), "lucid coils retain native tier matching");
        check(spec.getAsJsonObject("bindings").getAsJsonObject("P").get("preview").getAsString()
                        .equals("kubejs:starmetal_coil_block"), "lucid preview retains schematic coil");
        check(spec.get("sourceSha256").getAsString().equals(sha256(Files.readAllBytes(
                Path.of("SchemTool/input/lucid_etchdreamer.schem")))), "lucid exact schematic input");
        byte[] payload;
        try (var input = new GZIPInputStream(Files.newInputStream(root.resolve("lucid_etchdreamer.pattern.gz")))) {
            payload = input.readAllBytes();
        }
        check(spec.get("sha256").getAsString().equals(sha256(payload)), "lucid generated geometry hash");
        List<String[]> aisles = read(root.resolve("lucid_etchdreamer.pattern.gz"));
        check(aisles.size() == 233 && aisles.getFirst().length == 233 && aisles.getFirst()[0].length() == 233,
                "lucid 233-cube dimensions");
        int[] counts = new int[128];
        for (int z = 0; z < aisles.size(); z++) {
            for (int y = 0; y < aisles.get(z).length; y++) {
                String row = aisles.get(z)[y];
                for (int x = 0; x < row.length(); x++) {
                    char symbol = row.charAt(x);
                    counts[symbol]++;
                    if (symbol == 'C') check(x == 84 && y == 104 && z == 156, "lucid controller anchor");
                    if (symbol == 'X') check(z == 156 && x >= 82 && x <= 86 && y >= 102 && y <= 106
                            && !(x == 84 && y == 104), "lucid hatch face is the 5x5 ring");
                }
            }
        }
        check(counts['C'] == 1 && counts['X'] == 24 && counts['P'] == 8488,
                "lucid controller, hatch panel and coil positions retained");
        for (int c = 0; c < counts.length; c++) if (counts[c] > 0)
            check(spec.getAsJsonObject("bindings").has(String.valueOf((char) c)),
                    "lucid every symbol has a runtime predicate");

        List<String[]> dragon = read(STRUCTURES.resolve("dragonfieldproliferationcorestructure.pattern.gz"));
        check(dragon.size() == 78 && dragon.getFirst().length == 73 && dragon.getFirst()[0].length() == 75,
                "dragon structure dimensions retained");
        int dragonHatches = 0;
        for (int z = 0; z < dragon.size(); z++) {
            for (int y = 0; y < dragon.get(z).length; y++) {
                String row = dragon.get(z)[y];
                for (int x = 0; x < row.length(); x++) if (row.charAt(x) == 'X') dragonHatches++;
            }
        }
        check(dragonHatches == 101, "dragon panel has original 14 plus 87 new hatch slots");
        for (int y = 3; y <= 8; y++) {
            String row = dragon.get(70)[y];
            for (int x = 29; x <= 45; x++) {
                check(row.charAt(x) == ((x == 37 && y == 5) ? 'C' : 'X'),
                        "dragon entire controller panel accepts hatches");
            }
        }
        check(dragon.get(69)[5].charAt(37) == 'B' && dragon.get(70)[1].charAt(37) == 'A',
                "dragon rear and roof casings remain structural");
        return checks;
    }

    private static List<String[]> read(Path path) throws Exception {
        List<String[]> aisles = new ArrayList<>();
        StructureData.read(Files.newInputStream(path)).forEachAisle(aisles::add);
        return aisles;
    }

    private static String sha256(byte[] content) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        checks++;
    }
}
