package com.gtl.enhancedcore.mixin.gtlcore;

import com.gtl.enhancedcore.GTLEnhancedcore;
import net.minecraft.core.BlockPos;
import org.gtlcore.gtlcore.common.machine.multiblock.electric.FissionReactorMachine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Fission Reactor 修改（GTL-Enhancedcore）：
 * 1. 永远不会爆炸（doExplosion 被取消）；
 * 2. 损坏达到 100% 后封顶 99，不再触发爆炸分支。
 * 物品 tips 简化与版权行由 MachineDefinitionFissionTooltipMixin 处理。
 */
@Mixin(value = FissionReactorMachine.class, remap = false)
public abstract class FissionReactorMachineMixin {

    @Shadow(remap = false)
    private int damaged;

    @Inject(method = "doExplosion(Lnet/minecraft/core/BlockPos;F)V",
            at = @At("HEAD"), cancellable = true, remap = false)
    private void gtlcore$noExplosion(BlockPos pos, float explosionPower, CallbackInfo ci) {
        GTLEnhancedcore.LOGGER.debug("[FissionReactor] 爆炸已阻止（GTL-Enhancedcore 魔改）");
        ci.cancel();
    }

    @Inject(method = "heatUpdate", at = @At("HEAD"), remap = false)
    private void gtlcore$capDamage(CallbackInfo ci) {
        if (this.damaged > 99) {
            this.damaged = 99;
        }
    }
}
