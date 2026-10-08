package com.gtl.enhancedcore.common.recipe;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability;
import com.gregtechceu.gtceu.common.data.GTRecipeTypes;
import com.gregtechceu.gtceu.data.recipe.builder.GTRecipeBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.registries.ForgeRegistries;

/** Only the station controller's hypercube and circuit cost change. */
public final class LightHunterRecipeCost {
    private static final ResourceLocation HYPERCUBE = new ResourceLocation("kubejs", "hypercube");

    private LightHunterRecipeCost() {}

    public static boolean matches(GTRecipeBuilder builder) {
        if (builder.id == null || !"gtladditions".equals(builder.id.getNamespace())
                || builder.recipeType != GTRecipeTypes.ASSEMBLY_LINE_RECIPES) return false;
        String name = builder.id.getPath();
        if (name.startsWith("assembly_line/")) name = name.substring("assembly_line/".length());
        return name.equals("light_hunter_space_station");
    }

    public static void upgrade(GTRecipeBuilder builder) {
        var item = ForgeRegistries.ITEMS.getValue(HYPERCUBE);
        if (item == null || item == Items.AIR) throw new IllegalStateException("Missing recipe item " + HYPERCUBE);
        for (var content : builder.input.getOrDefault(ItemRecipeCapability.CAP, java.util.List.of())) {
            var json = ItemRecipeCapability.CAP.of(content.content).toJson();
            if (maxCircuit(json)) content.content = Ingredient.fromJson(json);
        }
        builder.inputItems(new ItemStack(item));
    }

    private static boolean maxCircuit(JsonElement json) {
        boolean changed = false;
        if (json.isJsonObject()) {
            for (var entry : json.getAsJsonObject().entrySet()) {
                if (entry.getKey().equals("tag") && entry.getValue().isJsonPrimitive()
                        && entry.getValue().getAsString().equals("gtceu:circuits/uxv")) {
                    entry.setValue(new JsonPrimitive("gtceu:circuits/max"));
                    changed = true;
                } else changed |= maxCircuit(entry.getValue());
            }
        } else if (json.isJsonArray()) {
            for (var element : json.getAsJsonArray()) changed |= maxCircuit(element);
        }
        return changed;
    }
}
