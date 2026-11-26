package com.gtl.enhancedcore.mixin.gtladditions;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.common.data.GTRecipeTypes;
import com.gregtechceu.gtceu.data.recipe.builder.GTRecipeBuilder;
import com.gtladd.gtladditions.data.recipes.NewMultiBlockMachineController;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.function.Consumer;
import net.minecraft.data.recipes.FinishedRecipe;
import org.gtlcore.gtlcore.utils.Registries;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Retiers the five ADD controller recipes without replacing their research or publishing alternate recipes. */
@Mixin(value = NewMultiBlockMachineController.class, remap = false)
public abstract class LightHunterAssemblyRecipesMixin {
    @WrapOperation(method = "init", at = @At(value = "INVOKE", target =
            "Lcom/gregtechceu/gtceu/data/recipe/builder/GTRecipeBuilder;save(Ljava/util/function/Consumer;)V"),
            require = 1)
    private void enhancedcore$maxAssembly(GTRecipeBuilder builder, Consumer<FinishedRecipe> provider,
                                          Operation<Void> original) {
        if (builder.recipeType == GTRecipeTypes.ASSEMBLY_LINE_RECIPES
                && builder.id.getNamespace().equals("gtladditions")
                && switch (builder.id.getPath()) {
                    case "light_hunter_space_station", "nexus_satellite_factory_mk1", "nexus_satellite_factory_mk2",
                            "nexus_satellite_factory_mk3", "nexus_satellite_factory_mk4" -> true;
                    default -> false;
                }) {
            builder.inputItems(Registries.getItemStack("kubejs:suprachronal_mainframe_complex", 1))
                    .EUt(GTValues.VA[GTValues.MAX]);
        }
        original.call(builder, provider);
    }
}
