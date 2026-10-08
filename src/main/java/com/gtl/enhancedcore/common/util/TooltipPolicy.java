package com.gtl.enhancedcore.common.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Predicate;
import java.util.regex.Pattern;

public final class TooltipPolicy {
    public static final int MAX_SUMMARY_LINES = 10;
    public static final int MAX_DETAIL_LINES = 18;
    public static final int RECIPES_PER_LINE = 4;
    public static final String SHIFT_HINT = "tooltip.gtl_enhancedcore.hold_shift";
    public static final String SOURCE_KEY = "tooltip.gtl_enhancedcore.credit_modified";
    public static final String CROSS_RECIPE_KEY = "tooltip.gtl_enhancedcore.iv_cross_recipe_toggle";
    public static final String RECIPE_HEADER = "tooltip.gtl_enhancedcore.recipe_types";
    public static final String RECIPE_GROUP = "tooltip.gtl_enhancedcore.recipe_group";
    public static final String RECIPE_SEPARATOR = "gtl_enhancedcore.recipe_list_separator";
    private static final String PREFIX = "tooltip.gtl_enhancedcore.";
    public static final String JOINT_FACTORY_FLAVOR = PREFIX + "universal_joint_factory.intro";
    private static final Pattern FORMAT = Pattern.compile("%(?:(\\d+)\\$)?([A-Za-z%]|$)");
    private static final Map<String, Layout<String>> PAGES = Map.ofEntries(
            Map.entry("large_furnace", page(
                    List.of("large_furnace.efficient", "large_furnace.base_parallel", "large_furnace.base_threads",
                            "large_furnace.cross_recipe_parallel", "large_furnace.voltage_scaling",
                            "large_furnace.parallel_hatch", "large_furnace.thread_engine"), List.of())),
            Map.entry("steam_platform", page(
                    List.of("steam_platform.intro", "steam_platform.steam_cost", "steam_platform.parallel_fixed",
                            "steam_platform.hull_voltage", "steam_platform.steam_input"), List.of())),
            Map.entry("plasma_machine_tool", isolatedPage("plasma_machine_tool")),
            Map.entry("hadron_refinery", isolatedPage("hadron_refinery")),
            Map.entry("quantum_mass_array", isolatedPage("quantum_mass_array")),
            Map.entry("fusion_assembler", isolatedPage("fusion_assembler")),
            Map.entry("universal_joint_factory", page(
                    List.of("iv_cross_recipe_toggle", "universal_joint_factory.summary", "iv_only_super_buffer",
                            "universal_joint_factory.parallel", "universal_joint_factory.no_parallel_hatch",
                            "iv_shared_catalysts", "iv_cancel", "iv_admission", "iv_break"),
                    List.of("iv_result", "universal_joint_factory.intro"))),
            Map.entry("processing_plus", plusPage("processing_plus")),
            Map.entry("assembling_plus", plusPage("assembling_plus")),
            Map.entry("separating_plus", plusPage("separating_plus")),
            Map.entry("mixing_plus", plusPage("mixing_plus")),
            Map.entry("ore_plant", page(
                    List.of("ore_plant.intro", "ore_plant.parallel", "ore_plant.threads", "ore_plant.no_parallel_hatch",
                            "ore_plant.hatch_slots"), List.of())),
            Map.entry("qft", page(
                    List.of("qft.cross_recipe", "qft.threads"), List.of())),
            Map.entry("weather_anchor", page(
                    List.of("weather_anchor.intro", "weather_anchor.power", "weather_anchor.supply", "supports_laser_hatch"),
                    List.of("weather_anchor.reform"))),
            Map.entry("causality_terminal", page(
                    List.of("causality_terminal.intro", "causality_terminal.power", "causality_terminal.duration",
                            "causality_terminal.fishbig", "causality_terminal.output_random", "causality_terminal.output",
                            "only_laser_hatch"),
                    List.of("causality_terminal.editor"))),
            Map.entry("infinity_singularity", page(
                    List.of("infinity_singularity.intro", "infinity_singularity.power", "infinity_singularity.duration",
                            "supports_laser_hatch"),
                    List.of())),
            Map.entry("neutron_control_factory", page(
                    List.of("neutron_control_factory.intro", "neutron_control_factory.convert",
                            "neutron_control_factory.muffler", "neutron_control_factory.parallel"), List.of())),
            Map.entry("platinum_refining_matrix", page(
                    List.of("platinum_refining_matrix.tips", "platinum_refining_matrix.parallel", "no_laser_input"),
                    List.of())),
            Map.entry("void_constrained_mining_field", page(
                    List.of("void_constrained_mining_field.tips", "void_constrained_mining_field.parallel",
                            "void_constrained_mining_field.output", "void_constrained_mining_field.input", "no_laser_input"), List.of())),
            Map.entry("dragon_field_proliferation_core", page(
                    List.of("dragon_field_proliferation_core.tips", "dragon_field_proliferation_core.parallel",
                            "supports_laser_hatch", "laser_requires_energy_hatch"),
                    List.of())),
            Map.entry("hyperstructural_chemical_distorter", page(
                    List.of("hyperstructural_chemical_distorter.tips", "hyperstructural_chemical_distorter.coil",
                            "hyperstructural_chemical_distorter.parallel", "hyperstructural_chemical_distorter.thread",
                            "supports_laser_hatch", "laser_requires_energy_hatch"),
                    List.of("hyperstructural_chemical_distorter.coil_parallel", "engine_stars"))),
            // 用户 2026-10-05 指定：恒星堆提示只写“无限并行与无限线程”和“电量从电网直接获取”两句。
            Map.entry("stellar_confinement_fusion_reactor", page(
                    List.of("stellar_confinement_fusion_reactor.parallel",
                            "stellar_confinement_fusion_reactor.wireless"),
                    List.of())),
            Map.entry("circuit_encoder_hatch", page(
                    List.of("circuit_encoder_hatch.intro", "circuit_encoder_hatch.mechanism", "circuit_encoder_hatch.limit"),
                    List.of())),
            Map.entry("me_drive", page(
                    List.of("me_drive.intro", "me_drive.auto_fill_desc"),
                    List.of("me_drive.priority_desc"))),
            Map.entry("claim_replacement", page(
                    List.of("claim_replacement.intro", "claim_replacement.force_loaded", "claim_replacement.no_nbt"),
                    List.of())),
            Map.entry("quantum_data_access_hatch", page(
                    List.of("quantum_data_access_hatch.0", "quantum_data_access_hatch.1", "quantum_data_access_hatch.2",
                            "quantum_data_access_hatch.blank"), List.of())),
            Map.entry("creative_computation_receiver_hatch", page(
                    List.of("creative_computation_receiver_hatch.intro", "creative_computation_receiver_hatch.install"),
                    List.of())),
            Map.entry("crystal_resonator", page(
                    List.of("crystal_resonator.intro", "crystal_resonator.rate", "crystal_resonator.usage",
                            "crystal_resonator.wireless"), List.of("crystal_resonator.detail", "crystal_resonator.buffer"))),
            Map.entry("wireless_charger", page(
                    List.of("wireless_charger.intro", "wireless_charger.range", "wireless_charger.power",
                            "wireless_charger.tier_limit"), List.of("wireless_charger.scan"))),
            Map.entry("native_isolation", page(
                    List.of("gtl_enhancedcore.tooltip.iv_native.1",
                            "gtl_enhancedcore.tooltip.iv_native_threads",
                            "iv_shared_catalysts",
                            "gtl_enhancedcore.tooltip.iv_native.3", "iv_break"), List.of())),
            Map.entry("suprachronal_module_host", page(List.of("suprachronal_no_modules"), List.of())),
            Map.entry("suprachronal_module", page(List.of("suprachronal_module_disabled"), List.of())),
            Map.entry("optional_maintenance", page(
                    List.of("gtl_enhancedcore.tooltip.optional_maintenance"), List.of())),
            Map.entry("forbidden_maintenance", page(List.of("no_maintenance_hatch"), List.of())),
            Map.entry("fusion_reactor", page(List.of("fusion_reactor.max_parallel"), List.of())),
            Map.entry("fission_reactor", page(List.of("fission_reactor.simple"), List.of())),
            Map.entry("star_ultimate_material_forge", page(
                    List.of("star_ultimate_material_forge.parallel"), List.of("star_ultimate_material_forge.field"))),
            Map.entry("engraving_laser_plant", page(List.of("engraving_laser_plant.free_cwu"), List.of())),
            Map.entry("pattern_buffer", page(List.of("pattern_buffer.support"), List.of())),
            Map.entry("assembly_line", page(List.of("pattern_buffer.support", "assembly_line.parallel_fixed"), List.of())),
            Map.entry("dimensional_focus_engraving_array", page(
                    List.of("dimensional_focus_engraving_array.modes"), List.of())));

