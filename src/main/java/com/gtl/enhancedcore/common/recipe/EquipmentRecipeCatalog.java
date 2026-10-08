package com.gtl.enhancedcore.common.recipe;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Bundled equipment costs, independent of Minecraft registries for validation and review. */
public final class EquipmentRecipeCatalog {
    public static final List<String> TIERS = List.of("ulv", "lv", "mv", "hv", "ev", "iv", "luv", "zpm",
            "uv", "uhv", "uev", "uiv", "uxv", "opv", "max");
    /** 无限资源仓室只通过创造模式或命令获取，不生成生存配方。 */
    public static final Set<String> CREATIVE_MODE_ONLY_OUTPUTS = Set.of("creative_computation_receiver_hatch");
    public record Ingredient(String id, boolean tag, int count) {}
    public record Fluid(String id, int amount) {}
    public record Recipe(String output, int tier, String method, int duration, List<Ingredient> ingredients,
                         List<Fluid> fluids, List<String> pattern, Map<Character, Ingredient> key) {}

    private EquipmentRecipeCatalog() {}

    public static List<Recipe> read(Reader reader) {
        JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
        if (root.get("schema").getAsInt() != 1) throw new IllegalArgumentException("Unsupported equipment recipe schema");
        List<Recipe> recipes = new ArrayList<>();
        Set<String> outputs = new HashSet<>();
        for (var element : root.getAsJsonArray("recipes")) {
            JsonObject json = element.getAsJsonObject();
            String output = json.get("output").getAsString();
            if (!output.matches("[a-z0-9_]+") || !outputs.add(output)) throw new IllegalArgumentException("Invalid/duplicate output " + output);
            int tier = TIERS.indexOf(json.get("tier").getAsString());
            if (tier < 0) throw new IllegalArgumentException("Invalid tier for " + output);
            String method = json.get("method").getAsString();
            List<Ingredient> items = new ArrayList<>();
            List<Fluid> fluids = new ArrayList<>();
            List<String> pattern = new ArrayList<>();
            Map<Character, Ingredient> key = new LinkedHashMap<>();
            int duration = 0;
            if (method.equals("crafting")) {
                json.getAsJsonArray("pattern").forEach(row -> pattern.add(row.getAsString()));
                if (pattern.size() != 3 || pattern.stream().anyMatch(row -> row.length() != 3)) throw new IllegalArgumentException("Invalid shaped pattern " + output);
                json.getAsJsonObject("key").entrySet().forEach(entry -> {
                    if (entry.getKey().length() != 1 || entry.getKey().equals(" ")) throw new IllegalArgumentException("Invalid pattern key");
                    Ingredient value = ingredient(entry.getValue().getAsJsonObject());
                    if (value.count != 1) throw new IllegalArgumentException("Crafting key count must be one");
                    key.put(entry.getKey().charAt(0), value);
                });
                Set<Character> used = new HashSet<>();
                pattern.forEach(row -> row.chars().filter(c -> c != ' ').forEach(c -> used.add((char) c)));
                if (!used.equals(key.keySet())) throw new IllegalArgumentException("Pattern/key mismatch " + output);
            } else {
                int maxItems = switch (method) { case "assembler" -> 9; case "assembly_line" -> 16;
                    default -> throw new IllegalArgumentException("Unknown recipe method " + method); };
                duration = json.get("duration").getAsInt();
                json.getAsJsonArray("ingredients").forEach(value -> items.add(ingredient(value.getAsJsonObject())));
                if (duration <= 0 || items.isEmpty() || items.size() > maxItems) throw new IllegalArgumentException("Invalid machine recipe " + output);
                json.getAsJsonArray("fluids").forEach(value -> {
                    JsonObject fluid = value.getAsJsonObject();
                    String id = fluid.get("fluid").getAsString();
                    int amount = fluid.get("amount").getAsInt();
                    if (!validId(id) || amount <= 0) throw new IllegalArgumentException("Invalid fluid " + id);
                    fluids.add(new Fluid(id, amount));
                });
                if (fluids.size() > (method.equals("assembler") ? 1 : 4)) throw new IllegalArgumentException("Too many fluids " + output);
                if (method.equals("assembly_line") && tier < 6) throw new IllegalArgumentException("Assembly line is LuV+ " + output);
            }
            recipes.add(new Recipe(output, tier, method, duration, List.copyOf(items), List.copyOf(fluids),
                    List.copyOf(pattern), Map.copyOf(key)));
        }
        return List.copyOf(recipes);
    }

    private static Ingredient ingredient(JsonObject json) {
        boolean tag = json.has("tag");
        if (tag == json.has("item")) throw new IllegalArgumentException("Expected one item or tag");
        String id = json.get(tag ? "tag" : "item").getAsString();
        int count = json.get("count").getAsInt();
        // 上限 64 是原版单堆上限，但 GTCEu 配方允许更大的单条投料量
        // （装配线惯用数百个，如龙蛋 128；构造期由 GTRecipeBuilder 直接接收数量，不按堆拆分）。
        // 2026-09-24 用户配方「128× minecraft:dragon_egg」触发该断言，故上限放宽到 INTEGER 上限的合理值。
        if (!validId(id) || count < 1 || count > MAX_INGREDIENT_COUNT) {
            throw new IllegalArgumentException("Invalid ingredient " + id);
        }
        return new Ingredient(id, tag, count);
    }

    /** 单条配方的单种材料上限：GTCEu 无 64 限制，这里仅防御明显异常的配置值。 */
    private static final int MAX_INGREDIENT_COUNT = 1_000_000;

    private static boolean validId(String id) { return id.matches("[a-z0-9_.-]+:[a-z0-9_./-]+"); }
}
