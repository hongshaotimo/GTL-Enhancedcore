package com.gtl.enhancedcore.mixin.gtceu;

import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gregtechceu.gtceu.common.data.GTRecipeTypes;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Arrays;

/**
 * 给 {@code gtceu:dimensional_focus_engraving_array}（维度聚焦刻印阵列）
 * 新增一个"激光蚀刻机"模式（LASER_ENGRAVER_RECIPES），
 * 使其 GUI 侧栏出现激光蚀刻机配方模式切换选项。
 */
@Mixin(value = MachineDefinition.class, remap = false)
public class MachineDefinitionDimensionalFocusEngravingArrayMixin {

    private static final ResourceLocation TARGET_ID = new ResourceLocation("gtceu", "dimensional_focus_engraving_array");
    @Unique private GTRecipeType[] enhanced$sourceTypes;
    @Unique private GTRecipeType[] enhanced$extendedTypes;

    @Inject(method = "getRecipeTypes", at = @At("RETURN"), cancellable = true, remap = false)
    private void gtlcore$addLaserEngraverMode(CallbackInfoReturnable<GTRecipeType[]> cir) {
        MachineDefinition self = (MachineDefinition) (Object) this;
        if (!TARGET_ID.equals(self.getId())) {
            return;
        }
        GTRecipeType[] original = cir.getReturnValue();
        if (original != null && enhanced$extendedTypes != null && Arrays.equals(original, enhanced$sourceTypes)) {
            cir.setReturnValue(enhanced$extendedTypes);
            return;
        }
        if (original == null) {
            cir.setReturnValue(new GTRecipeType[]{GTRecipeTypes.LASER_ENGRAVER_RECIPES});
            return;
        }
        // 检查是否已包含
        for (GTRecipeType type : original) {
            if (type == GTRecipeTypes.LASER_ENGRAVER_RECIPES) {
                return;
            }
        }
        GTRecipeType[] extended = Arrays.copyOf(original, original.length + 1);
        extended[original.length] = GTRecipeTypes.LASER_ENGRAVER_RECIPES;
        enhanced$sourceTypes = original.clone();
        enhanced$extendedTypes = extended;
        cir.setReturnValue(extended);
    }
}
