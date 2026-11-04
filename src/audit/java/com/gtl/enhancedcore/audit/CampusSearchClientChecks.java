package com.gtl.enhancedcore.audit;

import com.google.gson.GsonBuilder;
import com.gtl.enhancedcore.client.plot.CampusPlanScreen;
import com.gtl.enhancedcore.common.world.plot.PlotSettings;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.network.chat.Component;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import org.lwjgl.glfw.GLFW;

/** Search interaction audit on synthetic preview/session screens; never opens or connects to a world. */
@Mod("enhancedcore_audit")
public final class CampusSearchClientChecks {
    private static final Path ROOT = Path.of("C:/IDEA/GTL-Enhancedcore/_audit/campus-search-20261104").toAbsolutePath().normalize();
    private static final int[][] SIZES = {{960, 600, 2}, {1280, 720, 3}, {640, 480, 2}};
    private final List<String> checks = new ArrayList<>(), failures = new ArrayList<>();
    private final List<Map<String, Object>> screenshots = new ArrayList<>();
    private Path directory;
    private CampusPlanScreen screen;
    private EditBox search;
    private int sizeIndex, mode, stage, frames, ticks;
    private boolean done;
    private int movementTicks, uploadsBeforeMovement;
    private String immediateFile;

    public CampusSearchClientChecks() {
        MinecraftForge.EVENT_BUS.addListener(this::tick);
        MinecraftForge.EVENT_BUS.addListener(this::render);
    }
    private void render(TickEvent.RenderTickEvent event) {
        if (!done && directory != null && event.phase == TickEvent.Phase.END
                && Minecraft.getInstance().getOverlay() == null) frames++;
    }
    private void tick(TickEvent.ClientTickEvent event) {
        if (done || event.phase != TickEvent.Phase.END || !Boolean.getBoolean("gtl.enhancedcore.campusSearchAudit")) return;
        Minecraft mc = Minecraft.getInstance();
        try {
            if (directory == null) {
                if (mc.getOverlay() != null || !(mc.screen instanceof TitleScreen)) return;
                directory = mc.gameDirectory.toPath().toAbsolutePath().normalize();
                require(directory.startsWith(ROOT) && !directory.equals(ROOT)
                        && Files.isRegularFile(directory.resolve("campus-search.marker")), "Explicit isolated title-screen fixture");
                require(mc.level == null && mc.getSingleplayerServer() == null && mc.getConnection() == null,
                        "No world, integrated server or network connection is open");
                Files.createDirectories(directory.resolve("screenshots"));
                open(mc, false); return;
            }
            if (++ticks > 6000) throw new AssertionError("Campus search audit timed out");
            if (mc.getOverlay() != null || frames < (stage == 5 ? 1 : stage == 4 ? 0 : 16) || !atlasReady()) return;
            if (stage == 0) {
                bounds(); capture("overview");
                testQuery(mc, "GTCEU:ASSEMBLY_LINE", 1, true);
                testQuery(mc, "advanced assembly", -1, true);
                String localized = net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(new ResourceLocation("gtceu:assembly_line")).getName().getString();
                testQuery(mc, localized, -1, true);
                testQuery(mc, "nano_forge_2", 1, true);
                testQuery(mc, "gtceu:electric_blast_furnace", 12, true);
                var matches = (List<?>)field("searchMatches").get(screen);
                clickNext();
                require(field("selected").get(screen).equals(matches.get(1)), "Next mouse click cycles repeated slots");
                key(257); require(field("selected").get(screen).equals(matches.get(2)), "Enter cycles forward");
                keyShiftEnter(); require(field("selected").get(screen).equals(matches.get(1)), "Shift+Enter cycles backward");
                for (int i = 1; i < matches.size(); i++) clickNext();
                require(field("selected").get(screen).equals(matches.get(0)), "Match navigation wraps");
                require(!(boolean)field("pending").get(screen), "Search never starts a teleport request");
                screen.resize(mc, screen.width, screen.height);
                search = (EditBox)field("searchBox").get(screen);
                require(search.getValue().equals("gtceu:electric_blast_furnace"), "Search text survives resize");
                require(field("selected").get(screen).equals(matches.get(0)), "Resize preserves selected match");
                stage = 1; frames = 0;
            } else if (stage == 1) {
                capture("matches");
                testQuery(mc, "no-such-machine", 0, false);
                stage = 2; frames = 0;
            } else if (stage == 2) {
                capture("no-results");
                search.setValue(""); require(((List<?>)field("searchMatches").get(screen)).isEmpty(), "Clear removes results");
                require(!(boolean)field("pending").get(screen), "No search path sends a teleport request");
                screen.mouseScrolled(mapCenterX(), mapCenterY(), -6); stage = 3; frames = 0;
            } else if (stage == 3) {
                require(atlasCount("sourceBuilds") == 1, "Atlas samples its world plan once per screen including resize");
                uploadsBeforeMovement = atlasCount("textureUploads"); movementTicks = 0;
                screen.mouseClicked(mapCenterX(), mapCenterY(), 0);
                screen.mouseDragged(mapCenterX() + 6, mapCenterY(), 0, 6, 0);
                stage = 4; frames = 0;
            } else if (stage == 4) {
                double dx = movementTicks % 2 == 0 ? 6 : -6;
                screen.mouseDragged(mapCenterX() + 6 + dx, mapCenterY(), 0, dx, 0);
                screen.mouseScrolled(mapCenterX(), mapCenterY(), movementTicks % 2 == 0 ? .02 : -.02);
                require(atlasCount("sourceBuilds") == 1 && atlasCount("textureUploads") == uploadsBeforeMovement,
                        "Continuous drag/wheel within one level reuses source and texture");
                if (++movementTicks >= 24) {
                    screen.mouseReleased(mapCenterX(), mapCenterY(), 0);
                    pointer(0, 0); screen.mouseScrolled(mapCenterX(), mapCenterY(), .02);
                    stage = 5; frames = 0;
                }
            } else if (stage == 5) {
                immediateFile = capture("wheel-immediate"); stage = 6; frames = 0;
            } else if (stage == 6) {
                String idleFile = capture("wheel-idle");
                compareMap(immediateFile, idleFile);
                require(atlasCount("sourceBuilds") == 1, "Idle time never regenerates the atlas source");
                if (mode == 1) {
                    testQuery(mc, "gtceu:assembly_line", 1, true); pointer(mapCenterX(), mapCenterY());
                    stage = 7; frames = 0;
                } else advance(mc);
            } else if (stage == 7) {
                capture("hover-name-only"); advance(mc);
            }
        } catch (Throwable failure) { failures.add(failure.toString()); failure.printStackTrace(); finish(mc); }
    }
    private void open(Minecraft mc, boolean resize) throws Exception {
        int[] size = SIZES[sizeIndex];
        mc.options.guiScale().set(size[2]); GLFW.glfwSetWindowSize(mc.getWindow().getWindow(), size[0], size[1]); mc.resizeDisplay();
        Screen parent = new TitleScreen();
        if (mode == 0) screen = new CampusPlanScreen(parent, PlotSettings.plannedCampus());
        else {
            Class<?> sessionType = Class.forName(CampusPlanScreen.class.getName() + "$TeleportSession");
            var sessionConstructor = sessionType.getDeclaredConstructor(BlockPos.class, ResourceLocation.class, UUID.class);
            sessionConstructor.setAccessible(true);
            Object session = sessionConstructor.newInstance(new BlockPos(0, 80, 0), Level.OVERWORLD.location(), UUID.randomUUID());
            var constructor = CampusPlanScreen.class.getDeclaredConstructor(Screen.class, PlotSettings.class, sessionType);
            constructor.setAccessible(true);
            screen = (CampusPlanScreen)constructor.newInstance(null, PlotSettings.plannedCampus(), session);
        }
        mc.setScreen(screen); search = (EditBox)field("searchBox").get(screen);
        if (resize) { type("assembly"); }
        stage = resize ? 1 : 0; frames = 0;
        GLFW.glfwSetCursorPos(mc.getWindow().getWindow(), 0, 0);
    }
    private void testQuery(Minecraft mc, String query, int count, boolean focus) {
        search.setValue("");
        screen.mouseClicked(search.getX() + 5, search.getY() + 5, 0);
        for (int index = 0; index < query.length(); index++) {
            char value = query.charAt(index);
            require(screen.charTyped(value, 0), "EditBox accepts typed search character");
        }
        List<?> matches = (List<?>)get("searchMatches");
        require(count < 0 ? !matches.isEmpty() : matches.size() == count, "Query returns expected result count: " + query + " => " + (count < 0 ? ">=1" : count));
        if (count < 0) require(matches.stream().anyMatch(value -> ((com.gtl.enhancedcore.common.world.campus.CampusPlanAccess.Target)value).plot().id().equals("gtceu:assembly_line")),
                "Name query includes canonical assembly-line reservation");
        if (count != 0) {
            require(((Number)get("searchIndex")).intValue() == 0, "New query selects its first match");
            require(get("selected").equals(matches.get(0)), "Search focuses and selects the first result");
        } else require(Component.translatable("gtl_enhancedcore.campus.teleport.search_none").getString().length() > 0,
                "No-result state has localized text");
    }
    private void type(String value) { search.setValue(""); for (char ch : value.toCharArray()) search.charTyped(ch, 0); }
    private void key(int code) throws Exception { screen.keyPressed(code, 0, 0); }
    private void keyShiftEnter() throws Exception { screen.keyPressed(257, 0, 1); }
    private void clickNext() throws Exception {
        var widget = (AbstractWidget)field("nextMatch").get(screen);
        require(widget.active && screen.mouseClicked(widget.getX() + 5, widget.getY() + 5, 0), "Real Next button mouse click");
        screen.mouseReleased(widget.getX() + 5, widget.getY() + 5, 0);
        screen.mouseClicked(search.getX() + 5, search.getY() + 5, 0);
    }
    private void bounds() {
        for (var child : screen.children()) if (child instanceof AbstractWidget widget && widget.visible)
            require(widget.getX() >= 0 && widget.getY() >= 0 && widget.getX() + widget.getWidth() <= screen.width
                    && widget.getY() + widget.getHeight() <= screen.height, "Visible search/map control fits viewport: " + widget.getMessage().getString());
    }
    private String capture(String name) throws Exception {
        var mc = Minecraft.getInstance(); int[] size = SIZES[sizeIndex];
        require(mc.getWindow().getWidth() == size[0] && mc.getWindow().getHeight() == size[1], "Actual framebuffer has requested dimensions");
        if (sizeIndex == 2) require(mc.getWindow().getGuiScaledWidth() == 320 && mc.getWindow().getGuiScaledHeight() == 240,
                "Actual logical minimum is 320x240");
        String file = "screenshots/" + (mode == 0 ? "preview-" : "session-") + name + "-" + size[0] + "x" + size[1] + ".png";
        try (var image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) { image.writeToFile(directory.resolve(file)); }
        screenshots.add(Map.of("file", file, "frames", frames, "logicalWidth", mc.getWindow().getGuiScaledWidth(),
                "logicalHeight", mc.getWindow().getGuiScaledHeight(), "guiScale", size[2]));
        return file;
    }
    private void advance(Minecraft mc) throws Exception {
        if (++mode < 2) open(mc, false);
        else if (++sizeIndex < SIZES.length) { mode = 0; open(mc, false); }
        else finish(mc);
    }
    private int mapCenterX() throws Exception { return (field("mapLeftEdge").getInt(screen) + field("mapRightEdge").getInt(screen)) / 2; }
    private int mapCenterY() throws Exception { return (field("mapTop").getInt(screen) + field("mapBottom").getInt(screen)) / 2; }
    private boolean atlasReady() throws Exception { Object map = field("map").get(screen); return (boolean)map.getClass().getMethod("ready").invoke(map); }
    private int atlasCount(String getter) throws Exception { Object map = field("map").get(screen); return (int)map.getClass().getMethod(getter).invoke(map); }
    private void pointer(int x, int y) throws Exception {
        var mc = Minecraft.getInstance();
        double px = (double)x * mc.getWindow().getScreenWidth() / mc.getWindow().getGuiScaledWidth();
        double py = (double)y * mc.getWindow().getScreenHeight() / mc.getWindow().getGuiScaledHeight();
        GLFW.glfwSetCursorPos(mc.getWindow().getWindow(), px, py);
        ObfuscationReflectionHelper.findField(MouseHandler.class, "f_91507_").setDouble(mc.mouseHandler, px);
        ObfuscationReflectionHelper.findField(MouseHandler.class, "f_91508_").setDouble(mc.mouseHandler, py);
        screen.mouseMoved(x, y);
    }
    private void compareMap(String first, String second) throws Exception {
        var mc = Minecraft.getInstance();
        var a = javax.imageio.ImageIO.read(directory.resolve(first).toFile());
        var b = javax.imageio.ImageIO.read(directory.resolve(second).toFile());
        double sx = (double)a.getWidth() / screen.width, sy = (double)a.getHeight() / screen.height;
        int left = (int)Math.ceil(field("mapLeftEdge").getInt(screen) * sx);
        int right = (int)Math.floor(field("mapRightEdge").getInt(screen) * sx);
        int top = (int)Math.ceil(field("mapTop").getInt(screen) * sy);
        int bottom = (int)Math.floor(field("mapBottom").getInt(screen) * sy);
        int changed = 0;
        for (int y = top; y < bottom; y++) for (int x = left; x < right; x++) if (a.getRGB(x, y) != b.getRGB(x, y)) changed++;
        require(changed == 0, "Immediate wheel frame and idle frame have identical map pixels: changed=" + changed);
    }
    private Object get(String name) { try { return field(name).get(screen); } catch (Exception error) { throw new IllegalStateException(error); } }
    private Field field(String name) throws Exception { var field = CampusPlanScreen.class.getDeclaredField(name); field.setAccessible(true); return field; }
    private void require(boolean value, String text) { if (!value) throw new AssertionError(text); checks.add(text); }
    private void finish(Minecraft mc) {
        done = true;
        try { Files.writeString(directory.resolve("campus-search-checks.json"), new GsonBuilder().setPrettyPrinting().create().toJson(
                Map.of("status", failures.isEmpty() ? "PASS" : "FAIL", "checks", checks, "failures", failures, "screenshots", screenshots,
                        "scope", "Production preview and reflective synthetic teleporter session. EditBox typing/buttons/keys, localization/ID/upgrade aliases, duplicate-slot cycling, no-results, clearing and resize retention. Search never calls teleport. No server connection or world opened."))); }
        catch (Exception failure) { failure.printStackTrace(); }
        mc.stop();
    }
}
