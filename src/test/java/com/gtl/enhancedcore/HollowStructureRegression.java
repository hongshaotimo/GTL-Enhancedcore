package com.gtl.enhancedcore;

import com.google.gson.JsonParser;
import com.gtl.enhancedcore.common.structure.HollowStructureResources;
import com.gtl.enhancedcore.common.structure.StructureData;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

/** Validate the actual runtime layout separately from the preserved source-import baselines. */
final class HollowStructureRegression {
    private static final String ROOT = "/data/gtl_enhancedcore/structures/";
    private static int checks;

    static int run() throws Exception {
        Path resources = Path.of("src/main/resources");
        byte[] manifestBytes = Files.readAllBytes(resources.resolve(ROOT.substring(1) + "hollow/manifest.json"));
        try (var input = HollowStructureRegression.class.getResourceAsStream(ROOT + "hollow/manifest.json")) {
            check(input != null && java.util.Arrays.equals(input.readAllBytes(), manifestBytes), "packaged review manifest");
        }
        var manifest = JsonParser.parseString(new String(manifestBytes, StandardCharsets.UTF_8)).getAsJsonObject();
        byte[] bindingBytes = Files.readAllBytes(Path.of("tools/hidden_structure_bindings.json"));
        check(sha(bindingBytes).equals(manifest.get("bindingsSha256").getAsString()), "reviewed predicate evidence unchanged");
        var bindings = JsonParser.parseString(new String(bindingBytes, StandardCharsets.UTF_8)).getAsJsonObject()
                .getAsJsonObject("resources");
        Map<String, com.google.gson.JsonObject> specs = new HashMap<>();
        for (var entry : bindings.entrySet()) {
            var spec = entry.getValue().getAsJsonObject();
            specs.put(spec.get("resource").getAsString(), spec);
        }
        check(manifest.getAsJsonObject("resources").keySet().equals(specs.keySet()), "every reviewed active layout audited");
        long total = 0;
        for (var entry : manifest.getAsJsonObject("resources").entrySet()) {
            String resource = entry.getKey();
            var spec = entry.getValue().getAsJsonObject();
            byte[] source = payload(resources.resolve(resource.substring(1)));
            check(sha(source).equals(spec.get("sourceSha256").getAsString()), resource + " source hash");
            List<String[]> old = rows(resources.resolve(resource.substring(1)));
            String resolved = HollowStructureResources.resolve(resource, true);
            check(HollowStructureResources.resolve(resource, false).equals(resource),
                    resource + " replacement switch restores original geometry");
            if (!spec.has("optimizedResource")) {
                check(resolved.equals(resource) && spec.get("removed").getAsInt() == 0, resource + " reviewed without deletion");
                continue;
            }
            check(resolved.equals(spec.get("optimizedResource").getAsString()), resource + " actual runtime resolution");
            Path target = resources.resolve(resolved.substring(1));
            byte[] optimized = payload(target);
            check(sha(optimized).equals(spec.get("optimizedSha256").getAsString()), resource + " optimized hash");
            try (var input = HollowStructureRegression.class.getResourceAsStream(resolved)) {
                check(input != null && java.util.Arrays.equals(new GZIPInputStream(input).readAllBytes(), optimized),
                        resource + " optimized classpath payload");
            }
            var now = rows(target);
            check(old.size() == now.size(), resource + " depth and orientation");
            int[] removed = new int[128], retained = new int[128];
            var rules = specs.get(resource).getAsJsonObject("symbols");
            for (int z = 0; z < old.size(); z++) {
                check(old.get(z).length == now.get(z).length, resource + " height");
                for (int y = 0; y < old.get(z).length; y++) {
                    String before = old.get(z)[y], after = now.get(z)[y];
                    check(before.length() == after.length(), resource + " width");
                    for (int x = 0; x < before.length(); x++) {
                        char a = before.charAt(x), b = after.charAt(x);
                        retained[b]++;
                        if (a == b) continue;
                        if (b != ' ' || a == ' ') throw new AssertionError(resource + " only block-to-wildcard deletion allowed");
                        var rule = rules.getAsJsonObject(String.valueOf(a));
                        if (rule == null || !rule.get("removableHidden").getAsBoolean()
                                || rule.get("protected").getAsBoolean())
                            throw new AssertionError(resource + " protected functional position removed: " + a);
                        removed[a]++;
                    }
                }
            }
            int count = 0;
            for (int c = 0; c < removed.length; c++) {
                count += removed[c];
                int expected = spec.getAsJsonObject("removedBySymbol").has(String.valueOf((char)c))
                        ? spec.getAsJsonObject("removedBySymbol").get(String.valueOf((char)c)).getAsInt() : 0;
                check(removed[c] == expected, resource + " exact deletion count");
            }
            for (var ruleEntry : rules.entrySet()) {
                var rule = ruleEntry.getValue().getAsJsonObject();
                if (rule.has("minimumRetained"))
                    check(retained[ruleEntry.getKey().charAt(0)] >= rule.get("minimumRetained").getAsInt(),
                            resource + " minimum/context representative preserved: " + ruleEntry.getKey());
            }
            check(count == spec.get("removed").getAsInt() && count > 0, resource + " reviewed deletion total");
            total += count;
        }
        check(total > 0, "interior blocks actually removed from runtime geometry");
        check(HollowStructureResources.resolve(ROOT + "legacy/compact_high_power.pattern.gz", true)
                .equals(ROOT + "legacy/compact_high_power.pattern.gz"), "inactive legacy geometry retained");
        String loader = Files.readString(Path.of("src/main/java/com/gtl/enhancedcore/common/structure/StructurePatterns.java"));
        check(loader.contains("HollowStructureResources.resolve(path)") && loader.contains("builder.where(' '"),
                "runtime uses reviewed optimized geometry and old-block-compatible wildcard");
        System.out.println("Hollow structures: " + total + " hidden blocks removed from runtime layouts");
        return checks;
    }

    private static byte[] payload(Path path) throws Exception {
        try (var input = new GZIPInputStream(new ByteArrayInputStream(Files.readAllBytes(path)))) {
            return input.readAllBytes();
        }
    }

    private static List<String[]> rows(Path path) throws Exception {
        List<String[]> result = new ArrayList<>();
        StructureData.read(Files.newInputStream(path)).forEachAisle(result::add);
        return result;
    }

    private static String sha(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private static void check(boolean valid, String message) {
        if (!valid) throw new AssertionError(message);
        checks++;
    }
}
