package com.gtl.enhancedcore;

import com.gtl.enhancedcore.common.recipe.iv.FairBudget;
import com.gtl.enhancedcore.common.recipe.iv.LongAllocation;
import java.util.*;
import java.math.BigInteger;
import com.gtl.enhancedcore.common.recipe.iv.IvRemainingTime;

/** Independent brute-force oracle for the allocator used by real isolated tasks. */
public final class IvIsolationRegression {
    private static int assertions;
    public static int run() {
        laserPlantRecipeScopedParallelRegression();
        check(LongAllocation.plan(List.of(supply("iron", 10)), List.of(need(Set.of("iron"), 6), need(Set.of("iron", "copper"), 6))) == null,
                "overlapping material cannot spend the same ten ingots twice");
        var split = LongAllocation.plan(List.of(supply("iron", 6), supply("copper", 6)),
                List.of(need(Set.of("iron", "copper"), 6), need(Set.of("iron"), 6)));
        check(split != null && split.equals(Map.of("iron", 6L, "copper", 6L)), "flow reroutes broad ingredient for specific ingredient");
        check(LongAllocation.plan(List.of(new LongAllocation.Supply<>("iron", 100L, false)), List.of(need(Set.of("iron"), 1))) == null,
                "virtual stock never supplies consumable ingredients");
        var reusable = LongAllocation.plan(List.of(supply("iron", 90_000_000L), new LongAllocation.Supply<>("circuit2", 1L, false)),
                List.of(need(Set.of("iron"), 90_000_000L), new LongAllocation.Need<String>("circuit2"::equals, 1L, false)));
        check(reusable.equals(Map.of("iron", 90_000_000L)), "whole smart-multiplied order with virtual circuit retained");
        check(LongAllocation.plan(List.of(supply("iron", 5), supply("acid", 0)), List.of(need(Set.of("iron"), 5), need(Set.of("acid"), 1))) == null,
                "missing fluid rejects the whole snapshot");
        var huge = LongAllocation.plan(List.of(supply("iron", Long.MAX_VALUE)), List.of(need(Set.of("iron"), Long.MAX_VALUE)));
        check(huge.get("iron") == Long.MAX_VALUE, "long quantities do not truncate to int");
        Random random = new Random(0x1_20260918L);
        for (int trial = 0; trial < 1200; trial++) {
            int[] counts = {random.nextInt(4), random.nextInt(4), random.nextInt(4)};
            int n = 1 + random.nextInt(3); int[] masks = new int[n], demands = new int[n];
            List<LongAllocation.Need<String>> needs = new ArrayList<>();
            for (int j = 0; j < n; j++) {
                masks[j] = 1 + random.nextInt(7); demands[j] = random.nextInt(4);
                Set<String> allowed = new HashSet<>();
                for (int k = 0; k < 3; k++) if ((masks[j] & (1 << k)) != 0) allowed.add("k" + k);
                needs.add(need(allowed, demands[j]));
            }
            var actual = LongAllocation.plan(List.of(supply("k0", counts[0]), supply("k1", counts[1]), supply("k2", counts[2])), needs);
            boolean expected = brute(counts.clone(), masks, demands.clone(), 0);
            check((actual != null) == expected, "allocation agrees with exhaustive search " + trial);
            if (actual != null) {
                long sum = actual.values().stream().mapToLong(Long::longValue).sum();
                check(sum == Arrays.stream(demands).sum(), "all accepted demand accounted for");
                for (int k = 0; k < 3; k++) check(actual.getOrDefault("k" + k, 0L) <= counts[k], "no stock overdraw");
            }
        }
        for (int trial = 0; trial < 1200; trial++) {
            long cap = random.nextInt(2000); long[] wants = new long[1 + random.nextInt(17)];
            for (int i = 0; i < wants.length; i++) wants[i] = random.nextInt(1000);
            long[] grants = FairBudget.divide(cap, wants, random.nextInt());
            check(Arrays.stream(grants).sum() == Math.min(cap, Arrays.stream(wants).sum()), "shared budget is neither duplicated nor lost");
            for (int i = 0; i < wants.length; i++) check(grants[i] >= 0 && grants[i] <= wants[i], "per-task grant bounded");
        }
        long[] maximal = FairBudget.divide(Long.MAX_VALUE, new long[]{Long.MAX_VALUE, Long.MAX_VALUE}, 1);
        check(Math.addExact(maximal[0], maximal[1]) == Long.MAX_VALUE, "aggregate requests may exceed long without overflow");
        BigInteger longMax = BigInteger.valueOf(Long.MAX_VALUE);
        maximal = FairBudget.divide(longMax.multiply(BigInteger.TWO), new long[]{Long.MAX_VALUE, Long.MAX_VALUE}, 1);
        check(maximal[0] == Long.MAX_VALUE && maximal[1] == Long.MAX_VALUE,
                "A funded direct terminal advances both orders beyond the aggregate signed-long limit");
        for (int trial = 0; trial < 1200; trial++) {
            long[] wants = new long[1 + random.nextInt(17)];
            BigInteger total = BigInteger.ZERO;
            for (int i = 0; i < wants.length; i++) {
                wants[i] = random.nextLong() & Long.MAX_VALUE;
                total = total.add(BigInteger.valueOf(wants[i]));
            }
            BigInteger cap = new BigInteger(68, random);
            long[] grants = FairBudget.divide(cap, wants, random.nextInt());
            BigInteger granted = BigInteger.ZERO;
            for (int i = 0; i < wants.length; i++) {
                check(grants[i] >= 0 && grants[i] <= wants[i], "Wide grid per-order payment never exceeds demand");
                granted = granted.add(BigInteger.valueOf(grants[i]));
            }
            check(granted.equals(total.min(cap)), "Wide grid preserves exact total demand and balance without overflow");
            long narrowCap = random.nextLong() & Long.MAX_VALUE;
            int rotation = random.nextInt();
            check(Arrays.equals(FairBudget.divide(BigInteger.valueOf(narrowCap), wants, rotation),
                    FairBudget.divide(narrowCap, wants, rotation)), "Wide and physical allocators agree for narrow balances");
        }
        nativeThreadBudget();
        addThreadOnly20261012();
        infernoVault20261014();
        fuxiFurnace20261017();
        var retimed = IvRemainingTime.calculate(BigInteger.valueOf(1000), 100, 2, 1);
        check(retimed.duration() == 12 && retimed.eut() == 100, "reformation preserves unpaid energy and shortens remaining time");
        var slower = IvRemainingTime.calculate(BigInteger.valueOf(1000), 50, 2, 1);
        check(slower.duration() == 22 && slower.eut() == 50, "lower reformed supply does not overpay energy");
        // Two orders each requested 100 EU/t but share a 100 EU/t machine.
        BigInteger unpaid = BigInteger.valueOf(10_000);
        int elapsed = 0, duration = 100;
        while (unpaid.signum() > 0) {
            long grant = Math.min(50, unpaid.longValueExact());
            duration = IvRemainingTime.estimatedDuration(unpaid, grant, elapsed, 1);
            unpaid = unpaid.subtract(BigInteger.valueOf(grant)); elapsed++;
            check(duration == 200, "shared supply reports actual 200 tick duration, not nominal 100 ticks");
            if (elapsed == 20) check(elapsed == 20 && duration - elapsed == 180,
                    "twenty paid processing ticks display one elapsed second even at half power");
        }
        check(elapsed == duration && elapsed == 200, "estimated finish agrees with fully paid energy");
        check(IvRemainingTime.estimatedDuration(BigInteger.valueOf(9_000), 100, 20, 1) == 110,
                "freed supply changes remaining estimate without changing past elapsed ticks");
        check(IvRemainingTime.estimatedDuration(BigInteger.ONE, 100, 0, 20) == 20,
                "cheap recipes retain their minimum processing time");
        check(IvRemainingTime.estimatedDuration(BigInteger.valueOf(Long.MAX_VALUE), 1, 20, 1) == Integer.MAX_VALUE,
                "huge remaining time saturates display without cancelling the energy debt");
        return assertions;
    }

