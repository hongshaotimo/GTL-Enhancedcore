package com.gtl.enhancedcore.mixin.gtceu;

import com.gregtechceu.gtceu.api.capability.recipe.IRecipeCapabilityHolder;
import com.gregtechceu.gtceu.api.capability.recipe.RecipeCapability;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.modifier.ParallelLogic;
import com.gtl.enhancedcore.common.recipe.iv.IvNativeMatchContext;
import java.util.function.Predicate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = ParallelLogic.class, remap = false, priority = 1200)
public abstract class IvNativeParallelMixin {
    @Redirect(method = "doParallelRecipes", at = @At(value = "INVOKE",
            target = "Lorg/gtlcore/gtlcore/api/recipe/IParallelLogic;getRecipeOutputChance(Lcom/gregtechceu/gtceu/api/capability/recipe/IRecipeCapabilityHolder;Lcom/gregtechceu/gtceu/api/recipe/GTRecipe;)Lcom/gregtechceu/gtceu/api/recipe/GTRecipe;"))
    private static GTRecipe iv$deferChance(IRecipeCapabilityHolder holder, GTRecipe recipe) {
        return IvNativeMatchContext.forMachine(holder) != null ? recipe
                : org.gtlcore.gtlcore.api.recipe.IParallelLogic.getRecipeOutputChance(holder, recipe);
    }
    @Inject(method = "getMaxRecipeMultiplier", at = @At("HEAD"), cancellable = true)
    private static void iv$inputs(GTRecipe recipe, IRecipeCapabilityHolder holder, int limit, CallbackInfoReturnable<Integer> cir) {
        var context = IvNativeMatchContext.forMachine(holder);
        if (context != null) {
            int available = (int)context.limit(recipe, limit);
            for (var cap : recipe.tickInputs.keySet())
                if (cap.doMatchInRecipe()) available = Math.min(available, cap.getMaxParallelRatio(holder, recipe, available));
            cir.setReturnValue(available);
        }
    }
    @Inject(method = "limitByOutputMerging", at = @At("HEAD"), cancellable = true)
    private static void iv$outputs(GTRecipe recipe, IRecipeCapabilityHolder holder, int limit,
                                  Predicate<RecipeCapability<?>> predicate, CallbackInfoReturnable<Integer> cir) {
        if (IvNativeMatchContext.forMachine(holder) != null) cir.setReturnValue(limit);
    }
}
