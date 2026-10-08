package com.gtl.enhancedcore.common.config;

import com.gtl.enhancedcore.GTLEnhancedcore;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * 旧配置文件迁移：把 config/ 根目录下的历史配置文件一次性移动到
 * config/GTL-Enhancedcore/ 文件夹（用户需求：配置文件统一收进 GTL-Enhancedcore 目录）。
 */
public final class ConfigMigration {

    private ConfigMigration() {
    }

    /** 迁移的旧文件名列表。 */
    private static final List<String> LEGACY_FILES = List.of(
            "gtl_enhancedcore_ae_pins.json",
            "gtl_enhancedcore_recipe_type_sets.json",
            "gtl_enhancedcore-server.toml"
    );

    public static void migrateLegacyFiles() {
        try {
            Path configDir = FMLPaths.CONFIGDIR.get();
            Path targetDir = configDir.resolve("GTL-Enhancedcore");
            Files.createDirectories(targetDir);
            for (String name : LEGACY_FILES) {
                Path legacy = configDir.resolve(name);
                Path target = targetDir.resolve(name.equals("gtl_enhancedcore_ae_pins.json") ? "ae_pins.json"
                        : name.equals("gtl_enhancedcore-server.toml") ? "common.toml" : name);
                if (Files.exists(legacy) && !Files.exists(target)) {
                    Files.move(legacy, target);
                    GTLEnhancedcore.LOGGER.info("[ConfigMigration] Moved {} -> {}", legacy, target);
                }
            }
        } catch (IOException | RuntimeException ex) {
            GTLEnhancedcore.LOGGER.warn("[ConfigMigration] Unable to migrate legacy config files", ex);
        }
    }
}