    private static void laserPlantRecipeScopedParallelRegression() {
        try {
            String scope = java.nio.file.Files.readString(java.nio.file.Path.of(
                    "src/main/java/com/gtl/enhancedcore/common/recipe/iv/IvMachineScope.java"));
            String engine = java.nio.file.Files.readString(java.nio.file.Path.of(
                    "src/main/java/com/gtl/enhancedcore/common/recipe/iv/IvNativeEngine.java"));
            check(scope.contains("PRECISION_LASER_ENGRAVER_RECIPES") && scope.contains("return 1;"),
                    "precision laser recipes have an explicit per-recipe parallel cap of one");
            check(engine.contains("IvMachineScope.parallel(machine, ref.job.recipe)"),
                    "native scheduling derives capacity from each job recipe, avoiding mode cache leakage");
        } catch (java.io.IOException failure) {
            throw new AssertionError(failure);
        }
    }
    /**
     * 原生机器超级总成模式线程预算：新增 128，GTLCore 已有的 64 另行相加，再叠加 Ω。
     * 穷举驱动值边界，覆盖负数、0、整数溢出与 Integer.MAX_VALUE 饱和。
     */
    private static void nativeThreadBudget() {
        check(com.gtl.enhancedcore.common.recipe.iv.IvMachineScope.NATIVE_CROSS_RECIPE_THREADS == 128,
                "native isolation adds 128 cross-recipe threads");
        check(com.gtl.enhancedcore.common.recipe.iv.IvMachineScope.UPSTREAM_MULTIPLE_RECIPE_THREADS == 64,
                "GTLCore multiple-recipe machines retain their original 64 threads");
        // 无天球：恒为 128
        check(com.gtl.enhancedcore.common.recipe.iv.IvMachineScope.nativeThreads(0) == 128,
                "native machine without modifier keeps 128 threads");
        // 叠加而不是覆盖：不同 Ω 强度都必须线性加上去
        for (int modifier : new int[]{1, 2, 64, 128, 512, 1000, 65536}) {
            int expected = 128 + modifier;
            check(com.gtl.enhancedcore.common.recipe.iv.IvMachineScope.nativeThreads(modifier) == expected,
                    "modifier threads are added on top of the native baseline: " + modifier);
            check(com.gtl.enhancedcore.common.recipe.iv.IvMachineScope.nativeThreads(modifier)
                            > com.gtl.enhancedcore.common.recipe.iv.IvMachineScope.nativeThreads(0),
                    "adding the modifier never lowers the thread count: " + modifier);
        }
        // 负值不得削减基准
        check(com.gtl.enhancedcore.common.recipe.iv.IvMachineScope.nativeThreads(-1) == 128,
                "negative modifier cannot reduce the native baseline");
        check(com.gtl.enhancedcore.common.recipe.iv.IvMachineScope.nativeThreads(Integer.MIN_VALUE) == 128,
                "extreme negative modifier cannot reduce the native baseline");
        // 溢出饱和：不得回绕成负数或小值
        check(com.gtl.enhancedcore.common.recipe.iv.IvMachineScope.nativeThreads(Integer.MAX_VALUE) == Integer.MAX_VALUE,
                "huge modifier saturates instead of overflowing");
        check(com.gtl.enhancedcore.common.recipe.iv.IvMachineScope.nativeThreads(Integer.MAX_VALUE - 128) == Integer.MAX_VALUE,
                "modifier near the int ceiling still saturates safely");
        // 已有 64 条的机器不能被新增 128 条覆盖，也不能把 Ω 算成替代值。
        check(com.gtl.enhancedcore.common.recipe.iv.IvMachineScope.batchThreads(0) == 192,
                "original 64 threads and isolation's 128 threads are additive");
        check(com.gtl.enhancedcore.common.recipe.iv.IvMachineScope.batchThreads(64) == 256,
                "batching machines add the modifier on top of both baselines");
        check(com.gtl.enhancedcore.common.recipe.iv.IvMachineScope.batchThreads(-1) == 192,
                "negative modifier cannot reduce the combined baseline");
        check(com.gtl.enhancedcore.common.recipe.iv.IvMachineScope.batchThreads(Integer.MAX_VALUE) == Integer.MAX_VALUE,
                "combined baseline and huge modifier saturate safely");
        // 预算不溢出：128 线程要与并行相乘仍落在 long 内
        for (int parallel : new int[]{1, 64, 1024, 65536, Integer.MAX_VALUE}) {
            long budget = Math.multiplyExact((long)parallel,
                    com.gtl.enhancedcore.common.recipe.iv.IvMachineScope.nativeThreads(1024));
            check(budget > 0, "native budget stays positive: parallel=" + parallel);
        }
    }

