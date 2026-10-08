package com.gtl.enhancedcore.mixin.gtlcore;

import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gtl.enhancedcore.common.recipe.iv.IvMachineScope;
import com.gtladd.gtladditions.api.machine.logic.GTLAddMultipleRecipesLogic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = GTLAddMultipleRecipesLogic.class, remap = false, priority = 1200)
public abstract class IvNativeGtlAddThreadLimitMixin {
    @Inject(method = "getMultipleThreads", at = @At("HEAD"), cancellable = true)
    private void iv$singleThreadWithoutBuffer(CallbackInfoReturnable<Integer> cir) {
        var machine = ((RecipeLogic)(Object)this).getMachine();
        if (IvMachineScope.nativeTarget(machine) && !IvMachineScope.crossRecipeEnabled(machine)) cir.setReturnValue(1);
    }
}
