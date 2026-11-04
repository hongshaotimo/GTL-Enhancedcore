package com.gtl.enhancedcore.common.recipe.iv;

import com.gregtechceu.gtceu.api.capability.recipe.EURecipeCapability;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import org.gtlcore.gtlcore.api.recipe.IGTRecipe;

/** The committed recipe is callback context; only its extra tick IO reaches native payment. */
public final class IvNativeRecipeContext {
    private IvNativeRecipeContext() {}

    public static GTRecipe tickRecipe(GTRecipe committed) {
        GTRecipe tick = committed.copy();
        tick.inputs.clear(); tick.outputs.clear(); tick.tickInputs.remove(EURecipeCapability.CAP);
        IGTRecipe.of(tick).setHasTick(!tick.tickInputs.isEmpty() || !tick.tickOutputs.isEmpty());
        return tick;
    }
}
