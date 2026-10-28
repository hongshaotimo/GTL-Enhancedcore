package com.gtl.enhancedcore.common.structure;

import com.google.gson.JsonParser;
import com.gtl.enhancedcore.common.config.MachineOverrideConfig;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/** Reviewed deletion-only layouts; missing or malformed review data must never silently change geometry. */
public final class HollowStructureResources {
    private static final String ROOT = "/data/gtl_enhancedcore/structures/";
    private static final Map<String, String> RESOURCES = read();

    private HollowStructureResources() {}

    public static String resolve(String source) {
        return resolve(source, MachineOverrideConfig.current().replaceOriginalStructures());
    }

    public static String resolve(String source, boolean replaceOriginalStructures) {
        if (!replaceOriginalStructures) return source;
        return RESOURCES.getOrDefault(source, source);
    }

    private static Map<String, String> read() {
        try (var input = HollowStructureResources.class.getResourceAsStream(ROOT + "hollow/manifest.json")) {
            if (input == null) throw new IOException("Missing hollow structure review manifest");
            var manifest = JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonObject();
            if (manifest.get("schema").getAsInt() != 1) throw new IOException("Unknown hollow structure schema");
            Map<String, String> result = new HashMap<>();
            for (var entry : manifest.getAsJsonObject("resources").entrySet()) {
                var spec = entry.getValue().getAsJsonObject();
                if (!spec.has("optimizedResource")) continue;
                String source = entry.getKey(), target = spec.get("optimizedResource").getAsString();
                if (!source.startsWith(ROOT) || source.contains("..") || !source.endsWith(".pattern.gz")
                        || !target.equals(ROOT + "hollow/" + source.substring(ROOT.length()))) {
                    throw new IOException("Invalid hollow structure resource mapping: " + source);
                }
                result.put(source, target);
            }
            return Map.copyOf(result);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
