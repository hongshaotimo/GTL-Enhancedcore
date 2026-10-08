package com.gtl.enhancedcore.common.recipe;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/** Changes the acquisition recipe's circuit grade without replacing its shape or other materials. */
public final class MvCircuitAssemblerRecipe {
    public static final String ID = "gtceu:mv_circuit_assembler";
    public static final String RECIPE_ID = "gtceu:shaped/mv_circuit_assembler";
    private MvCircuitAssemblerRecipe() {}

    public static JsonElement useMvCircuits(JsonElement source) {
        if (!source.isJsonObject()) return source;
        JsonObject recipe = source.getAsJsonObject();
        if (!recipe.has("result") || !recipe.get("result").isJsonObject()) return source;
        JsonObject result = recipe.getAsJsonObject("result");
        if (!result.has("item") || !ID.equals(result.get("item").getAsString()) || !recipe.has("key")) return source;
        JsonObject changed = recipe.deepCopy();
        return replaceCircuitTags(changed.get("key")) ? changed : source;
    }

    private static boolean replaceCircuitTags(JsonElement element) {
        boolean changed = false;
        if (element.isJsonArray()) {
            for (JsonElement child : element.getAsJsonArray()) changed |= replaceCircuitTags(child);
        } else if (element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();
            JsonElement tag = object.get("tag");
            if (tag != null && tag.isJsonPrimitive() && tag.getAsJsonPrimitive().isString()
                    && tag.getAsString().startsWith("gtceu:circuits/") && !tag.getAsString().equals("gtceu:circuits/mv")) {
                object.addProperty("tag", "gtceu:circuits/mv");
                changed = true;
            }
            for (var entry : object.entrySet()) if (!entry.getKey().equals("tag")) changed |= replaceCircuitTags(entry.getValue());
        }
        return changed;
    }
}
