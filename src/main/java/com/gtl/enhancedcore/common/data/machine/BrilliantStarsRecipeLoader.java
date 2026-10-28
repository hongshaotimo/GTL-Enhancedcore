package com.gtl.enhancedcore.common.data.machine;
import com.gregtechceu.gtceu.api.GTValues;
import com.gtl.enhancedcore.GTLEnhancedcore;
import com.gtl.enhancedcore.common.data.GTLEnhancedcoreRecipeTypes;
import java.util.function.Consumer;
import net.minecraft.data.recipes.FinishedRecipe;
/** JEI documents the continuous field; its controller performs tick-exact wireless payment. */
public final class BrilliantStarsRecipeLoader {
    private BrilliantStarsRecipeLoader() {}
    public static void registerRecipes(Consumer<FinishedRecipe> provider) {
        GTLEnhancedcoreRecipeTypes.BRILLIANT_STARS.recipeBuilder(GTLEnhancedcore.id("brilliant_stars/galactic_field"))
                .circuitMeta(1).duration(20).EUt(GTValues.V[GTValues.MAX]).save(provider);
    }
}
