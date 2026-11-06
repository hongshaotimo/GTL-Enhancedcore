package com.gtl.enhancedcore.mixin.gtladditions;

import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gtl.enhancedcore.common.recipe.iv.IvMachineScope;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The laboratory owns both its certificate path and its ordinary recipe path. */
@Mixin(targets = "com.gtladd.gtladditions.common.machine.multiblock.controller.BiologicalSimulationLaboratory$BiologicalSimulationLaboratoryLogic",
        remap = false, priority = 1200)
public abstract class IvNativeBiologicalInputGuardMixin {
    @Inject(method = {"findAndHandleRecipe", "onRecipeFinish"}, at = @At("HEAD"), cancellable = true, require = 2, allow = 2)
    private void iv$noSharedExecution(CallbackInfo ci) {
        if (IvMachineScope.crossRecipeEnabled(((RecipeLogic)(Object)this).getMachine())) ci.cancel();
    }
}