    private TooltipPolicy() {}

    public record Layout<T>(List<T> summary, List<T> details, List<T> recipeRows) {
        public Layout(List<T> summary, List<T> details) {
            this(summary, details, List.of());
        }

        public Layout {
            summary = List.copyOf(summary);
            details = List.copyOf(details);
            recipeRows = List.copyOf(recipeRows);
            if (summary.size() > MAX_SUMMARY_LINES || details.size() > MAX_DETAIL_LINES) {
                throw new IllegalArgumentException("Tooltip line budget exceeded");
            }
        }

        public List<T> select(boolean expanded, T hint) {
            var selected = new LinkedHashSet<>(summary);
            selected.addAll(recipeRows);
            var supplemental = new LinkedHashSet<>(details);
            supplemental.removeAll(selected);
            if (expanded) {
                selected.addAll(supplemental);
            } else if (!supplemental.isEmpty()) {
                selected.add(Objects.requireNonNull(hint, "Collapsed details require a hint"));
            }
            return List.copyOf(selected);
        }
    }

    public static Layout<String> page(String id) {
        return Objects.requireNonNull(PAGES.get(id), "Unknown tooltip page: " + id);
    }

    public static Map<String, Layout<String>> pages() {
        return PAGES;
    }

    public static List<String> nativePages(String path) {
        return switch (path) {
            case "dimensional_focus_engraving_array" -> List.of("dimensional_focus_engraving_array", "native_isolation");
            case "suprachronal_assembly_line" -> List.of("suprachronal_module_host", "native_isolation");
            // qft 走 ADD 可变多配方路线（非 IV 隔离），只追加跨配方并行与线程声明。
            case "qft" -> List.of("qft");
            default -> List.of("native_isolation");
        };
    }

