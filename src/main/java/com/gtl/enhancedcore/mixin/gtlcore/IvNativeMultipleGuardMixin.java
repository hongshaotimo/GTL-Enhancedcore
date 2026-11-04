package com.gtl.enhancedcore.mixin.gtlcore;

import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gtl.enhancedcore.common.recipe.iv.IvMachineScope;
import com.gtl.enhancedcore.common.recipe.iv.IvNativeAccess;
import com.gtladd.gtladditions.api.machine.logic.GTLAddMultipleRecipesLogic;
import com.gtladd.gtladditions.api.machine.logic.MutableRecipesLogic;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.gtlcore.gtlcore.common.machine.trait.MultipleRecipesLogic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Protect subtype entry points as well: the displayed recipe never owns real output stock. */
@Mixin(value = {MultipleRecipesLogic.class, MutableRecipesLogic.class, GTLAddMultipleRecipesLogic.class}, remap = false, priority = 1200)
public abstract class IvNativeMultipleGuardMixin {
    @Inject(method = {"findAndHandleRecipe", "onRecipeFinish", "handleRecipeWorking"}, at = @At("HEAD"), cancellable = true)
    private void iv$noSharedExecution(CallbackInfo ci) {
        if (!((IvNativeAccess)this).iv$nativeInputExecution()
                && IvMachineScope.crossRecipeEnabled(((RecipeLogic)(Object)this).getMachine())) ci.cancel();
    }
    @WrapMethod(method = "onRecipeFinish")
    private void iv$finishNativeInputBatch(Operation<Void> original) {
        ((IvNativeAccess)this).iv$finishNativeInputs(() -> original.call());
    }
    // Mutable's multi mode settles in its helper; its single mode delegates to
    // RecipeLogic. Core Multiple and ADD Multiple each settle in onRecipeFinish.
    @Inject(method = {"onRecipeFinish", "onMultipleRecipeFinish"}, at = @At(value = "INVOKE", target =
            "Lcom/gregtechceu/gtceu/api/machine/feature/IRecipeLogicMachine;afterWorking()V"), require = 1, allow = 1)
    private void iv$nativeOutputAccepted(CallbackInfo ci) {
        ((IvNativeAccess)this).iv$nativeOutputAccepted();
    }
    // These upstream lookups also consume the next batch's physical inputs.
    // Return the normal "no next recipe" result after output and suspend handling,
    // so upstream finish cleanup still runs without consuming another batch.
    @WrapOperation(method = {"onRecipeFinish", "onMultipleRecipeFinish"}, at = {
            @At(value = "INVOKE", target = "Lorg/gtlcore/gtlcore/common/machine/trait/MultipleRecipesLogic;getRecipe()Lcom/gregtechceu/gtceu/api/recipe/GTRecipe;"),
            @At(value = "INVOKE", target = "Lcom/gtladd/gtladditions/api/machine/logic/MutableRecipesLogic;getRecipe()Lcom/gregtechceu/gtceu/api/recipe/GTRecipe;"),
            @At(value = "INVOKE", target = "Lcom/gtladd/gtladditions/api/machine/logic/GTLAddMultipleRecipesLogic;getGTRecipe()Lcom/gregtechceu/gtceu/api/recipe/GTRecipe;")
    }, require = 1, allow = 1)
    private GTRecipe iv$yieldBeforeNextNativeBatch(@Coerce Object owner, Operation<GTRecipe> original) {
        var access = (IvNativeAccess)this;
        return access.iv$nativeInputsActive() && access.iv$nativeInputExecution() ? null : original.call(owner);
    }
    @Inject(method = "getMultipleThreads", at = @At("HEAD"), cancellable = true, require = 0)
    private void iv$singleThreadWithoutBuffer(CallbackInfoReturnable<Integer> cir) {
        var machine = ((RecipeLogic)(Object)this).getMachine();
        if (machine instanceof com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine electric
                && IvMachineScope.unlimited(electric)) return;
        if (!IvMachineScope.nativeTarget(machine) || IvMachineScope.crossRecipeEnabled(machine)) return;
        // 同 IvNativeGtlAddThreadLimitMixin：个别机器无总成时保留其上游原生线程。
        if (machine instanceof com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine electric
                && IvMachineScope.keepsNativeThreads(electric)) return;
        cir.setReturnValue(1);
    }
}
