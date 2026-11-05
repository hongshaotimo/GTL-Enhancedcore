package com.gtl.enhancedcore.mixin.gtladditions;

import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gtl.enhancedcore.common.recipe.HarmonyAddRecipes;
import com.gtladd.gtladditions.common.machine.multiblock.controller.ArcanicAstrograph;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.gtlcore.gtlcore.api.recipe.RecipeRunnerHelper;

/** Keep Arcanic's final native logic and compression lifecycle; replace only cosmos admission. */
@Mixin(targets = "com.gtladd.gtladditions.common.machine.multiblock.controller.ArcanicAstrograph$Companion$ArcanicAstrographRecipeLogic",
        remap = false)
public abstract class ArcanicAddRecipesMixin {
    @Inject(method = "findCosmosSimulationRecipe", at = @At("HEAD"), cancellable = true, require = 1, allow = 1)
    private void gtle$cosmosBatch(ArcanicAstrograph owner, CallbackInfoReturnable<GTRecipe> cir) {
        if (HarmonyAddRecipes.enabled(owner))
            cir.setReturnValue(HarmonyAddRecipes.cosmos(owner, (RecipeLogic) (Object) this));
    }

    @Inject(method = "onRecipeFinish", at = @At("HEAD"), cancellable = true, require = 1, allow = 1)
    private void gtle$holdPaidCosmosOutputs(CallbackInfo ci) {
        var logic = (RecipeLogic) (Object) this;
        var recipe = logic.getLastRecipe();
        if (recipe != null && recipe.data.contains(HarmonyAddRecipes.RECIPE_COUNT)
                && !RecipeRunnerHelper.matchRecipeOutput(logic.machine, recipe)) {
            logic.setWaiting(null);
            ci.cancel();
        }
    }
}
