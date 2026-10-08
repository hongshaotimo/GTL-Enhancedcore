package com.gtl.enhancedcore.mixin.gtlcore;

import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.ingredient.FluidIngredient;
import com.gtl.enhancedcore.common.recipe.iv.IvBuffers;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import org.gtlcore.gtlcore.common.machine.multiblock.part.ae.MEPatternBufferPartMachineBase;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "org.gtlcore.gtlcore.common.machine.multiblock.part.ae.MEPatternBufferRecipeHandlerTraitBase$MEFluidHandlerBase", remap = false)
public abstract class IvBufferFluidGuardMixin {
    @Shadow public abstract MEPatternBufferPartMachineBase getMachine();
    @Inject(method = "meHandleRecipeInner", at = @At("HEAD"), cancellable = true)
    private void iv$guard(GTRecipe recipe, Object2LongMap<FluidIngredient> left, boolean simulate, int slot, CallbackInfoReturnable<Boolean> cir) {
        if (IvBuffers.isolated(getMachine())) cir.setReturnValue(false);
    }
}
