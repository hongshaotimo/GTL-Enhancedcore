package com.gtl.enhancedcore.mixin.jei;

import com.gtl.enhancedcore.client.gui.RecipeViewport;
import mezz.jei.api.gui.IRecipeLayoutDrawable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(targets = "mezz.jei.gui.recipes.RecipeLayoutWithButtons", remap = false)
public abstract class JeiRecipeViewportMixin {
    @ModifyVariable(method = "create", at = @At("HEAD"), argsOnly = true, ordinal = 0, require = 1)
    private static <T> IRecipeLayoutDrawable<T> enhanced$recipeViewport(IRecipeLayoutDrawable<T> layout) {
        return RecipeViewport.wrap(layout);
    }
}
