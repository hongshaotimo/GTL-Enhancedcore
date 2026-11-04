package com.gtl.enhancedcore.mixin.gtladditions;

import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gtl.enhancedcore.common.recipe.iv.IvMachineScope;
import com.gtl.enhancedcore.common.recipe.iv.IvNativeAccess;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Retains compression cleanup when the completed native batch yields to cloud work. */
@Mixin(targets = "com.gtladd.gtladditions.common.machine.multiblock.controller.ArcanicAstrograph$Companion$ArcanicAstrographRecipeLogic",
        remap = false, priority = 1200)
public abstract class IvNativeAstrographInputGuardMixin {
    @Inject(method = {"findAndHandleRecipe", "onRecipeFinish"}, at = @At("HEAD"), cancellable = true, require = 2, allow = 2)
    private void iv$noSharedExecution(CallbackInfo ci) {
        if (!((IvNativeAccess)this).iv$nativeInputExecution()
                && IvMachineScope.crossRecipeEnabled(((RecipeLogic)(Object)this).getMachine())) ci.cancel();
    }
    @WrapMethod(method = "onRecipeFinish")
    private void iv$finishNativeInputBatch(Operation<Void> original) {
        ((IvNativeAccess)this).iv$finishNativeInputs(() -> original.call());
    }
    @Inject(method = "onRecipeFinish", at = @At(value = "INVOKE", target =
            "Lcom/gregtechceu/gtceu/api/machine/feature/IRecipeLogicMachine;afterWorking()V"), require = 1, allow = 1)
    private void iv$nativeOutputAccepted(CallbackInfo ci) {
        ((IvNativeAccess)this).iv$nativeOutputAccepted();
    }
    @WrapOperation(method = "onRecipeFinish", at = @At(value = "INVOKE", target =
            "Lcom/gtladd/gtladditions/common/machine/multiblock/controller/ArcanicAstrograph$Companion$ArcanicAstrographRecipeLogic;getRecipe()Lcom/gregtechceu/gtceu/api/recipe/GTRecipe;"),
            require = 1, allow = 1)
    private GTRecipe iv$yieldBeforeNextNativeBatch(@Coerce Object owner, Operation<GTRecipe> original) {
        var access = (IvNativeAccess)this;
        return access.iv$nativeInputsActive() && access.iv$nativeInputExecution() ? null : original.call(owner);
    }
}
