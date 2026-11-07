package com.gtl.enhancedcore.common.recipe;

import com.gregtechceu.gtceu.api.recipe.GTRecipe;

/** Shared IDs and recipe data used by the primitive void ore route mixins. */
public final class PrimitiveVoidOreRouting {
    public static final String TARGET_DATA = "gtl_enhancedcore_target_dimension";
    private PrimitiveVoidOreRouting() {}

    public static int target(GTRecipe recipe) {
        if (recipe == null) return 0;
        int value = recipe.data.getInt(TARGET_DATA);
        return value >= 1 && value <= 3 ? value : 0;
    }
}
