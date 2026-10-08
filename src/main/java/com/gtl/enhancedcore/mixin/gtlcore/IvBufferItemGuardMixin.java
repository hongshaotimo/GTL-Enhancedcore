package com.gtl.enhancedcore.mixin.gtlcore;

import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gtl.enhancedcore.common.recipe.iv.IvBuffers;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import net.minecraft.world.item.crafting.Ingredient;
import org.gtlcore.gtlcore.common.machine.multiblock.part.ae.MEPatternBufferPartMachineBase;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "org.gtlcore.gtlcore.common.machine.multiblock.part.ae.MEPatternBufferRecipeHandlerTraitBase$MEItemInputHandlerBase", remap = false)
public abstract class IvBufferItemGuardMixin {
    @Shadow public abstract MEPatternBufferPartMachineBase getMachine();
    @Inject(method = "meHandleRecipeInner", at = @At("HEAD"), cancellable = true)
    private void iv$guard(GTRecipe recipe, Object2LongMap<Ingredient> left, boolean simulate, int slot, CallbackInfoReturnable<Boolean> cir) {
        if (IvBuffers.isolated(getMachine())) cir.setReturnValue(false);
    }
}
