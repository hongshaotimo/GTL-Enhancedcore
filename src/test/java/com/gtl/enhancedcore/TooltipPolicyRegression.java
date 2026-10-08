package com.gtl.enhancedcore;

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.gtl.enhancedcore.common.util.TooltipPolicy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class TooltipPolicyRegression {
    private static int assertions;

    private TooltipPolicyRegression() {}

    public static void main(String[] arguments) throws Exception {
        System.out.println("Tooltip policy passed: " + run() + " assertions (layout behavior and translation contracts)");
    }

    public static int run() throws Exception {
        assertions = 0;
        layoutBehavior();
        requestedFlavorBehavior();
        inheritedBehavior();
        nativeCompositionBehavior();
        qftAddMutableBehavior();
        nativeThreadBudgetBehavior();
        recipeGrouping();
        rejectionBehavior();
        translationContracts();
        return assertions;
    }

    /** qft 走 ADD 可变多配方路线（用户 2026-10-06 指定）：提示与线程常量必须一致，且不套用 IV 隔离页。 */
    private static void qftAddMutableBehavior() throws Exception {
        var qft = TooltipPolicy.page("qft");
        require(qft.summary().contains("tooltip.gtl_enhancedcore.qft.cross_recipe"), "qft declares cross-recipe parallel");
        require(qft.summary().contains("tooltip.gtl_enhancedcore.qft.threads"), "qft declares its cross-recipe threads");
        var pages = TooltipPolicy.nativePages("qft");
        require(pages.size() == 1 && pages.get(0).equals("qft"),
                "qft uses the ADD mutable supplement, not the IV isolation composition");
        require(com.gtl.enhancedcore.common.machine.QftMutableMachine.CROSS_RECIPE_THREADS == 512,
                "qft cross-recipe threads are fixed at 512");
        require(com.gtl.enhancedcore.common.recipe.iv.IvMachineScope.nativeTarget(
                        net.minecraft.resources.ResourceLocation.tryParse("gtceu:gravitation_shockburst")),
                "gravitation_shockburst joined the native isolation scope");
        require(!com.gtl.enhancedcore.common.recipe.iv.IvMachineScope.nativeTarget(
                        net.minecraft.resources.ResourceLocation.tryParse("gtceu:qft")),
                "qft is not an IV isolation target (it uses ADD mutable logic)");
        for (String lang : List.of("zh_cn", "en_us")) {
            var text = Files.readString(Path.of("src/main/resources/assets/gtl_enhancedcore/lang/" + lang + ".json"),
                    StandardCharsets.UTF_8);
            require(text.contains("tooltip.gtl_enhancedcore.qft.cross_recipe"), lang + ": qft cross-recipe key");
            require(text.contains("tooltip.gtl_enhancedcore.qft.threads"), lang + ": qft thread key");
        }
    }

    /**
     * 超级总成模式新增 128 条线程，GTLCore 多配方机器还需加原有 64 条，再叠加 Ω。
     */
    private static void nativeThreadBudgetBehavior() throws Exception {
        var scope = com.gtl.enhancedcore.common.recipe.iv.IvMachineScope.class;
        require(com.gtl.enhancedcore.common.recipe.iv.IvMachineScope.NATIVE_CROSS_RECIPE_THREADS == 128,
                "native isolation adds 128 cross-recipe threads");
        require(com.gtl.enhancedcore.common.recipe.iv.IvMachineScope.UPSTREAM_MULTIPLE_RECIPE_THREADS == 64,
                "GTLCore multiple-recipe machines contribute their original 64 threads");
        require(java.util.Arrays.stream(scope.getDeclaredMethods())
                        .anyMatch(method -> method.getName().equals("modifierThreads")),
                "modifier threads are collected separately so the engine can stack them");
        var source = Files.readString(Path.of(
                "src/main/java/com/gtl/enhancedcore/common/recipe/iv/IvMachineScope.java"), StandardCharsets.UTF_8);
        require(source.contains("nativeThreads(modifier)") && source.contains("batchThreads(modifier)"),
                "native and batching branches add the modifier instead of replacing it");
        require(source.contains("BATCH_IDS.contains(id.getPath())"),
                "the native branch is decided by machine id so it cannot drift from the tooltip");
        require(source.contains("NATIVE_CROSS_RECIPE_THREADS + Math.max(0, modifierThreads)"),
                "the native baseline adds a non-negative modifier contribution");
        require(!source.contains("- com.gregtechceu.gtceu.api.GTValues.IV) * 4 + 1"),
                "the voltage-only fallback no longer caps native cross-recipe threads");
        // 基准按注册 ID 固定；物品 Tips 没有机器实例，不能按运行期 mutable 状态猜测。
        var batch = com.gtl.enhancedcore.common.recipe.iv.IvMachineScope.BATCH_IDS;
        require(batch.equals(Set.of("mega_extractor", "mega_presser", "mega_distillery",
                        "field_extruder_factory", "holy_separator", "cooling_tower", "super_blast_smelter")),
                "only GTLCore's seven 64-thread machines receive both baselines");
        require(com.gtl.enhancedcore.common.recipe.iv.IvMachineScope.baseThreadsFor("mega_extractor") == 192,
                "coil multi-recipe machines report 64 + 128 threads");
        require(com.gtl.enhancedcore.common.recipe.iv.IvMachineScope.baseThreadsFor("holy_separator") == 192,
                "parallel-hatch multi-recipe machines report 64 + 128 threads");
        require(com.gtl.enhancedcore.common.recipe.iv.IvMachineScope.baseThreadsFor("super_blast_smelter") == 192,
                "super blast smelter keeps its original 64 and gains 128 threads");
        require(com.gtl.enhancedcore.common.recipe.iv.IvMachineScope.baseThreadsFor("dimensionally_transcendent_mixer") == 128,
                "plain native machines report 128 threads");
        require(com.gtl.enhancedcore.common.recipe.iv.IvMachineScope.baseThreadsFor(null) == 128,
                "unknown ids fall back to the 128 baseline");
        for (String id : batch) {
            require(com.gtl.enhancedcore.common.recipe.iv.IvMachineScope.baseThreadsFor(id) == 192,
                    "every upstream multi-recipe id reports 64 + 128: " + id);
        }
        // 显示值不得再被超级总成门槛截断为 1；引擎侧另用 activeThreads。
        var threadsStart = source.indexOf("public static int threads(WorkableElectricMultiblockMachine machine)");
        require(threadsStart > 0, "threads() exists");
        var threadsHead = source.substring(threadsStart, source.indexOf("public static int activeThreads(", threadsStart));
        require(!threadsHead.contains("crossRecipeEnabled(machine)) return 1"),
                "threads() is no longer gated behind the super-buffer check");
        require(source.contains("public static int activeThreads("),
                "the engine reads activeThreads while display paths read threads");
        // 订单摘要的容量仍与线程同源，避免「线程 N 但总容量 1」的内部矛盾。
        require(source.contains("public static long displayCapacity("),
                "order summaries derive capacity from the same thread value");
        var engine = Files.readString(Path.of(
                "src/main/java/com/gtl/enhancedcore/common/recipe/iv/IvNativeEngine.java"), StandardCharsets.UTF_8);
        require(engine.contains("summary.putLong(\"budget\", IvMachineScope.displayCapacity(machine))"),
                "the native summary publishes the display capacity, not the isolation-gated budget");
        // 把有效线程写入 GTLAdditions 原有 Jade 键；由上游负责同一行的文案和样式。
        var parallelMixin = Files.readString(Path.of(
                "src/main/java/com/gtl/enhancedcore/mixin/jade/IvParallelProviderMixin.java"), StandardCharsets.UTF_8);
        require(parallelMixin.contains("@Mixin(targets = \"com.gregtechceu.gtceu.integration.jade.provider.ParallelProvider\"")
                        && parallelMixin.contains("@Inject(method = \"appendServerData("),
                "effective Jade threads are injected into GTLAdditions' parallel provider");
        require(parallelMixin.contains("!machine.isFormed()")
                        && parallelMixin.contains("!IvMachineScope.crossRecipeEnabled(machine)"),
                "effective Jade threads require a formed machine in cross-recipe mode");
        require(parallelMixin.contains("IvMachineScope.nativeTarget(machine) || machine instanceof TieredParallelMachine"),
                "effective Jade threads are limited to native isolation targets and owned machines");
        require(parallelMixin.contains("data.putLong(\"threads\", IvMachineScope.threads(machine))"),
                "GTLAdditions' shared Jade key receives the effective thread count");
        var provider = Files.readString(Path.of(
                "src/main/java/com/gtl/enhancedcore/integration/jade/IvOrderInfoProvider.java"), StandardCharsets.UTF_8);
        require(provider.contains("IvMachineScope.nativeTarget(electric)"),
                "the Jade order summary is limited to native isolation targets");
        require(!provider.contains("THREADS_KEY") && !provider.contains("ADD_THREADS_KEY")
                        && !provider.contains("gtladditions.multiblock.threads")
                        && !provider.contains("data.putLong(\"threads\"")
                        && !provider.contains("data.putInt(\"threads\""),
                "the Jade order provider no longer creates its own thread data or duplicate row");
        // 线程必须在物品提示默认可见（2026-10-07 用户反馈上轮只改了逻辑没改 tips）。
        var threadKey = "gtl_enhancedcore.tooltip.iv_native_threads";
        require(TooltipPolicy.page("native_isolation").summary().contains(threadKey),
                "native machines show their cross-recipe threads by default");
        require(!TooltipPolicy.page("native_isolation").details().contains(threadKey),
                "the thread line is not hidden behind Shift");
        for (String lang : List.of("zh_cn", "en_us")) {
            var line = readLocale(lang).get(threadKey);
            require(line != null, lang + ": native thread tooltip key exists");
            require(TooltipPolicy.placeholders(line).equals(Map.of(1, 1)),
                    lang + ": the thread line takes the actual value as one parameter");
            require(line.contains(lang.equals("zh_cn") ? "跨配方线程" : "cross-recipe threads"),
                    lang + ": the parameter describes cross-recipe threads");
        }
        // 2026-10-07 加入的目标机器必须真的进入白名单。
        require(com.gtl.enhancedcore.common.recipe.iv.IvMachineScope.nativeTarget(
                        net.minecraft.resources.ResourceLocation.tryParse("gtceu:dimensionally_transcendent_mixer")),
                "dimensionally_transcendent_mixer joined the native isolation scope");
    }

    private static void layoutBehavior() {
        for (var entry : TooltipPolicy.pages().entrySet()) {
            var layout = entry.getValue();
            var expected = new ArrayList<>(layout.summary());
            for (String detail : layout.details()) if (!expected.contains(detail)) expected.add(detail);
            for (int iteration = 0; iteration < 24; iteration++) {
                boolean expanded = iteration % 2 == 0;
                var rendered = layout.select(expanded, TooltipPolicy.SHIFT_HINT);
                require(rendered.containsAll(layout.summary()), entry.getKey() + ": constraints survive Shift toggles");
                require(rendered.size() == new HashSet<>(rendered).size(), entry.getKey() + ": no duplicate rows");
                if (expanded) {
                    require(rendered.equals(expected), entry.getKey() + ": details expand in declaration order");
                    require(!rendered.contains(TooltipPolicy.SHIFT_HINT), entry.getKey() + ": expanded view has no hint");
                } else {
                    var collapsed = new ArrayList<>(layout.summary());
                    if (!layout.details().isEmpty()) collapsed.add(TooltipPolicy.SHIFT_HINT);
                    require(rendered.equals(collapsed), entry.getKey() + ": collapsed view contains summary and one hint only");
                    require(rendered.size() <= TooltipPolicy.MAX_SUMMARY_LINES + 1, entry.getKey() + ": default line budget");
                }
            }
            rejects(UnsupportedOperationException.class, () -> layout.summary().add("mutation"));
            rejects(UnsupportedOperationException.class, () -> layout.select(true, null).clear());
        }
        var mutableSummary = new ArrayList<>(List.of("constraint"));
        var mutableDetails = new ArrayList<>(List.of("operation"));
        var snapshot = new TooltipPolicy.Layout<>(mutableSummary, mutableDetails);
        mutableSummary.clear();
        mutableDetails.add("late edit");
        require(snapshot.select(true, null).equals(List.of("constraint", "operation")), "layouts snapshot caller-owned lists");
        var mutableRecipes = new ArrayList<>(List.of("recipe one", "recipe two"));
        var catalogSnapshot = new TooltipPolicy.Layout<>(List.of("constraint"), List.of("operation"), mutableRecipes);
        mutableRecipes.clear();
        require(catalogSnapshot.select(false, "hint").equals(List.of("constraint", "recipe one", "recipe two", "hint")),
                "the complete recipe catalog is visible without Shift and snapshots its caller");
        require(catalogSnapshot.select(true, null).equals(List.of("constraint", "recipe one", "recipe two", "operation")),
                "Shift never replaces or hides the recipe catalog");
        rejects(UnsupportedOperationException.class, () -> catalogSnapshot.recipeRows().clear());
    }

    private static void requestedFlavorBehavior() {
        var joint = TooltipPolicy.page("universal_joint_factory");
        String summary = "tooltip.gtl_enhancedcore.universal_joint_factory.summary";
        for (int iteration = 0; iteration < 128; iteration++) {
            boolean expanded = iteration % 2 == 0;
            var selected = joint.select(expanded, TooltipPolicy.SHIFT_HINT);
            require(selected.contains(summary), "joint factory always retains the actionable short summary");
            require(selected.contains(TooltipPolicy.JOINT_FACTORY_FLAVOR) == expanded,
                    "requested historical flavor appears only in Shift details");
            require(selected.contains("tooltip.gtl_enhancedcore.iv_only_super_buffer"),
                    "preserving flavor never hides the exclusive input requirement");
        }
        require(TooltipPolicy.translationIssues(TooltipPolicy.JOINT_FACTORY_FLAVOR, "flavor", "en_us", true)
                .stream().anyMatch(issue -> issue.contains("Shift details only")), "historical flavor cannot be assigned to a summary");
        require(TooltipPolicy.translationIssues(TooltipPolicy.JOINT_FACTORY_FLAVOR, "a".repeat(151), "en_us", false).isEmpty(),
                "the preserved English flavor has a bounded 151-character detail allowance");
        require(!TooltipPolicy.translationIssues(TooltipPolicy.JOINT_FACTORY_FLAVOR, "a".repeat(152), "en_us", false).isEmpty(),
                "the historical allowance does not permit further expansion");
        require(!TooltipPolicy.translationIssues("other-tip", "a".repeat(151), "en_us", false).isEmpty(),
                "the historical allowance does not weaken other tips");
        require(!TooltipPolicy.translationIssues(TooltipPolicy.JOINT_FACTORY_FLAVOR, "字".repeat(61), "zh_cn", false).isEmpty(),
                "the historical allowance does not weaken the Chinese detail limit");
    }

    private static void inheritedBehavior() {
        var mandatory = TooltipPolicy.mandatoryKeys("fission_reactor");
        var replaced = TooltipPolicy.replacedKeys("fission_reactor");
        String fuelRule = "gtceu.machine.fission_reactor.tooltip.0";
        String explosionRule = "gtceu.machine.fission_reactor.tooltip.2";
        String modifiedRule = TooltipPolicy.page("fission_reactor").summary().get(0);
        var inherited = new ArrayList<>(List.of(fuelRule, explosionRule, "coolant coefficient", "coolant coefficient", "legacy credit"));
        var before = List.copyOf(inherited);
        var additions = new TooltipPolicy.Layout<>(List.of("source", modifiedRule), List.of("operation"));
        for (int iteration = 0; iteration < 400; iteration++) {
            boolean expanded = iteration % 2 == 0;
            var selected = TooltipPolicy.compose(additions, inherited,
                    replaced::contains, mandatory::contains, expanded, "hint");
            require(selected.contains(fuelRule) && selected.contains(modifiedRule), "fuel restriction and changed behavior remain visible");
            require(selected.contains(explosionRule), "the original warning is retained alongside the explicit modification");
            require(selected.contains("legacy credit"), "the upstream attribution remains visible");
            require(selected.stream().filter("source"::equals).count() == 1, "one concise source row");
            require(selected.stream().filter("coolant coefficient"::equals).count() == 2, "original explanatory rows and duplicate counts are preserved");
            require(selected.subList(0, before.size()).equals(before), "unmodified upstream rows remain the exact visible prefix");
            require(selected.contains("operation") && !selected.contains("hint"), "third-party modifications never introduce Shift hiding");
            require(selected.indexOf("source") < selected.indexOf(modifiedRule), "the modification follows its source heading");
            require(inherited.equals(before), "wrapping never mutates an upstream builder result");
            var rewrapped = TooltipPolicy.compose(additions, selected, "hint"::equals, mandatory::contains, expanded, "hint");
            require(rewrapped.equals(selected), "repeated wrapping is idempotent");
        }
        var differentArguments = List.of(new Row("parallel", List.of("64")), new Row("parallel", List.of("128")));
        var parameterLayout = new TooltipPolicy.Layout<Row>(List.of(), List.of());
        require(TooltipPolicy.compose(parameterLayout, differentArguments, ignored -> false, ignored -> false,
                true, null).equals(differentArguments), "equal keys with different arguments remain distinct");
        for (String path : List.of("dimensional_focus_engraving_array", "mega_presser", "mega_extractor",
                "advanced_vacuum_drying_furnace", "super_blast_smelter", "suprachronal_assembly_line", "atomic_energy_excitation_plant")) {
            var required = TooltipPolicy.nativeSummaryKeys(path);
            var detail = new ArrayList<>(required);
            detail.add("upstream operation");
            var selected = TooltipPolicy.compose(TooltipPolicy.page("native_isolation"), detail,
                    TooltipPolicy.replacedKeys("native_isolation")::contains, required::contains, false, TooltipPolicy.SHIFT_HINT);
            require(selected.containsAll(required), path + ": machine-specific coil/channel rules stay visible");
            require(selected.containsAll(TooltipPolicy.page("native_isolation").summary()), path + ": isolated input and capacity requirements stay visible");
            require(selected.contains("upstream operation"), path + ": original operations never move into Shift");
            require(!selected.contains(TooltipPolicy.SHIFT_HINT), path + ": the wrapper adds no Shift menu");
        }
        var originalLongPage = java.util.stream.IntStream.range(0, 80).mapToObj(index -> "original row " + index).toList();
        var originalLongResult = TooltipPolicy.compose(additions, originalLongPage, ignored -> false, ignored -> false, false, null);
        require(originalLongResult.subList(0, originalLongPage.size()).equals(originalLongPage),
                "a long original tooltip is never clipped to an arbitrary foreign line budget");
        var explicitRemoval = TooltipPolicy.compose(additions, List.of("kept rule", "removed module"),
                "removed module"::equals, ignored -> false, false, null);
        require(explicitRemoval.contains("kept rule") && !explicitRemoval.contains("removed module"),
                "only a user-requested superseded feature can be removed without hiding unrelated information");
        require(TooltipPolicy.criticalInheritedKey("gtceu.machine.fusion_reactor.capacity"), "fusion startup storage is never collapsed");
        require(TooltipPolicy.criticalInheritedKey("gtceu.machine.research.required"), "research restrictions are never collapsed");
        require(!TooltipPolicy.criticalInheritedKey("gtceu.machine.duration_multiplier.tooltip"), "detailed duration arithmetic can collapse");
    }

    private static void nativeCompositionBehavior() {
        var paths = List.of("dimensional_focus_engraving_array", "mega_presser", "mega_extractor",
                "advanced_vacuum_drying_furnace", "super_blast_smelter", "suprachronal_assembly_line",
                "atomic_energy_excitation_plant", "dimensionally_transcendent_mixer");
        var summaryKeys = new HashSet<String>();
        TooltipPolicy.pages().values().forEach(page -> summaryKeys.addAll(page.summary()));
        for (String path : paths) {
            var pages = TooltipPolicy.nativePages(path);
            require(pages.getLast().equals("native_isolation"), path + ": either registered callback selects the complete isolation composition");
            int expectedPages = path.equals("dimensional_focus_engraving_array") || path.equals("suprachronal_assembly_line") ? 2 : 1;
            require(pages.size() == expectedPages, path + ": only explicit recipe and module changes receive a supplement");
            for (boolean expanded : List.of(false, true)) {
                List<String> rows = List.copyOf(TooltipPolicy.nativeSummaryKeys(path));
                for (String page : pages) {
                    rows = TooltipPolicy.compose(TooltipPolicy.page(page), rows,
                            key -> key.equals(TooltipPolicy.SHIFT_HINT) || TooltipPolicy.replacedKeys(page).contains(key),
                            key -> summaryKeys.contains(key) || TooltipPolicy.nativeSummaryKeys(path).contains(key),
                            expanded, TooltipPolicy.SHIFT_HINT);
                }
                for (String page : pages) require(rows.containsAll(TooltipPolicy.page(page).summary()), path + ": composed callbacks retain " + page);
                require(rows.size() == new HashSet<>(rows).size(), path + ": composition has no duplicated rows");
                var repeated = rows;
                for (String page : pages) {
                    repeated = TooltipPolicy.compose(TooltipPolicy.page(page), repeated,
                            key -> key.equals(TooltipPolicy.SHIFT_HINT) || TooltipPolicy.replacedKeys(page).contains(key),
                            key -> summaryKeys.contains(key) || TooltipPolicy.nativeSummaryKeys(path).contains(key),
                            expanded, TooltipPolicy.SHIFT_HINT);
                }
                require(new HashSet<>(repeated).equals(new HashSet<>(rows)), path + ": nested callback compositions are idempotent");
                require(!rows.contains(TooltipPolicy.SHIFT_HINT), path + ": original-machine wrappers never add a Shift menu");
                require(rows.containsAll(TooltipPolicy.nativeSummaryKeys(path)), path + ": all inherited machine rules remain visible");
                for (String page : pages) require(rows.containsAll(TooltipPolicy.page(page).details()),
                        path + ": modification notes are visible without Shift");
            }
        }
        var original = List.of("gtceu.machine.suprachronal_assembly_line.tooltip.0",
                "gtceu.machine.suprachronal_assembly_line.tooltip.1", "gtceu.machine.research.required");
        var changed = TooltipPolicy.compose(TooltipPolicy.page("suprachronal_module_host"), original,
                TooltipPolicy.replacedKeys("suprachronal_module_host")::contains, ignored -> false, false, null);
        require(changed.contains(original.getFirst()) && changed.contains(original.getLast()),
                "module removal preserves the original flavor and research requirement");
        require(!changed.contains(original.get(1)) && changed.contains("tooltip.gtl_enhancedcore.suprachronal_no_modules"),
                "only the outdated module support claim is replaced");
        require(TooltipPolicy.page("suprachronal_module").select(false, null)
                        .contains("tooltip.gtl_enhancedcore.suprachronal_module_disabled"),
                "the registered legacy extension item advertises its disabled state without Shift");
        var oldModuleRows = List.of("gtceu.machine.suprachronal_assembly_line_module.tooltip.0",
                "gtceu.machine.available_recipe_map_2.tooltip", TooltipPolicy.RECIPE_HEADER, "upstream preserved detail");
        var disabledModuleRows = TooltipPolicy.compose(TooltipPolicy.page("suprachronal_module"), oldModuleRows,
                TooltipPolicy.replacedKeys("suprachronal_module")::contains, ignored -> false, false, null);
        require(disabledModuleRows.contains("upstream preserved detail"), "disabling an extension keeps unrelated original details");
        require(!disabledModuleRows.contains(TooltipPolicy.RECIPE_HEADER)
                        && !disabledModuleRows.contains("gtceu.machine.available_recipe_map_2.tooltip"),
                "a disabled legacy extension no longer advertises obsolete recipe availability");
    }

    private static void recipeGrouping() {
        for (int count = 0; count <= 72; count++) {
            var recipes = new ArrayList<Integer>();
            for (int index = 0; index < count; index++) recipes.add(index);
            var grouped = TooltipPolicy.recipeGroups(recipes);
            var flattened = grouped.stream().flatMap(List::stream).toList();
            require(flattened.equals(recipes), "recipe grouping keeps all names and their order: " + count);
            require(grouped.size() == (count + TooltipPolicy.RECIPES_PER_LINE - 1) / TooltipPolicy.RECIPES_PER_LINE,
                    "recipe group line count: " + count);
            require(grouped.stream().allMatch(group -> group.size() <= TooltipPolicy.RECIPES_PER_LINE), "no overlong recipe row: " + count);
            recipes.clear();
            require(flattened.size() == count, "recipe groups snapshot mutable inputs: " + count);
            if (!grouped.isEmpty()) rejects(UnsupportedOperationException.class, () -> grouped.get(0).add(-1));
        }
        var joint = TooltipPolicy.page("universal_joint_factory");
        var catalog = new ArrayList<String>();
        for (int index = 0; index < 24; index += TooltipPolicy.RECIPES_PER_LINE) catalog.add("recipe group " + index);
        var full = new TooltipPolicy.Layout<>(joint.summary(), joint.details(), catalog);
        var collapsed = full.select(false, TooltipPolicy.SHIFT_HINT);
        require(collapsed.containsAll(catalog), "all 24 recipe types are visible on the default joint-factory page");
        require(full.select(true, null).containsAll(catalog), "all 24 recipe types stay visible when Shift is held");
        require(collapsed.size() == joint.summary().size() + catalog.size() + 1,
                "recipe rows are independent from the short player-note budget");
        var largeCatalog = java.util.stream.IntStream.range(0, 72).mapToObj(index -> "recipe " + index).toList();
        var catalogOnly = new TooltipPolicy.Layout<>(List.of("constraint"), List.of(), largeCatalog);
        require(catalogOnly.select(false, null).containsAll(largeCatalog), "even a long supported recipe catalog is never moved to Shift");
        require(!catalogOnly.select(false, null).contains(TooltipPolicy.SHIFT_HINT), "a catalog alone does not create a Shift menu");
    }

    private static void rejectionBehavior() {
        rejects(IllegalArgumentException.class, () -> new TooltipPolicy.Layout<>(
                java.util.Collections.nCopies(TooltipPolicy.MAX_SUMMARY_LINES + 1, 1), List.of()));
        rejects(IllegalArgumentException.class, () -> new TooltipPolicy.Layout<>(List.of(), java.util.Collections.nCopies(19, 1)));
        rejects(NullPointerException.class, () -> TooltipPolicy.page("unknown-page"));
        rejects(NullPointerException.class, () -> new TooltipPolicy.Layout<>(List.of("summary"), List.of("detail")).select(false, null));
        rejects(IllegalArgumentException.class, () -> TooltipPolicy.placeholders("%d"));
        rejects(IllegalArgumentException.class, () -> TooltipPolicy.placeholders("%0$s"));
        rejects(IllegalArgumentException.class, () -> TooltipPolicy.placeholders("100%"));
        rejects(IllegalArgumentException.class, () -> TooltipPolicy.placeholders("100% complete"));
        require(TooltipPolicy.placeholders("%s / %s").equals(TooltipPolicy.placeholders("%2$s / %1$s")), "indexed English reordering preserves argument use");
        require(TooltipPolicy.placeholders("%1$s / %1$s").equals(Map.of(1, 2)), "repeated argument multiplicity is retained");
        require(TooltipPolicy.placeholders("1%%–100%%").isEmpty(), "literal percentages are not arguments");
        require(!TooltipPolicy.translationIssues("test", "a".repeat(113), "en_us", true).isEmpty(), "overlong English summary is rejected");
        require(!TooltipPolicy.translationIssues("test", "字".repeat(45), "zh_cn", true).isEmpty(), "overlong Chinese summary is rejected");
        require(!TooltipPolicy.translationIssues("test", "line\nline", "en_us", false).isEmpty(), "embedded line breaks are rejected");
        require(!TooltipPolicy.translationIssues("test", "§cwatermark", "en_us", false).isEmpty(), "embedded color codes are rejected");
    }

    private static void translationContracts() throws Exception {
        var chinese = readLocale("zh_cn");
        var english = readLocale("en_us");
        require(chinese.keySet().equals(english.keySet()), "Chinese and English contain exactly the same keys");
        var summaryKeys = new HashSet<String>();
        for (var layout : TooltipPolicy.pages().values()) summaryKeys.addAll(layout.summary());
        summaryKeys.add(TooltipPolicy.SHIFT_HINT);
        summaryKeys.add(TooltipPolicy.SOURCE_KEY);
        for (String key : List.of(TooltipPolicy.SHIFT_HINT, TooltipPolicy.SOURCE_KEY, TooltipPolicy.RECIPE_HEADER,
                TooltipPolicy.RECIPE_GROUP, TooltipPolicy.RECIPE_SEPARATOR)) {
            require(chinese.containsKey(key), "runtime tooltip infrastructure is translated: " + key);
        }
        for (String key : chinese.keySet()) {
            if (!TooltipPolicy.isTooltipKey(key)) continue;
            require(TooltipPolicy.translationIssues(key, chinese.get(key), "zh_cn", summaryKeys.contains(key)).isEmpty(),
                    "Chinese tip policy: " + key + " " + TooltipPolicy.translationIssues(key, chinese.get(key), "zh_cn", summaryKeys.contains(key)));
            require(TooltipPolicy.translationIssues(key, english.get(key), "en_us", summaryKeys.contains(key)).isEmpty(),
                    "English tip policy: " + key + " " + TooltipPolicy.translationIssues(key, english.get(key), "en_us", summaryKeys.contains(key)));
            require(TooltipPolicy.placeholders(chinese.get(key)).equals(TooltipPolicy.placeholders(english.get(key))),
                    "Placeholder argument indexes and multiplicities agree: " + key);
        }
        for (var entry : TooltipPolicy.pages().entrySet()) {
            var layout = entry.getValue();
            for (String key : layout.select(true, null)) require(chinese.containsKey(key), entry.getKey() + ": every declared row is translated");
        }
        for (String page : List.of("plasma_machine_tool", "hadron_refinery", "quantum_mass_array", "fusion_assembler")) {
            var collapsed = TooltipPolicy.page(page).select(false, TooltipPolicy.SHIFT_HINT);
            require(collapsed.contains("tooltip.gtl_enhancedcore.iv_only_super_buffer"),
                    page + ": default-mode warning and cross-type buffer requirement are visible");
            require(collapsed.contains("tooltip.gtl_enhancedcore.iv_maintenance_penalty"), page + ": maintenance penalty is mandatory");
            require(collapsed.contains(TooltipPolicy.CROSS_RECIPE_KEY), page + ": rare cross-type parallelism is prominently visible");
            require(collapsed.contains("tooltip.gtl_enhancedcore.iv_break"), page + ": Shift-only destructive removal warning is visible without Shift");
            require(collapsed.contains("tooltip.gtl_enhancedcore.iv_cancel"), page + ": cancellation and refund behavior is never hidden");
            require(collapsed.contains("tooltip.gtl_enhancedcore.iv_admission"), page + ": the AE cancellation reminder after a refund is never hidden");
        }
        require(chinese.get("tooltip.gtl_enhancedcore.iv_maintenance_penalty").equals("限制：不接受维护仓的耗时减免"),
                "the IV maintenance limit uses the requested player-facing wording");
        for (String key : List.of("tooltip.gtl_enhancedcore.iv_only_super_buffer", "gtl_enhancedcore.tooltip.iv_native.1")) {
            require(chinese.get(key).equals("原版输入/输出仓室启用原版机器模式，超级样板总成启用跨配方类型并行模式"),
                    "Chinese uses the requested machine mode wording: " + key);
            require(english.get(key).equals("Original I/O hatches enable original mode; Super Pattern Buffers enable parallelism across recipe types"),
                    "English explains both machine modes: " + key);
        }
        require(TooltipPolicy.page("forbidden_maintenance").select(false, null)
                        .contains("tooltip.gtl_enhancedcore.no_maintenance_hatch"),
                "Elevator formation prohibition is visible without Shift");
        require(!TooltipPolicy.page("forbidden_maintenance").select(false, null)
                        .contains("gtl_enhancedcore.tooltip.optional_maintenance"),
                "Elevators do not advertise optional maintenance");
        require(chinese.get(TooltipPolicy.CROSS_RECIPE_KEY).equals("支持跨配方类型并行"),
                "cross-type parallelism uses the exact concise capability label");
        require(chinese.get(TooltipPolicy.SOURCE_KEY).equals("由GTL-Enhancedcore修改"),
                "third-party changes use the requested provenance heading");
        require(chinese.get(TooltipPolicy.RECIPE_HEADER).equals("可用配方类型： %s")
                && english.get(TooltipPolicy.RECIPE_HEADER).equals("Available recipe types: %s"),
                 "recipe catalogs always use one consistent translated prefix");
        require(chinese.get(TooltipPolicy.RECIPE_SEPARATOR).equals(" ") && english.get(TooltipPolicy.RECIPE_SEPARATOR).equals(" "),
                "Both languages use the requested space-separated recipe catalog format");
        require(TooltipPolicy.page("universal_joint_factory").select(false, TooltipPolicy.SHIFT_HINT)
                        .contains("tooltip.gtl_enhancedcore.iv_admission"),
                "The joint factory shows the AE cancellation reminder without Shift");
        require(chinese.get("tooltip.gtl_enhancedcore.void_constrained_mining_field.input").startsWith("两种模式均消耗钻井液")
                        && chinese.get("tooltip.gtl_enhancedcore.void_constrained_mining_field.input").contains("精准模式还需矿脉精华"),
                "Both mining modes need drilling fluid and targeted mining additionally needs vein essence");
        require(english.get("tooltip.gtl_enhancedcore.void_constrained_mining_field.input").startsWith("Both modes consume drilling fluid")
                        && english.get("tooltip.gtl_enhancedcore.void_constrained_mining_field.input").contains("targeted mode also requires vein essence"),
                "The English mining input description preserves both consumable requirements");
        require(!chinese.get(TooltipPolicy.SHIFT_HINT).contains("配方"), "the Shift hint never implies hidden supported recipes");
        for (String page : List.of("weather_anchor", "infinity_singularity", "dragon_field_proliferation_core",
                "hyperstructural_chemical_distorter")) {
            require(TooltipPolicy.page(page).summary().contains("tooltip.gtl_enhancedcore.supports_laser_hatch"),
                    page + ": supported laser target hatches are advertised without Shift");
        }
        // 用户 2026-10-05 指定：恒星堆提示只写两句，且不得再出现跨配方类型并行标识。
        var stellar = TooltipPolicy.page("stellar_confinement_fusion_reactor");
        for (boolean expanded : List.of(false, true)) {
            var visible = stellar.select(expanded, TooltipPolicy.SHIFT_HINT);
            require(!visible.contains(TooltipPolicy.CROSS_RECIPE_KEY),
                    "stellar fusion: the rejected cross-recipe-type label must not come back");
            require(!visible.contains("tooltip.gtl_enhancedcore.supports_laser_hatch")
                            && !visible.contains("tooltip.gtl_enhancedcore.laser_requires_energy_hatch"),
                    "stellar fusion: wireless-only power must not claim wired hatches");
        }
        require(stellar.summary().equals(List.of(
                        "tooltip.gtl_enhancedcore.stellar_confinement_fusion_reactor.parallel",
                        "tooltip.gtl_enhancedcore.stellar_confinement_fusion_reactor.wireless")),
                "stellar fusion: the requested two-line tooltip must stay exactly two lines");
        require(stellar.details().isEmpty(), "stellar fusion: no Shift-only extras were requested");
        require(chinese.get("tooltip.gtl_enhancedcore.stellar_confinement_fusion_reactor.parallel").equals("拥有无限并行与无限线程")
                        && english.get("tooltip.gtl_enhancedcore.stellar_confinement_fusion_reactor.parallel").equals("Infinite parallel and infinite threads"),
                "stellar fusion: both languages state infinite parallel and threads verbatim");
        require(chinese.get("tooltip.gtl_enhancedcore.stellar_confinement_fusion_reactor.wireless").equals("电量从电网直接获取")
                        && english.get("tooltip.gtl_enhancedcore.stellar_confinement_fusion_reactor.wireless").equals("EU is drawn directly from the energy grid"),
                "stellar fusion: both languages state the direct grid draw verbatim");
        for (String removed : List.of("tips", "all_tiers", "fixed_time", "no_energy_hatches", "buffer", "working_ticks", "thread")) {
            require(!chinese.containsKey("tooltip.gtl_enhancedcore.stellar_confinement_fusion_reactor." + removed)
                            && !english.containsKey("tooltip.gtl_enhancedcore.stellar_confinement_fusion_reactor." + removed),
                    "stellar fusion: the removed " + removed + " line must not come back");
        }
        require(TooltipPolicy.page("causality_terminal").summary().contains("tooltip.gtl_enhancedcore.only_laser_hatch"),
                "the laser-only input restriction remains prominent");
        for (String page : List.of("platinum_refining_matrix", "void_constrained_mining_field")) {
            require(TooltipPolicy.page(page).summary().contains("tooltip.gtl_enhancedcore.no_laser_input"),
                    page + ": the explicit laser target hatch restriction remains default-visible");
        }
        require(chinese.get("tooltip.gtl_enhancedcore.ore_plant.no_parallel_hatch").contains("不支持维护仓"),
                "unsupported maintenance hatches are an explicit limitation, not an optional feature");
        for (String page : List.of("steam_platform", "processing_plus", "assembling_plus", "separating_plus", "mixing_plus",
                "hyperstructural_chemical_distorter", "stellar_confinement_fusion_reactor")) {
            require(!TooltipPolicy.page(page).summary().contains(TooltipPolicy.CROSS_RECIPE_KEY),
                    page + ": a single active recipe or same-recipe channel machine must not claim cross-type parallelism");
        }
        for (var entry : TooltipPolicy.pages().entrySet()) {
            for (String key : entry.getValue().summary()) {
                require(!chinese.get(key).contains("能源仓可用"), entry.getKey() + ": ordinary power-port support is not filler text");
                require(!key.equals("tooltip.gtl_enhancedcore.neutron_control_factory.maintenance")
                        && !key.equals("tooltip.gtl_enhancedcore.universal_joint_factory.maintenance"),
                        entry.getKey() + ": normal maintenance-hatch support is not repeated");
            }
        }
        require(TooltipPolicy.page("universal_joint_factory").summary().contains("tooltip.gtl_enhancedcore.universal_joint_factory.no_parallel_hatch"),
                "fixed factory capacity cannot be mistaken for hatch-multiplied capacity");
        require(!TooltipPolicy.isTooltipKey("gtl_enhancedcore.diagnostic.power_detail"), "GUI/Jade diagnostics are outside the item-tooltip budget");
        require(TooltipPolicy.translationIssues("diagnostic-example", "diagnostic".repeat(100), "en_us", true).size() == 1,
                "text budgets detect length independently of diagnostic namespace exemptions at the caller");
        diagnosticContracts(chinese, english);
    }

    private static void diagnosticContracts(Map<String, String> chinese, Map<String, String> english) {
        var arguments = Map.ofEntries(
                Map.entry("parts_unloaded", 0), Map.entry("power_detail", 3), Map.entry("voltage_detail", 4),
                Map.entry("steam_detail", 2), Map.entry("condition_detail", 1), Map.entry("order_reason", 2),
                Map.entry("computation", 0), Map.entry("research", 0), Map.entry("temperature", 0),
                Map.entry("fluid", 0), Map.entry("skylight", 0), Map.entry("energy_output", 0), Map.entry("iv_threads", 0),
                Map.entry("iv_native_loading", 0), Map.entry("iv_native_buffer", 0), Map.entry("iv_native_wireless_offline", 0),
                Map.entry("iv_native_threads", 2), Map.entry("iv_native_voltage", 2), Map.entry("iv_native_temperature", 2),
                Map.entry("iv_native_data", 1), Map.entry("iv_native_modifier", 1), Map.entry("iv_native_startup", 1),
                Map.entry("iv_native_working", 1), Map.entry("iv_native_power", 3), Map.entry("iv_native_wireless_energy", 2),
                Map.entry("iv_native_output", 1), Map.entry("iv_native_fault", 1),
                Map.entry("iv_native_research_hatch", 1), Map.entry("iv_native_research_missing", 1));
        for (var entry : arguments.entrySet()) {
            String key = "gtl_enhancedcore.diagnostic." + entry.getKey();
            var expected = new LinkedHashMap<Integer, Integer>();
            for (int argument = 1; argument <= entry.getValue(); argument++) expected.put(argument, 1);
            require(!TooltipPolicy.isTooltipKey(key), "machine diagnostics never inherit item-tip budgets: " + key);
            for (var translations : List.of(chinese, english)) {
                String text = translations.get(key);
                require(text != null && !text.isBlank(), "diagnostic translation is available: " + key);
                require(TooltipPolicy.placeholders(text).equals(expected), "diagnostic arguments match the production call: " + key);
            }
        }
        for (var translations : List.of(chinese, english)) {
            require(!translations.get("gtl_enhancedcore.diagnostic.iv_native_wireless_energy").contains("EU/t"),
                    "wireless payment diagnostics display total EU, not a per-tick quantity");
        }
    }

    private static Map<String, String> readLocale(String locale) throws Exception {
        Path path = Path.of("src/main/resources/assets/gtl_enhancedcore/lang", locale + ".json");
        var result = new LinkedHashMap<String, String>();
        try (var reader = new JsonReader(Files.newBufferedReader(path, StandardCharsets.UTF_8))) {
            reader.setLenient(false);
            reader.beginObject();
            while (reader.hasNext()) {
                String key = reader.nextName();
                require(!result.containsKey(key), locale + ": no duplicate JSON key: " + key);
                require(reader.peek() == JsonToken.STRING, locale + ": all translation values are strings: " + key);
                result.put(key, reader.nextString());
            }
            reader.endObject();
            require(reader.peek() == JsonToken.END_DOCUMENT, locale + ": no trailing JSON content");
        }
        return result;
    }

    private record Row(String key, List<String> arguments) {}

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        assertions++;
    }

    private static void rejects(Class<? extends Throwable> expected, Runnable operation) {
        try {
            operation.run();
        } catch (Throwable failure) {
            require(expected.isInstance(failure), "Expected " + expected.getSimpleName() + ", got " + failure);
            return;
        }
        throw new AssertionError("Expected " + expected.getSimpleName());
    }
}
