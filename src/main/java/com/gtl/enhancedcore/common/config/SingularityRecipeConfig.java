package com.gtl.enhancedcore.common.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Legacy line files and JSON share registry-independent validation. */
public final class SingularityRecipeConfig {
    private static final Pattern LEGACY = Pattern.compile("\"([^\"]+)\"\\s*([0-9]+)\\s*(?:#.*)?");
    private static final Pattern ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");

    public record Entry(String id, int count, boolean fluid) {
        public String recipePath() {
            int colon = id.indexOf(':');
            return (fluid ? "fluid/" : "item/") + id.substring(0, colon) + "/" + id.substring(colon + 1);
        }
    }

    public record Result(List<Entry> entries, List<String> errors) {
        public Result {
            entries = List.copyOf(entries);
            errors = List.copyOf(errors);
        }
    }

    private SingularityRecipeConfig() {}

    public static Result parse(String input, Predicate<String> itemExists, Predicate<String> fluidExists) {
        String text = input.replaceFirst("^\\uFEFF", "").strip();
        Map<String, Entry> entries = new LinkedHashMap<>();
        List<String> errors = new ArrayList<>();
        if (text.startsWith("[") || text.startsWith("{")) {
            try {
                JsonElement root = JsonParser.parseString(text);
                JsonElement array = root.isJsonObject() ? root.getAsJsonObject().get("recipes") : root;
                if (array == null || !array.isJsonArray()) throw new IllegalArgumentException("Expected recipes array");
                int index = 0;
                for (JsonElement element : array.getAsJsonArray()) {
                    String label = "Recipe " + (++index);
                    try {
                        JsonObject object = element.getAsJsonObject();
                        String id = object.get("id").getAsString();
                        if (object.has("type")) {
                            String type = object.get("type").getAsString();
                            if (!type.equals("item") && !type.equals("fluid")) throw new IllegalArgumentException("Unknown type " + type);
                            id = type + ":" + id;
                        }
                        add(entries, errors, label, id, object.get("count").getAsString(), itemExists, fluidExists);
                    } catch (RuntimeException e) {
                        errors.add(label + ": " + e.getMessage());
                    }
                }
            } catch (RuntimeException e) {
                errors.add("Invalid JSON: " + e.getMessage());
            }
        } else {
            String[] lines = text.split("\\R");
            for (int i = 0; i < lines.length; i++) {
                String line = lines[i].strip();
                if (line.isEmpty() || line.startsWith("#")) continue;
                Matcher matcher = LEGACY.matcher(line);
                if (!matcher.matches()) {
                    errors.add("Line " + (i + 1) + ": expected \"id\"positive-count");
                    continue;
                }
                add(entries, errors, "Line " + (i + 1), matcher.group(1), matcher.group(2), itemExists, fluidExists);
            }
        }
        return new Result(new ArrayList<>(entries.values()), errors);
    }

    private static void add(Map<String, Entry> entries, List<String> errors, String label, String rawId,
                            String rawCount, Predicate<String> itemExists, Predicate<String> fluidExists) {
        try {
            boolean forcedFluid = rawId.startsWith("fluid:");
            boolean forcedItem = rawId.startsWith("item:");
            String id = forcedFluid ? rawId.substring(6) : forcedItem ? rawId.substring(5) : rawId;
            if (!ID.matcher(id).matches()) throw new IllegalArgumentException("Invalid id " + id);
            int count = Integer.parseInt(rawCount);
            if (count <= 0) throw new IllegalArgumentException("Count must be positive");
            boolean fluid = forcedFluid || (!forcedItem && !itemExists.test(id));
            if (!(fluid ? fluidExists : itemExists).test(id)) throw new IllegalArgumentException("Unknown " + (fluid ? "fluid " : "item ") + id);
            Entry entry = new Entry(id, count, fluid);
            if (entries.putIfAbsent(entry.recipePath(), entry) != null) throw new IllegalArgumentException("Duplicate material " + rawId);
        } catch (RuntimeException e) {
            errors.add(label + ": " + e.getMessage());
        }
    }
}
