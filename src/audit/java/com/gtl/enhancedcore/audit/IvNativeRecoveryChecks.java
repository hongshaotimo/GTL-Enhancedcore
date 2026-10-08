package com.gtl.enhancedcore.audit;

import com.gregtechceu.gtceu.api.capability.recipe.CWURecipeCapability;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.content.Content;
import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import java.util.List;
import org.gtlcore.gtlcore.api.recipe.IGTRecipe;

public final class IvNativeRecoveryChecks {
    private IvNativeRecoveryChecks() {}

    public static void requireComputation(GTRecipe recipe, int computation) {
        if (computation < 1) throw new IllegalArgumentException("Native test computation must be positive");
        recipe.tickInputs.put(CWURecipeCapability.CAP, List.of(new Content(computation, 10000, 10000, 0, null, null)));
        IGTRecipe.of(recipe).setHasTick(true);
    }

    public static void seedFailedCache(RecipeLogic logic, GTRecipe recipe) {
        logic.lastFailedMatches = List.of(recipe.copy());
    }
}
