package com.gtl.enhancedcore.common.machine;

import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gtladd.gtladditions.api.machine.mutable.MutableElectricParallelHatchMultiblockMachine;

/**
 * QFT（量子力场推进器）的 ADD 跨配方并行接入（用户 2026-10-06 指定）。
 *
 * <p>沿用 GTLAdditions 官方“可变机器”体系，不做自研调度：机器类直接继承 ADD 的
 * {@link MutableElectricParallelHatchMultiblockMachine}，其 {@code createRecipeLogic()} 返回
 * ADD 的 {@code MutableRecipesLogic}，总并行 = {@code getMaxParallel() × getMultipleThreads()}。
 * <ul>
 *   <li>并行：ADD 该基类 {@code getMaxParallel()} 取<b>并行仓当前并行</b>（无并行仓为 1），
 *       与 qft 原有的 {@code GTRecipeModifiers.PARALLEL_HATCH} 语义一致，未被改成无限并行。</li>
 *   <li>跨配方线程：覆写 {@link #getAdditionalThread()} 固定 512（用户指定），
 *       不再依赖 Ω-天球分歧引擎部件；ADD 的 {@code getMultipleThreads()} 取该值。</li>
 *   <li>跨配方模式：ADD 默认只在装入线程修改部件时开启（{@code ThreadPartMachine.addedToController}），
 *       本机无该仓，故在成型时显式 {@code setUseMultipleRecipes(true)} 常驻启用；
 *       脱成型由 ADD 基类 {@code onStructureInvalid()} 自动关闭。</li>
 * </ul>
 *
 * <p>提示：ADD 基类 {@code addDisplayText} 自带并行与跨配方线程行；本机另有 Tips 声明
 * 「支持跨配方并行 / 拥有 512 条跨配方线程」（见 {@code TooltipPolicy} 与语言键）。
 */
public class QftMutableMachine extends MutableElectricParallelHatchMultiblockMachine {

    /** 用户指定的跨配方线程数（2026-10-06，勿改）。 */
    public static final int CROSS_RECIPE_THREADS = 512;

    public QftMutableMachine(IMachineBlockEntity holder, Object... args) {
        super(holder, args);
    }

    /** ADD 的线程来源：{@code MutableRecipesLogic.getMultipleThreads()} 读该值，单位线程不吃部件。 */
    @Override
    public int getAdditionalThread() {
        return CROSS_RECIPE_THREADS;
    }

    @Override
    public void onStructureFormed() {
        super.onStructureFormed();
        // ADD 基类 onStructureInvalid() 会关闭多配方模式，成型后重新开启以保证常驻跨配方并行。
        getRecipeLogic().setUseMultipleRecipes(true);
    }
}
