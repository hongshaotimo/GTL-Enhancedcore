package com.gtl.enhancedcore;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.gtl.enhancedcore.common.world.plot.CampusPresetRegistry;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/** File registration, immutable published metadata and explicit authoring failures. */
final class CampusPresetRegistryRegression {
    private static int checks;
    static int run() throws Exception {
        checks = 0;
        var quartz = CampusPresetRegistry.get("quartz_works");
        check(CampusPresetRegistry.definitions().contains(quartz) && CampusPresetRegistry.contains(quartz.id()),
                "The file-authored factory appears in the registry and exact ID lookup");
        check(quartz.engine().equals("quartz_factory") && quartz.materials().get("quartz").equals("minecraft:smooth_quartz")
                        && quartz.dimensions().get("mainRoadHalfWidth") == 16
                        && quartz.dimensions().get("defaultBedrockY") == -64 && quartz.dimensions().get("defaultSurfaceY") == -53,
                "The factory's engine, palette and geometry defaults come from its manifest");
        rejects(() -> CampusPresetRegistry.get("missing_preset"), "Unknown identities cannot fall back to another preset");
        check(!CampusPresetRegistry.contains("celestial_megastructure"), "The removed celestial preset has no registry entry");
        rejects(() -> CampusPresetRegistry.get("celestial_megastructure"), "The removed celestial identity is rejected as unknown");
        check(!CampusPresetRegistry.contains("woodland_lake_v2"), "The withdrawn woodland V2 has no creation-menu entry");
        rejects(() -> CampusPresetRegistry.get("woodland_lake_v2"), "Withdrawn woodland V2 is rejected without aliasing another preset");
        for (String resource : new String[] {"woodland_v2_machines.json", "woodland_v2_layout.json", "woodland_v2_architecture.json"})
            check(CampusPresetRegistry.class.getResource("/data/gtl_enhancedcore/worldgen/" + resource) == null,
                    "Withdrawn woodland V2 resource is absent: " + resource);
        immutable(() -> CampusPresetRegistry.definitions().clear(), "Registry menu ordering is immutable");
        immutable(() -> quartz.materials().put("quartz", "minecraft:stone"), "A caller cannot recolor a shared definition");
        immutable(() -> quartz.dimensions().put("gridPeriod", 1), "A caller cannot move a shared definition's dimensions");
        immutable(() -> quartz.previewColors().put("quartz", 0), "A caller cannot recolor a shared preview palette");
        JsonObject original;
        try (var reader = new InputStreamReader(CampusPresetRegistry.class.getResourceAsStream(CampusPresetRegistry.RESOURCE), StandardCharsets.UTF_8)) {
            original = JsonParser.parseReader(reader).getAsJsonObject();
        }
        var extended = original.deepCopy();
        var additional = original.getAsJsonArray("presets").get(0).getAsJsonObject().deepCopy();
        additional.addProperty("id", "second_file_preset");
        additional.addProperty("nameKey", "gtl_enhancedcore.preset.second.name");
        additional.addProperty("descriptionKey", "gtl_enhancedcore.preset.second.description");
        extended.getAsJsonArray("presets").add(additional);
        var decoded = CampusPresetRegistry.decode(extended);
        check(decoded.size() == CampusPresetRegistry.definitions().size() + 1
                        && decoded.getLast().id().equals("second_file_preset"),
                "Adding another file entry requires no RoadStyle enum or Java registration");
        var invalid = extended.deepCopy();
        invalid.getAsJsonArray("presets").get(1).getAsJsonObject().addProperty("id", quartz.id());
        rejects(() -> CampusPresetRegistry.decode(invalid), "Duplicate preset IDs fail at the authoring boundary");
        var duplicateName = extended.deepCopy();
        duplicateName.getAsJsonArray("presets").get(1).getAsJsonObject().addProperty("nameKey", quartz.nameKey());
        rejects(() -> CampusPresetRegistry.decode(duplicateName), "Duplicate translated menu identities are rejected");
        var unknownField = original.deepCopy(); unknownField.addProperty("typo", true);
        rejects(() -> CampusPresetRegistry.decode(unknownField), "Unknown registry fields cannot be silently ignored");
        var missingResource = original.deepCopy();
        missingResource.getAsJsonArray("presets").get(0).getAsJsonObject()
                .addProperty("layoutResource", "/data/gtl_enhancedcore/worldgen/absent_preset_layout.json");
        rejects(() -> CampusPresetRegistry.decode(missingResource), "A missing layout fails before a player creates a dimension");
        var invalidPath = original.deepCopy();
        invalidPath.getAsJsonArray("presets").get(0).getAsJsonObject().addProperty("layoutResource", "../layout.json");
        rejects(() -> CampusPresetRegistry.decode(invalidPath), "Preset resources use explicit classpath paths");
        var fractional = original.deepCopy();
        fractional.getAsJsonArray("presets").get(0).getAsJsonObject().getAsJsonObject("terrain")
                .getAsJsonObject("dimensions").addProperty("gridPeriod", 2.5);
        rejects(() -> CampusPresetRegistry.decode(fractional), "Block dimensions must be integral");
        var wrongSchema = original.deepCopy(); wrongSchema.addProperty("schemaVersion", 2);
        rejects(() -> CampusPresetRegistry.decode(wrongSchema), "Unsupported manifest schemas fail explicitly");
        var outOfRgb = original.deepCopy();
        outOfRgb.getAsJsonArray("presets").get(0).getAsJsonObject().getAsJsonObject("terrain")
                .getAsJsonObject("previewColors").addProperty("quartz", 0x1000000);
        rejects(() -> CampusPresetRegistry.decode(outOfRgb), "Preview colors use a complete bounded RGB palette");
        var overlongId = original.deepCopy();
        overlongId.getAsJsonArray("presets").get(0).getAsJsonObject()
                .addProperty("id", "a" + "b".repeat(CampusPresetRegistry.MAX_ID_LENGTH));
        rejects(() -> CampusPresetRegistry.decode(overlongId), "File preset IDs reject the 193-character network boundary");
        return checks;
    }
    private static void rejects(Runnable action, String message) {
        try { action.run(); } catch (IllegalArgumentException expected) { checks++; return; }
        throw new AssertionError(message);
    }
    private static void immutable(Runnable action, String message) {
        try { action.run(); } catch (UnsupportedOperationException expected) { checks++; return; }
        throw new AssertionError(message);
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); checks++; }
}
