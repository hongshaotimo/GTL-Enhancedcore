package com.gtl.enhancedcore.common.recipe.iv;

import com.gregtechceu.gtceu.api.machine.multiblock.CoilWorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gtl.enhancedcore.common.machine.TieredParallelMachine;
import com.gtladd.gtladditions.api.machine.IThreadModifierMachine;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import org.gtlcore.gtlcore.api.machine.multiblock.ParallelMachine;
import org.gtlcore.gtlcore.common.data.GTLRecipeModifiers;

/** Explicit opt-in. Structure replacements such as mega_canner/mega_wiremill are not opt-ins. */
public final class IvMachineScope {

    /** 超级样板总成隔离模式新增的跨配方线程；上游已有线程另行相加。 */
    public static final int NATIVE_CROSS_RECIPE_THREADS = 128;
    /** GTLCore MultipleRecipesLogic 的既有线程数（1.2.3.2-fix3 字节码：MAX_THREADS=64）。 */
    public static final int UPSTREAM_MULTIPLE_RECIPE_THREADS = 64;

    public static final Set<String> NATIVE_IDS = Set.of(
            "crystalline_infinity", "dimensional_focus_engraving_array", "suprachronal_assembly_line",
            "mega_extractor", "field_extruder_factory", "advanced_vacuum_drying_furnace",
            "mega_presser", "mega_distillery", "holy_separator", "cooling_tower",
            "mage_assembler", "super_blast_smelter", "superconducting_electromagnetism",
            "atomic_energy_excitation_plant", "gravitation_shockburst",
            "dimensionally_transcendent_mixer");
    private IvMachineScope() {}

    public static boolean nativeTarget(ResourceLocation id) {
        return id != null && id.getNamespace().equals("gtceu") && NATIVE_IDS.contains(id.getPath());
    }
    public static boolean nativeTarget(Object machine) {
        return machine instanceof WorkableElectricMultiblockMachine electric && nativeTarget(electric.getDefinition().getId());
    }
    public static boolean crossRecipeEnabled(WorkableElectricMultiblockMachine machine) {
        return machine != null && IvBuffers.targetController(machine)
                && IvBuffers.collect(machine).stream().anyMatch(IvBuffers::isolated);
    }
    public static boolean crossRecipeEnabled(Object machine) {
        return machine instanceof WorkableElectricMultiblockMachine electric && crossRecipeEnabled(electric);
    }
    public static int parallel(WorkableElectricMultiblockMachine machine) {
        if (machine instanceof ParallelMachine parallel) return Math.max(1, parallel.getMaxParallel());
        if (machine instanceof CoilWorkableElectricMultiblockMachine coil)
            return Math.max(1, (int)Math.min(Integer.MAX_VALUE, Math.pow(2, coil.getCoilType().getCoilTemperature() / 900.0)));
        return Math.max(1, GTLRecipeModifiers.getHatchParallel(machine));
    }
    /**
     * 超级样板总成隔离模式的跨配方线程上限（引擎/GUI/Jade/物品 tips 的共同来源）。
     * 普通输入/输出仓室模式应显示上游原模式，不应把这里新增的 128 条算进普通模式。
     */
    public static int threads(WorkableElectricMultiblockMachine machine) {
        if (machine == null) return 1;
        if (machine instanceof TieredParallelMachine iv) return Math.max(1, iv.getThreadsForTier());
        int modifier = modifierThreads(machine);
        if (nativeTarget(machine)) {
            // 按机器 ID 判定原有 64 线程，避免 ADD mutable 模式/部件状态改变提示口径。
            var id = machine.getDefinition().getId();
            boolean batch = id != null && BATCH_IDS.contains(id.getPath());
            return batch ? batchThreads(modifier) : nativeThreads(modifier);
        }
        if (machine.getRecipeLogic() instanceof com.gtladd.gtladditions.api.machine.logic.MutableRecipesLogic<?> mutable && mutable.isMultipleRecipeMode())
            return Math.max(1, mutable.getMultipleThreads());
        if (machine.getRecipeLogic() instanceof com.gtladd.gtladditions.api.machine.logic.GTLAddMultipleRecipesLogic gtlAdd)
            return Math.max(1, gtlAdd.getMultipleThreads());
        return batching(machine) ? upstreamBatchThreads(modifier) : nativeThreads(modifier);
    }

    /**
     * 引擎实际使用的线程：仅当启用隔离订单时才用机器线程，否则退回单线程（原版单配方语义）。
     * 显示路径一律用 {@link #threads(WorkableElectricMultiblockMachine)}，二者不要混用。
     */
    public static int activeThreads(WorkableElectricMultiblockMachine machine) {
        return crossRecipeEnabled(machine) ? threads(machine) : 1;
    }

