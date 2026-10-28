package com.gtl.enhancedcore.common.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.loading.FMLPaths;
import com.gtl.enhancedcore.common.recipe.iv.IvMachineScope;

/** Complete machine IDs only; frozen so structure, tooltip and runtime agree. */
public final class GalaxyConnectionConfig {
    public static final String FILE = "galaxy_connections.txt";
    private GalaxyConnectionConfig() {}
    private static final class Snapshot {
        private static final Set<String> IDS = load(FMLPaths.CONFIGDIR.get().resolve("GTL-Enhancedcore").resolve(FILE));
    }
    public static Set<String> defaults() {
        var ids = new LinkedHashSet<String>();
        IvMachineScope.NATIVE_IDS.forEach(id -> ids.add("gtceu:" + id));
        IvMachineScope.ADD_THREAD_ONLY_IDS.forEach(id -> ids.add("gtceu:" + id));
        IvMachineScope.FOREIGN_NATIVE_IDS.forEach((namespace, paths) -> paths.forEach(id -> ids.add(namespace + ":" + id)));
        // Explicit supported targets only: do not construct every registered giant pattern at startup.
        for (String id : new String[]{"qft", "wood_distillation",
                "advanced_integrated_ore_processor", "component_assembly_line", "mega_canner",
                "mega_fluid_heater", "a_mass_fabricator", "nano_core"}) ids.add("gtceu:" + id);
        for (String id : new String[]{"dimensionally_transcendent_chemical_plant",
                "antientropy_condensation_center", "draconic_collapse_core"}) ids.add("gtladditions:" + id);
        for (String id : new String[]{"large_furnace", "basic_ore_processing_plant",
                "universal_joint_factory", "star_core_stripper", "high_flux_breeder_reactor",
                "hydrothermal_leaching_complex", "neutron_control_factory", "hyperstructural_chemical_distorter",
                "processing_plus", "assembling_plus", "separating_plus", "mixing_plus",
                "plasma_machine_tool", "hadron_catalytic_refinery", "quantum_mass_spectrum_array",
                "superconducting_fusion_assembler", "industrial_steam_platform", "causality_terminal",
                "infinity_singularity_compressor", "platinum_refining_matrix",
                "void_constrained_mining_field", "dragon_field_proliferation_core"})
            ids.add("gtl_enhancedcore:" + id);
        return Set.copyOf(ids);
    }
    public static Set<String> parse(String text) {
        if (text.length() > 8000) throw new IllegalArgumentException("Connection allowlist exceeds 8000 characters");
        var ids = new LinkedHashSet<String>();
        for (String raw : text.replace("\uFEFF", "").split("\\R")) {
            String line = raw.strip();
            if (line.isEmpty() || line.startsWith("#")) continue;
            if (!line.matches("[a-z0-9_.-]+:[a-z0-9_./-]+") || ResourceLocation.tryParse(line) == null)
                throw new IllegalArgumentException("Expected one complete machine ID per line: " + line);
            ids.add(line);
        }
        return Set.copyOf(ids);
    }
    public static String defaultText() {
        return "# 星辰连接机器白名单 / Galaxy connection machine allowlist\n"
                + "# 每行一个完整机器ID；#开头为注释。空白名单禁用所有靶仓。\n"
                + "# One namespace:machine_id per line. Empty list denies all targets.\n"
                + "# 双端修改后完整重启；服务器名单决定安装和运行。\n"
                + "# Restart both sides after editing; server enforces structure and links.\n"
                + "# 极限反应堆与恒星约束聚变堆保留专用发电/无线规则，不默认接入。\n"
                + "# Extreme Reactor and Stellar Confinement Fusion keep their dedicated power rules.\n"
                + String.join("\n", defaults().stream().sorted().toList()) + "\n";
    }
    public static void ensureDefaultConfig(Path file) throws IOException {
        // Existing text is authoritative, including an empty deny-all list and deliberate removals.
        if (!Files.exists(file)) ConfigFiles.writeAtomically(file, defaultText());
    }
    private static Set<String> load(Path file) {
        try { ensureDefaultConfig(file); return parse(Files.readString(file)); }
        catch (IOException | RuntimeException failure) {
            com.gtl.enhancedcore.GTLEnhancedcore.LOGGER.error("Galaxy connection allowlist rejected; targets disabled: {}", file, failure);
            return Set.of();
        }
    }
    public static boolean allows(ResourceLocation id) { return id != null && Snapshot.IDS.contains(id.toString()); }
}
