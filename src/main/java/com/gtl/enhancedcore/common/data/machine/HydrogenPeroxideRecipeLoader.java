package com.gtl.enhancedcore.common.data.machine;

import com.gregtechceu.gtceu.api.GTValues;
import com.gtl.enhancedcore.GTLEnhancedcore;
import java.util.function.Consumer;
import net.minecraft.data.recipes.FinishedRecipe;
import org.gtlcore.gtlcore.common.data.GTLMaterials;
import org.gtlcore.gtlcore.common.data.GTLRecipeTypes;

import static com.gregtechceu.gtceu.api.data.tag.TagPrefix.dust;
import static com.gregtechceu.gtceu.common.data.GTMaterials.Hydrogen;
import static com.gregtechceu.gtceu.common.data.GTMaterials.Oxygen;
import static com.gregtechceu.gtceu.common.data.GTMaterials.Palladium;
import static com.gregtechceu.gtceu.common.data.GTRecipeTypes.CHEMICAL_RECIPES;

/** Direct catalytic synthesis and its bulk chemical distortion recipe. */
public final class HydrogenPeroxideRecipeLoader {
    private HydrogenPeroxideRecipeLoader() {}

    public static void registerRecipes(Consumer<FinishedRecipe> provider) {
        CHEMICAL_RECIPES.recipeBuilder(GTLEnhancedcore.id("hydrogen_peroxide_direct"))
                .notConsumable(dust, Palladium)
                .inputFluids(Hydrogen.getFluid(2000), Oxygen.getFluid(2000))
                .outputFluids(GTLMaterials.HydrogenPeroxide.getFluid(1000))
                .EUt(GTValues.VA[GTValues.HV])
                .duration(600)
                .save(provider);

        GTLRecipeTypes.DISTORT_RECIPES.recipeBuilder(GTLEnhancedcore.id("hydrogen_peroxide_bulk"))
                .notConsumable(dust, Palladium)
                .inputFluids(Hydrogen.getFluid(64000), Oxygen.getFluid(64000))
                .outputFluids(GTLMaterials.HydrogenPeroxide.getFluid(32000))
                .EUt(GTValues.VA[GTValues.ZPM])
                .duration(120)
                .blastFurnaceTemp(5600)
                .save(provider);
    }
}
