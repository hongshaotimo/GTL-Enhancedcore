package com.gtl.enhancedcore.common.data.machine;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.recipe.ingredient.FluidIngredient;
import com.gregtechceu.gtceu.common.data.GTRecipeTypes;
import com.gregtechceu.gtceu.data.recipe.builder.GTRecipeBuilder;
import com.gtl.enhancedcore.GTLEnhancedcore;
import com.gtl.enhancedcore.common.recipe.EquipmentRecipeCatalog;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.recipes.FinishedRecipe;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.ShapedRecipeBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.registries.ForgeRegistries;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import static net.minecraft.advancements.critereon.InventoryChangeTrigger.TriggerInstance.hasItems;

/** Equipment acquisition is owned by the mod and participates in GTCEu's normal recipe data pack. */
public final class EquipmentRecipeLoader {
    private EquipmentRecipeLoader() {}

    public static void registerRecipes(Consumer<FinishedRecipe> provider) {
        List<EquipmentRecipeCatalog.Recipe> recipes;
        try (var reader = new InputStreamReader(Objects.requireNonNull(EquipmentRecipeLoader.class.getResourceAsStream(
                "/data/gtl_enhancedcore/equipment_recipes.json")), StandardCharsets.UTF_8)) {
            recipes = EquipmentRecipeCatalog.read(reader);
        } catch (Exception e) {
            throw new IllegalStateException("Cannot read bundled equipment recipes", e);
        }
        validateItems(recipes);
        Set<String> covered = new HashSet<>();
        for (var recipe : recipes) {
            if (EquipmentRecipeCatalog.CREATIVE_MODE_ONLY_OUTPUTS.contains(recipe.output())) {
                throw new IllegalStateException("Creative-only item must not have an equipment recipe: " + recipe.output());
            }
            Item output = requiredItem(GTLEnhancedcore.MOD_ID + ":" + recipe.output());
            covered.add(recipe.output());
            if (recipe.method().equals("crafting")) {
                ShapedRecipeBuilder builder = ShapedRecipeBuilder.shaped(RecipeCategory.MISC, output);
                recipe.pattern().forEach(builder::pattern);
                recipe.key().forEach((symbol, ingredient) -> {
                    if (ingredient.tag()) builder.define(symbol, TagKey.create(Registries.ITEM, new ResourceLocation(ingredient.id())));
                    else builder.define(symbol, requiredItem(ingredient.id()));
                });
                builder.unlockedBy("has_bronze", hasItems(requiredItem("gtceu:bronze_plate")))
                        .save(provider, GTLEnhancedcore.id("equipment/" + recipe.output()));
                continue;
            }
            var type = recipe.method().equals("assembler") ? GTRecipeTypes.ASSEMBLER_RECIPES : GTRecipeTypes.ASSEMBLY_LINE_RECIPES;
            GTRecipeBuilder builder = type.recipeBuilder(GTLEnhancedcore.id("equipment/" + recipe.output()))
                    .duration(recipe.duration()).EUt(GTValues.VA[recipe.tier()]).outputItems(new ItemStack(output));
            for (var ingredient : recipe.ingredients()) {
                if (ingredient.tag()) builder.inputItems(TagKey.create(Registries.ITEM, new ResourceLocation(ingredient.id())), ingredient.count());
                else builder.inputItems(new ItemStack(requiredItem(ingredient.id()), ingredient.count()));
            }
            for (var ingredient : recipe.fluids()) {
                var fluid = ForgeRegistries.FLUIDS.getValue(new ResourceLocation(ingredient.id()));
                if (fluid == null || fluid == Fluids.EMPTY) throw new IllegalStateException("Missing recipe fluid " + ingredient.id());
                builder.inputFluids(FluidIngredient.of(ingredient.amount(), fluid));
            }
            builder.save(provider);
        }
        for (ResourceLocation id : ForgeRegistries.ITEMS.getKeys()) {
            if (id.getNamespace().equals(GTLEnhancedcore.MOD_ID)
                    && !covered.contains(id.getPath())
                    && !EquipmentRecipeCatalog.CREATIVE_MODE_ONLY_OUTPUTS.contains(id.getPath())) {
                throw new IllegalStateException("Registered item has no equipment recipe: " + id);
            }
        }
        GTLEnhancedcore.LOGGER.info("Registered {} staged equipment recipes", covered.size());
    }

    private static Item requiredItem(String id) {
        Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(id));
        if (item == null || item == Items.AIR) throw new IllegalStateException("Missing equipment recipe ingredient " + id);
        return item;
    }

    private static void validateItems(List<EquipmentRecipeCatalog.Recipe> recipes) {
        Set<String> required = new java.util.TreeSet<>();
        required.add("gtceu:bronze_plate");
        for (var recipe : recipes) {
            required.add(GTLEnhancedcore.MOD_ID + ":" + recipe.output());
            for (var ingredient : recipe.ingredients()) if (!ingredient.tag()) required.add(ingredient.id());
            for (var ingredient : recipe.key().values()) if (!ingredient.tag()) required.add(ingredient.id());
        }
        List<String> missing = new ArrayList<>();
        for (String id : required) {
            Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(id));
            if (item == null || item == Items.AIR) missing.add(id);
        }
        if (!missing.isEmpty()) throw new IllegalStateException("Missing equipment recipe ingredients: " + missing);
    }
}
