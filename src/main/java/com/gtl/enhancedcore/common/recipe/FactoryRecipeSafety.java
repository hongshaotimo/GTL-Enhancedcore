package com.gtl.enhancedcore.common.recipe;

import com.gregtechceu.gtceu.api.capability.recipe.FluidRecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.RecipeHelper;
import com.gregtechceu.gtceu.api.recipe.ingredient.SizedIngredient;
import org.gtlcore.gtlcore.api.recipe.ingredient.LongIngredient;

/** Rejects malformed quantities before a Plus factory can consume inputs. */
public final class FactoryRecipeSafety {
    private FactoryRecipeSafety() {}

    public static boolean valid(GTRecipe recipe) {
        if (recipe.duration <= 0 || RecipeHelper.getInputEUt(recipe) < 0) return false;
        for (var contents : java.util.List.of(recipe.inputs, recipe.outputs)) {
            for (var content : contents.getOrDefault(ItemRecipeCapability.CAP, java.util.List.of())) {
                var ingredient = ItemRecipeCapability.CAP.of(content.content);
                long amount = ingredient instanceof LongIngredient large ? large.getActualAmount()
                        : ingredient instanceof SizedIngredient sized ? sized.getAmount() : 1;
                if (amount <= 0) return false;
            }
            for (var content : contents.getOrDefault(FluidRecipeCapability.CAP, java.util.List.of())) {
                if (FluidRecipeCapability.CAP.of(content.content).getAmount() <= 0) return false;
            }
        }
        return true;
    }
}