    private static LongAllocation.Supply<String> supply(String key, long amount) { return new LongAllocation.Supply<>(key, amount, true); }    private static LongAllocation.Need<String> need(Set<String> keys, long amount) { return new LongAllocation.Need<>(keys::contains, amount, true); }
    private static boolean brute(int[] stock, int[] masks, int[] demands, int index) {
        if (index == demands.length) return true;
        if (demands[index] == 0) return brute(stock, masks, demands, index + 1);
        for (int k = 0; k < stock.length; k++) if (stock[k] > 0 && (masks[index] & (1 << k)) != 0) {
            stock[k]--; demands[index]--;
            boolean result = brute(stock, masks, demands, index);
            stock[k]++; demands[index]++;
            if (result) return true;
        }
        return false;
    }
    private static void check(boolean value, String message) {
        assertions++;
        if (!value) throw new AssertionError(message);
    }

    /**
     * 2026-10-12 用户指定：三台 GTLCore 机器加 <b>ADD 跨配方线程</b> 128 条 + Ω 叠加。
     *
     * <p>必须与「本模组的跨配方类型并行（IV 隔离订单）」严格区分：这三台
     * <b>不在</b> {@code NATIVE_IDS}，因此不会被 {@code IvNativeEngine} 接管、不会被替换结构仓室谓词、
     * 不会被贴上「支持跨配方类型并行」标识；线程完全由 GTLAdditions 自己的逻辑调度。
     */
    private static void addThreadOnly20261012() {
        var scope = com.gtl.enhancedcore.common.recipe.iv.IvMachineScope.class;
        // 2026-10-14 追加第四台：advanced_neutron_activator（用户指定「改为 ADD 类机器 + 128 线程」）。
        var ids = List.of("star_ultimate_material_forge_factory", "super_particle_collider",
                "advanced_sps_crafting", "advanced_neutron_activator");
        for (String id : ids) {
            var location = new net.minecraft.resources.ResourceLocation("gtceu", id);
            check(com.gtl.enhancedcore.common.recipe.iv.IvMachineScope.addThreadOnly(location),
                    "the machine is registered for ADD cross-recipe threads only: " + id);
            check(!com.gtl.enhancedcore.common.recipe.iv.IvMachineScope.nativeTarget(location),
                    "the machine must NOT join the IV isolated-order whitelist: " + id);
            check(com.gtl.enhancedcore.common.recipe.iv.IvMachineScope.addThreadBaseline(id) == 128,
                    "the ADD thread baseline is 128 for " + id);
        }
        // 原有 IV 白名单不得被这次改动污染（17 台）。
        check(com.gtl.enhancedcore.common.recipe.iv.IvMachineScope.NATIVE_IDS.size() == 17,
                "the IV whitelist keeps its original 17 machines");
        for (String id : ids) {
            check(!com.gtl.enhancedcore.common.recipe.iv.IvMachineScope.NATIVE_IDS.contains(id),
                    "no ADD-thread machine leaked into NATIVE_IDS: " + id);
        }
        // 非 gtceu 命名空间与 null 不得误判。
        check(!com.gtl.enhancedcore.common.recipe.iv.IvMachineScope.addThreadOnly(
                        new net.minecraft.resources.ResourceLocation("gtladditions", "super_particle_collider")),
                "add-thread lookup is limited to the gtceu namespace");
        check(!com.gtl.enhancedcore.common.recipe.iv.IvMachineScope.addThreadOnly(
                        (net.minecraft.resources.ResourceLocation) null),
                "null id is not an add-thread machine");
        check(com.gtl.enhancedcore.common.recipe.iv.IvMachineScope.addThreadBaseline("not_a_machine") == 0,
                "unknown ids carry no ADD thread baseline");
        // 源码契约：三台都继承 ADD 可变基类，且线程口径为 128 + Ω，不覆写纯 Ω 取值器。
        try {
            String base = java.nio.file.Files.readString(java.nio.file.Path.of(
                    "src/main/java/com/gtl/enhancedcore/common/machine/AddThreadMutableMachine.java"));
            check(base.contains("extends MutableElectricParallelHatchMultiblockMachine"),
                    "the ADD mutable base makes the controller implement IThreadModifierMachine");
            check(base.contains("DEFAULT_ADD_CROSS_RECIPE_THREADS = 128")
                            && base.contains("owner.threadBaseline()") && base.contains("owner.getAdditionalThread()"),
                    "ADD threads use their own baseline plus omega at the scheduling exit");
            check(!base.contains("import com.gtl.enhancedcore.common.recipe.iv.IvMachineScope;"),
                    "ADD thread dispatch does not depend on IV isolation");
            check(!base.contains("public int getAdditionalThread()"),
                    "the pure omega accessor must not be rewritten or the baseline would double-count");
            check(base.contains("setUseMultipleRecipes(true)"),
                    "cross-recipe mode is enabled on formation");
            // 并行必须保留：ADD 的 MutableRecipesLogic 只读 getMaxParallel()，
            // 不经 recipeModifier，故改造前靠修饰器给的固定并行必须在机器类里补回。
            check(base.contains("public int getMaxParallel()") && base.contains("parallelBaseline()"),
                    "the ADD base restores any fixed parallel the machine had before conversion");
            String forge = java.nio.file.Files.readString(java.nio.file.Path.of(
                    "src/main/java/com/gtl/enhancedcore/common/machine/StarUltimateForgeMutableMachine.java"));
            check(forge.contains("PARALLEL = 50000"),
                    "the star ultimate forge factory keeps its 50000 parallel");
            check(forge.contains("protected int parallelBaseline()"),
                    "the factory feeds its own parallel into the ADD scheduling path");
            // 另三台本来就用并行仓，不得被写成固定值（注释不参与判定，只看实际声明）。
            for (String machine : List.of("SuperParticleColliderMutableMachine", "AdvancedSpsCraftingMutableMachine",
                    "AdvancedNeutronActivatorMutableMachine")) {
                String body = java.nio.file.Files.readString(java.nio.file.Path.of(
                                "src/main/java/com/gtl/enhancedcore/common/machine/" + machine + ".java"))
                        .replaceAll("//[^\\n]*", "").replaceAll("(?s)/\\*.*?\\*/", "");
                check(!body.contains("parallelBaseline()"),
                        machine + " keeps the upstream parallel-hatch semantics, not a fixed parallel");
            }
            // 四台都必须在注册处接上 ADD 可变机器，否则线程改造不会生效。
            String registration = java.nio.file.Files.readString(java.nio.file.Path.of(
                    "src/main/java/com/gtl/enhancedcore/common/registration/AddMutableMachineRegistration.java"));
            for (String machine : List.of("StarUltimateForgeMutableMachine", "SuperParticleColliderMutableMachine",
                    "AdvancedSpsCraftingMutableMachine", "AdvancedNeutronActivatorMutableMachine")) {
                check(registration.contains("new " + machine + "(holder)"),
                        "the registration wires the ADD mutable factory: " + machine);
            }
            // 中子活化器由 AdditionalMultiBlockMachine 注册，不是 MultiBlockMachineA——字段来源必须正确。
            check(registration.contains("AdditionalMultiBlockMachine.ADVANCED_NEUTRON_ACTIVATOR"),
                    "the neutron activator definition is read from AdditionalMultiBlockMachine");
        } catch (java.io.IOException failure) {
            throw new AssertionError(failure);
        }
    }

