package com.gtl.enhancedcore.mixin.gtlcore;

import com.gregtechceu.gtceu.api.capability.recipe.IRecipeCapabilityHolder;
import com.gregtechceu.gtceu.api.machine.feature.IRecipeLogicMachine;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gtl.enhancedcore.common.recipe.iv.IvNativeMatchContext;
import org.gtlcore.gtlcore.api.recipe.BatchProcessing;
import org.gtlcore.gtlcore.api.recipe.IParallelLogic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** GTL's optional time-window batching uses the same isolated stock and defers chance resolution. */
@Mixin(value = BatchProcessing.class, remap = false)
public abstract class IvNativeBatchMixin {
    @Inject(method = "getParallelAmountWithoutEU", at = @At("HEAD"), cancellable = true)
    private static void iv$stock(IRecipeLogicMachine machine, GTRecipe recipe, int limit, CallbackInfoReturnable<Integer> cir) {
        var context = IvNativeMatchContext.forMachine(machine);
        if (context != null) cir.setReturnValue((int)context.limit(recipe, limit));
    }
    @Redirect(method = "apply(Lcom/gregtechceu/gtceu/api/machine/MetaMachine;Lcom/gregtechceu/gtceu/api/recipe/GTRecipe;Z)Lcom/gregtechceu/gtceu/api/recipe/GTRecipe;",
            at = @At(value = "INVOKE", target = "Lorg/gtlcore/gtlcore/api/recipe/IParallelLogic;getRecipeOutputChance(Lcom/gregtechceu/gtceu/api/capability/recipe/IRecipeCapabilityHolder;Lcom/gregtechceu/gtceu/api/recipe/GTRecipe;)Lcom/gregtechceu/gtceu/api/recipe/GTRecipe;"))
    private static GTRecipe iv$chance(IRecipeCapabilityHolder holder, GTRecipe recipe) {
        return IvNativeMatchContext.forMachine(holder) != null ? recipe : IParallelLogic.getRecipeOutputChance(holder, recipe);
    }
    @Redirect(method = "isCustomSubTickParallelized(Lcom/gregtechceu/gtceu/api/machine/MetaMachine;Lcom/gregtechceu/gtceu/api/recipe/GTRecipe;Lcom/gregtechceu/gtceu/api/recipe/GTRecipe;)Z",
            at = @At(value = "INVOKE", target = "Lorg/gtlcore/gtlcore/api/recipe/IParallelLogic;getMaxParallel(Lcom/gregtechceu/gtceu/api/capability/recipe/IRecipeCapabilityHolder;Lcom/gregtechceu/gtceu/api/recipe/GTRecipe;J)J"))
    private static long iv$subtickStock(IRecipeCapabilityHolder holder, GTRecipe recipe, long limit) {
        var context = IvNativeMatchContext.forMachine(holder);
        return context == null ? IParallelLogic.getMaxParallel(holder, recipe, limit) : context.limit(recipe, limit);
    }
}