    public static Set<String> nativeSummaryKeys(String path) {
        return switch (path) {
            case "dimensional_focus_engraving_array" -> Set.of("gtceu.multiblock.coil_parallel",
                    PREFIX + "dimensional_focus_engraving_array.modes");
            case "mega_presser", "mega_extractor", "advanced_vacuum_drying_furnace" -> Set.of("gtceu.multiblock.coil_parallel");
            case "super_blast_smelter" -> Set.of("gtceu.machine.electric_blast_furnace.tooltip.a");
            case "atomic_energy_excitation_plant" -> Set.of("gtladditions.multiblock.thread.atomic_energy_excitation_plant.tooltip.0");
            case "qft" -> Set.of();
            default -> Set.of();
        };
    }

    public static Set<String> replacedKeys(String page) {
        return switch (page) {
            case "suprachronal_module_host" -> Set.of("gtceu.machine.suprachronal_assembly_line.tooltip.1");
            case "suprachronal_module" -> Set.of("gtceu.machine.suprachronal_assembly_line_module.tooltip.0",
                    "gtceu.machine.available_recipe_map_1.tooltip", "gtceu.machine.available_recipe_map_2.tooltip",
                    RECIPE_HEADER, RECIPE_GROUP);
            case "star_ultimate_material_forge" -> Set.of(PREFIX + "star_ultimate_material_forge.modified");
            case "dimensional_focus_engraving_array" -> Set.of("gtceu.machine.available_recipe_map_1.tooltip",
                    "gtceu.machine.available_recipe_map_2.tooltip", RECIPE_HEADER, RECIPE_GROUP);
            default -> Set.of();
        };
    }

    public static Set<String> mandatoryKeys(String page) {
        return switch (page) {
            case "fusion_reactor" -> Set.of("gtceu.machine.fusion_reactor.capacity");
            case "fission_reactor" -> Set.of("gtceu.machine.fission_reactor.tooltip.0");
            case "engraving_laser_plant" -> Set.of("gtceu.machine.engraving_laser_plant.tooltip.0");
            case "pattern_buffer", "assembly_line" -> Set.of("gtceu.machine.assembly_line.tooltip.0",
                    "gtceu.machine.assembly_line.tooltip.1", "gtceu.machine.circuit_assembly_line.tooltip.0");
            case "dimensional_focus_engraving_array" -> Set.of("gtceu.multiblock.coil_parallel");
            default -> Set.of();
        };
    }

