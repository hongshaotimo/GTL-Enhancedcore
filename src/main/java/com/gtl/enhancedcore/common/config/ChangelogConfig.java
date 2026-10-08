package com.gtl.enhancedcore.common.config;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.GsonBuilder;
import com.gtl.enhancedcore.GTLEnhancedcore;

import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 更新公告配置（GTL-Enhancedcore，2026-09-02）。
 *
 * 单独一份 JSON 配置：{@code config/GTL-Enhancedcore/changelog.json}，与 common.toml 分开，
 * 因为公告是长文本 + 多段结构，TOML 表达起来很难看，且整合包作者每版都要改这一处。
 *
 * <p>结构（首次启动自动生成示例，即 1.1 公告）：
 * <pre>
 * {
 *   "enabled": true,          // 总开关
 *   "version": "1.1",         // 公告版本号；改了它，"不再提示"的记录自动失效，公告会重新弹一次
 *   "title": "GregTech Leisure-增强版：1.1",
 *   "sections": [             // 分段；每段一个小标题 + 若干行
 *     { "heading": "问题修复", "lines": ["...", "..."] }
 *   ]
 * }
 * </pre>
 *
 * <p>"不再提示" 记录写在 {@code config/GTL-Enhancedcore/changelog_seen.txt}（存的是版本号），
 * 不写进本配置，避免玩家点了按钮就把整合包作者的配置文件改脏。
 */
public final class ChangelogConfig {

    private static final Path CONFIG_FILE = FMLPaths.CONFIGDIR.get()
            .resolve("GTL-Enhancedcore").resolve("changelog.json");
    private static final Path SEEN_FILE = FMLPaths.CONFIGDIR.get()
            .resolve("GTL-Enhancedcore").resolve("changelog_seen.txt");

    /** 写出默认配置用的格式化 Gson（不转义中文）。 */
    private static final com.google.gson.Gson GSON = new GsonBuilder()
            .setPrettyPrinting().disableHtmlEscaping().create();

    private static ChangelogConfig loaded;

    private final boolean enabled;
    private final String version;
    private final String title;
    private final List<Section> sections;

    private ChangelogConfig(boolean enabled, String version, String title, List<Section> sections) {
        this.enabled = enabled;
        this.version = version;
        this.title = title;
        this.sections = sections;
    }

    /** 公告一段：小标题 + 正文行。 */
    public record Section(String heading, List<String> lines) {}

    public boolean isEnabled() {
        return this.enabled;
    }

    public String getVersion() {
        return this.version;
    }

    public String getTitle() {
        return this.title;
    }

    public List<Section> getSections() {
        return this.sections;
    }

