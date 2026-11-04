package com.gtl.enhancedcore.audit;

import com.google.gson.GsonBuilder;
import com.gtl.enhancedcore.client.plot.CreateWorldPlotScreen;
import com.gtl.enhancedcore.client.plotinfo.PersonalPlotWorldScreen;
import com.gtl.enhancedcore.common.world.personalplot.PersonalPlotNetwork;
import com.gtl.enhancedcore.common.world.personalplot.PersonalPlotWorlds;
import com.gtl.enhancedcore.common.world.plot.PlotPresetShare;
import com.gtl.enhancedcore.common.world.plot.PlotSettings;
import io.netty.buffer.Unpooled;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/** Real dashboard/editor clicks at the title screen; no connection, packets, saves or worlds. */
@Mod("enhancedcore_audit")
public final class PersonalPlotTemplateClientChecks {
    private static final Path ROOT = Path.of("C:/IDEA/GTL-Enhancedcore/_audit/personal-plot-template-20261104").toAbsolutePath().normalize();
    private static final int[][] SIZES = {{960, 600, 2}, {1280, 720, 3}, {640, 480, 2}};
    private final List<String> checks = new ArrayList<>(), failures = new ArrayList<>();
    private final List<Map<String, Object>> screenshots = new ArrayList<>();
    private Path directory;
    private int sizeIndex, scenario, stage, frames, ticks;
    private Screen dashboard;
    private CreateWorldPlotScreen editor;
    private PersonalPlotNetwork.Snapshot snapshot;
    private PlotSettings submitted;
    private int submissions;
    private boolean done;

