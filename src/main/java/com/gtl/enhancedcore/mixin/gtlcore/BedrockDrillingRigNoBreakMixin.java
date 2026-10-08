package com.gtl.enhancedcore.mixin.gtlcore;

import org.gtlcore.gtlcore.common.machine.multiblock.electric.BedrockDrillingRig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.concurrent.ThreadLocalRandom;

/**
 * 基岩钻机（gtceu:bedrock_drilling_rig）免破坏基岩：
 * 原版 afterWorking 里 ThreadLocalRandom.nextInt(10)==0 时把钻头下方基岩替换为空气（10% 概率）。
 * 把 nextInt 调用重定向为恒返回 1，使破坏分支永不触发。
 */
@Mixin(BedrockDrillingRig.class)
public abstract class BedrockDrillingRigNoBreakMixin {

    @Redirect(method = "afterWorking", at = @At(value = "INVOKE", target = "Ljava/util/concurrent/ThreadLocalRandom;nextInt(I)I"), remap = false)
    private int gtlcore$neverBreakBedrock(ThreadLocalRandom random, int bound) {
        return 1;
    }
}
