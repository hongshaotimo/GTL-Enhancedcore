package com.gtl.enhancedcore.mixin.gtlcore;

import com.gregtechceu.gtceu.data.recipe.builder.GTRecipeBuilder;
import com.gtl.enhancedcore.common.recipe.PrimitiveVoidOreRouting;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.function.Consumer;
import net.minecraft.data.recipes.FinishedRecipe;
import net.minecraft.resources.ResourceLocation;
import org.gtlcore.gtlcore.common.data.GTLRecipeTypes;
import org.gtlcore.gtlcore.data.recipe.Misc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Slice;

/** Replaces the upstream single primitive-ore recipe with three circuit-selected routes. */
@Mixin(value = Misc.class, remap = false)
public abstract class PrimitiveVoidOreRecipeMixin {
    @WrapOperation(
            method = "init",
            at = @At(value = "INVOKE",
                    target = "Lcom/gregtechceu/gtceu/data/recipe/builder/GTRecipeBuilder;save(Ljava/util/function/Consumer;)V",
                    remap = false),
            slice = @Slice(
                    from = @At(value = "FIELD",
                            target = "Lorg/gtlcore/gtlcore/common/data/GTLRecipeTypes;PRIMITIVE_VOID_ORE_RECIPES:Lcom/gregtechceu/gtceu/api/recipe/GTRecipeType;",
                            remap = false),
                    to = @At(value = "FIELD",
                            target = "Lorg/gtlcore/gtlcore/common/data/GTLRecipeTypes;WOOD_DISTILLATION_RECIPES:Lcom/gregtechceu/gtceu/api/recipe/GTRecipeType;",
                            remap = false)),
            require = 1, allow = 1, remap = false)
    private static void enhancedcore$routes(GTRecipeBuilder builder, Consumer<FinishedRecipe> consumer,
                                            Operation<Void> original) {
        if (builder.recipeType != GTLRecipeTypes.PRIMITIVE_VOID_ORE_RECIPES) {
            original.call(builder, consumer);
            return;
        }
        for (int circuit = 1; circuit <= 3; circuit++) {
            ResourceLocation id = new ResourceLocation("gtl_enhancedcore", "dimension_" + circuit);
            builder.copy(id)
                    .circuitMeta(circuit)
                    .addData(PrimitiveVoidOreRouting.TARGET_DATA, circuit)
                    .save(consumer);
        }
    }
}
