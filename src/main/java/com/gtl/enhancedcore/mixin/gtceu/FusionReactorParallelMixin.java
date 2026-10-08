package com.gtl.enhancedcore.mixin.gtceu;

import com.gtl.enhancedcore.common.recipe.FusionParallelPolicy;
import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableMultiblockMachine;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.common.machine.multiblock.electric.FusionReactorMachine;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * 聚变反应堆并行（GTL-Enhancedcore）。
 *
 * 本类只负责 GUI 并行行显示；并行的实际设置在
 * {@link MachineDefinitionFusionParallelMixin}（getRecipeModifier 链尾追加，2026-09-03 重写）。
 *
 * 档位：MK1/MK2（luv/zpm）128；MK3~MK5（uv/uhv/uev）512。仅非压缩版（压缩版自带并行控制仓）。
 * 电压机制保持原版（getMaxVoltage 不覆写）。
 */
@Mixin(value = FusionReactorMachine.class, remap = false)
public abstract class FusionReactorParallelMixin {

    /** MK3~MK5：512 并行。 */

    /** MK1~MK2：128 并行（2026-09-02 用户新增）。 */

    /** 返回该聚变反应堆的并行上限；非目标机器返回 0。 */
    private static int parallelFor(MachineDefinition def) {
        ResourceLocation id = def == null ? null : def.getId();
        if (id == null) {
            return 0;
        }
        return FusionParallelPolicy.limit(id.getNamespace(), id.getPath());
    }

    @Inject(method = "addDisplayText", at = @At("TAIL"), remap = false)
    private void gtlcore$fusionParallelDisplay(List<Component> textList, CallbackInfo ci) {
        if (parallelFor(((MetaMachine) (Object) this).getDefinition()) <= 0) {
            return;
        }
        int maxParallel = parallelFor(((MetaMachine) (Object) this).getDefinition());
        GTRecipe last = ((WorkableMultiblockMachine) (Object) this).getRecipeLogic().getLastRecipe();
        int shown = (last != null && last.parallels > 1) ? last.parallels : maxParallel;
        textList.add(Component.translatable("gui.gtl_enhancedcore.fusion_reactor.parallel",
                Component.literal(String.valueOf(shown)).withStyle(ChatFormatting.LIGHT_PURPLE))
                .withStyle(ChatFormatting.GRAY));
    }

}
