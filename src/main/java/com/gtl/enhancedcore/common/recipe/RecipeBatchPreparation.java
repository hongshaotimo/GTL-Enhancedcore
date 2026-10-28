package com.gtl.enhancedcore.common.recipe;

import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import org.gtlcore.gtlcore.api.recipe.BatchProcessing;

/** Run the same time-window/subtick preparation as RecipeLogic input payment, without rolling
 * probabilities during admission. The final owner remains responsible for one input/output roll. */
public final class RecipeBatchPreparation {
    private static final ThreadLocal<Boolean> PREPARING = ThreadLocal.withInitial(() -> false);
    private RecipeBatchPreparation() {}
    public static boolean preparing() { return PREPARING.get(); }
    public static GTRecipe apply(MetaMachine machine, GTRecipe origin, GTRecipe modified) {
        if (modified == null || modified.data.getBoolean("duration_is_total_cwu")) return modified;
        boolean previous = PREPARING.get();
        PREPARING.set(true);
        try { return BatchProcessing.applyInPlace(machine, origin, modified) ? modified : null; }
        finally { if (previous) PREPARING.set(true); else PREPARING.remove(); }
    }
}
