package com.gtl.enhancedcore.mixin.gtlcore;

import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gtl.enhancedcore.common.recipe.iv.IvMachineScope;
import com.gtladd.gtladditions.api.machine.logic.MutableRecipesLogic;
import org.gtlcore.gtlcore.common.machine.trait.MultipleRecipesLogic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Protect subtype entry points as well: the displayed recipe never owns real output stock. */
@Mixin(value = {MultipleRecipesLogic.class, MutableRecipesLogic.class}, remap = false, priority = 1200)
public abstract class IvNativeMultipleGuardMixin {
    @Inject(method = {"findAndHandleRecipe", "onRecipeFinish", "handleRecipeWorking"}, at = @At("HEAD"), cancellable = true)
    private void iv$noSharedExecution(CallbackInfo ci) {
        if (IvMachineScope.crossRecipeEnabled(((RecipeLogic)(Object)this).getMachine())) ci.cancel();
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
