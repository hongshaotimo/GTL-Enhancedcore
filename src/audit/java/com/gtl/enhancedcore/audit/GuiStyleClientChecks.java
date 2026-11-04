package com.gtl.enhancedcore.audit;

import com.google.gson.GsonBuilder;
import com.gtl.enhancedcore.client.config.ClientTooltipConfig;
import com.gtl.enhancedcore.common.config.TooltipOverrides;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.common.Mod;

/** Real production screens, rendered frames and detached extension scenes; never opens a saved world. */
@Mod("enhancedcore_audit")
public final class GuiStyleClientChecks {
    private static final Path ROOT = Path.of("C:/IDEA/GTL-Enhancedcore/_work/gui-unification-20261102").toAbsolutePath().normalize();
    private static final Path JADE_ROOT = Path.of("C:/IDEA/GTL-Enhancedcore/_work/jade-compact-20261102").toAbsolutePath().normalize();
    private static final Path JADE_NATIVE_ROOT = Path.of("C:/IDEA/GTL-Enhancedcore/_work/jade-native-20261102").toAbsolutePath().normalize();
    private static final Path JEI_ROLLBACK_ROOT = Path.of("C:/IDEA/GTL-Enhancedcore/_work/jei-team-20261104").toAbsolutePath().normalize();
    private static final List<String> CHECKS = new ArrayList<>();
    @FunctionalInterface public interface ScreenFactory { Screen create() throws Exception; }
    @FunctionalInterface public interface ScreenCheck { void verify(Screen screen) throws Exception; }
    public record Scene(String id, ScreenFactory factory, ScreenCheck verify) {
        public Scene(String id, ScreenFactory factory) { this(id, factory, screen -> {}); }
    }
    private final List<Scene> scenes = new ArrayList<>();
    private final List<Map<String, Object>> results = new ArrayList<>();
    private final List<String> optionalExtensions = new ArrayList<>();
    private final List<java.lang.reflect.Method> scenePreparers = new ArrayList<>();
    private final boolean baseline = Boolean.getBoolean("gtl.enhancedcore.guiStyleBaseline");
    private Path directory;
    private Screen current;
    private int index, renderedFrames;
    private boolean done;

