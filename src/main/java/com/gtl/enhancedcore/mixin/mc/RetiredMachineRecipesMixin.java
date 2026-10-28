package com.gtl.enhancedcore.mixin.mc;

import com.google.gson.JsonElement;
import com.gtl.enhancedcore.common.registration.RetiredMachines;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Runs after ordinary HEAD recipe contributors and before deserialization/GT lookup rebuild. */
@Mixin(value = RecipeManager.class, priority = 100)
public abstract class RetiredMachineRecipesMixin {
    @ModifyVariable(method = "apply(Ljava/util/Map;Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/util/profiling/ProfilerFiller;)V",
            at = @At("HEAD"), argsOnly = true, require = 1)
    private Map<ResourceLocation, JsonElement> retired$removeRecipes(Map<ResourceLocation, JsonElement> recipes) {
        return RetiredMachines.filterRecipes(recipes);
    }
}
