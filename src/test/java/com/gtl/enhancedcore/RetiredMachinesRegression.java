package com.gtl.enhancedcore;

import com.google.gson.JsonParser;
import com.gtl.enhancedcore.common.registration.RetiredMachines;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

final class RetiredMachinesRegression {
    private static int checks;
    static int run() throws Exception {
        check(RetiredMachines.ids().equals(Set.of("gtceu:transfinite_computation_array", "gtceu:me_crafting_cpu_interface")),
                "retirement matches exactly the two requested IDs");
        for (String id : RetiredMachines.ids()) {
            for (String json : new String[]{
                    "{\"type\":\"minecraft:crafting_shaped\",\"result\":{\"item\":\"%s\"}}",
                    "{\"type\":\"minecraft:crafting_shapeless\",\"result\":\"%s\"}",
                    "{\"outputs\":{\"item\":[{\"content\":{\"type\":\"gtceu:sized\",\"ingredient\":{\"item\":\"%s\"}}}]}}",
                    "{\"ingredients\":[[{\"item\":\"minecraft:stone\"},{\"item\":\"%s\"}]]}",
                    "{\"key\":{\"A\":{\"item\":\"%s\"}}}",
                    "{\"inputs\":{\"item\":[{\"content\":{\"ingredient\":{\"item\":\"%s\"},\"count\":1}}]}}"})
                check(RetiredMachines.recipeReferencesRetiredItem(JsonParser.parseString(json.formatted(id))),
                        "recursive recipe input/output removal");
            check(!RetiredMachines.contains(id + "_extra"), "ID matching is exact");
        }
        var keep = JsonParser.parseString("{\"result\":{\"item\":\"minecraft:stone\"}}");
        var remove = JsonParser.parseString("{\"result\":{\"item\":\"gtceu:me_crafting_cpu_interface\"}}");
        var original = Map.of("keep", keep, "remove", remove);
        check(RetiredMachines.filterRecipes(original).equals(Map.of("keep", keep)) && original.size() == 2,
                "immutable recipe map is filtered without mutating caller data");
        Path root = Path.of("src/main/java/com/gtl/enhancedcore");
        String helper = Files.readString(root.resolve("common/structure/RetiredLightHunter.java"));
        check(helper.contains("RetiredMachines.contains(id.toString())"), "retired array uses impossible file geometry");
        String factory = Files.readString(root.resolve("mixin/gtceu/RetiredMachineDefinitionMixin.java"));
        check(factory.contains("MultiblockControllerMachine::new : MetaMachine::new")
                && !factory.contains("MachineOverrideConfig"), "inert shells never instantiate upstream AE CPU logic");
        String recipe = Files.readString(root.resolve("mixin/mc/RetiredMachineRecipesMixin.java"));
        check(recipe.contains("priority = 100") && recipe.contains("At(\"HEAD\")")
                && recipe.contains("RetiredMachines.filterRecipes") && !recipe.contains("MachineOverrideConfig"),
                "recipe retirement applies before parsing regardless of feature switches");
        String entry = Files.readString(root.resolve("GTLEnhancedcore.java"));
        check(entry.contains("EventPriority.LOWEST") && entry.contains("removed.forEach(event.getEntries()::remove)"),
                "all creative tabs are filtered after contributors");
        String jei = Files.readString(root.resolve("integration/jei/GTLEnhancedcoreJEIPlugin.java"));
        check(jei.contains("onRuntimeAvailable") && jei.contains("removeIngredientsAtRuntime"), "JEI ingredient entry removed");
        String placement = Files.readString(root.resolve("common/event/RetiredMachinePlacement.java"));
        check(placement.contains("BlockEvent.EntityPlaceEvent") && placement.contains("setCanceled(true)"), "placement rejected");
        String ability = Files.readString(root.resolve("mixin/gtceu/RetiredMachinePartAbilityMixin.java"));
        check(ability.contains("method = \"register\"") && ability.contains("ci.cancel()"),
                "retired shell cannot enter part ability candidates");
        return checks;
    }
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        checks++;
    }
}
