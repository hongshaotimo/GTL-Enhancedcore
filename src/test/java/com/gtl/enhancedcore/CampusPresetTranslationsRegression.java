package com.gtl.enhancedcore;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.gtl.enhancedcore.common.world.plot.CampusPresetRegistry;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/** Checks translations reached through preset IDs, including pages beyond the layout tab. */
final class CampusPresetTranslationsRegression {
    private static int checks;

    static int run() throws Exception {
        checks = 0;
        String screen = Files.readString(Path.of(
                "src/main/java/com/gtl/enhancedcore/client/plot/CreateWorldPlotScreen.java"));
        var calls = Pattern.compile("presetHint\\([^\\n]*?,\\s*\"([^\"]+)\"\\)").matcher(screen);
        Set<String> suffixes = new HashSet<>();
        while (calls.find()) suffixes.add(calls.group(1));
        check(suffixes.containsAll(Set.of("ae_hint", "districts_hint", "controller_hint", "authored", "materials")),
                "Preset translation coverage includes layout, roads and materials page calls");

        for (String locale : List.of("zh_cn", "en_us")) {
            JsonObject language = JsonParser.parseString(Files.readString(Path.of(
                    "src/main/resources/assets/gtl_enhancedcore/lang/" + locale + ".json"))).getAsJsonObject();
            for (var definition : CampusPresetRegistry.definitions()) {
                translated(language, definition.nameKey(), locale);
                translated(language, definition.descriptionKey(), locale);
                String prefix = switch (definition.engine()) {
                    case "quartz_factory" -> "factory";
                    case "file_scene" -> definition.id();
                    default -> "file";
                };
                for (String suffix : suffixes)
                    translated(language, "gtl_enhancedcore.create_world_plot." + prefix + "_" + suffix, locale);
            }
            for (String prefix : List.of("campus", "file"))
                for (String suffix : suffixes)
                    translated(language, "gtl_enhancedcore.create_world_plot." + prefix + "_" + suffix, locale);
        }
        return checks;
    }

    private static void translated(JsonObject language, String key, String locale) {
        check(language.has(key) && language.get(key).isJsonPrimitive()
                        && language.get(key).getAsJsonPrimitive().isString()
                        && !language.get(key).getAsString().isBlank()
                        && !language.get(key).getAsString().equals(key),
                locale + " resolves preset translation " + key);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        checks++;
    }
}
