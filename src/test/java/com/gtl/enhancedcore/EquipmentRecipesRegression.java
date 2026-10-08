package com.gtl.enhancedcore;

import com.google.gson.JsonParser;
import com.gtl.enhancedcore.common.recipe.EquipmentRecipeCatalog;
import com.gtl.enhancedcore.common.recipe.PlantResearchMigration;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

public final class EquipmentRecipesRegression {
    public static int run() throws Exception {
        int checked = 0;
        var byOutput = new HashMap<String, EquipmentRecipeCatalog.Recipe>();
        try (var reader = Files.newBufferedReader(Path.of("src/main/resources/data/gtl_enhancedcore/equipment_recipes.json"))) {
            for (var recipe : EquipmentRecipeCatalog.read(reader)) byOutput.put(recipe.output(), recipe);
        }
        var baseline = JsonParser.parseString(Files.readString(Path.of("src/test/resources/registration-baseline.json"))).getAsJsonObject();
        Set<String> expected = new HashSet<>(Set.of("pattern_generator"));
        for (String list : new String[]{"literal_machine_ids", "tiered_machine_ids"}) {
            baseline.getAsJsonArray(list).forEach(value -> expected.add(value.getAsString()));
        }
        baseline.getAsJsonArray("plus_factory_ids").forEach(value -> expected.add(value.getAsString()));
        baseline.getAsJsonArray("resonator_tiers").forEach(value -> expected.add(value.getAsString().toLowerCase(java.util.Locale.ROOT) + "_crystal_resonator"));
        baseline.getAsJsonArray("charger_tiers").forEach(value -> expected.add(value.getAsString().toLowerCase(java.util.Locale.ROOT) + "_wireless_charger"));
        require(expected.containsAll(EquipmentRecipeCatalog.CREATIVE_MODE_ONLY_OUTPUTS),
                "Creative-only equipment must be registered"); checked++;
        expected.removeAll(EquipmentRecipeCatalog.CREATIVE_MODE_ONLY_OUTPUTS);
        require(byOutput.keySet().equals(expected), "Every non-creative registered item needs exactly one acquisition recipe"); checked++;
        var upgradedCircuits = java.util.Map.of(
                "stellar_confinement_fusion_reactor", "kubejs:cosmic_mainframe",
                "hyperstructural_chemical_distorter", "kubejs:exotic_mainframe");
        for (var entry : upgradedCircuits.entrySet()) {
            var recipe = byOutput.get(entry.getKey());
            require(recipe.ingredients().stream().anyMatch(i -> i.id().equals(entry.getValue()) && i.count() == 16),
                    "Requested mainframe x16 required: " + entry.getKey()); checked++;
            require(recipe.ingredients().stream().noneMatch(i -> i.id().equals("gtceu:nano_processor_mainframe")
                            || i.id().equals("gtceu:wetware_processor_mainframe")),
                    "Old mainframe must not remain: " + entry.getKey()); checked++;
        }
        for (var recipe : byOutput.values()) {
            for (var ingredient : recipe.ingredients()) {
                if (ingredient.id().startsWith("gtl_enhancedcore:")) {
                    var prerequisite = byOutput.get(ingredient.id().substring("gtl_enhancedcore:".length()));
                    // 允许同阶段前置：MV 无线充能器的配方电压为 LV，其前置 lv_wireless_charger 同为 LV
                    //（2026-09-22 用户指定「配方电压改成 LV」）。真正的约束是不允许前置阶段高于本配方，
                    // 也不允许自引用，否则会出现阶段倒挂或循环依赖。
                    require(prerequisite != null && prerequisite.tier() <= recipe.tier(),
                            "No self/forward dependency for " + recipe.output()); checked++;
                }
            }
            if (!recipe.method().equals("crafting")) {
                require(recipe.ingredients().stream().anyMatch(ingredient -> ingredient.id().equals(
                        "gtceu:circuits/" + EquipmentRecipeCatalog.TIERS.get(recipe.tier()))), "Own-tier circuits required: " + recipe.output()); checked++;
            }
        }
        String previous = null;
        for (int tier = 1; tier <= 14; tier++) {
            String name = EquipmentRecipeCatalog.TIERS.get(tier) + "_crystal_resonator";
            var recipe = byOutput.get(name);
            require(recipe.tier() == tier, "Resonator processing voltage must match tier"); checked++;
            if (previous != null) {
                String prerequisite = "gtl_enhancedcore:" + previous;
                require(recipe.ingredients().stream().anyMatch(i -> i.id().equals(prerequisite) && i.count() == 1), "Resonator upgrade consumes previous tier"); checked++;
            }
            previous = name;
        }
        var original = JsonParser.parseString("""
                {"inputs":{"item":[{"content":{"item":"gtceu:processing_plant"}}]},
                 "outputs":{"item":[{"content":{"item":"gtceu:data_module","nbt":"1x_gtceu_processing_plant"}}]},
                 "duration":4096000,"tickInputs":{"eu":2013265920}}
                """);
        var migrated = PlantResearchMigration.migrate("gtceu:research_station/1_x_gtceu_processing_plant", original);
        require(!original.equals(migrated), "Research item migrated"); checked++;
        require(migrated.getAsJsonObject().get("inputs").toString().contains("gtl_enhancedcore:processing_plus"),
                "Research consumes the replacement factory"); checked++;
        require(original.toString().contains("\"item\":\"gtceu:processing_plant\""), "Original JSON untouched"); checked++;
        require(migrated.getAsJsonObject().get("outputs").equals(original.getAsJsonObject().get("outputs")), "Research output ID/NBT unchanged"); checked++;
        require(migrated.getAsJsonObject().get("duration").equals(original.getAsJsonObject().get("duration")), "Research cost unchanged"); checked++;
        require(PlantResearchMigration.migrate("gtceu:other", original) == original, "Other recipes untouched"); checked++;
        require(PlantResearchMigration.migrate("gtceu:research_station/1_x_gtceu_processing_plant", migrated).equals(migrated), "Migration is idempotent"); checked++;
        require(PlantResearchMigration.recipeIds().equals(Set.of(
                "gtceu:research_station/1_x_gtceu_processing_plant",
                "gtceu:research_station/1_x_gtceu_assemble_plant",
                "gtceu:research_station/1_x_gtceu_separated_plant",
                "gtceu:research_station/1_x_gtceu_mixed_plant")), "Targeted migration covers exactly four existing IDs"); checked++;
        for (String plant : Set.of("processing_plant", "assemble_plant", "separated_plant", "mixed_plant")) {
            var research = JsonParser.parseString("{\"inputs\":{\"item\":[{\"content\":{\"item\":\"gtceu:" + plant
                    + "\"}}]},\"outputs\":{\"research\":\"" + plant + "\"}}");
            var replacement = PlantResearchMigration.migrate("gtceu:research_station/1_x_gtceu_" + plant, research).getAsJsonObject();
            String expectedReplacement = switch (plant) {
                case "processing_plant" -> "processing_plus";
                case "assemble_plant" -> "assembling_plus";
                case "separated_plant" -> "separating_plus";
                case "mixed_plant" -> "mixing_plus";
                default -> throw new AssertionError("unknown plant " + plant);
            };
            require(replacement.get("inputs").toString().contains("gtl_enhancedcore:" + expectedReplacement),
                    "Every retired research input uses the combined factory"); checked++;
            require(replacement.get("outputs").equals(research.getAsJsonObject().get("outputs")),
                    "Existing research identity is retained"); checked++;
        }
        var recipes = new HashMap<String, com.google.gson.JsonElement>();
        recipes.put("gtceu:other", original);
        recipes.put("gtceu:research_station/1_x_gtceu_processing_plant", original);
        for (String id : PlantResearchMigration.recipeIds()) {
            recipes.computeIfPresent(id, PlantResearchMigration::migrate);
        }
        require(recipes.size() == 2 && recipes.get("gtceu:other") == original,
                "Targeted migration neither scans nor inserts absent recipes"); checked++;
        require(recipes.get("gtceu:research_station/1_x_gtceu_processing_plant").equals(migrated),
                "Targeted migration retains the existing result"); checked++;
        return checked;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
