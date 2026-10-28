package com.gtl.enhancedcore.common.config;

import com.electronwill.nightconfig.toml.TomlParser;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.StringReader;
import java.util.List;

/** Fixed file allowlist and validation shared by the local editor and server packets. */
public enum ConfigCatalog {
    STARTUP("startup.properties", "restart", true),
    COMMON("common.toml", "instant", true),
    SINGULARITY("infinity_singularity_recipes.json", "recipes", true),
    COPY_KEYS("copy_keys.json", "instant", false),
    TIPS("item_tooltips.json", "instant", false),
    CHANGELOG("changelog.json", "announcement", false),
    PINS("ae_pins.json", "instant", false),
    SEEN("changelog_seen.txt", "announcement", false),
    PREVIEW("preview.properties", "instant", false),
    DISMANTLE("terminal_dismantle.properties", "hot_reload", true),
    GALAXY("galaxy_connections.txt", "restart", true);

    public final String file;
    public final String effect;
    public final boolean server;
    ConfigCatalog(String file, String effect, boolean server) { this.file = file; this.effect = effect; this.server = server; }
    public String key() { return "files." + name().toLowerCase(java.util.Locale.ROOT); }
    public record ClaimSettings(boolean enabled, int interval) {}
    public static ClaimSettings claimSettings(String text) {
        var config = new TomlParser().parse(new StringReader(text));
        Object enabled = config.get("claim_replacement_terminal.enabled");
        Object interval = config.get("claim_replacement_terminal.replace_interval_ticks");
        if (!(enabled instanceof Boolean on) || !(interval instanceof Number n)
                || !(interval instanceof Integer || interval instanceof Long) || n.intValue() < 1 || n.intValue() > 200)
            throw new IllegalArgumentException("claim_replacement_terminal: enabled=true/false; replace_interval_ticks=1..200");
        return new ClaimSettings(on, n.intValue());
    }
    public void validate(String text) {
        if (text.length() > (server ? 8_000 : 2_000_000)) throw new IllegalArgumentException("Configuration too large");
        switch (this) {
            case STARTUP -> StartupConfig.parse(text);
            case PREVIEW -> PreviewLayerSettings.parse(text);
            case DISMANTLE -> TerminalDismantleConfig.parse(text);
            case GALAXY -> GalaxyConnectionConfig.parse(text);
            case COMMON -> claimSettings(text);
            case TIPS -> TooltipOverrides.parse(text);
            case COPY_KEYS -> {
                var root = JsonParser.parseString(text).getAsJsonObject();
                for (String key : List.of("copyItemId", "copyFluidId", "copyRecipe", "liveGenerateRecipe")) bool(root, key);
            }
            case CHANGELOG -> {
                var root = JsonParser.parseString(text).getAsJsonObject();
                bool(root, "enabled"); string(root, "version"); string(root, "title");
                if (root.getAsJsonArray("sections").size() > 128) throw new IllegalArgumentException("Too many sections");
                for (var element : root.getAsJsonArray("sections")) {
                    var section = element.getAsJsonObject(); string(section, "heading");
                    for (var line : section.getAsJsonArray("lines"))
                        if (!line.isJsonPrimitive() || !line.getAsJsonPrimitive().isString())
                            throw new IllegalArgumentException("Announcement lines must be strings");
                }
            }
            case PINS -> {
                var entries = JsonParser.parseString(text).getAsJsonArray();
                if (entries.size() > 9) throw new IllegalArgumentException("At most 9 pinned keys");
                for (var entry : entries) if (!entry.isJsonObject()) throw new IllegalArgumentException("Invalid pinned key");
            }
            case SEEN -> {
                if (text.strip().length() > 128 || text.strip().contains("\n") || text.strip().contains("\r"))
                    throw new IllegalArgumentException("Expected a single version, or an empty file");
            }
            case SINGULARITY -> { /* Registry-aware validation is performed by the recipe loader. */ }
        }
    }
    private static void bool(JsonObject root, String key) {
        if (!root.has(key) || !root.get(key).isJsonPrimitive() || !root.getAsJsonPrimitive(key).isBoolean())
            throw new IllegalArgumentException(key + ": expected boolean");
    }
    private static void string(JsonObject root, String key) {
        if (!root.has(key) || !root.get(key).isJsonPrimitive() || !root.getAsJsonPrimitive(key).isString())
            throw new IllegalArgumentException(key + ": expected string");
    }
}
