package com.gtl.enhancedcore.common.recipe.iv;

import com.gregtechceu.gtceu.api.machine.multiblock.CoilWorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gtl.enhancedcore.common.machine.TieredParallelMachine;
import com.gtladd.gtladditions.api.machine.IThreadModifierMachine;
import java.util.Map;
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
            "atomic_energy_excitation_plant", "gravitation_shockburst", "huge_incubator",
            "dimensionally_transcendent_mixer");

    /**
     * 非 {@code gtceu} 命名空间的原生隔离目标（用户 2026-10-14 指定）。
     *
     * <p>白名单历史上只含 {@code gtceu:} 机器（GTLCore 注册），故 {@link #nativeTarget} 原先硬编码
     * 命名空间判断。{@code gtladditions} 自注册的机器需要独立登记命名空间。分开存放而非混进
     * {@link #NATIVE_IDS}，是为了让「gtceu 白名单」保持可读、可穷举，也便于回归分别断言。
     */
    public static final Map<String, Set<String>> FOREIGN_NATIVE_IDS = Map.of(
            "gtladditions", Set.of(
                    "inferno_cleft_smelting_vault",
                    "fuxi_bagua_heaven_forging_furnace"));

    /**
     * 未装超级样板总成时**不**把跨配方线程压到 1 的白名单机器（用户 2026-10-14 指定）。
     *
     * <p>默认规则：白名单机器在未启用隔离（无超级总成）时退回单配方语义，线程 = 1
     * （见 {@code IvNativeGtlAddThreadLimitMixin}）。但下列机器改造前就是 ADD 多配方机器、
     * 无总成时本就在跑 128 条线程；若一并压到 1，接入 IV 反而**削弱**了它们原有的能力。
     * 故保留原生线程，装了总成再切到隔离口径。
     */
    public static final Set<String> KEEP_NATIVE_THREADS_WITHOUT_BUFFER = Set.of(
            "inferno_cleft_smelting_vault",
            "fuxi_bagua_heaven_forging_furnace");

    /** 未启用隔离时是否保留上游原生线程（而不是压到 1）。 */
    public static boolean keepsNativeThreads(WorkableElectricMultiblockMachine machine) {
        var id = machine == null ? null : machine.getDefinition().getId();
        return id != null && KEEP_NATIVE_THREADS_WITHOUT_BUFFER.contains(id.getPath());
    }

    private IvMachineScope() {}

    public static boolean nativeTarget(ResourceLocation id) {
        if (id == null) return false;
        String path = id.getPath();
        if (id.getNamespace().equals("gtceu")) return NATIVE_IDS.contains(path);
        Set<String> foreign = FOREIGN_NATIVE_IDS.get(id.getNamespace());
        return foreign != null && foreign.contains(path);
    }

    /**
     * 只需 ADD 跨配方线程、**不**接入本模组 IV 隔离订单的机器（用户 2026-10-12 指定）。
     *
     * <p>与 {@link #NATIVE_IDS} 严格区分：
     * <ul>
     *   <li>{@code NATIVE_IDS} = 我们的<b>跨配方类型并行</b>（IV 隔离订单、超级总成、自研引擎、
     *       结构仓室替换）。</li>
     *   <li>本集合 = 只用 ADD 自己的<b>跨配方线程</b>；不加进 NATIVE_IDS，
     *       因此不会被 {@code IvNativeEngine} 接管，也不会被 {@code GtlMegastructurePatterns}
     *       替换结构仓室谓词、不会被贴上「支持跨配方类型并行」标识。</li>
     * </ul>
     *
     * <p>线程数由各机器的 ADD 逻辑提供（见 {@code AddMutableMachineRegistration} 与
     * {@code IvAddThreadBaselineMixin}）：128 基准 + 天球分歧引擎（Ω）叠加。
     */
    public static final Set<String> ADD_THREAD_ONLY_IDS = Set.of(
            "star_ultimate_material_forge_factory", "super_particle_collider", "advanced_sps_crafting",
            "advanced_neutron_activator");

    /** 是否只需 ADD 跨配方线程（不进 IV 隔离）。 */
    public static boolean addThreadOnly(ResourceLocation id) {
        return id != null && id.getNamespace().equals("gtceu") && ADD_THREAD_ONLY_IDS.contains(id.getPath());
    }

    public static boolean addThreadOnly(Object machine) {
        return machine instanceof WorkableElectricMultiblockMachine electric
                && addThreadOnly(electric.getDefinition().getId());
    }

    /** 128 条 ADD 跨配方线程基准（不含 Ω，供定义级显示使用）。 */
    public static int addThreadBaseline(String path) {
        return ADD_THREAD_ONLY_IDS.contains(path) ? NATIVE_CROSS_RECIPE_THREADS : 0;
    }
    public static boolean nativeTarget(Object machine) {
        return machine instanceof WorkableElectricMultiblockMachine electric
                && (com.gtl.enhancedcore.common.config.MachineOverrideConfig.current().enhanceOriginalMachines()
                && nativeTarget(electric.getDefinition().getId()));
    }
    public static boolean crossRecipeEnabled(WorkableElectricMultiblockMachine machine) {
        return machine != null && IvBuffers.targetController(machine)
                && IvBuffers.collect(machine).stream().anyMatch(IvBuffers::isolated);
    }
    public static boolean crossRecipeEnabled(Object machine) {
        return machine instanceof WorkableElectricMultiblockMachine electric && crossRecipeEnabled(electric);
    }
    public static int parallel(WorkableElectricMultiblockMachine machine) {
        if (unlimited(machine)) return Integer.MAX_VALUE;
        if (machine instanceof ParallelMachine parallel) return Math.max(1, parallel.getMaxParallel());
        if (IvBuffers.genericCloudTarget(machine)) return Math.max(1, GTLRecipeModifiers.getHatchParallel(machine));
        if (machine instanceof CoilWorkableElectricMultiblockMachine coil)
            return Math.max(1, (int)Math.min(Integer.MAX_VALUE, Math.pow(2, coil.getCoilType().getCoilTemperature() / 900.0)));
        return Math.max(1, GTLRecipeModifiers.getHatchParallel(machine));
    }

    /**
     * Per recipe parallel admission.  The engraving laser plant exposes ordinary and
     * precision recipe maps on one controller; precision recipes are deliberately single
     * operation while ordinary laser recipes retain the installed hatch capacity.
     */
    public static long parallel(WorkableElectricMultiblockMachine machine, GTRecipe recipe) {
        long capacity = parallel(machine);
        if (recipe != null && recipe.recipeType == org.gtlcore.gtlcore.common.data.GTLRecipeTypes.PRECISION_LASER_ENGRAVER_RECIPES)
            return 1;
        return capacity;
    }
    /**
     * 超级样板总成隔离模式的跨配方线程上限（引擎/GUI/Jade/物品 tips 的共同来源）。
     * 普通输入/输出仓室模式应显示上游原模式，不应把这里新增的 128 条算进普通模式。
     */
    public static int threads(WorkableElectricMultiblockMachine machine) {
        if (machine == null) return 1;
        if (unlimited(machine)) return Integer.MAX_VALUE;
        if (IvBuffers.genericCloudTarget(machine)) {
            // A cloud endpoint is storage/routing, not a grant of the opt-in IV +128 threads.
            var logic = machine.getRecipeLogic();
            if (logic instanceof com.gtladd.gtladditions.api.machine.logic.MutableRecipesLogic<?> mutable && mutable.isMultipleRecipeMode())
                return Math.max(1, mutable.getMultipleThreads());
            if (logic instanceof com.gtladd.gtladditions.api.machine.logic.GTLAddMultipleRecipesLogic multiple)
                return Math.max(1, multiple.getMultipleThreads());
            if (logic instanceof org.gtlcore.gtlcore.common.machine.trait.MultipleRecipesLogic)
                return upstreamBatchThreads(modifierThreads(machine));
            return 1;
        }
        if (machine instanceof TieredParallelMachine iv) return grantGalaxyThreads(machine, Math.max(1, iv.getThreadsForTier()));
        int modifier = modifierThreads(machine);
        if (nativeTarget(machine)) {
            // 按机器 ID 判定原有 64 线程，避免 ADD mutable 模式/部件状态改变提示口径。
            var id = machine.getDefinition().getId();
            boolean batch = id != null && BATCH_IDS.contains(id.getPath());
            return grantGalaxyThreads(machine, batch ? batchThreads(modifier) : nativeThreads(modifier));
        }
        if (machine.getRecipeLogic() instanceof com.gtladd.gtladditions.api.machine.logic.MutableRecipesLogic<?> mutable && mutable.isMultipleRecipeMode())
            return Math.max(1, mutable.getMultipleThreads());
        if (machine.getRecipeLogic() instanceof com.gtladd.gtladditions.api.machine.logic.GTLAddMultipleRecipesLogic gtlAdd)
            return Math.max(1, gtlAdd.getMultipleThreads());
        return grantGalaxyThreads(machine, batching(machine) ? upstreamBatchThreads(modifier) : nativeThreads(modifier));
    }
    private static int grantGalaxyThreads(WorkableElectricMultiblockMachine machine, int base) {
        return com.gtl.enhancedcore.common.machine.galaxy.GalaxyLinks.threads(machine, base);
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
        return (int)Math.max(1, Math.min(Integer.MAX_VALUE,
                (long) NATIVE_CROSS_RECIPE_THREADS + Math.max(0, modifierThreads)));
    }

    /** 原有 64 线程机器的隔离线程 = 原有 64 + 新增 128 + Ω。 */
    public static int batchThreads(int modifierThreads) {
        return (int)Math.max(1, Math.min(Integer.MAX_VALUE,
                (long) UPSTREAM_MULTIPLE_RECIPE_THREADS
                        + (long) NATIVE_CROSS_RECIPE_THREADS
                        + Math.max(0, modifierThreads)));
    }

    private static int upstreamBatchThreads(int modifierThreads) {
        return (int)Math.min(Integer.MAX_VALUE,
                (long) UPSTREAM_MULTIPLE_RECIPE_THREADS + Math.max(0, modifierThreads));
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
        if (crossRecipeEnabled(machine) && unlimited(machine)) return Long.MAX_VALUE;
        return Math.multiplyExact((long)parallel(machine), crossRecipeEnabled(machine) ? threads(machine) : 1);
    }

    /**
     * 超级总成模式的显示容量 = 并行 × 跨配方线程，与 {@link #threads} 同源。
     * 引擎实际分配仍用 {@link #budget}；普通模式应交给上游显示。
     */
    public static long displayCapacity(WorkableElectricMultiblockMachine machine) {
        if (unlimited(machine)) return Long.MAX_VALUE;
        return (long)parallel(machine) * Math.max(1, threads(machine));
    }

    /** Effective capacity is granted only by a live, paid galaxy source. */
    public static boolean unlimited(WorkableElectricMultiblockMachine machine) {
        return com.gtl.enhancedcore.common.machine.galaxy.GalaxyLinks.unlimited(machine);
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
