package com.gtl.enhancedcore.common.world.plot;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** File-authored campus definitions, independent of Minecraft registration and world state. */
public final class CampusPresetRegistry {
    public static final String RESOURCE = "/data/gtl_enhancedcore/worldgen/campus_presets.json";
    /** The file identity is also carried by the bounded plot-info wire. */
    public static final int MAX_ID_LENGTH = 192;
    public record Definition(String id, String nameKey, String descriptionKey, String catalogResource,
                             String layoutResource, String architectureResource, String engine,
                             Map<String, String> materials, Map<String, Integer> dimensions,
                             Map<String, Integer> previewColors) {
        public Definition {
            materials = Map.copyOf(materials);
            dimensions = Map.copyOf(dimensions);
            previewColors = Map.copyOf(previewColors);
        }
    }
    private record Registry(List<Definition> definitions, Map<String, Definition> byId) {}
    private static final Set<String> MATERIALS = Set.of("quartz", "dark", "floor", "road", "light", "yellow",
            "green", "leaves", "slab", "iron", "column", "accent");
    private static final Set<String> DIMENSIONS = Set.of("mainRoadHalfWidth", "outerRoadInset", "boundaryWalkWidth",
            "gridPeriod", "serviceRingWidth", "defaultBedrockY", "defaultSurfaceY");
    private static final Set<String> PREVIEW_COLORS = Set.of("background", "facade", "quartz", "dark", "floor", "road",
            "green", "yellow", "steam", "ore", "metallurgy", "chemistry", "biology", "energy", "computation", "space", "precision");
    private static final Set<String> FIELDS = Set.of("id", "nameKey", "descriptionKey", "catalogResource",
            "layoutResource", "architectureResource", "engine", "terrain");
    private static final class Holder { private static final Registry REGISTRY = load(); }
    private CampusPresetRegistry() {}

    /** All published identities, including retired presets needed by existing worlds. */
    public static List<Definition> definitions() { return Holder.REGISTRY.definitions; }
    /** Creation ordering excludes retired identities while lookup and world loading retain them. */
    public static List<Definition> creationDefinitions() {
        return definitions().stream().filter(definition -> isAvailableForCreation(definition.id())).toList();
    }
    public static boolean isAvailableForCreation(String id) {
        return contains(id) && !id.equals("pink_dream_home_v1");
    }
    public static boolean contains(String id) { return Holder.REGISTRY.byId.containsKey(id); }
    public static Definition get(String id) {
        Definition definition = Holder.REGISTRY.byId.get(id);
        if (definition == null) throw new IllegalArgumentException("Unknown campus preset: " + id);
        return definition;
    }

