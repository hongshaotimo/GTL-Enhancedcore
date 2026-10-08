package com.gtl.enhancedcore.mixin.gtceu;

import com.gregtechceu.gtceu.api.recipe.modifier.RecipeModifier;
import com.gregtechceu.gtceu.api.recipe.modifier.RecipeModifierList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * 暴露 RecipeModifierList 内部 modifiers 数组（GTL-Enhancedcore）。
 *
 * 用途：聚变并行需要把「设置并行」的 modifier 追加到原链的同一层数组中；
 * 若包成嵌套 RecipeModifierList，内层 apply 结束时 result.reset() 会把
 * duration 清 0，外层并行分支整体跳过（2.1.3 实测无效的根因）。
 */
@Mixin(value = RecipeModifierList.class, remap = false)
public interface RecipeModifierListAccessor {

    @Accessor("modifiers")
    RecipeModifier[] gtlEnhancedcore$getModifiers();
}