    /**
     * 上游已有 64 条线程的原生机器；超级总成隔离模式在 64 条上新增 128 条，再叠加 Ω。
     * 这些机器由 GTLCore 注册为 {@code CoilWorkableElectricMultipleRecipesMultiblockMachine}
     * 或 {@code WorkableElectricParallelHatchMultipleRecipesMachine}，超级冶炼炉使用
     * {@code MultiBlockMachineA$1} 派生的 {@code MultipleRecipesLogic}（依赖字节码实证，2026-10-07）。
     * 物品提示拿不到机器实例，只能按 ID 判定，故在此显式列出。
     */
    public static final Set<String> BATCH_IDS = Set.of(
            "mega_extractor", "mega_presser", "mega_distillery",
            "field_extruder_factory", "holy_separator", "cooling_tower", "super_blast_smelter");

    /** 按机器 ID 给出超级总成模式的线程基准（不含 Ω，供物品提示等定义级显示使用）。 */
    public static int baseThreadsFor(String path) {
        if (path == null) return NATIVE_CROSS_RECIPE_THREADS;
        return BATCH_IDS.contains(path) ? batchThreads(0) : nativeThreads(0);
    }

    /** 原生跨配方机器基础线程（纯函数，便于离线穷举验证）。 */
    public static int nativeThreads(int modifierThreads) {
        return (int)Math.max(1, Math.min(Integer.MAX_VALUE, (long)NATIVE_CROSS_RECIPE_THREADS + Math.max(0, modifierThreads)));
    }

    /** 原有 64 线程机器的隔离线程 = 原有 64 + 新增 128 + Ω。 */
    public static int batchThreads(int modifierThreads) {
        return (int)Math.max(1, Math.min(Integer.MAX_VALUE,
                (long)UPSTREAM_MULTIPLE_RECIPE_THREADS + NATIVE_CROSS_RECIPE_THREADS + Math.max(0, modifierThreads)));
    }

    private static int upstreamBatchThreads(int modifierThreads) {
        return (int)Math.min(Integer.MAX_VALUE,
                (long)UPSTREAM_MULTIPLE_RECIPE_THREADS + Math.max(0, modifierThreads));
    }

    /**
     * 天球分歧引擎（Ω）附加线程，叠加而非覆盖。
     *
     * <p>ADD 的 {@code ThreadPartMachine.addedToController} 只在控制器实现 {@code IThreadModifierMachine}
     * 时计算部件线程；本白名单中允许安装 Ω 的 11 台均通过 ADD soft interface 或 mutable 类实现接口。
     * 部件扫描仅作为已生效线程的兜底读取，不能使不支持 Ω 的结构凭空获得线程。
     */
    public static int modifierThreads(WorkableElectricMultiblockMachine machine) {
        if (machine instanceof IThreadModifierMachine modifier && modifier.getAdditionalThread() > 0)
            return modifier.getAdditionalThread();
        int total = 0;
        for (var part : machine.getParts()) {
            if (part instanceof com.gtladd.gtladditions.api.machine.feature.IThreadModifierPart threadPart) {
                total = (int)Math.min(Integer.MAX_VALUE, (long)total + Math.max(0, threadPart.getThreadCount()));
            }
        }
        return total;
    }
    public static long budget(WorkableElectricMultiblockMachine machine) {
        return Math.multiplyExact((long)parallel(machine), crossRecipeEnabled(machine) ? threads(machine) : 1);
    }

    /**
     * 超级总成模式的显示容量 = 并行 × 跨配方线程，与 {@link #threads} 同源。
     * 引擎实际分配仍用 {@link #budget}；普通模式应交给上游显示。
     */
    public static long displayCapacity(WorkableElectricMultiblockMachine machine) {
        return (long)parallel(machine) * Math.max(1, threads(machine));
    }
    public static boolean batching(WorkableElectricMultiblockMachine machine) {
        return machine.getRecipeLogic() instanceof org.gtlcore.gtlcore.common.machine.trait.MultipleRecipesLogic
                || machine.getRecipeLogic() instanceof com.gtladd.gtladditions.api.machine.logic.MutableRecipesLogic<?> mutable && mutable.isMultipleRecipeMode();
    }
    public static boolean unmeteredBatch(WorkableElectricMultiblockMachine machine,
                                         com.gregtechceu.gtceu.api.recipe.GTRecipe recipe) {
        // GTLAdd's atomic-factory calculateParallel returns (all available fuel recipes, 0).
        return machine.getDefinition().getId().equals(new ResourceLocation("gtceu", "atomic_energy_excitation_plant"))
                && machine.getRecipeLogic() instanceof com.gtladd.gtladditions.api.machine.logic.MutableRecipesLogic<?> mutable
                && mutable.isMultipleRecipeMode()
                && recipe.recipeType == org.gtlcore.gtlcore.common.data.GTLRecipeTypes.FUEL_REFINING_RECIPES;
    }
}
