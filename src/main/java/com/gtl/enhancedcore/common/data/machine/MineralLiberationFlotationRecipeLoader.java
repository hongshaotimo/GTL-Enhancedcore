package com.gtl.enhancedcore.common.data.machine;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.recipe.ingredient.FluidIngredient;
import com.gtl.enhancedcore.GTLEnhancedcore;
import com.gtl.enhancedcore.common.data.GTLEnhancedcoreRecipeTypes;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.recipes.FinishedRecipe;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.registries.ForgeRegistries;

/** Direct UHV ore liberation and flotation, with the original highest-yield mineral balance. */
public final class MineralLiberationFlotationRecipeLoader {
    public static final int RAW_ORE_INPUT = 64;
    public static final int ORE_BLOCK_INPUT = 32;
    public static final int DISTILLED_WATER_MB = 1000;
    public static final int FROTH_OUTPUT_MB = 3000;
    public static final int DURATION_TICKS = 1200;
    public static final int ENRICHED_DURATION_TICKS = 2400;

    // Collector kind and turpentine amounts come from the instance's ore_processing.js flotation table.
    private static final List<OreSpec> ORES = List.of(
            new OreSpec("almandine", "sodium", 18000),
            new OreSpec("chalcopyrite", "potassium", 12000),
            new OreSpec("grossular", "potassium", 28000),
            new OreSpec("monazite", "sodium", 30000),
            new OreSpec("nickel", "potassium", 25000),
            new OreSpec("pentlandite", "potassium", 14000),
            new OreSpec("platinum", "sodium", 35000),
            new OreSpec("pyrope", "sodium", 8000),
            new OreSpec("redstone", "sodium", 13000),
            new OreSpec("spessartine", "potassium", 35000),
            new OreSpec("sphalerite", "sodium", 14000),
            new OreSpec("enriched_naquadah", "potassium", 140000));

    private MineralLiberationFlotationRecipeLoader() {}

    public static void registerRecipes(Consumer<FinishedRecipe> provider) {
        Fluid water = requiredFluid("gtceu:distilled_water");
        Fluid turpentine = requiredFluid("gtceu:turpentine");
        Item sodiumCollector = requiredItem("gtceu:sodium_ethylxanthate_dust");
        Item potassiumCollector = requiredItem("gtceu:potassium_ethylxanthate_dust");
        for (OreSpec ore : ORES) {
            Item rawOre = requiredItem("gtceu:raw_" + ore.name());
            Fluid froth = requiredFluid("gtceu:" + ore.name() + "_front");
            Item collector = ore.collector().equals("sodium") ? sodiumCollector : potassiumCollector;
            boolean enriched = ore.name().equals("enriched_naquadah");
            int collectorAmount = enriched ? 48 : 24;
            // 64 raw or 32 ore blocks yield 192 milled ore, hence exactly three original flotation batches.
            // Closed-loop recovery saves 75% of those batches' collectors and turpentine; no ore is multiplied.
            int turpentineAmount = ore.originalTurpentineMb() * 3 / 4;
            TagKey<Item> oreBlocks = TagKey.create(Registries.ITEM,
                    new ResourceLocation("forge", "ores/" + ore.name()));
            for (boolean blockInput : new boolean[]{false, true}) {
                var builder = GTLEnhancedcoreRecipeTypes.MINERAL_LIBERATION_FLOTATION_RECIPES
                        .recipeBuilder(GTLEnhancedcore.id((blockInput ? "ore/" : "raw/") + ore.name()));
                if (blockInput) builder.inputItems(oreBlocks, ORE_BLOCK_INPUT);
                else builder.inputItems(rawOre, RAW_ORE_INPUT);
                builder.inputItems(collector, collectorAmount)
                        .inputFluids(FluidIngredient.of(DISTILLED_WATER_MB, water),
                                FluidIngredient.of(turpentineAmount, turpentine))
                        .outputFluids(FluidIngredient.of(FROTH_OUTPUT_MB, froth))
                        .EUt(GTValues.VA[GTValues.UHV])
                        .duration(enriched ? ENRICHED_DURATION_TICKS : DURATION_TICKS)
                        .save(provider);
            }
        }
    }

    private static Item requiredItem(String id) {
        Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(id));
        if (item == null || item == Items.AIR)
            throw new IllegalStateException("Missing mineral liberation/flotation item " + id);
        return item;
    }

    private static Fluid requiredFluid(String id) {
        Fluid fluid = ForgeRegistries.FLUIDS.getValue(new ResourceLocation(id));
        if (fluid == null || fluid == Fluids.EMPTY)
            throw new IllegalStateException("Missing mineral liberation/flotation fluid " + id);
        return fluid;
    }

    private record OreSpec(String name, String collector, int originalTurpentineMb) {}
}