    /** 公告是否有实际内容（标题或任意一段非空）。 */
    public boolean hasContent() {
        if (!this.title.isBlank()) {
            return true;
        }
        for (Section section : this.sections) {
            if (!section.heading().isBlank() || !section.lines().isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /** 读取配置（懒加载并缓存）；文件不存在时写出 1.1 示例。 */
    public static ChangelogConfig get() {
        if (loaded == null) {
            loaded = load();
        }
        return loaded;
    }

    /** 是否该在本次启动弹出公告：总开关开 + 有内容 + 当前版本未被"不再提示"。 */
    public static boolean shouldShow() {
        ChangelogConfig config = get();
        if (!config.isEnabled() || !config.hasContent()) {
            return false;
        }
        return !config.getVersion().equals(readSeenVersion());
    }

    /** 记录"不再提示"：把当前公告版本号写入 seen 文件。 */
    public static void markSeen() {
        ChangelogConfig config = get();
        try {
            Files.createDirectories(SEEN_FILE.getParent());
            ConfigFiles.writeAtomically(SEEN_FILE, config.getVersion());
        } catch (IOException exception) {
            GTLEnhancedcore.LOGGER.error("[Changelog] 写入不再提示标记失败: {}", SEEN_FILE, exception);
        }
    }

    private static String readSeenVersion() {
        try {
            if (Files.exists(SEEN_FILE)) {
                return Files.readString(SEEN_FILE, StandardCharsets.UTF_8).trim();
            }
        } catch (IOException exception) {
            GTLEnhancedcore.LOGGER.error("[Changelog] 读取不再提示标记失败: {}", SEEN_FILE, exception);
        }
        return "";
    }

    private static ChangelogConfig load() {
        try {
            if (!Files.exists(CONFIG_FILE)) {
                writeDefault();
                return defaults();
            }
            JsonObject root = JsonParser.parseString(
                    Files.readString(CONFIG_FILE, StandardCharsets.UTF_8)).getAsJsonObject();
            boolean enabled = !root.has("enabled") || root.get("enabled").getAsBoolean();
            String version = root.has("version") ? root.get("version").getAsString() : "";
            String title = root.has("title") ? root.get("title").getAsString() : "";
            List<Section> sections = new ArrayList<>();
            if (root.has("sections")) {
                for (JsonElement element : root.getAsJsonArray("sections")) {
                    JsonObject object = element.getAsJsonObject();
                    String heading = object.has("heading") ? object.get("heading").getAsString() : "";
                    List<String> lines = new ArrayList<>();
                    if (object.has("lines")) {
                        for (JsonElement line : object.getAsJsonArray("lines")) {
                            lines.add(line.getAsString());
                        }
                    }
                    sections.add(new Section(heading, Collections.unmodifiableList(lines)));
                }
            }
            return new ChangelogConfig(enabled, version, title, Collections.unmodifiableList(sections));
        } catch (Exception exception) {
            GTLEnhancedcore.LOGGER.error("[Changelog] 读取 {} 失败，改用内置示例", CONFIG_FILE, exception);
            return defaults();
        }
    }

    private static void writeDefault() {
        try {
            Files.createDirectories(CONFIG_FILE.getParent());
            ConfigFiles.writeAtomically(CONFIG_FILE, toJson(defaults()));
            GTLEnhancedcore.LOGGER.info("[Changelog] 已生成默认更新公告配置: {}", CONFIG_FILE);
        } catch (IOException exception) {
            GTLEnhancedcore.LOGGER.error("[Changelog] 生成默认配置失败: {}", CONFIG_FILE, exception);
        }
    }

    private static String toJson(ChangelogConfig config) {
        JsonObject root = new JsonObject();
        root.addProperty("enabled", config.isEnabled());
        root.addProperty("version", config.getVersion());
        root.addProperty("title", config.getTitle());
        JsonArray sections = new JsonArray();
        for (Section section : config.getSections()) {
            JsonObject object = new JsonObject();
            object.addProperty("heading", section.heading());
            JsonArray lines = new JsonArray();
            for (String line : section.lines()) {
                lines.add(line);
            }
            object.add("lines", lines);
            sections.add(object);
        }
        root.add("sections", sections);
        return GSON.toJson(root);
    }

    /** 内置默认 = 2.5 更新公告（用户 2026-10-07 提供，桌面更新说明.txt）。 */
    private static ChangelogConfig defaults() {
        List<Section> sections = List.of(
                new Section("问题修复", List.of(
                        "修复了LDLib可能造成的多方块结构与机器预览界面的崩溃问题",
                        "修复了跨配方类型机器 配方运行时间与实际不一致的问题",
                        "修复了自动重命名机制会覆盖已经命名过的总成的问题")),
                new Section("添加", List.of(
                        "添加新的仓室 创造算力数据靶仓",
                        "无限算力的数据靶仓 可以直接安装到需要算力的多方块结构上")),
                new Section("机制更改", List.of(
                        "跨配方类型机器 运行时的取消并退回按钮 逻辑更改为强制中断当前所有任务 并且退回未加工的物品",
                        "恒星约束聚变堆数值任然不足 重做为拥有无限并行与无限线程",
                        "装配线获得了64并行",
                        "关闭了循环计算",
                        "去除了某些结构的遗留方块 没错 超级冶炼炉的那个灯",
                        "量子操纵者 添加跨配方并行机制 获得512条跨配方线程",
                        "强引力震爆器 获得跨配方类型并行机制",
                        "超维度搅拌机 获得跨配方类型并行机制",
                        "拥有跨配方类型并行机制的原版机器全部添加了1-128的跨配方线程",
                        "统一了数值的显示和位置 部分机制与数值现在看的更明显了",
                        "补齐了跨配方类型并行机器的最大配方等级功能",
                        "重新调整了龙式场约束增殖核心的仓室位置")),
                new Section("结构重置", List.of(
                        "光之蚀梦者 结构重置")),
                new Section("任务", List.of(
                        "重新调整了部分任务的描述与奖励",
                        "重新调整了部分任务的位置与前置")),
                new Section("兼容性警告", List.of(
                        "增强版核心模组的改动特别大 未经测试 不推荐安装任何其他私货模组或者KJS文件",
                        "也请不要更新增强版的任何模组 如因上述操作导致崩溃 BUG的一概不予受理")));
        return new ChangelogConfig(true, "2.5", "GregTech Leisure-增强版：2.5", sections);

    }
}
