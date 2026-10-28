package com.gtl.enhancedcore.common.registration;

import com.google.gson.JsonElement;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Unconditional removals. Definitions stay registered only for upstream static recipe references. */
public final class RetiredMachines {
    public static final String ARRAY = "gtceu:transfinite_computation_array";
    public static final String CPU_INTERFACE = "gtceu:me_crafting_cpu_interface";
    private static final Set<String> IDS = Set.of(ARRAY, CPU_INTERFACE);
    private RetiredMachines() {}
    public static boolean contains(String id) { return IDS.contains(id); }
    public static Set<String> ids() { return IDS; }
    /** Covers GT content/ingredient wrappers, vanilla results and recursive ingredient alternatives. */
    public static boolean recipeReferencesRetiredItem(JsonElement value) {
        if (value == null || value.isJsonNull()) return false;
        if (value.isJsonPrimitive())
            return value.getAsJsonPrimitive().isString() && contains(value.getAsString());
        if (value.isJsonArray()) {
            for (var child : value.getAsJsonArray()) if (recipeReferencesRetiredItem(child)) return true;
        } else if (value.isJsonObject()) {
            for (var entry : value.getAsJsonObject().entrySet())
                if (recipeReferencesRetiredItem(entry.getValue())) return true;
        }
        return false;
    }
    public static <K> Map<K, JsonElement> filterRecipes(Map<K, JsonElement> source) {
        Map<K, JsonElement> result = new LinkedHashMap<>(source);
        result.entrySet().removeIf(entry -> recipeReferencesRetiredItem(entry.getValue()));
        return result;
    }
}
