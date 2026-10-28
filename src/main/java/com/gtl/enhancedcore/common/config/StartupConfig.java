package com.gtl.enhancedcore.common.config;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import net.minecraftforge.fml.loading.FMLPaths;

/** JDK-only file parsing is safe before game classes and Forge config registration. */
public final class StartupConfig {
    public static final String FILE = "startup.properties";
    public record Settings(boolean replaceOriginalStructures, boolean enhanceOriginalMachines,
                           boolean autoThreads, int backgroundThreads, int builderThreads, int mapRasterThreads) {}
    private StartupConfig() {}
    private static final class Loaded {
        private static final Settings SETTINGS = loadDirectory(FMLPaths.CONFIGDIR.get().resolve("GTL-Enhancedcore"));
    }
    public static Settings current() { return Loaded.SETTINGS; }
    public static Settings loadDirectory(Path directory) {
        try {
            Path target = directory.resolve(FILE);
            if (!Files.exists(target)) {
                Properties combined = new Properties();
                for (String name : new String[]{"performance.properties", "foreign_machines.properties"}) {
                    Path old = directory.resolve(name);
                    if (Files.exists(old)) combined.putAll(properties(Files.readString(old)));
                }
                ConfigFiles.writeAtomically(target, serialize(parse(combined), combined));
            }
            return read(target);
        } catch (IOException error) { throw new UncheckedIOException(error); }
    }
    public static Settings read(Path path) {
        try {
            if (!Files.exists(path)) ConfigFiles.writeAtomically(path, serialize(defaults()));
            return parse(Files.readString(path));
        } catch (IOException error) { throw new UncheckedIOException(error); }
    }
    public static Settings defaults() { return new Settings(true, true, true, 0, 0, 0); }
    public static Settings parse(String text) { return parse(properties(text)); }
    private static Properties properties(String text) {
        var result = new Properties();
        try { result.load(new StringReader(text)); }
        catch (IOException impossible) { throw new UncheckedIOException(impossible); }
        return result;
    }
    private static Settings parse(Properties p) {
        return new Settings(flag(p, "replaceOriginalStructures"), flag(p, "enhanceOriginalMachines"),
                flag(p, "autoThreads"), count(p, "backgroundThreads"), count(p, "builderThreads"),
                count(p, "mapRasterThreads", 8));
    }
    private static boolean flag(Properties p, String key) {
        String value = p.getProperty(key, "true").trim();
        if (value.equalsIgnoreCase("true")) return true;
        if (value.equalsIgnoreCase("false")) return false;
        throw new IllegalArgumentException(key + ": expected true / false");
    }
    private static int count(Properties p, String key) {
        return count(p, key, 255);
    }
    private static int count(Properties p, String key, int max) {
        int value = Integer.parseInt(p.getProperty(key, "0").trim());
        if (value < 0 || value > max) throw new IllegalArgumentException(key + ": expected 0.." + max);
        return value;
    }
    public static String serialize(Settings settings) { return serialize(settings, new Properties()); }
    public static String update(String original, Settings settings) {
        String result = serialize(settings, properties(original));
        parse(result);
        return result;
    }
    private static String serialize(Settings s, Properties p) {
        p.setProperty("replaceOriginalStructures", Boolean.toString(s.replaceOriginalStructures()));
        p.setProperty("enhanceOriginalMachines", Boolean.toString(s.enhanceOriginalMachines()));
        p.setProperty("autoThreads", Boolean.toString(s.autoThreads()));
        p.setProperty("backgroundThreads", Integer.toString(s.backgroundThreads()));
        p.setProperty("builderThreads", Integer.toString(s.builderThreads()));
        p.setProperty("mapRasterThreads", Integer.toString(s.mapRasterThreads()));
        var writer = new StringWriter();
        writer.write("# 修改后完整重启游戏/服务端 / Restart required. 0 threads = auto.\n"
                + "# replaceOriginalStructures: 替换第三方结构并启用已审查的隐藏方块优化；enhanceOriginalMachines: 加强第三方机器（相互独立）。\n"
                + "# autoThreads/backgroundThreads/builderThreads: 客户端线程设置。\n"
                + "# mapRasterThreads: FTB地图像素线程；0=按硬件自动，1=串行，2..8=指定worker。\n");
        try { p.store(writer, null); } catch (IOException impossible) { throw new UncheckedIOException(impossible); }
        return writer.toString();
    }
}