    public static boolean criticalInheritedKey(String key) {
        return key.contains(".research") || key.contains(".computation") || key.contains(".temperature")
                || key.contains(".requires") || key.contains(".required") || key.contains(".voltage")
                || key.equals("gtceu.machine.fusion_reactor.capacity");
    }

    public static <T> List<List<T>> recipeGroups(List<T> recipes) {
        var groups = new ArrayList<List<T>>();
        for (int offset = 0; offset < recipes.size(); offset += RECIPES_PER_LINE) {
            groups.add(List.copyOf(recipes.subList(offset, Math.min(recipes.size(), offset + RECIPES_PER_LINE))));
        }
        return List.copyOf(groups);
    }

    public static <T> List<T> compose(Layout<T> additions, Collection<T> inherited,
                                      Predicate<T> removed, Predicate<T> mandatory,
                                      boolean expanded, T hint) {
        var selected = new ArrayList<T>();
        for (T line : inherited) {
            if (!removed.test(line)) selected.add(line);
        }
        for (Collection<T> section : List.of(additions.summary(), additions.recipeRows(), additions.details())) {
            for (T line : section) if (!selected.contains(line)) selected.add(line);
        }
        return List.copyOf(selected);
    }

    public static boolean isTooltipKey(String key) {
        return key.startsWith(PREFIX) || key.startsWith("gtl_enhancedcore.tooltip.");
    }

    public static Map<Integer, Integer> placeholders(String text) {
        var result = new TreeMap<Integer, Integer>();
        var matcher = FORMAT.matcher(text);
        int nextArgument = 1;
        int offset = 0;
        int percent;
        while ((percent = text.indexOf('%', offset)) >= 0) {
            matcher.region(percent, text.length());
            if (!matcher.lookingAt()) throw new IllegalArgumentException("Literal percent must use %%");
            offset = matcher.end();
            String conversion = matcher.group(2);
            if (conversion.equals("%") && matcher.group(1) == null) continue;
            if (!conversion.equals("s")) {
                throw new IllegalArgumentException("Unsupported translation placeholder: " + matcher.group());
            }
            int argument = matcher.group(1) == null ? nextArgument++ : Integer.parseInt(matcher.group(1));
            if (argument < 1) throw new IllegalArgumentException("Translation arguments are one-based");
            result.merge(argument, 1, Integer::sum);
        }
        return Map.copyOf(result);
    }

    public static List<String> translationIssues(String key, String text, String locale, boolean summary) {
        var issues = new ArrayList<String>();
        if (text == null || text.isBlank()) {
            return List.of(key + ": missing or blank translation");
        }
        if (text.indexOf('\n') >= 0 || text.indexOf('\r') >= 0) issues.add(key + ": embedded line break");
        if (text.indexOf('§') >= 0) issues.add(key + ": embedded formatting code");
        String visible = FORMAT.matcher(text).replaceAll("?").replace("%%", "%");
        int limit = locale.equals("zh_cn") ? (summary ? 44 : 60) : (summary ? 112 : 150);
        if (key.equals(JOINT_FACTORY_FLAVOR)) {
            if (summary) issues.add(key + ": requested flavor belongs in Shift details only");
            else if (locale.equals("en_us")) limit = 151;
        }
        if (visible.codePointCount(0, visible.length()) > limit) issues.add(key + ": text budget exceeds " + limit);
        try {
            placeholders(text);
        } catch (IllegalArgumentException failure) {
            issues.add(key + ": " + failure.getMessage());
        }
        return List.copyOf(issues);
    }

    private static Layout<String> page(List<String> summary, List<String> details) {
        return new Layout<>(summary.stream().map(TooltipPolicy::key).toList(),
                details.stream().map(TooltipPolicy::key).toList());
    }

    private static Layout<String> isolatedPage(String id) {
        return page(List.of("iv_cross_recipe_toggle", id + ".intro", "iv_only_super_buffer", id + ".voltage_thread",
                        id + ".parallel_hatch", "iv_maintenance_penalty", "iv_shared_catalysts", "iv_cancel", "iv_admission", "iv_break"),
                List.of("iv_result", "iv_batch_progress"));
    }

    private static Layout<String> plusPage(String id) {
        return page(List.of(id + ".intro", "plus_factory.parallel"), List.of());
    }

    private static String key(String suffix) {
        return suffix.startsWith("gtl_enhancedcore.tooltip.") ? suffix : PREFIX + suffix;
    }
}
