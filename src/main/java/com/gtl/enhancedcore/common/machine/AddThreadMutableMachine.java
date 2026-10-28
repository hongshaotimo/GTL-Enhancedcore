package com.gtl.enhancedcore.common.machine;

import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gtladd.gtladditions.api.machine.logic.MutableRecipesLogic;
import com.gtladd.gtladditions.api.machine.mutable.MutableElectricParallelHatchMultiblockMachine;

/**
 * 「ADD 跨配方线程」接入的公共基类（用户 2026-10-12 指定）。
 *
 * <p><b>只加 ADD 的跨配方线程，不接入本模组的跨配方类型并行（IV 隔离订单）</b>：
 * 这些机器<b>不在</b> {@code IvMachineScope.NATIVE_IDS} 内，因此不会被 {@code IvNativeEngine} 接管、
 * 不会被 {@code GtlMegastructurePatterns} 替换结构仓室谓词、也不会被贴上
 * 「支持跨配方类型并行」标识。线程完全由 GTLAdditions 自己的
 * {@code MutableRecipesLogic} 调度。
 *
 * <p><b>线程口径</b>：默认 128 条基准 + 天球分歧引擎（Ω）叠加；个别接入可覆盖基数。
 * {@code MutableRecipesLogic.getMultipleThreads()} 原实现为
 * {@code getAdditionalThread() > 0 ? 该值 : 1}，其 {@code getAdditionalThread()} 即 Ω 交付的线程数
 * （无 Ω 为 0），故覆写为 128 + Ω。
 *
 * <p><b>为什么必须是 ADD 可变机器</b>：上游这些机器是普通
 * {@code WorkableElectricMultiblockMachine}，既不产生跨配方线程，也不实现 ADD 的
 * {@code IThreadModifierMachine}；而 {@code ThreadPartMachine.addedToController()} 只在控制器实现
 * 该接口时才交付 Ω 线程（否则 {@code threadMultiplier = 0}）。
 * 继承 {@link MutableElectricParallelHatchMultiblockMachine} 后两点同时满足。
 */
public abstract class AddThreadMutableMachine extends MutableElectricParallelHatchMultiblockMachine {
    public static final int DEFAULT_ADD_CROSS_RECIPE_THREADS = 128;

    /** 机器显示名，用于日志与诊断。 */
    private final String path;

    protected AddThreadMutableMachine(IMachineBlockEntity holder, String path, Object... args) {
        super(holder, args);
        this.path = path;
    }

    /** 供日志使用。 */
    public final String machinePath() {
        return path;
    }

    /**
     * 该机器改造前的并行基准；{@code <= 0} 表示沿用 ADD 基类的并行仓并行。
     *
     * <p>为何需要它：ADD 的 {@code MutableRecipesLogic} 只读
     * {@code getMaxParallel()}（基类实现 = 并行仓当前并行，无仓为 1），
     * <b>不经过</b> {@code MachineDefinition.getRecipeModifier()}。
     * 因此改造前靠 recipeModifier 设定的固定并行会被绕过（2026-10-12 用户发现
     * 恒星终极物质锻造工厂的 50000 并行消失）。子类在此声明自己的固定并行即可保留。
     */
    protected int parallelBaseline() {
        return 0;
    }

    protected int threadBaseline() {
        return DEFAULT_ADD_CROSS_RECIPE_THREADS;
    }

    /**
     * 并行：有固定基准则用基准（保留改造前的并行语义），否则沿用 ADD 的并行仓并行。
     */
    @Override
    public int getMaxParallel() {
        if (com.gtl.enhancedcore.common.machine.galaxy.GalaxyLinks.unlimited(this)) return Integer.MAX_VALUE;
        int baseline = parallelBaseline();
        return baseline > 0 ? baseline : super.getMaxParallel();
    }

    @Override
    protected RecipeLogic createRecipeLogic(Object... args) {
        return new AddThreadRecipeLogic(this);
    }

    @Override
    public void onStructureFormed() {
        super.onStructureFormed();
        // ADD 默认只在装入线程修改部件时才开启跨配方模式；本类常驻开启，
        // 脱成型由 ADD 基类 onStructureInvalid() 关闭（与 qft 接入同款处理）。
        getRecipeLogic().setUseMultipleRecipes(true);
    }

    /** 128 基准 + Ω：Ω 取自 ADD 的 {@code getAdditionalThread()}（无 Ω 为 0）。 */
    static final class AddThreadRecipeLogic extends MutableRecipesLogic<MutableElectricParallelHatchMultiblockMachine> {
        private final AddThreadMutableMachine owner;

        AddThreadRecipeLogic(AddThreadMutableMachine machine) {
            super(machine);
            this.owner = machine;
        }

        @Override
        public int getMultipleThreads() {
            if (com.gtl.enhancedcore.common.machine.galaxy.GalaxyLinks.active(owner))
                return com.gtl.enhancedcore.common.machine.galaxy.GalaxyLinks.threads(owner, (int)Math.min(Integer.MAX_VALUE, (long)owner.threadBaseline() + Math.max(0, owner.getAdditionalThread())));
            long total = (long) owner.threadBaseline()
                    + Math.max(0, owner.getAdditionalThread());
            return (int) Math.max(1L, Math.min(Integer.MAX_VALUE, total));
        }
    }
}
