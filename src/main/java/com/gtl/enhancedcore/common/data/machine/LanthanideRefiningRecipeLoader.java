package com.gtl.enhancedcore.common.data.machine;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.recipe.ingredient.FluidIngredient;
import com.gtl.enhancedcore.GTLEnhancedcore;
import com.gtl.enhancedcore.common.data.GTLEnhancedcoreRecipeTypes;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.data.recipes.FinishedRecipe;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.registries.ForgeRegistries;

/** Deterministic LuV monazite refining, registered through GTCEu's recipe data pack. */
public final class LanthanideRefiningRecipeLoader {
    public static final int DUST_AMOUNT = 128;
    public static final int FLUID_AMOUNT_MB = 1000;
    public static final int DURATION_TICKS = 1200;
    public static final List<String> OUTPUT_ITEMS = List.of(
            "gtceu:lanthanum_dust", "gtceu:praseodymium_dust", "gtceu:neodymium_dust",
            "gtceu:cerium_dust", "gtceu:europium_dust", "gtceu:gadolinium_dust",
            "gtceu:samarium_dust", "gtceu:terbium_dust", "gtceu:dysprosium_dust",
            "gtceu:holmium_dust", "gtceu:erbium_dust", "gtceu:thulium_dust",
            "gtceu:ytterbium_dust", "gtceu:lutetium_dust", "gtceu:scandium_dust", "gtceu:yttrium_dust");

    private LanthanideRefiningRecipeLoader() {}

    public static void registerRecipes(Consumer<FinishedRecipe> provider) {
        Item monazite = requiredItem("gtceu:monazite_dust");
        Item carbon = requiredItem("gtceu:carbon_dust");
        Fluid water = requiredFluid("minecraft:water");
        Fluid acid = requiredFluid("gtceu:sulfuric_acid");
        // Resolve the complete catalog before emitting a recipe; a missing dust must not lose output.
        List<Item> outputs = OUTPUT_ITEMS.stream().map(LanthanideRefiningRecipeLoader::requiredItem).toList();
        // GTRecipeBuilder.build().getId() prefixes the recipe type path when saving.
        var builder = GTLEnhancedcoreRecipeTypes.LANTHANIDE_REFINING
                .recipeBuilder(GTLEnhancedcore.id("monazite"))
                .inputItems(monazite, DUST_AMOUNT)
                .inputItems(carbon, DUST_AMOUNT)
                .inputFluids(FluidIngredient.of(FLUID_AMOUNT_MB, water),
                        FluidIngredient.of(FLUID_AMOUNT_MB, acid));
        for (Item output : outputs) builder.outputItems(output, DUST_AMOUNT);
        builder.EUt(GTValues.VA[GTValues.LuV]).duration(DURATION_TICKS).save(provider);
    }

    private static Item requiredItem(String id) {
        Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(id));
        if (item == null || item == Items.AIR) throw new IllegalStateException("Missing lanthanide refining item " + id);
        return item;
    }

    private static Fluid requiredFluid(String id) {
        Fluid fluid = ForgeRegistries.FLUIDS.getValue(new ResourceLocation(id));
        if (fluid == null || fluid == Fluids.EMPTY) throw new IllegalStateException("Missing lanthanide refining fluid " + id);
        return fluid;
    }
}
