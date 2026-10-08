package com.gtl.enhancedcore.mixin.gtlcore;

import com.gregtechceu.gtceu.api.capability.recipe.CWURecipeCapability;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import org.gtlcore.gtlcore.common.data.GTLRecipeTypes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 太空探测器接收机（gtceu:space_probe_surface_reception）与
 * 宇宙探测器接收阵列（gtceu:space_cosmic_probe_receivers）取消算力要求：
 * 在最终 GTRecipe 构造完成时，对这两个 recipeType 的配方移除 tickInputs 中的 CWU。
 * 覆盖所有注册路径：gtlcore 代码（GTRecipeBuilder.CWUt）与 KubeJS（GTRecipeJS.inputCWU
 * 走独立 schema，绕过 builder）最终都会构造 GTRecipe，在这里统一处理。
 * 机器结构算力仓（COMPUTATION_DATA_RECEPTION）是 OR 可选位，去掉配方 CWU 后
 * 机器无需算力仓即可运行，已装的算力仓不再消耗。
 */
@Mixin(value = GTRecipe.class, remap = false)
public abstract class SpaceProbeRecipeNoCWUMixin {

    @Shadow(remap = false)
    public GTRecipeType recipeType;
    @Shadow(remap = false) @Final @Mutable
    public java.util.Map<com.gregtechceu.gtceu.api.capability.recipe.RecipeCapability<?>,
            java.util.List<com.gregtechceu.gtceu.api.recipe.content.Content>> tickInputs;

    @Inject(method = "<init>", at = @At("TAIL"), remap = false)
    private void gtlcore$removeCWUForSpaceProbes(CallbackInfo ci) {
        GTRecipeType type = this.recipeType;
        if (type == GTLRecipeTypes.SPACE_COSMIC_PROBE_RECEIVERS_RECIPES ||
                type == GTLRecipeTypes.SPACE_PROBE_SURFACE_RECEPTION_RECIPES) {
            // Recipe builders may pass immutable maps or reuse them for another recipe.
            if (this.tickInputs.containsKey(CWURecipeCapability.CAP)) {
                this.tickInputs = new java.util.HashMap<>(this.tickInputs);
                this.tickInputs.remove(CWURecipeCapability.CAP);
            }
        }
    }
}