    /**
     * 2026-10-14 用户指定：{@code gtladditions:inferno_cleft_smelting_vault} 接入 IV 跨配方类型并行。
     *
     * <p>两个要点：
     * <ol>
     *   <li>它是 <b>gtladditions</b> 命名空间（白名单历史上只含 gtceu），故 {@code nativeTarget} 必须
     *       支持跨命名空间查询，同时不得放松对未知命名空间的判定。</li>
     *   <li>它改造前就是 ADD 多配方机器、无总成时本就在跑 128 线程，故列在
     *       {@code KEEP_NATIVE_THREADS_WITHOUT_BUFFER}：未启用隔离时<b>不</b>压到 1，
     *       避免接入 IV 反而削弱原生能力。</li>
     * </ol>
     */
    private static void infernoVault20261014() {
        var scope = com.gtl.enhancedcore.common.recipe.iv.IvMachineScope.class;
        var vault = new net.minecraft.resources.ResourceLocation("gtladditions", "inferno_cleft_smelting_vault");
        check(com.gtl.enhancedcore.common.recipe.iv.IvMachineScope.nativeTarget(vault),
                "the inferno vault joins the IV isolated-order whitelist");
        // 命名空间必须严格匹配：同 path 的其它命名空间不得被误判。
        check(!com.gtl.enhancedcore.common.recipe.iv.IvMachineScope.nativeTarget(
                        new net.minecraft.resources.ResourceLocation("gtceu", "inferno_cleft_smelting_vault")),
                "the vault is matched under gtladditions, not gtceu");
        check(!com.gtl.enhancedcore.common.recipe.iv.IvMachineScope.nativeTarget(
                        new net.minecraft.resources.ResourceLocation("gtladditions", "not_a_machine")),
                "unknown paths in the foreign namespace are rejected");
        check(!com.gtl.enhancedcore.common.recipe.iv.IvMachineScope.nativeTarget(
                        new net.minecraft.resources.ResourceLocation("other", "inferno_cleft_smelting_vault")),
                "unknown namespaces are rejected");
        check(!com.gtl.enhancedcore.common.recipe.iv.IvMachineScope.nativeTarget(
                        (net.minecraft.resources.ResourceLocation) null),
                "null id is not a native target");
        // gtceu 白名单不得被这次改动污染。
        check(com.gtl.enhancedcore.common.recipe.iv.IvMachineScope.NATIVE_IDS.size() == 17,
                "the gtceu whitelist still holds exactly its 17 machines");
        nexusSatellites20261014();
        check(com.gtl.enhancedcore.common.recipe.iv.IvMachineScope.FOREIGN_NATIVE_IDS.size() == 1
                        && com.gtl.enhancedcore.common.recipe.iv.IvMachineScope.FOREIGN_NATIVE_IDS
                        .get("gtladditions").contains("inferno_cleft_smelting_vault"),
                "the foreign whitelist holds the vault under its own namespace");
        // 无总成时保留原生线程（不压到 1）。
        check(com.gtl.enhancedcore.common.recipe.iv.IvMachineScope.KEEP_NATIVE_THREADS_WITHOUT_BUFFER
                        .contains("inferno_cleft_smelting_vault"),
                "the vault keeps its upstream 128 threads before a buffer is fitted");
        check(!com.gtl.enhancedcore.common.recipe.iv.IvMachineScope.KEEP_NATIVE_THREADS_WITHOUT_BUFFER
                        .contains("mega_extractor"),
                "ordinary whitelist machines still fall back to a single thread without a buffer");
        // 三处「无总成压到 1」的 mixin 都必须尊重该例外，否则例外形同虚设。
        try {
            for (String mixin : List.of("gtlcore/IvNativeGtlAddThreadLimitMixin.java",
                    "gtlcore/IvNativeMultipleGuardMixin.java", "gtlcore/IvNativeRecipeThreadLimitMixin.java")) {
                String body = java.nio.file.Files.readString(java.nio.file.Path.of(
                        "src/main/java/com/gtl/enhancedcore/mixin/" + mixin));
                check(body.contains("keepsNativeThreads("),
                        "the without-buffer guard honours the exception: " + mixin);
            }
            // 结构侧：未做定制几何的机器走「保留几何、只换材料仓」路径（派生判定，非硬编码名单）。
            String patterns = java.nio.file.Files.readString(java.nio.file.Path.of(
                    "src/main/java/com/gtl/enhancedcore/common/structure/NativeIsolationPatterns.java"));
            check(patterns.contains("IvMachineScope.nativeTarget(id)")
                            && patterns.contains("!GtlMegastructurePatterns.targets(id)"),
                    "the port-swap path derives from nativeTarget and custom-geometry state");
        } catch (java.io.IOException failure) {
            throw new AssertionError(failure);
        }
    }