    public GuiStyleClientChecks() {
        MinecraftForge.EVENT_BUS.addListener(this::tick);
        MinecraftForge.EVENT_BUS.addListener(this::renderTick);
    }
    public static void require(boolean condition, String description) {
        if (!condition) throw new AssertionError(description);
        CHECKS.add(description);
    }
    private void renderTick(TickEvent.RenderTickEvent event) {
        if (!done && current != null && event.phase == TickEvent.Phase.END
                && Minecraft.getInstance().screen == current && Minecraft.getInstance().getOverlay() == null) renderedFrames++;
    }
    private void tick(TickEvent.ClientTickEvent event) {
        if (done || event.phase != TickEvent.Phase.END || !Boolean.getBoolean("gtl.enhancedcore.guiStyleAudit")) return;
        var mc = Minecraft.getInstance();
        if (directory == null && (mc.getOverlay() != null || !(mc.screen instanceof TitleScreen))) return;
        try {
            if (directory == null) {
                directory = mc.gameDirectory.toPath().toAbsolutePath().normalize();
                require((directory.startsWith(ROOT) && !directory.equals(ROOT))
                        || (directory.startsWith(JADE_ROOT) && !directory.equals(JADE_ROOT))
                        || (directory.startsWith(JADE_NATIVE_ROOT) && !directory.equals(JADE_NATIVE_ROOT))
                        || (directory.startsWith(JEI_ROLLBACK_ROOT) && !directory.equals(JEI_ROLLBACK_ROOT)), "Dedicated GUI task instance");
                require(Files.isRegularFile(directory.resolve("gui-style.marker")), "Explicit GUI audit marker");
                require(mc.level == null && mc.player == null && mc.getSingleplayerServer() == null, "Starts at title screen without a world");
                Files.createDirectories(directory.resolve("screenshots"));
                ClientTooltipConfig.initialize();
                if (!Boolean.getBoolean("gtl.enhancedcore.guiStyleSkipConfig")) configurationScenes();
                extensionScenes();
                require(!scenes.isEmpty(), "At least one real production scene");
            }
            if (current == null) {
                if (index == scenes.size()) { finish(null); return; }
                for (var preparer : scenePreparers)
                    if (!((Boolean)preparer.invoke(null, scenes.get(index).id()))) return;
                current = scenes.get(index).factory().create();
                require(current != null, scenes.get(index).id() + " creates a production screen");
                mc.setScreen(current);
                renderedFrames = 0;
                return;
            }
            if (mc.getOverlay() != null || renderedFrames < 12) return;
            require(mc.screen == current, scenes.get(index).id() + " remains the active screen");
            var result = inspect(current);
            scenes.get(index).verify().verify(current);
            String filename = String.format("screenshots/%02d-%s.png", index, scenes.get(index).id());
            try (var image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
                require(image.getWidth() > 0 && image.getHeight() > 0, "Rendered framebuffer has dimensions");
                image.writeToFile(directory.resolve(filename));
                result.put("pixelWidth", image.getWidth()); result.put("pixelHeight", image.getHeight());
            }
            result.put("scene", scenes.get(index).id()); result.put("screen", current.getClass().getName());
            result.put("screenshot", filename); result.put("renderedFrames", renderedFrames);
            results.add(result);
            index++; current = null;
        } catch (Throwable failure) { failure.printStackTrace(); finish(failure); }
    }
    private void configurationScenes() throws Exception {
        scenes.add(new Scene("config-home", () -> construct("EnhancedConfigScreen", new Class<?>[]{Screen.class}, (Object)null)));
        scenes.add(new Scene("config-files", () -> construct("ConfigFilesScreen", new Class<?>[]{Screen.class}, (Object)null)));
        scenes.add(new Scene("tips-browser", () -> construct("ItemTipBrowser", new Class<?>[]{Screen.class}, (Object)null)));
        scenes.add(new Scene("tips-empty-search", () -> {
            var screen = construct("ItemTipBrowser", new Class<?>[]{Screen.class}, (Object)null);
            field(screen.getClass(), "query").set(screen, "no_item_matches_this_query_20261102");
            return screen;
        }, screen -> {
            var search = screen.children().stream().filter(EditBox.class::isInstance).map(EditBox.class::cast).findFirst().orElseThrow();
            require(search.getValue().equals("no_item_matches_this_query_20261102"), "Search query survives screen initialization");
        }));
        String item = ClientTooltipConfig.itemIds().stream().filter(id -> id.endsWith(":extreme_reactor")).findFirst()
                .orElseGet(() -> ClientTooltipConfig.itemIds().getFirst());
        scenes.add(new Scene("tips-item", () -> construct("ItemTipScreen", new Class<?>[]{Screen.class, String.class}, null, item)));
        scenes.add(new Scene("tips-line-editor", () -> construct("TipLineScreen",
                new Class<?>[]{Screen.class, TooltipOverrides.Line.class, Consumer.class}, null,
                TooltipOverrides.Line.text("[白色]支持模式：[动态彩虹]组装机[关闭彩虹] · Assembler\n[灰色]文字预览 / Preview"),
                (Consumer<TooltipOverrides.Line>)line -> {})));
        scenes.add(new Scene("tips-color-picker", () -> {
            var choiceClass = Class.forName("com.gtl.enhancedcore.client.config.TooltipColorButton$Choice");
            var ctor = choiceClass.getDeclaredConstructor(String.class, boolean.class); ctor.setAccessible(true);
            return construct("TooltipColorScreen", new Class<?>[]{Screen.class, choiceClass, Consumer.class}, null,
                    ctor.newInstance("#146A78", false), (Consumer<Object>)choice -> {});
        }));
        scenes.add(new Scene("tips-font-picker", () -> construct("TooltipFontScreen",
                new Class<?>[]{Screen.class, String.class, boolean.class, Consumer.class}, null, "minecraft:uniform", false,
                (Consumer<String>)font -> {})));
        scenes.add(new Scene("tips-confirmation", () -> construct("ConfirmScreen",
                new Class<?>[]{Consumer.class, Component.class, Component.class}, (Consumer<Boolean>)answer -> {},
                Component.translatable("config.gtl_enhancedcore.mode_reset_title"), Component.translatable("config.gtl_enhancedcore.mode_reset_warning"))));
    }
    private void extensionScenes() throws Exception {
        String configured = System.getProperty("gtl.enhancedcore.guiStyleExtensions", "ChamberStyleChecks");
        for (String name : configured.split(",")) {
            if (name.isBlank()) continue;
            String className = name.contains(".") ? name.strip() : "com.gtl.enhancedcore.audit." + name.strip();
            Class<?> extension;
            try { extension = Class.forName(className); }
            catch (ClassNotFoundException absent) { optionalExtensions.add(className + " absent"); continue; }
            try { scenePreparers.add(extension.getMethod("prepareScene", String.class)); }
            catch (NoSuchMethodException absent) { /* Most scene providers need no asynchronous lifecycle preparation. */ }
            Object value = extension.getMethod("scenes").invoke(null);
            if (!(value instanceof List<?> list)) throw new IllegalArgumentException(className + ".scenes must return List<Scene>");
            for (Object scene : list) scenes.add((Scene)scene);
            optionalExtensions.add(className + " loaded");
        }
    }
    public static Screen construct(String shortName, Class<?>[] types, Object... args) throws Exception {
        var type = Class.forName("com.gtl.enhancedcore.client.config." + shortName);
        Constructor<?> constructor = type.getDeclaredConstructor(types); constructor.setAccessible(true);
        return (Screen)constructor.newInstance(args);
    }
    public static Field field(Class<?> type, String name) throws NoSuchFieldException {
        for (Class<?> candidate = type; candidate != null; candidate = candidate.getSuperclass()) {
            try { var field = candidate.getDeclaredField(name); field.setAccessible(true); return field; }
            catch (NoSuchFieldException absent) { /* The target field may belong to ConfigPage. */ }
        }
        throw new NoSuchFieldException(type.getName() + "." + name);
    }
    private Map<String, Object> inspect(Screen screen) {
        var widgets = screen.children().stream().filter(AbstractWidget.class::isInstance).map(AbstractWidget.class::cast)
                .filter(widget -> widget.visible).toList();
        var bounds = new ArrayList<Map<String, Object>>();
        var issues = new ArrayList<String>();
        for (var widget : widgets) {
            var entry = new LinkedHashMap<String, Object>();
            entry.put("type", widget.getClass().getName()); entry.put("label", widget.getMessage().getString());
            entry.put("x", widget.getX()); entry.put("y", widget.getY());
            entry.put("width", widget.getWidth()); entry.put("height", widget.getHeight()); entry.put("active", widget.active);
            bounds.add(entry);
            boolean inBounds = widget.getX() >= 0 && widget.getY() >= 0 && widget.getWidth() > 0 && widget.getHeight() > 0
                    && widget.getX() + widget.getWidth() <= screen.width && widget.getY() + widget.getHeight() <= screen.height;
            if (!inBounds) issues.add("Outside viewport: " + widget.getMessage().getString());
        }
        for (int i = 0; i < widgets.size(); i++) for (int j = i + 1; j < widgets.size(); j++) {
            var a = widgets.get(i); var b = widgets.get(j);
            if (a.getX() < b.getX() + b.getWidth() && a.getX() + a.getWidth() > b.getX()
                    && a.getY() < b.getY() + b.getHeight() && a.getY() + a.getHeight() > b.getY())
                issues.add("Overlapping widgets: " + a.getMessage().getString() + " / " + b.getMessage().getString());
        }
        if (!baseline) require(issues.isEmpty(), scenes.get(index).id() + " controls fit without overlap: " + issues);
        var result = new LinkedHashMap<String, Object>();
        result.put("guiWidth", screen.width); result.put("guiHeight", screen.height);
        result.put("widgets", bounds); result.put("layoutIssues", issues);
        return result;
    }
    private void finish(Throwable failure) {
        done = true;
        var result = new LinkedHashMap<String, Object>();
        result.put("status", failure == null ? "PASS" : "FAIL"); result.put("baseline", baseline);
        result.put("checks", CHECKS); result.put("scenes", results); result.put("extensions", optionalExtensions);
        result.put("worldOpened", System.getProperty("gtl.enhancedcore.guiStyleCreatedWorld") != null);
        result.put("auditWorld", System.getProperty("gtl.enhancedcore.guiStyleCreatedWorld", "none"));
        result.put("scope", "Real Forge production configuration screens and detached scenes begin at title screen. JEI may create the dedicated fresh flat gui-style-jei audit world for its real login/recipe-sync lifecycle. No player save is copied or opened.");
        if (failure != null) result.put("error", failure.toString());
        try {
            if (directory == null) directory = Minecraft.getInstance().gameDirectory.toPath();
            Files.writeString(directory.resolve("gui-style-checks.json"), new GsonBuilder().setPrettyPrinting().create().toJson(result));
        } catch (Exception error) { error.printStackTrace(); }
        // Shutdown unloads JEI before the next render; discard screens retaining its runtime first.
        Minecraft.getInstance().setScreen(new TitleScreen());
        Minecraft.getInstance().stop();
    }
}
