package com.gtl.enhancedcore.mixin.gtceu;

import com.gregtechceu.gtceu.api.machine.multiblock.WorkableMultiblockMachine;
import com.gtl.enhancedcore.common.recipe.iv.SuperBufferNaming;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 多方块成型时自动填写超级样板总成右上角的“重命名样板总成”。
 *
 * <p>命名来源是机器的配方类型表，按总成在机器上的**位置序号**取名：
 * 第 1 个总成 = 配方表第 1 项，第 2 个 = 第 2 项……超出配方类型数量的总成留空。
 *
 * <p>位置序号用方块坐标计算，不用 {@code getParts()} 的下标 ——
 * GTCEu 的 {@code partSorter} 默认 {@code null}，遍历顺序每次重启都可能变，
 * 用下标会导致名字互换或重复（这正是“分批摆总成后出现重复/遗漏”的原因）。
 *
 * <p>服务端通过独立中文词典保存名称，不使用当前语言；手动名称由来源标志保护。
 */
@Mixin(value = WorkableMultiblockMachine.class, remap = false)
public abstract class MultiblockAutoRenameSuperBufferMixin {

    @Inject(method = "onStructureFormed", at = @At("TAIL"), remap = false)
    private void gtlEnhancedcore$autoRenameSuperPatternBuffer(CallbackInfo ci) {
        WorkableMultiblockMachine self = (WorkableMultiblockMachine) (Object) this;
        SuperBufferNaming.applyNames(self);
    }
}
