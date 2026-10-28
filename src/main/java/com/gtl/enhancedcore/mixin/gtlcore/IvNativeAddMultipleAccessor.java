package com.gtl.enhancedcore.mixin.gtlcore;

import com.gregtechceu.gtceu.api.machine.feature.IRecipeLogicMachine;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gtladd.gtladditions.api.machine.logic.GTLAddMultipleRecipesLogic;
import java.util.function.BiPredicate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Retain ADD's original per-recipe checks when an isolated order replaces shared-stock execution. */
@Mixin(value = GTLAddMultipleRecipesLogic.class, remap = false)
public interface IvNativeAddMultipleAccessor {
    @Invoker("getRecipeCheck") BiPredicate<GTRecipe, IRecipeLogicMachine> iv$addRecipeCheck();
    @Invoker("getBeforeWorking") java.util.function.Predicate<IRecipeLogicMachine> iv$addBeforeWorking();
}
