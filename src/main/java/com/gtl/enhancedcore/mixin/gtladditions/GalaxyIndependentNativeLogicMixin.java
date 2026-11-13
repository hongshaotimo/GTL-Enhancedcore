package com.gtl.enhancedcore.mixin.gtladditions;

import com.gtl.enhancedcore.common.recipe.GalaxyIndependentRecipeAccess;
import com.gtladd.gtladditions.api.machine.logic.GTLAddMultipleRecipesLogic;
import com.gtladd.gtladditions.api.machine.logic.MutableRecipesLogic;
import org.gtlcore.gtlcore.common.machine.trait.MultipleRecipesLogic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;

/** These native engines override GTCEu's virtual search and completion methods. */
@Mixin(value = {MultipleRecipesLogic.class, GTLAddMultipleRecipesLogic.class, MutableRecipesLogic.class},
        remap = false, priority = 1350)
public abstract class GalaxyIndependentNativeLogicMixin {
    @Inject(method = "findAndHandleRecipe", at = @At("HEAD"), cancellable = true, require = 1)
    private void galaxy$search(CallbackInfo ci) {
        if (((GalaxyIndependentRecipeAccess)this).galaxy$blockNativeSearch()) ci.cancel();
    }

    @Inject(method = {"onRecipeFinish", "handleRecipeWorking"}, at = @At("HEAD"), cancellable = true, require = 0)
    private void galaxy$keepOwnedWork(CallbackInfo ci) {
        if (((GalaxyIndependentRecipeAccess)this).galaxy$hasIndependentJobs()) ci.cancel();
    }

    @WrapOperation(method = "onRecipeFinish", at = @At(value = "INVOKE", target =
            "Lorg/gtlcore/gtlcore/common/machine/trait/MultipleRecipesLogic;getRecipe()Lcom/gregtechceu/gtceu/api/recipe/GTRecipe;"),
            require = 0)
    private GTRecipe galaxy$stopMultipleRefill(MultipleRecipesLogic logic, Operation<GTRecipe> original) {
        return ((GalaxyIndependentRecipeAccess)this).galaxy$blockNativeSearch() ? null : original.call(logic);
    }

    @WrapOperation(method = "onRecipeFinish", at = @At(value = "INVOKE", target =
            "Lcom/gtladd/gtladditions/api/machine/logic/GTLAddMultipleRecipesLogic;getGTRecipe()Lcom/gregtechceu/gtceu/api/recipe/GTRecipe;"),
            require = 0)
    private GTRecipe galaxy$stopGtlAddRefill(GTLAddMultipleRecipesLogic logic, Operation<GTRecipe> original) {
        return ((GalaxyIndependentRecipeAccess)this).galaxy$blockNativeSearch() ? null : original.call(logic);
    }

    @WrapOperation(method = "onMultipleRecipeFinish", at = @At(value = "INVOKE", target =
            "Lcom/gtladd/gtladditions/api/machine/logic/MutableRecipesLogic;getRecipe()Lcom/gregtechceu/gtceu/api/recipe/GTRecipe;"),
            require = 0)
    private GTRecipe galaxy$stopMutableRefill(MutableRecipesLogic<?> logic, Operation<GTRecipe> original) {
        return ((GalaxyIndependentRecipeAccess)this).galaxy$blockNativeSearch() ? null : original.call(logic);
    }
}
