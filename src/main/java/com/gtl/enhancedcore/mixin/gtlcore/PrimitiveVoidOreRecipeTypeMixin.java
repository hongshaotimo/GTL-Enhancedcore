package com.gtl.enhancedcore.mixin.gtlcore;

import org.gtlcore.gtlcore.common.data.GTLRecipeTypes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Adds the single non-consumable circuit input slot required by the three routes. */
@Mixin(value = GTLRecipeTypes.class, remap = false)
public abstract class PrimitiveVoidOreRecipeTypeMixin {
    @Inject(method = "<clinit>", at = @At("RETURN"), require = 1, remap = false)
    private static void enhancedcore$allowCircuit(CallbackInfo callback) {
        if (GTLRecipeTypes.PRIMITIVE_VOID_ORE_RECIPES != null)
            GTLRecipeTypes.PRIMITIVE_VOID_ORE_RECIPES.setMaxIOSize(1, 0, 1, 0);
    }
}
