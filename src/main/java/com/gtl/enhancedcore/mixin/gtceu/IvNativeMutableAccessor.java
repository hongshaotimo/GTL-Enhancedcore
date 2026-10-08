package com.gtl.enhancedcore.mixin.gtceu;

import com.gregtechceu.gtceu.api.machine.feature.IRecipeLogicMachine;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gtladd.gtladditions.api.machine.logic.MutableRecipesLogic;
import java.util.function.BiPredicate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(value = MutableRecipesLogic.class, remap = false)
public interface IvNativeMutableAccessor {
    @Invoker("getRecipeCheck") BiPredicate<GTRecipe,IRecipeLogicMachine> iv$recipeCheck();
    @Invoker("getEuMultiplier") double iv$euMultiplier();
    @Invoker("getRecipeEut") long iv$recipeEUt(GTRecipe recipe);
}
