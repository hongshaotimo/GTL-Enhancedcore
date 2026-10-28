package com.gtl.enhancedcore.mixin.gtlcore;

import com.gregtechceu.gtceu.api.capability.recipe.IRecipeCapabilityHolder;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gtl.enhancedcore.common.recipe.GalaxyRecipeBatch;
import org.gtlcore.gtlcore.api.recipe.BatchProcessing;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Modifier simulation must not roll chances before the final admitted batch exists. */
@Mixin(value=BatchProcessing.class,remap=false)
public abstract class GalaxyChanceSimulationMixin {
    @Inject(method="apply(Lcom/gregtechceu/gtceu/api/machine/MetaMachine;Lcom/gregtechceu/gtceu/api/recipe/GTRecipe;Z)Lcom/gregtechceu/gtceu/api/recipe/GTRecipe;",at=@At("HEAD"),cancellable=true,require=1)
    private static void galaxy$defer(MetaMachine machine, GTRecipe recipe, boolean subTick, CallbackInfoReturnable<GTRecipe> cir) {
        if (GalaxyRecipeBatch.modifying() && !com.gtl.enhancedcore.common.recipe.RecipeBatchPreparation.preparing())
            cir.setReturnValue(recipe);
    }
    // This mixin is independent of the optional original-machine enhancements: galaxy
    // admission must defer chances even when the IV integration mixins are disabled.
    @Redirect(method = "apply(Lcom/gregtechceu/gtceu/api/machine/MetaMachine;Lcom/gregtechceu/gtceu/api/recipe/GTRecipe;Z)Lcom/gregtechceu/gtceu/api/recipe/GTRecipe;",
            at = @At(value = "INVOKE", target = "Lorg/gtlcore/gtlcore/api/recipe/IParallelLogic;getRecipeOutputChance(Lcom/gregtechceu/gtceu/api/capability/recipe/IRecipeCapabilityHolder;Lcom/gregtechceu/gtceu/api/recipe/GTRecipe;)Lcom/gregtechceu/gtceu/api/recipe/GTRecipe;"), require = 1)
    private static GTRecipe galaxy$deferChance(IRecipeCapabilityHolder holder, GTRecipe recipe) {
        return com.gtl.enhancedcore.common.recipe.iv.IvNativeMatchContext.forMachine(holder) != null
                || com.gtl.enhancedcore.common.recipe.RecipeBatchPreparation.preparing()
                ? recipe : org.gtlcore.gtlcore.api.recipe.IParallelLogic.getRecipeOutputChance(holder, recipe);
    }
}
