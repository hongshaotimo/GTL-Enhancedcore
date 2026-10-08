package com.gtl.enhancedcore.common.recipe.iv;

import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

/** Reads bundled Chinese assets on either side without touching Minecraft's active language. */
public final class SuperBufferChineseNames {
    private static final String FALLBACK = "\u672a\u547d\u540d\u52a0\u5de5\u6a21\u5f0f";

    private SuperBufferChineseNames() {}

    private static final class Dictionary {
        private static final Map<String, String> NAMES = load();
    }

    public static String resolve(String keyOrName) {
        if (keyOrName == null || keyOrName.isEmpty()) return "";
        String translated = Dictionary.NAMES.get(keyOrName);
        if (translated != null && containsChinese(translated)) return translated;
        return containsChinese(keyOrName) ? keyOrName : FALLBACK;
    }

    public static boolean hasTranslation(String key) {
        return containsChinese(Dictionary.NAMES.getOrDefault(key, ""));
    }

    private static boolean containsChinese(String value) {
        return value.codePoints().anyMatch(code -> Character.UnicodeScript.of(code) == Character.UnicodeScript.HAN);
    }

    private static Map<String, String> load() {
        Map<String, String> names = new HashMap<>();
        var mods = net.minecraftforge.fml.ModList.get();
        // Addons also provide assets/gtceu translations; mod ID and asset namespace are not interchangeable.
        for (String modId : new String[]{"gtceu", "gtmthings", "gtlcore", "gtladditions", "gtl_enhancedcore"}) {
            try {
                if (mods != null) {
                    var mod = mods.getModFileById(modId);
                    if (mod == null) continue;
                    var assets = mod.getFile().findResource("assets");
                    if (!Files.isDirectory(assets)) continue;
                    try (var namespaces = Files.list(assets)) {
                        for (var namespace : namespaces.sorted().toList()) {
                            var file = namespace.resolve("lang/zh_cn.json");
                            if (Files.isRegularFile(file)) {
                                try (var reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                                    read(names, reader);
                                }
                            }
                        }
                    }
                } else {
                    // Plain-classpath tests do not initialize Forge's module resource lookup.
                    var resources = SuperBufferChineseNames.class.getClassLoader()
                            .getResources("assets/" + modId + "/lang/zh_cn.json");
                    while (resources.hasMoreElements()) {
                        try (var reader = new InputStreamReader(resources.nextElement().openStream(), StandardCharsets.UTF_8)) {
                            read(names, reader);
                        }
                    }
                }
            } catch (IOException | RuntimeException failure) {
                com.gtl.enhancedcore.GTLEnhancedcore.LOGGER.warn("Cannot load automatic buffer names from {}", modId, failure);
            }
        }
        return Map.copyOf(names);
    }

    private static void read(Map<String, String> names, java.io.Reader reader) {
        JsonParser.parseReader(reader).getAsJsonObject().entrySet().forEach(entry -> {
            if (entry.getValue().isJsonPrimitive()) names.put(entry.getKey(), entry.getValue().getAsString());
        });
    }
}
