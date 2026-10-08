package com.gtl.enhancedcore.common.machine;

import com.gregtechceu.gtceu.api.GTValues;
import com.gtl.enhancedcore.common.recipe.ThreadLimitedRecipeLogic;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gtladd.gtladditions.api.machine.feature.IThreadModifierPart;
import com.gtladd.gtladditions.api.machine.multiblock.GTLAddWorkableElectricParallelHatchMultipleRecipesMachine;

import org.gtlcore.gtlcore.common.data.GTLRecipeModifiers;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * 大型电炉 —— 11×12×10 多方块，使用 GTCEu FURNACE_RECIPES。
 *
 * 机制（2026-08-03 用户指定，采用 gtladd 官方“绝大部分机器”模式，字节码实证）：
 * - 继承 GTLAddWorkableElectricParallelHatchMultipleRecipesMachine（OreProcessorMachine 同系基类），
 *   配方逻辑为 GTLAddMultipleRecipesLogic：常驻跨配方并行，无模式开关，
 *   remain = getMaxParallel() × getMultipleThreads() 贪婪合并多个配方。
 * - LV 基准：基础并行 64、跨配方线程 2；电压每高于 LV 一级，并行与线程各 ×2。
 * - 并行控制仓：最多 1 个，并行再乘仓倍率（GTLRecipeModifiers.getHatchParallel，只乘一次）。
 * - Ω-天球分歧引擎：本类覆写 setThreadPartMachine/getThreadPartMachine 存储部件
 *   （GTLAdd 基类默认空实现，不覆写则引擎线程恒 0），线程数追加 getAdditionalThread()。
 * - 显示：基类 addDisplayText 自带并行行（gtceu.multiblock.parallel）与跨配方线程行
 *   （gtladditions.multiblock.threads）；Jade 由 gtladd ParallelProviderMixin 的
 *   GTLAddWorkableElectricMultipleRecipesMachine 分支自动输出并行 + 线程。
 * - 能量：GTLAddMultipleRecipesLogic.buildFinalNormalRecipe 按批次总 EU 守恒运行。
 */
public class LargeFurnaceMachine extends GTLAddWorkableElectricParallelHatchMultipleRecipesMachine {

    /** LV 基础并行（用户指定，勿改）。 */
    public static final int BASE_PARALLEL = 64;

    /** LV 基础跨配方线程（用户指定，勿改）。 */
    public static final int BASE_THREADS = 2;

    /** 天球分歧引擎部件（基类默认 setThreadPartMachine 为空实现，必须自行存储）。 */
    private IThreadModifierPart threadPartMachine;

    public LargeFurnaceMachine(IMachineBlockEntity holder, Object... args) {
        super(holder, args);
    }

    @Override
    public boolean keepSubscribing() {
        return true;
    }

    public static int getBaseParallel() {
        return BASE_PARALLEL;
    }

    public static int getBaseThreads() {
        return BASE_THREADS;
    }

    /** 电压并行：LV 基础 64，每高于 LV 一级 ×2；移位并钳制到 int 上限防溢出。 */
    public int getParallelForTier() {
        int tier = this.getTier();
        long result = (long) BASE_PARALLEL << Math.min(30, Math.max(0, tier - GTValues.LV));
        return (int) Math.min(Integer.MAX_VALUE, result);
    }

    /** 电压线程：LV 基础 2，每高于 LV 一级 ×2；移位并钳制到 int 上限防溢出。 */
    public int getThreadsForTier() {
        int tier = this.getTier();
        long result = (long) BASE_THREADS << Math.min(30, Math.max(0, tier - GTValues.LV));
        return (int) Math.min(Integer.MAX_VALUE, result);
    }

    /** 总并行 = 电压并行 × 并行控制仓倍率（官方 GTLRecipeModifiers.getHatchParallel，只乘一次）。 */
    @Override
    public int getMaxParallel() {
        long result = (long) this.getParallelForTier() * (long) GTLRecipeModifiers.getHatchParallel(this);
        return (int) Math.min(Integer.MAX_VALUE, result);
    }

    /** 当前跨配方线程数 = 电压线程 + 天球分歧引擎额外线程。 */
    public int getCurrentThreads() {
        return this.getRecipeLogic().getMultipleThreads();
    }

    @Override
    public IThreadModifierPart getThreadPartMachine() {
        return this.threadPartMachine;
    }

    @Override
    public void setThreadPartMachine(IThreadModifierPart threadPartMachine) {
        this.threadPartMachine = threadPartMachine;
    }

    @Override
    public void onStructureInvalid() {
        super.onStructureInvalid();
        this.threadPartMachine = null;
    }

    @Override
    public void onPartUnload() {
        super.onPartUnload();
        this.threadPartMachine = null;
    }

    @Override
    public RecipeLogic createRecipeLogic(Object... args) {
        return new ThreadLimitedRecipeLogic(this, () -> (int) Math.max(1L, Math.min(Integer.MAX_VALUE, (long) getThreadsForTier() + getAdditionalThread())));
    }

    public static Component getRecipeTypeName(GTRecipeType type) {
        ResourceLocation rl = type.registryName;
        return Component.translatable("tooltip.gtl_enhancedcore.available_recipe",
                        Component.translatable(rl.getNamespace() + "." + rl.getPath()).withStyle(ChatFormatting.DARK_PURPLE))
                .withStyle(ChatFormatting.GRAY);
    }

    /** 所有机器 Tooltip 底部的双语彩虹水印；颜色码存放在语言文件。 */
    public static Component getCreditLine() {
        return Component.translatable("tooltip.gtl_enhancedcore.credit");
    }

}