    public PersonalPlotTemplateClientChecks() {
        MinecraftForge.EVENT_BUS.addListener(this::tick);
        MinecraftForge.EVENT_BUS.addListener(this::render);
    }
    private void render(TickEvent.RenderTickEvent event) {
        if (!done && directory != null && event.phase == TickEvent.Phase.END
                && Minecraft.getInstance().getOverlay() == null) frames++;
    }
    private void tick(TickEvent.ClientTickEvent event) {
        if (done || event.phase != TickEvent.Phase.END || !Boolean.getBoolean("gtl.enhancedcore.personalPlotTemplateAudit")) return;
        Minecraft mc = Minecraft.getInstance();
        try {
            if (directory == null) {
                if (mc.getOverlay() != null || mc.screen == null) return;
                directory = mc.gameDirectory.toPath().toAbsolutePath().normalize();
                require(directory.startsWith(ROOT) && !directory.equals(ROOT)
                        && Files.isRegularFile(directory.resolve("personal-plot-template.marker")), "Explicit isolated fixture");
                require(mc.level == null && mc.getSingleplayerServer() == null && mc.getConnection() == null,
                        "No player world or server connection is open");
                Files.createDirectories(directory.resolve("screenshots"));
                verifySnapshotCodec(); openDashboard(mc); return;
            }
            if (++ticks > 6000) throw new AssertionError("Template UI audit timed out");
            if (mc.getOverlay() != null || frames < 16) return;
            if (stage == 0) {
                bounds(dashboard); capture("dashboard"); openEditor(mc);
                require(draft().equals(initial()), "Opening template editor preserves the original/default settings");
                require(field(CreateWorldPlotScreen.class, "page").getInt(editor) == 0, "Template editor opens its explicit presets page");
                require(button(editor, "gtl_enhancedcore.create_world_plot.campus_preset").visible,
                        "Campus is visible immediately from the real server-template entry");
                stage = 1;
            } else if (stage == 1) {
                bounds(editor); capture("presets");
                click(editor, "gtl_enhancedcore.create_world_plot.campus_preset");
                require(draft().equals(PlotSettings.plannedCampus()), "Real campus preset click selects current production campus");
                require(submissions == 0, "Selecting a terrain preset does not submit a template");
                stage = 2;
            } else if (stage == 2) {
                capture("campus"); click(editor, "gui.cancel");
                require(mc.screen == dashboard && submissions == 0, "Cancel returns to original dashboard without saving");
                openEditor(mc);
                require(draft().equals(initial()), "Reopening after cancel restores the unchanged original/default template");
                click(editor, "gtl_enhancedcore.create_world_plot.campus_preset");
                click(editor, "screen.gtl_enhancedcore.personal_plot.save_template");
                require(mc.screen == dashboard && submissions == 1 && submitted.equals(PlotSettings.plannedCampus()),
                        "Real Save confirms exactly one campus draft and returns to dashboard");
                require(snapshot.templateSettings() == null || snapshot.templateSettings().equals(initial()),
                        "Local draft never mutates the server snapshot");
                openEditor(mc);
                click(editor, "screen.gtl_enhancedcore.personal_plot.save_template");
                require(submissions == 1 && submitted.equals(initial()), "Saving without edits preserves existing/default template");
                if (++scenario < 2) openDashboard(mc);
                else if (++sizeIndex < SIZES.length) { scenario = 0; openDashboard(mc); }
                else { finish(mc); return; }
            }
            frames = 0;
        } catch (Throwable failure) {
            failures.add(failure.toString()); failure.printStackTrace(); finish(mc);
        }
    }
    private PlotSettings initial() {
        return scenario == 0 ? PlotSettings.defaults() : PlotSettings.preset(7).withGroundBlock("minecraft:red_concrete");
    }
    private PlotSettings draft() throws Exception { return (PlotSettings)field(CreateWorldPlotScreen.class, "draft").get(editor); }
    private void openDashboard(Minecraft mc) throws Exception {
        int[] size = SIZES[sizeIndex];
        mc.options.guiScale().set(size[2]); GLFW.glfwSetWindowSize(mc.getWindow().getWindow(), size[0], size[1]); mc.resizeDisplay();
        PlotSettings saved = scenario == 0 ? null : initial();
        snapshot = new PersonalPlotNetwork.Snapshot(true, true, false, false, false, saved != null,
                saved, null, PersonalPlotWorlds.State.NONE, null, false, false, "");
        var constructor = PersonalPlotWorldScreen.class.getDeclaredConstructor(PersonalPlotNetwork.Snapshot.class);
        constructor.setAccessible(true); dashboard = constructor.newInstance(snapshot); mc.setScreen(dashboard);
        stage = 0; frames = 0; GLFW.glfwSetCursorPos(mc.getWindow().getWindow(), 0, 0);
    }
    private void openEditor(Minecraft mc) throws Exception {
        click(dashboard, "screen.gtl_enhancedcore.personal_plot.edit_template");
        require(mc.screen instanceof CreateWorldPlotScreen, "Real OP dashboard button opens production template editor");
        editor = (CreateWorldPlotScreen) mc.screen;
        // Replace only the final transport callback, after traversing the real entry. This client
        // remains at the title screen and cannot send TEMPLATE packets or touch a world.
        submissions = 0; submitted = null;
        field(CreateWorldPlotScreen.class, "accepted").set(editor, (Consumer<PlotSettings>) settings -> {
            submitted = PlotPresetShare.decode(PlotPresetShare.encode(settings)); submissions++;
        });
    }
    private void verifySnapshotCodec() throws Exception {
        var encode = PersonalPlotNetwork.class.getDeclaredMethod("encode", PersonalPlotNetwork.Snapshot.class, FriendlyByteBuf.class);
        var decode = PersonalPlotNetwork.class.getDeclaredMethod("decode", FriendlyByteBuf.class);
        encode.setAccessible(true); decode.setAccessible(true);
        var campus = PlotSettings.plannedCampus();
        var value = new PersonalPlotNetwork.Snapshot(true, true, false, false, false, true,
                campus, null, PersonalPlotWorlds.State.NONE, null, false, false, "");
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            encode.invoke(null, value, buffer);
            require(value.equals(decode.invoke(null, buffer)) && buffer.readableBytes() == 0,
                    "Real personal-plot snapshot codec roundtrips the current saved campus template");
        } finally { buffer.release(); }
    }
    private AbstractWidget button(Screen screen, String key) {
        String text = Component.translatable(key).getString();
        return screen.children().stream().filter(AbstractWidget.class::isInstance).map(AbstractWidget.class::cast)
                .filter(widget -> widget.getMessage().getString().equals(text)).findFirst().orElseThrow();
    }
    private void click(Screen screen, String key) {
        var button = button(screen, key);
        require(button.active && button.visible && screen.mouseClicked(button.getX() + button.getWidth() / 2.0,
                button.getY() + button.getHeight() / 2.0, 0), "Real mouse click: " + key);
        screen.mouseReleased(button.getX() + button.getWidth() / 2.0, button.getY() + button.getHeight() / 2.0, 0);
    }
    private void bounds(Screen screen) {
        for (var child : screen.children()) if (child instanceof AbstractWidget widget && widget.visible) {
            require(widget.getX() >= 0 && widget.getY() >= 0 && widget.getX() + widget.getWidth() <= screen.width
                    && widget.getY() + widget.getHeight() <= screen.height, "Visible widget fits logical viewport: " + widget.getMessage().getString());
            if (screen instanceof CreateWorldPlotScreen) for (String key : List.of(
                    "page_presets", "page_layout", "page_roads", "page_materials", "page_height", "campus_preset"))
                if (widget.getMessage().getString().equals(Component.translatable("gtl_enhancedcore.create_world_plot." + key).getString()))
                    require(Minecraft.getInstance().font.width(widget.getMessage()) <= widget.getWidth() - 10,
                            "Full translated preset/tab name fits measured font width: " + key);
        }
    }
    private void capture(String name) throws Exception {
        var mc = Minecraft.getInstance(); var expected = SIZES[sizeIndex];
        require(mc.getWindow().getWidth() == expected[0] && mc.getWindow().getHeight() == expected[1], "Actual framebuffer has requested size");
        if (sizeIndex == 2) require(mc.getWindow().getGuiScaledWidth() == 320 && mc.getWindow().getGuiScaledHeight() == 240, "Actual logical minimum is 320x240");
        String file = "screenshots/" + (scenario == 0 ? "missing" : "existing") + "-" + name + "-" + expected[0] + "x" + expected[1] + ".png";
        try (var image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) { image.writeToFile(directory.resolve(file)); }
        screenshots.add(Map.of("file", file, "frames", frames, "logicalWidth", mc.getWindow().getGuiScaledWidth(),
                "logicalHeight", mc.getWindow().getGuiScaledHeight(), "guiScale", expected[2]));
    }
    private static Field field(Class<?> type, String name) throws Exception { var field = type.getDeclaredField(name); field.setAccessible(true); return field; }
    private void require(boolean result, String text) { if (!result) throw new AssertionError(text); checks.add(text); }
    private void finish(Minecraft mc) {
        done = true;
        try { Files.writeString(directory.resolve("personal-plot-template-checks.json"), new GsonBuilder().setPrettyPrinting().create().toJson(
                Map.of("status", failures.isEmpty() ? "PASS" : "FAIL", "checks", checks, "failures", failures, "screenshots", screenshots,
                        "scope", "Real production OP dashboard entry, preset selection, cancel and save clicks at three framebuffer/GUI sizes. Synthetic server snapshots; only transport callback intercepted. No server authorization, disk write or restart tested; no worlds opened."))); }
        catch (Exception failure) { failure.printStackTrace(); }
        mc.stop();
    }
}
