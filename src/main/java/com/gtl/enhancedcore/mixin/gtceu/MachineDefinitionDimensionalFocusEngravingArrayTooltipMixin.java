package com.gtl.enhancedcore.mixin.gtceu;

import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gtl.enhancedcore.common.util.MachineTooltips;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.function.BiConsumer;

/**
 * 维度聚焦激光蚀刻阵列（gtceu:dimensional_focus_engraving_array）物品 tips：
 * 原「可用配方类型：维度聚焦激光蚀刻」更新为「可用配方类型：维度聚焦激光蚀刻，激光蚀刻机」，
 * 与新加的 LASER_ENGRAVER_RECIPES 模式一致（MachineDefinitionDimensionalFocusEngravingArrayMixin）。
 */
@Mixin(value = MachineDefinition.class, remap = false)
public abstract class MachineDefinitionDimensionalFocusEngravingArrayTooltipMixin {

    private static final ResourceLocation TARGET_ID = new ResourceLocation("gtceu", "dimensional_focus_engraving_array");

    @Inject(method = "getTooltipBuilder", at = @At("RETURN"), cancellable = true, remap = false)
    private void gtlcore$dimensionalFocusRecipeMapTooltip(CallbackInfoReturnable<BiConsumer<ItemStack, List<Component>>> cir) {
        MachineDefinition self = (MachineDefinition) (Object) this;
        ResourceLocation id = self.getId();
        if (id == null || !TARGET_ID.equals(id)) {
            return;
        }
        cir.setReturnValue(MachineTooltips.modifyNative(cir.getReturnValue(), id.getPath(), self.getRecipeTypes()));
    }
}
