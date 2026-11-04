package com.gtl.enhancedcore;

import com.google.gson.JsonParser;
import com.gtl.enhancedcore.common.data.machine.LanthanideRefiningRecipeLoader;
import com.gtl.enhancedcore.common.recipe.EquipmentRecipeCatalog;
import com.gtl.enhancedcore.common.structure.StructureData;
import com.gtl.enhancedcore.common.util.TooltipPolicy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Set;

/** Exact rare-earth catalog, supplied geometry and LuV acquisition contract. */
public final class LanthanideRefiningRegression {
    private static int assertions;

    public static int run() throws Exception {
        assertions = 0;
        check(Set.copyOf(LanthanideRefiningRecipeLoader.OUTPUT_ITEMS).equals(Set.of(
                "gtceu:lanthanum_dust", "gtceu:praseodymium_dust", "gtceu:neodymium_dust",
                "gtceu:cerium_dust", "gtceu:europium_dust", "gtceu:gadolinium_dust",
                "gtceu:samarium_dust", "gtceu:terbium_dust", "gtceu:dysprosium_dust",
                "gtceu:holmium_dust", "gtceu:erbium_dust", "gtceu:thulium_dust",
                "gtceu:ytterbium_dust", "gtceu:lutetium_dust", "gtceu:scandium_dust", "gtceu:yttrium_dust"))
                        && LanthanideRefiningRecipeLoader.OUTPUT_ITEMS.size() == 16,
                "All sixteen requested dusts occur exactly once");
        check(LanthanideRefiningRecipeLoader.DUST_AMOUNT == 128
                        && LanthanideRefiningRecipeLoader.FLUID_AMOUNT_MB == 1000
                        && LanthanideRefiningRecipeLoader.DURATION_TICKS == 1200,
                "The recipe preserves exact quantities and the 60-second LuV base duration");
        try (var reader = Files.newBufferedReader(Path.of(
                "src/main/resources/data/gtl_enhancedcore/equipment_recipes.json"))) {
            var recipe = EquipmentRecipeCatalog.read(reader).stream()
                    .filter(value -> value.output().equals("lanthanide_refining_hub")).findFirst().orElseThrow();
            check(recipe.tier() == 6 && recipe.method().equals("assembly_line") && recipe.duration() == 2400,
                    "Acquisition is a 120-second LuV assembly-line recipe");
            check(recipe.ingredients().stream().anyMatch(value -> value.id().equals("gtceu:europium_foil")
                            && !value.tag() && value.count() == 64),
                    "Acquisition consumes europium foil");
            check(recipe.fluids().stream().anyMatch(value -> value.id().equals("gtceu:europium")
                            && value.amount() == 2304),
                    "The required europium ID is a molten-fluid ingredient");
            check(recipe.ingredients().stream().noneMatch(value -> value.id().equals("gtceu:europium")),
                    "The fluid registry ID is never resolved as an item");
        }
        var counts = new HashMap<Character, Integer>();
        int[] aisles = {0};
        try (var input = Files.newInputStream(Path.of(
                "src/main/resources/data/gtl_enhancedcore/structures/lanthanide_refining_hub.pattern.gz"))) {
            var structure = StructureData.read(input);
            structure.forEachAisle(rows -> {
                check(rows.length == 45, "Every aisle retains its supplied height");
                aisles[0]++;
                for (String row : rows) {
                    check(row.length() == 57, "Every row retains its supplied width");
                    row.chars().forEach(c -> counts.merge((char)c, 1, Integer::sum));
                }
            });
        }
        check(aisles[0] == 71, "The supplied blueprint keeps its depth");
        check(counts.getOrDefault('C', 0) == 1 && counts.getOrDefault('X', 0) == 64,
                "Exactly one controller and all 64 marked hatch cells remain");
        var page = TooltipPolicy.page("lanthanide_refining_hub");
        check(page.details().isEmpty() && page.summary().contains(
                        "tooltip.gtl_enhancedcore.lanthanide_refining_hub.hatches"),
                "Hatch limits are visible without Shift");
        for (String locale : new String[]{"zh_cn", "en_us"}) {
            var lang = JsonParser.parseString(Files.readString(Path.of(
                    "src/main/resources/assets/gtl_enhancedcore/lang/" + locale + ".json"))).getAsJsonObject();
            check(lang.has("block.gtl_enhancedcore.lanthanide_refining_hub")
                            && lang.has("gtl_enhancedcore.lanthanide_refining"),
                    "Machine and recipe names are translated: " + locale);
        }
        return assertions;
    }

    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }
}
