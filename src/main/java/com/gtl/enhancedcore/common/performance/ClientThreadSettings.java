package com.gtl.enhancedcore.common.performance;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.loading.FMLPaths;

/** Read before Minecraft initializes its shared worker pool; never loads game registries. */
public final class ClientThreadSettings {
    private static final System.Logger LOG = System.getLogger("GTL-Enhancedcore/Threads");
    private static final ThreadBudget.Plan PLAN = ThreadBudget.plan(
            Runtime.getRuntime().availableProcessors(), Runtime.getRuntime().maxMemory());
    private static final Properties SETTINGS = readSettings();
    private static final boolean ENABLED = FMLEnvironment.dist.isClient()
            && Boolean.parseBoolean(SETTINGS.getProperty("autoThreads", "true"))
            && !Boolean.getBoolean("gtl.enhancedcore.disableAutoThreads");

    private ClientThreadSettings() {}

    public static void configureBackground() {
        if (!ENABLED) return;
        // JVM flags and earlier providers such as ModernFix own an existing limit.
        // Do not query management services during Mixin bootstrap to infer its origin.
        if (System.getProperty("max.bg.threads") != null) return;
        int count = configured("backgroundThreads", PLAN.backgroundThreads());
        String value = Integer.toString(count);
        System.setProperty("max.bg.threads", value);
        LOG.log(System.Logger.Level.INFO, "Automatic client workers: logicalCPUs={0}, maxHeapMiB={1}, background={2}, builderAuto={3}",
                Runtime.getRuntime().availableProcessors(), Runtime.getRuntime().maxMemory() / 1048576,
                count, configured("builderThreads", PLAN.builderThreads()));
    }

    public static int builderThreads(int upstream) {
        if (!ENABLED) return upstream;
        try {
            // Read the live options once per builder creation so GUI manual overrides keep working.
            Class<?> client = Class.forName("me.jellysquid.mods.sodium.client.SodiumClientMod");
            Object options = client.getMethod("options").invoke(null);
            Object performance = options.getClass().getField("performance").get(options);
            int manual = performance.getClass().getField("chunkBuilderThreads").getInt(performance);
            if (manual != 0) return upstream;
            int count = Math.min(upstream, configured("builderThreads", PLAN.builderThreads()));
            LOG.log(System.Logger.Level.INFO, "Automatic chunk builder workers: {0} (upstream auto {1})", count, upstream);
            return Math.max(1, count);
        } catch (ReflectiveOperationException | LinkageError error) {
            LOG.log(System.Logger.Level.WARNING, "Cannot read Embeddium options; retaining its worker count", error);
            return upstream;
        }
    }

    private static int configured(String key, int fallback) {
        String raw = SETTINGS.getProperty(key, "0").trim();
        try {
            int value = Integer.parseInt(raw);
            if (value == 0) return fallback;
            if (value >= 1 && value <= 255) return value;
        } catch (NumberFormatException ignored) {
        }
        LOG.log(System.Logger.Level.WARNING, "Invalid {0}={1}; using automatic value {2}", key, raw, fallback);
        return fallback;
    }

    private static Properties readSettings() {
        var result = new Properties();
        Path path = FMLPaths.CONFIGDIR.get().resolve("GTL-Enhancedcore/performance.properties");
        if (Files.isRegularFile(path)) {
            try (var reader = Files.newBufferedReader(path)) {
                result.load(reader);
            } catch (IOException | IllegalArgumentException error) {
                LOG.log(System.Logger.Level.WARNING, "Cannot read performance.properties; retaining upstream thread settings", error);
                result.setProperty("autoThreads", "false");
            }
        }
        return result;
    }
}
