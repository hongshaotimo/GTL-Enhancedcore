package com.gtl.enhancedcore.mixin.mc;

import com.google.gson.JsonElement;
import com.gtl.enhancedcore.common.recipe.PlantResearchMigration;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.crafting.RecipeManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.Map;

@Mixin(RecipeManager.class)
public abstract class PlantResearchRecipeMixin {
    @Inject(method = "apply(Ljava/util/Map;Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/util/profiling/ProfilerFiller;)V",
            at = @At("HEAD"), require = 1)
    private void gtlEnhancedcore$migratePlantResearch(Map<ResourceLocation, JsonElement> recipes,
            ResourceManager resources, ProfilerFiller profiler, CallbackInfo ci) {
        for (String id : PlantResearchMigration.recipeIds()) {
            recipes.computeIfPresent(new ResourceLocation(id),
                    (key, recipe) -> PlantResearchMigration.migrate(id, recipe));
        }
    }
}
