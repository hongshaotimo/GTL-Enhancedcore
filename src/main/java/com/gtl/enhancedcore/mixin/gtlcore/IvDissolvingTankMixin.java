package com.gtl.enhancedcore.mixin.gtlcore;

import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.feature.IRecipeLogicMachine;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.logic.OCParams;
import com.gregtechceu.gtceu.api.recipe.logic.OCResult;
import com.gregtechceu.gtceu.api.capability.recipe.FluidRecipeCapability;
import com.gtl.enhancedcore.common.recipe.iv.IvNativeMatchContext;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import org.gtlcore.gtlcore.api.recipe.RecipeResult;
import org.gtlcore.gtlcore.common.data.GTLRecipeModifiers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Keep the original ratio/overclock rules, reading only the currently simulated order. */
@Mixin(value = GTLRecipeModifiers.class, remap = false)
public abstract class IvDissolvingTankMixin {
    @ModifyExpressionValue(method = "dissolvingTankOverclock", at = @At(value = "INVOKE",
            target = "Lorg/gtlcore/gtlcore/common/data/GTLRecipeModifiers$FluidAmounts;first()J"), require = 1, allow = 1)
    private static long iv$firstFluid(long original, @Local(argsOnly = true) MetaMachine machine,
                                     @Local(argsOnly = true) GTRecipe recipe) {
        return iv$amount(original, machine, recipe, 0);
    }
    @ModifyExpressionValue(method = "dissolvingTankOverclock", at = @At(value = "INVOKE",
            target = "Lorg/gtlcore/gtlcore/common/data/GTLRecipeModifiers$FluidAmounts;second()J"), require = 1, allow = 1)
    private static long iv$secondFluid(long original, @Local(argsOnly = true) MetaMachine machine,
                                      @Local(argsOnly = true) GTRecipe recipe) {
        return iv$amount(original, machine, recipe, 1);
    }
    @Unique
    private static long iv$amount(long original, MetaMachine machine, GTRecipe recipe, int index) {
        var context = IvNativeMatchContext.forMachine(machine);
        if (context == null) return original;
        var ingredient = FluidRecipeCapability.CAP.of(recipe.inputs.get(FluidRecipeCapability.CAP).get(index).content);
        return context.fluidAmount(ingredient.getStacks()[0].getFluid());
    }
    @WrapOperation(method = "dissolvingTankOverclock", at = @At(value = "INVOKE",
            target = "Lorg/gtlcore/gtlcore/api/recipe/RecipeResult;ofWorking(Lcom/gregtechceu/gtceu/api/machine/feature/IRecipeLogicMachine;Lorg/gtlcore/gtlcore/api/recipe/RecipeResult;)V"), require = 2, allow = 2)
    private static void iv$ratioResult(IRecipeLogicMachine machine, RecipeResult result, Operation<Void> original) {
        original.call(machine, result);
        var context = IvNativeMatchContext.forMachine(machine);
        if (context != null && !result.isSuccess()) context.reject(result.reason());
    }
    @Inject(method = "dissolvingTankOverclock", at = @At("RETURN"), cancellable = true)
    private static void iv$rejectDestructiveBatch(MetaMachine machine, GTRecipe recipe, OCParams params,
                                                OCResult result, CallbackInfoReturnable<GTRecipe> cir) {
        var context = IvNativeMatchContext.forMachine(machine);
        // Native wrong-ratio processing intentionally clears outputs. Isolated orders
        // wait with their full stock instead of consuming a batch that cannot fulfil AE.
        if (context != null && context.rejection() != null) cir.setReturnValue(null);
    }
}
