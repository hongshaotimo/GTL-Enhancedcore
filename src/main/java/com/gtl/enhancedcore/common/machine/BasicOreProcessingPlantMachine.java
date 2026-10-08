package com.gtl.enhancedcore.common.machine;

import com.gregtechceu.gtceu.api.GTValues;
import com.gtl.enhancedcore.common.recipe.ThreadLimitedRecipeLogic;
import com.gregtechceu.gtceu.api.gui.fancy.IFancyConfigurator;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gregtechceu.gtceu.utils.GTUtil;
import com.gtladd.gtladditions.api.machine.feature.IThreadModifierPart;
import com.gtladd.gtladditions.api.machine.multiblock.GTLAddWorkableElectricMultipleRecipesMachine;

/**
 * 基础矿石处理厂：MV 阶段矿石处理枢纽，采用 gtladd 官方“绝大部分机器”跨配方并行体系
 * （GTLAddWorkableElectricMultipleRecipesMachine + GTLAddMultipleRecipesLogic，字节码实证）：
 * - 常驻跨配方并行，无模式开关，remain = getMaxParallel() × getMultipleThreads() 贪婪合并多个配方。
 * - 并行 = 64 + 100 × (能源仓电压等级 - MV)：MV 64 / HV 164 / EV 264 / IV 364，低于 MV 钳为 64（用户指定，勿改）。
 * - 跨配方线程 = max(1, 1 + 2 × (能源仓电压等级 - MV))：MV 1 / HV 3 / EV 5 / IV 7（用户指定，勿改）。
 * - 固定处理时长 40t（2s）：setLimitedDuration(FIXED_DURATION) 走 gtladd 官方 limitedDuration 机制，
 *   EUt = totalEu / 40；EUt 超能源仓供压时按官方行为满压并把时长拉长到能跑完。
 * - 覆写 createConfigurators() 返回 null，锁死固定时长（禁止 GUI 时长配置器修改 40t）。
 * - 支持 Ω-天球分歧引擎（IThreadModifierPart）：GTLAdd 基类默认空实现，本类覆写存储部件，线程追加 getAdditionalThread()。
 * - 不支持并行控制仓（注册端无 PARALLEL_HATCH 能力位）。
 *
 * 显示：基类 addDisplayText 自带并行行（gtceu.multiblock.parallel）与跨配方线程行
 * （gtladditions.multiblock.threads）；Jade 由 gtladd ParallelProviderMixin 的
 * GTLAddWorkableElectricMultipleRecipesMachine 分支自动输出并行 + 线程。
 */
public class BasicOreProcessingPlantMachine extends GTLAddWorkableElectricMultipleRecipesMachine {

    /** 固定处理时长：40 tick = 2 秒（用户指定，勿改）。 */
    public static final int FIXED_DURATION = 40;

    /** MV 基础并行（用户指定，勿改）。 */
    public static final int BASE_PARALLEL = 64;

    /** 电压每高于 MV 一级增加的并行（用户指定，勿改）。 */
    public static final int PARALLEL_PER_TIER = 100;

    /** MV 基础跨配方线程（用户指定，勿改）。 */
    public static final int BASE_THREADS = 1;

    /** 电压每高于 MV 一级增加的跨配方线程（用户指定，勿改）。 */
    public static final int THREADS_PER_TIER = 2;

    /** 天球分歧引擎部件（GTLAdd 基类默认 setThreadPartMachine 为空实现，必须自行存储）。 */
    private IThreadModifierPart threadPartMachine;

    public BasicOreProcessingPlantMachine(IMachineBlockEntity holder, Object... args) {
        super(holder, args);
        this.setLimitedDuration(FIXED_DURATION);
    }

    @Override
    public boolean keepSubscribing() {
        return true;
    }

    /** 静态并行公式（唯一实现，注册端 tooltip 与运行端逻辑共用）。 */
    public static int parallelForVoltage(long maxVoltage) {
        int tier = GTUtil.getFloorTierByVoltage(maxVoltage);
        return BASE_PARALLEL + PARALLEL_PER_TIER * Math.max(0, tier - GTValues.MV);
    }

    /** 当前电压并行：由能源仓电压决定。 */
    public int getParallelForTier() {
        return parallelForVoltage(this.getMaxVoltage());
    }

    /** 当前跨配方线程：MV 基础 1，电压每高于 MV 一级 +2。 */
    public int getThreadsForTier() {
        int tier = this.getTier();
        return Math.max(1, BASE_THREADS + THREADS_PER_TIER * Math.max(0, tier - GTValues.MV));
    }

    /** 总并行 = 电压并行（不支持并行控制仓，无仓倍率）。 */
    @Override
    public int getMaxParallel() {
        return this.getParallelForTier();
    }

    @Override
    public RecipeLogic createRecipeLogic(Object... args) {
        return new ThreadLimitedRecipeLogic(this, () -> (int) Math.max(1L, Math.min(Integer.MAX_VALUE, (long) getThreadsForTier() + getAdditionalThread())));
    }

    /** 锁死固定时长：禁止 gtladd LimitedDurationConfigurator 出现在 GUI。 */
    @Override
    protected IFancyConfigurator createConfigurators() {
        return null;
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

}
