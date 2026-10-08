package com.gtl.enhancedcore.mixin.mc;

import com.google.gson.JsonObject;
import com.gtl.enhancedcore.common.recipe.MvCircuitAssemblerRecipe;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.ShapedRecipe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

// Dynamic GT recipes are added after RecipeManager.apply starts, through this serializer.
@Mixin(ShapedRecipe.Serializer.class)
public abstract class MvCircuitAssemblerRecipeMixin {
    @ModifyVariable(method = "fromJson(Lnet/minecraft/resources/ResourceLocation;Lcom/google/gson/JsonObject;)Lnet/minecraft/world/item/crafting/ShapedRecipe;",
            at = @At("HEAD"), argsOnly = true, ordinal = 0, require = 1)
    private JsonObject enhancedcore$mvCircuitGrade(JsonObject recipe, ResourceLocation id, JsonObject original) {
        return MvCircuitAssemblerRecipe.RECIPE_ID.equals(id.toString())
                ? MvCircuitAssemblerRecipe.useMvCircuits(recipe).getAsJsonObject() : recipe;
    }
}
