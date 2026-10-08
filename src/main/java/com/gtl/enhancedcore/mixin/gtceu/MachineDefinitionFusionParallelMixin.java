package com.gtl.enhancedcore.mixin.gtceu;

import com.gtl.enhancedcore.common.recipe.FusionParallelPolicy;
import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gregtechceu.gtceu.api.recipe.modifier.RecipeModifier;
import com.gregtechceu.gtceu.api.recipe.modifier.RecipeModifierList;
import java.util.Arrays;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 聚变反应堆控制电脑并行（GTL-Enhancedcore，2026-09-03 重写实现）。
 *
 * 旧实现（FusionReactorParallelMixin 的 recipeModifier RETURN 注入）对 MK1/MK2 不生效：
 * 非压缩版 fusion_reactor 的 MK1~MK3（luv/zpm/uv）由 GTCEu `GTMachines` 注册、
 * MK4/MK5（uhv/uev）由 gtlcore `AdvancedMultiBlockMachine` 注册（字节码实证：
 * `registerTieredMultis("fusion_reactor", …, new int[]{9, 10})`），而 `FusionReactorMachine.recipeModifier`
 * 已被 gtlcore `FusionReactorMachineMixin` 整体 `@Overwrite`；对被 @Overwrite 的静态方法再做
 * RETURN 注入，`OCResult.parallel` 会在链尾被 gtlcore `RecipeModifierListMixin` 之前的处理覆盖，
 * 实测 MK1/MK2 无并行。
 *
 * 本实现（2026-09-03 修正）：在 `MachineDefinition.getRecipeModifier` 返回处把「设置并行」
 * 作为**最后一个** modifier 追加到原链**同一层**（原链若是 RecipeModifierList 则展开数组，
 * 避免嵌套 list 内层 apply 的 result.reset() 清掉 duration 导致并行分支整体跳过）；
 * 随后由 gtlcore `RecipeModifierListMixin.apply` 链尾统一 `ParallelLogic.applyParallel`
 * 做资源检查与限量倍增。档位与 2026-09-02 用户指定一致。
 *
 * 档位（用户指定）：MK1/MK2（luv/zpm）128 并行；MK3~MK5（uv/uhv/uev）512 并行。
 * 仅作用于非压缩版；压缩版（compressed_fusion_reactor）自带并行控制仓，不动。
 */
@Mixin(value = MachineDefinition.class, remap = false)
public abstract class MachineDefinitionFusionParallelMixin {

    @Unique private RecipeModifier enhanced$fusionBase;
    @Unique private RecipeModifier[] enhanced$fusionElements;
    @Unique private RecipeModifier enhanced$fusionCombined;

    @Inject(method = "getRecipeModifier", at = @At("RETURN"), cancellable = true, remap = false)
    private void gtlEnhancedcore$fusionParallel(CallbackInfoReturnable<RecipeModifier> cir) {
        MachineDefinition self = (MachineDefinition) (Object) this;
        ResourceLocation id = self.getId();
        if (id == null) {
            return;
        }
        int parallel = FusionParallelPolicy.limit(id.getNamespace(), id.getPath());
        if (parallel == 0) {
            return;
        }
        RecipeModifier base = cir.getReturnValue();
        if (base == null) {
            return;
        }
        RecipeModifier[] elements = base instanceof RecipeModifierList list
                ? ((RecipeModifierListAccessor) list).gtlEnhancedcore$getModifiers() : null;
        if (enhanced$fusionBase == base && enhanced$fusionCombined != null
                && Arrays.equals(enhanced$fusionElements, elements)) {
            cir.setReturnValue(enhanced$fusionCombined);
            return;
        }
        final int target = parallel;
        RecipeModifier tail = (machine, recipe, params, result) -> {
            if (result != null) {
                result.setParallel(target);
            }
            return recipe;
        };
        RecipeModifier combinedModifier;
        if (elements != null && elements.length > 0) {
            // 展开原链到同一层，避免嵌套 list 内层 apply 的 result.reset() 清掉 duration 导致并行分支跳过
            RecipeModifier[] combined = Arrays.copyOf(elements, elements.length + 1);
            combined[elements.length] = tail;
            combinedModifier = new RecipeModifierList(combined);
        } else combinedModifier = new RecipeModifierList(base, tail);
        enhanced$fusionBase = base;
        enhanced$fusionElements = elements == null ? null : elements.clone();
        enhanced$fusionCombined = combinedModifier;
        cir.setReturnValue(combinedModifier);
    }
}
