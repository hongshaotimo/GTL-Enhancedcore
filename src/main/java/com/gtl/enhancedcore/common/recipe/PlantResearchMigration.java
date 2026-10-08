package com.gtl.enhancedcore.common.recipe;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.util.Set;
import java.util.stream.Collectors;

/** Keeps the original research identity while routing each old plant to its restored Plus machine. */
public final class PlantResearchMigration {
    private static final Set<String> LEGACY_PLANTS = Set.of(
            "processing_plant", "assemble_plant", "separated_plant", "mixed_plant");
    private static final String PREFIX = "gtceu:research_station/1_x_gtceu_";
    private static final Set<String> RECIPE_IDS = LEGACY_PLANTS.stream()
            .map(PREFIX::concat).collect(Collectors.toUnmodifiableSet());

    private PlantResearchMigration() {}

    public static Set<String> recipeIds() { return RECIPE_IDS; }

    public static JsonElement migrate(String recipeId, JsonElement source) {
        if (!recipeId.startsWith(PREFIX)) return source;
        String oldPlant = recipeId.substring(PREFIX.length());
        if (!LEGACY_PLANTS.contains(oldPlant) || !source.isJsonObject()) return source;
        JsonObject result = source.getAsJsonObject().deepCopy();
        JsonElement inputs = result.get("inputs");
        if (inputs != null) replace(inputs, "gtceu:" + oldPlant, "gtl_enhancedcore:" + replacement(oldPlant));
        return result;
    }

    private static String replacement(String oldPlant) {
        return switch (oldPlant) {
            case "processing_plant" -> "processing_plus";
            case "assemble_plant" -> "assembling_plus";
            case "separated_plant" -> "separating_plus";
            case "mixed_plant" -> "mixing_plus";
            default -> throw new IllegalArgumentException("Unknown legacy plant: " + oldPlant);
        };
    }

    private static void replace(JsonElement element, String oldItem, String newItem) {
        if (element.isJsonObject()) {
            for (var entry : element.getAsJsonObject().entrySet()) {
                JsonElement value = entry.getValue();
                if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString() && value.getAsString().equals(oldItem)) {
                    entry.setValue(new JsonPrimitive(newItem));
                } else replace(value, oldItem, newItem);
            }
        } else if (element.isJsonArray()) {
            for (JsonElement value : element.getAsJsonArray()) replace(value, oldItem, newItem);
        }
    }
}
