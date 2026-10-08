package com.gtl.enhancedcore.mixin.gtceu;

import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gregtechceu.gtceu.api.recipe.OverclockingLogic;
import com.gregtechceu.gtceu.api.recipe.modifier.RecipeModifier;
import com.gregtechceu.gtceu.api.recipe.modifier.RecipeModifierList;
import com.gregtechceu.gtceu.common.data.GTRecipeModifiers;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 恒星终极物质锻造工厂（gtceu:star_ultimate_material_forge_factory，gtlcore 注册）并行调整：
 * 整体替换 recipeModifier 链，并行 1000 -> 50000。
 * 与原注册逐项一致（accurateParallel + PERFECT_OVERCLOCK_SUBTICK），仅常量不同；
 * 新 RecipeModifierList 仍走 gtlcore RecipeModifierListMixin 的 @Overwrite apply，链尾统一限量倍增。
 */
@Mixin(value = MachineDefinition.class, remap = false)
public abstract class MachineDefinitionStarUltimateForgeParallelMixin {

    private static final String TARGET_MACHINE = "star_ultimate_material_forge_factory";
    private static final int TARGET_PARALLEL = 50000;
    @Unique private RecipeModifier enhanced$starModifier;

    @Inject(method = "getRecipeModifier", at = @At("RETURN"), cancellable = true, remap = false)
    private void gtlcore$starUltimateForgeRecipeModifier(CallbackInfoReturnable<RecipeModifier> cir) {
        MachineDefinition self = (MachineDefinition) (Object) this;
        ResourceLocation id = self.getId();
        if (id == null || !"gtceu".equals(id.getNamespace()) || !TARGET_MACHINE.equals(id.getPath())) {
            return;
        }
        if (enhanced$starModifier == null) enhanced$starModifier = new RecipeModifierList(
                (machine, recipe, params, result) -> {
                    var parallel = GTRecipeModifiers.accurateParallel(machine, recipe, TARGET_PARALLEL, false);
                    return parallel.getSecond() > 0 ? parallel.getFirst() : null;
                },
                GTRecipeModifiers.ELECTRIC_OVERCLOCK.apply(OverclockingLogic.PERFECT_OVERCLOCK_SUBTICK)
        );
        cir.setReturnValue(enhanced$starModifier);
    }
}