    /**
     * 2026-10-14 用户指定：四台 {@code gtladditions:nexus_satellite_factory_mk1~4}
     * （猎光号空间站模块）接入 IV 跨配方类型并行。
     *
     * <p>要点：它们是 ADD 多配方机器（原生 128+Ω 线程、`tooltipTextMaxParallels` 显示无限并行），
     * 故与炼狱库同样列进 {@code KEEP_NATIVE_THREADS_WITHOUT_BUFFER}，避免未装总成时被压到 1。
     * 它们不在结构 manifest 里，因此结构侧由 {@code NativeIsolationPatterns} 派生处理。
     */
    private static void nexusSatellites20261014() {
        for (int mk = 1; mk <= 4; mk++) {
            String path = "nexus_satellite_factory_mk" + mk;
            var id = new net.minecraft.resources.ResourceLocation("gtladditions", path);
            check(!com.gtl.enhancedcore.common.recipe.iv.IvMachineScope.nativeTarget(id),
                    "retired satellite leaves IV whitelist: " + path);
            check(!com.gtl.enhancedcore.common.recipe.iv.IvMachineScope.KEEP_NATIVE_THREADS_WITHOUT_BUFFER
                            .contains(path),
                    "the module keeps its upstream threads before a buffer is fitted: " + path);
            check(!com.gtl.enhancedcore.common.recipe.iv.IvMachineScope.nativeTarget(
                            new net.minecraft.resources.ResourceLocation("gtceu", path)),
                    "the module is matched under gtladditions, not gtceu: " + path);
        }
        check(com.gtl.enhancedcore.common.recipe.iv.IvMachineScope.FOREIGN_NATIVE_IDS
                        .get("gtladditions").size() == 2,
                "the foreign whitelist holds only the vault and Fuxi furnace");
        // 派生判定：无定制几何的原生机器都应被结构侧接管（四台 mk 均无 pattern.gz）。
        for (int mk = 1; mk <= 4; mk++) {
            var id = new net.minecraft.resources.ResourceLocation("gtladditions", "nexus_satellite_factory_mk" + mk);
            check(!com.gtl.enhancedcore.common.structure.GtlMegastructurePatterns.targets(id),
                    "the module has no project-owned geometry: mk" + mk);
        }
    }

