package com.gtl.enhancedcore.mixin.gtceu;

import com.gregtechceu.gtceu.data.recipe.builder.GTRecipeBuilder;
import com.gtl.enhancedcore.common.recipe.LightHunterRecipeCost;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = GTRecipeBuilder.class, remap = false)
public abstract class LightHunterRecipeBuilderMixin {
    @Unique private boolean enhancedcore$hypercubeAdded;

    @Inject(method = "save", at = @At("HEAD"), require = 1)
    private void enhancedcore$cost(CallbackInfo ci) {
        var builder = (GTRecipeBuilder)(Object)this;
        if (!enhancedcore$hypercubeAdded && LightHunterRecipeCost.matches(builder)) {
            LightHunterRecipeCost.upgrade(builder);
            enhancedcore$hypercubeAdded = true;
        }
    }
}