    private static Registry load() {
        var stream = CampusPresetRegistry.class.getResourceAsStream(RESOURCE);
        if (stream == null) throw new IllegalStateException("Missing campus preset registry: " + RESOURCE);
        try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            return read(JsonParser.parseReader(reader).getAsJsonObject());
        } catch (IOException error) { throw new IllegalStateException("Cannot read campus preset registry", error); }
    }

    /** Pure decode entry also permits authoring checks without loading a Minecraft registry. */
    public static List<Definition> decode(JsonObject json) { return read(json).definitions; }

    private static Registry read(JsonObject json) {
        fields(json, Set.of("schemaVersion", "presets"), "registry");
        JsonElement schema = json.get("schemaVersion");
        if (!schema.isJsonPrimitive() || !schema.getAsJsonPrimitive().isNumber() || !schema.getAsString().equals("1"))
            throw new IllegalArgumentException("Unsupported campus preset registry schema");
        if (!json.get("presets").isJsonArray() || json.getAsJsonArray("presets").isEmpty())
            throw new IllegalArgumentException("Campus preset registry must contain presets");
        List<Definition> definitions = new ArrayList<>();
        Map<String, Definition> byId = new HashMap<>();
        Set<String> translationKeys = new HashSet<>(), resources = new HashSet<>();
        for (var element : json.getAsJsonArray("presets")) {
            if (!element.isJsonObject()) throw new IllegalArgumentException("Campus preset definition must be an object");
            var row = element.getAsJsonObject(); fields(row, FIELDS, "preset");
            String id = string(row, "id"), name = string(row, "nameKey"), description = string(row, "descriptionKey");
            String engine = string(row, "engine");
            if (id.length() > MAX_ID_LENGTH || !id.matches("[a-z0-9][a-z0-9_./-]*")
                    || id.contains("..") || !engine.matches("[a-z0-9_]+"))
                throw new IllegalArgumentException("Invalid campus preset ID or engine: " + id);
            if (!name.matches("[a-z0-9_.]+") || !description.matches("[a-z0-9_.]+")
                    || !translationKeys.add(name) || !translationKeys.add(description))
                throw new IllegalArgumentException("Invalid or duplicate campus preset translation key: " + id);
            if (!row.get("terrain").isJsonObject()) throw new IllegalArgumentException("Invalid campus terrain: " + id);
            var terrain = row.getAsJsonObject("terrain"); fields(terrain, Set.of("materials", "dimensions", "previewColors"), id + " terrain");
            if (!terrain.get("materials").isJsonObject() || !terrain.get("dimensions").isJsonObject() || !terrain.get("previewColors").isJsonObject())
                throw new IllegalArgumentException("Invalid campus terrain materials, dimensions or preview colors: " + id);
            var materialJson = terrain.getAsJsonObject("materials"); fields(materialJson, MATERIALS, id + " materials");
            Map<String, String> materials = new HashMap<>();
            for (String key : MATERIALS) {
                String value = string(materialJson, key);
                if (!value.matches("[a-z0-9_.-]+:[a-z0-9_./-]+(?:\\[[a-z0-9_=,]+\\])?"))
                    throw new IllegalArgumentException("Invalid authored material: " + value);
                materials.put(key, value);
            }
            var dimensionJson = terrain.getAsJsonObject("dimensions"); fields(dimensionJson, DIMENSIONS, id + " dimensions");
            Map<String, Integer> dimensions = new HashMap<>();
            for (String key : DIMENSIONS) {
                var value = dimensionJson.get(key);
                if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber() || !value.getAsString().matches("-?(0|[1-9][0-9]*)"))
                    throw new IllegalArgumentException("Invalid integral terrain dimension: " + id + "/" + key);
                dimensions.put(key, Integer.parseInt(value.getAsString()));
            }
            if (dimensions.get("mainRoadHalfWidth") < 1 || dimensions.get("outerRoadInset") < 1
                    || dimensions.get("boundaryWalkWidth") < 1
                    || dimensions.get("boundaryWalkWidth") >= dimensions.get("outerRoadInset") || dimensions.get("gridPeriod") < 1
                    || dimensions.get("serviceRingWidth") < 1 || dimensions.get("defaultBedrockY") < -64
                    || dimensions.get("defaultSurfaceY") <= dimensions.get("defaultBedrockY")
                    || dimensions.get("defaultSurfaceY") > 319)
                throw new IllegalArgumentException("Invalid campus terrain dimensions: " + id);
            var previewJson = terrain.getAsJsonObject("previewColors"); fields(previewJson, PREVIEW_COLORS, id + " preview colors");
            Map<String, Integer> previewColors = new HashMap<>();
            for (String key : PREVIEW_COLORS) {
                var value = previewJson.get(key);
                if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber() || !value.getAsString().matches("0|[1-9][0-9]*"))
                    throw new IllegalArgumentException("Invalid integral preview color: " + id + "/" + key);
                int color = Integer.parseInt(value.getAsString());
                if (color > 0xffffff) throw new IllegalArgumentException("Preview color exceeds RGB range: " + id + "/" + key);
                previewColors.put(key, color);
            }
            var definition = new Definition(id, name, description,
                    resource(row, "catalogResource", resources), resource(row, "layoutResource", resources),
                    resource(row, "architectureResource", resources), engine, materials, dimensions, previewColors);
            if (byId.putIfAbsent(id, definition) != null) throw new IllegalArgumentException("Duplicate campus preset ID: " + id);
            definitions.add(definition);
        }
        return new Registry(List.copyOf(definitions), Map.copyOf(byId));
    }
    private static void fields(JsonObject object, Set<String> expected, String context) {
        if (!object.keySet().equals(expected)) throw new IllegalArgumentException("Invalid fields in campus " + context);
    }
    private static String string(JsonObject object, String key) {
        JsonElement value = object.get(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString() || value.getAsString().isBlank())
            throw new IllegalArgumentException("Missing campus preset string: " + key);
        return value.getAsString();
    }
    private static String resource(JsonObject object, String key, Set<String> checked) {
        String path = string(object, key);
        if (!path.matches("/data/[a-z0-9_.-]+/[a-z0-9_./-]+\\.json") || path.contains(".."))
            throw new IllegalArgumentException("Invalid campus resource path: " + path);
        if (checked.add(path)) try (var stream = CampusPresetRegistry.class.getResourceAsStream(path)) {
            if (stream == null) throw new IllegalArgumentException("Missing campus resource: " + path);
        } catch (IOException error) { throw new IllegalStateException("Cannot inspect campus resource: " + path, error); }
        return path;
    }
}