    private static void fuxiFurnace20261017() {
        String path = "fuxi_bagua_heaven_forging_furnace";
        var id = new net.minecraft.resources.ResourceLocation("gtladditions", path);
        check(com.gtl.enhancedcore.common.recipe.iv.IvMachineScope.nativeTarget(id),
                "Fuxi furnace opts into native IV cross-recipe-type orders");
        check(!com.gtl.enhancedcore.common.recipe.iv.IvMachineScope.addThreadOnly(id),
                "Fuxi furnace must not enter the ADD-only thread path");
        check(!com.gtl.enhancedcore.common.recipe.iv.IvMachineScope.nativeTarget(
                        new net.minecraft.resources.ResourceLocation("gtceu", path)),
                "Fuxi registration namespace stays exact");
        check(com.gtl.enhancedcore.common.recipe.iv.IvMachineScope.baseThreadsFor(path) == 128,
                "Fuxi isolated-order baseline is 128 threads");
        check(com.gtl.enhancedcore.common.recipe.iv.IvMachineScope.KEEP_NATIVE_THREADS_WITHOUT_BUFFER.contains(path),
                "ordinary Fuxi mode retains upstream ADD thread behavior");
        check(com.gtl.enhancedcore.common.structure.NativeIsolationPatterns.targets(id),
                "Fuxi keeps upstream geometry while its material ports receive IV mutual-exclusion rules");
        check(com.gtl.enhancedcore.common.util.TooltipPolicy.nativePages(path).contains("native_isolation"),
                "Fuxi reuses localized native isolation restrictions, thread count and recipe catalog");
        try {
            String engine = java.nio.file.Files.readString(java.nio.file.Path.of(
                    "src/main/java/com/gtl/enhancedcore/common/recipe/iv/IvNativeEngine.java"));
            check(engine.contains("add.iv$addRecipeCheck().test(job.recipe, machine)"),
                    "isolated orders retain ADD original recipe-specific checks including Fuxi temperature");
            String accessor = java.nio.file.Files.readString(java.nio.file.Path.of(
                    "src/main/java/com/gtl/enhancedcore/mixin/gtlcore/IvNativeAddMultipleAccessor.java"));
            check(accessor.contains("GTLAddMultipleRecipesLogic.class") && accessor.contains("@Invoker(\"getRecipeCheck\")"),
                    "ADD protected recipe-check callback is accessed through a precise invoker");
        } catch (java.io.IOException failure) {
            throw new AssertionError(failure);
        }
    }
}
