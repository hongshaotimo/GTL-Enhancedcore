package com.gtl.enhancedcore.mixin.gtceu;

import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.capability.recipe.IRecipeCapabilityHolder;
import com.gregtechceu.gtceu.api.capability.recipe.RecipeCapability;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.content.Content;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import java.util.List;
import java.util.Map;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** GTCEu 1.4.4: an empty content map has no runner work, regardless of handler capacity. */
@Mixin(value = GTRecipe.class, remap = false, priority = 900)
public abstract class EmptyRecipeContentsMixin {
    @Inject(method = "matchRecipeContents", at = @At("HEAD"), cancellable = true, require = 1)
    private void enhancedcore$emptyMatch(IO io, IRecipeCapabilityHolder holder,
            Map<RecipeCapability<?>, List<Content>> contents, boolean tick,
            CallbackInfoReturnable<GTRecipe.ActionResult> cir) {
        if (contents.isEmpty()) cir.setReturnValue(GTRecipe.ActionResult.SUCCESS);
    }

    @Inject(method = "handleRecipe", at = @At("HEAD"), cancellable = true, require = 1)
    private void enhancedcore$emptyExecution(IO io, IRecipeCapabilityHolder holder, boolean tick,
            Map<RecipeCapability<?>, List<Content>> contents,
            Map<RecipeCapability<?>, Object2IntMap<?>> chances, CallbackInfoReturnable<Boolean> cir) {
        if (contents.isEmpty()) cir.setReturnValue(true);
    }
}
