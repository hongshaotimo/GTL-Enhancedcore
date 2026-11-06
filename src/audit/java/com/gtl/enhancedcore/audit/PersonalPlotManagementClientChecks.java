package com.gtl.enhancedcore.audit;

import com.google.gson.GsonBuilder;
import com.gtl.enhancedcore.client.plot.CreateWorldPlotScreen;
import com.gtl.enhancedcore.client.plotinfo.PersonalPlotManagementScreen;
import com.gtl.enhancedcore.client.plotinfo.PersonalPlotWorldScreen;
import com.gtl.enhancedcore.common.world.personalplot.PersonalPlotNetwork;
import com.gtl.enhancedcore.common.world.personalplot.PersonalPlotNetwork.Entry;
import com.gtl.enhancedcore.common.world.personalplot.PersonalPlotNetwork.ManagementSnapshot;
import com.gtl.enhancedcore.common.world.personalplot.PersonalPlotWorlds.State;
import com.gtl.enhancedcore.common.world.plot.PlotSettings;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/** Isolated title-screen rendering and real mouse clicks. No packet is executed and no world is opened. */
@Mod("enhancedcore_audit")
public final class PersonalPlotManagementClientChecks {
    private static final Path ROOT = Path.of("C:/IDEA/GTL-Enhancedcore/_audit/personal-plot-management-20261105").toAbsolutePath().normalize();
    private static final int[][] SIZES = {{960, 600, 2}, {1280, 720, 3}, {640, 480, 2}};
    private final List<String> checks = new ArrayList<>(), failures = new ArrayList<>();
    private final List<Map<String, Object>> screenshots = new ArrayList<>();
    private final UUID owner = UUID.fromString("00000000-0000-4000-8000-000000000001");
    private Path directory;
    private int sizeIndex, stage, frames, ticks, sends, stageTicks;
    private boolean done;
    private Screen dashboard;
    private PersonalPlotManagementScreen management;

    public PersonalPlotManagementClientChecks() {
        MinecraftForge.EVENT_BUS.addListener(this::tick);
        MinecraftForge.EVENT_BUS.addListener(this::render);
    }
    private void render(TickEvent.RenderTickEvent event) {
        if (!done && directory != null && event.phase == TickEvent.Phase.END && Minecraft.getInstance().getOverlay() == null) frames++;
    }
    private void tick(TickEvent.ClientTickEvent event) {
        if (done || event.phase != TickEvent.Phase.END || !Boolean.getBoolean("gtl.enhancedcore.personalPlotManagementAudit")) return;
        Minecraft mc = Minecraft.getInstance();
        try {
            if (directory == null) {
                if (mc.getOverlay() != null || mc.screen == null) return;
                directory = mc.gameDirectory.toPath().toAbsolutePath().normalize();
                require(directory.startsWith(ROOT) && !directory.equals(ROOT)
                        && Files.isRegularFile(directory.resolve("personal-plot-management.marker")), "Explicit isolated management fixture");
                require(mc.level == null && mc.getSingleplayerServer() == null && mc.getConnection() == null, "No world or server connection is open");
                try { HarmonyAddRuntimeChecks.run(directory, checks); }
                catch (Throwable failure) { failures.add(failure.toString()); failure.printStackTrace(); }
                Files.createDirectories(directory.resolve("screenshots")); openDashboard(mc); return;
            }
            if (++ticks > 6000) throw new AssertionError("Management UI audit timed out");
            stageTicks++;
            if (mc.getOverlay() != null || frames < 16) return;
            switch (stage) {
                case 0 -> {
                    bounds(dashboard); capture("dashboard-op");
                    click(dashboard, "manage_all"); require(sends == 1, "Real OP dashboard entry submits exactly one management request");
                    management = newManagement(new ManagementSnapshot(true, entries(), 0, 2, null, null, ""));
                    intercept(management); mc.setScreen(management); sends = 0; stage = 1;
                }
                case 1 -> {
                    bounds(management); capture("management-op");
                    require(!button(management, "previous_page").active && button(management, "next_page").active, "Server page boundaries control previous/next buttons");
                    click(management, "delete"); require(sends == 1 && mc.screen == management, "Delete only prepares a challenge and does not open a local-only confirmation");
                    apply(new ManagementSnapshot(true, entries(), 0, 2, owner, UUID.randomUUID(), ""));
                    require(mc.screen instanceof ConfirmScreen, "Matching server challenge opens native ConfirmScreen");
                    require(!button(mc.screen, "delete_confirm").active, "Native confirmation has a twenty-tick input delay");
                    stage = 2;
                }
                case 2 -> {
                    if (stageTicks < 25) return;
                    bounds(mc.screen); warningFits(mc.screen); capture("delete-warning");
                    clickKey(mc.screen, "gui.cancel");
                    require(mc.screen == management && sends == 1, "Cancel returns to management without sending delete confirmation");
                    click(management, "delete"); require(sends == 2, "Retry explicitly requests a fresh deletion challenge");
                    apply(new ManagementSnapshot(true, entries(), 0, 2, owner, UUID.randomUUID(), ""));
                    stage = 3;
                }
                case 3 -> {
                    if (stageTicks < 25) return;
                    click(mc.screen, "delete_confirm");
                    require(mc.screen == management && sends == 3, "Confirm sends exactly one transport operation after native confirmation");
                    apply(new ManagementSnapshot(false, List.of(new Entry(owner, "AuditOwner", State.DELETING, false, false)), 0, 1,
                            null, null, "gtl_enhancedcore.personal_plot.delete_restart_required"));
                    require(!button(management, "delete").active && !button(management, "management_create").active,
                            "Deletion and restart snapshot disables both destructive and creation operations");
                    stage = 4;
                }
                case 4 -> {
                    bounds(management); capture("management-deleting");
                    apply(new ManagementSnapshot(false, List.of(new Entry(owner, "AuditOwner", State.NONE, true, false)), 0, 1,
                            null, null, "gtl_enhancedcore.personal_plot.deleted"));
                    click(management, "management_create"); require(sends == 4, "Dedicated replacement creation sends one request");
                    apply(new ManagementSnapshot(false, List.of(new Entry(owner, "AuditOwner", State.NONE, false, false)), 0, 1,
                            null, null, "gtl_enhancedcore.personal_plot.deleted"));
                    require(!button(management, "management_create").active, "Singleplayer fixture cannot create without returning to customization");
                    stage = 5;
                }
                case 5 -> {
                    capture("management-single"); clickKey(management, "gui.back");
                    require(sends == 5 && mc.screen instanceof net.minecraft.client.gui.screens.TitleScreen,
                            "Back requests a refreshed dashboard and returns the disconnected fixture to its title screen");
                    dashboard = newDashboard(false, true); intercept(dashboard); mc.setScreen(dashboard); stage = 6;
                }
                case 6 -> {
                    bounds(dashboard); capture("dashboard-single");
                    require(button(dashboard, "manage_mine").active, "Singleplayer owner can open personal management");
                    click(dashboard, "customize_create"); require(mc.screen instanceof CreateWorldPlotScreen, "Singleplayer replacement uses original terrain customization entry");
                    Screen guest = newDashboard(false, false); intercept(guest); mc.setScreen(guest);
                    require(!button(guest, "manage_mine").active, "LAN guest management entry is disabled");
                    if (++sizeIndex < SIZES.length) openDashboard(mc); else { finish(mc); return; }
                }
                default -> throw new AssertionError("Unknown stage");
            }
            frames = 0; stageTicks = 0;
        } catch (Throwable failure) { failures.add(failure.toString()); failure.printStackTrace(); finish(mc); }
    }
    private List<Entry> entries() {
        return List.of(new Entry(owner, "AuditOwner", State.READY, false, true),
                new Entry(UUID.fromString("00000000-0000-4000-8000-000000000002"), "SecondPlayer", State.NONE, true, false),
                new Entry(UUID.fromString("00000000-0000-4000-8000-000000000003"), "PendingPlayer", State.PENDING, false, true),
                new Entry(UUID.fromString("00000000-0000-4000-8000-000000000004"), "UnavailablePlayer", State.UNAVAILABLE, false, true));
    }
    private void openDashboard(Minecraft mc) throws Exception {
        int[] size = SIZES[sizeIndex];
        mc.options.guiScale().set(size[2]); GLFW.glfwSetWindowSize(mc.getWindow().getWindow(), size[0], size[1]); mc.resizeDisplay();
        dashboard = newDashboard(true, true); intercept(dashboard); mc.setScreen(dashboard);
        sends = 0; stage = 0; frames = 0; stageTicks = 0; GLFW.glfwSetCursorPos(mc.getWindow().getWindow(), 0, 0);
    }
    private Screen newDashboard(boolean dedicated, boolean owner) throws Exception {
        var value = new PersonalPlotNetwork.Snapshot(dedicated, dedicated && owner, true, !dedicated && owner, true, false,
                PlotSettings.defaults(), PlotSettings.defaults(), State.NONE, null, false, false, "");
        var constructor = PersonalPlotWorldScreen.class.getDeclaredConstructor(PersonalPlotNetwork.Snapshot.class);
        constructor.setAccessible(true); return constructor.newInstance(value);
    }
    private PersonalPlotManagementScreen newManagement(ManagementSnapshot value) throws Exception {
        var constructor = PersonalPlotManagementScreen.class.getDeclaredConstructor(ManagementSnapshot.class);
        constructor.setAccessible(true); return constructor.newInstance(value);
    }
    private void intercept(Screen screen) throws Exception { field(screen.getClass(), "transport").set(screen, (Consumer<Runnable>) operation -> sends++); }
    private void apply(ManagementSnapshot value) throws Exception {
        var method = PersonalPlotManagementScreen.class.getDeclaredMethod("acceptSnapshot", ManagementSnapshot.class);
        method.setAccessible(true); method.invoke(management, value);
    }
    private AbstractWidget button(Screen screen, String suffix) { return buttonKey(screen, "screen.gtl_enhancedcore.personal_plot." + suffix); }
    private AbstractWidget buttonKey(Screen screen, String key) {
        String label = Component.translatable(key).getString();
        return screen.children().stream().filter(AbstractWidget.class::isInstance).map(AbstractWidget.class::cast)
                .filter(widget -> widget.getMessage().getString().equals(label)).findFirst().orElseThrow();
    }
    private void click(Screen screen, String suffix) { clickKey(screen, "screen.gtl_enhancedcore.personal_plot." + suffix); }
    private void clickKey(Screen screen, String key) {
        var widget = buttonKey(screen, key);
        require(widget.active && widget.visible && screen.mouseClicked(widget.getX() + widget.getWidth() / 2.0,
                widget.getY() + widget.getHeight() / 2.0, 0), "Real mouse click: " + key);
        screen.mouseReleased(widget.getX() + widget.getWidth() / 2.0, widget.getY() + widget.getHeight() / 2.0, 0);
    }
    private void warningFits(Screen screen) throws Exception {
        Component message = Component.translatable("screen.gtl_enhancedcore.personal_plot.delete_warning", "AuditOwner", owner.toString());
        require(screen.getNarrationMessage().getString().contains(message.getString()), "Native confirmation contains the complete data-loss warning");
        int messageHeight = Minecraft.getInstance().font.split(message, screen.width - 50).size() * 9;
        int titleTop = Math.max(10, Math.min(80, (screen.height - messageHeight) / 2 - 29));
        int buttonTop = screen.children().stream().filter(AbstractWidget.class::isInstance).map(AbstractWidget.class::cast).mapToInt(AbstractWidget::getY).min().orElseThrow();
        require(titleTop + 20 + messageHeight < buttonTop, "Complete native data-loss warning fits above confirmation buttons");
    }
    private void bounds(Screen screen) {
        for (var child : screen.children()) if (child instanceof AbstractWidget widget && widget.visible)
            require(widget.getX() >= 0 && widget.getY() >= 0 && widget.getX() + widget.getWidth() <= screen.width
                    && widget.getY() + widget.getHeight() <= screen.height, "Visible widget fits viewport: " + widget.getMessage().getString());
    }
    private void capture(String name) throws Exception {
        var mc = Minecraft.getInstance(); int[] size = SIZES[sizeIndex];
        require(mc.getWindow().getWidth() == size[0] && mc.getWindow().getHeight() == size[1], "Actual framebuffer matches requested dimensions");
        if (sizeIndex == 2) require(mc.getWindow().getGuiScaledWidth() == 320 && mc.getWindow().getGuiScaledHeight() == 240, "Actual logical minimum is 320x240");
        String file = "screenshots/" + name + "-" + size[0] + "x" + size[1] + ".png";
        try (var image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) { image.writeToFile(directory.resolve(file)); }
        screenshots.add(Map.of("file", file, "frames", frames, "logicalWidth", mc.getWindow().getGuiScaledWidth(), "logicalHeight", mc.getWindow().getGuiScaledHeight(), "guiScale", size[2]));
    }
    private static Field field(Class<?> type, String name) throws Exception { var value = type.getDeclaredField(name); value.setAccessible(true); return value; }
    private void require(boolean value, String message) { if (!value) throw new AssertionError(message); checks.add(message); }
    private void finish(Minecraft mc) {
        done = true;
        try { Files.writeString(directory.resolve("personal-plot-management-client-checks.json"), new GsonBuilder().setPrettyPrinting().create().toJson(
                Map.of("status", failures.isEmpty() ? "PASS" : "FAIL", "checks", checks, "failures", failures, "screenshots", screenshots,
                        "scope", "Production dashboard entry, management widgets, native deletion confirmation, cancel/confirm, replacement and LAN-owner gates at three real framebuffer sizes. Synthetic server snapshots and intercepted transport; no world/server/disk deletion/restart authorization tested."))); }
        catch (Exception failure) { failure.printStackTrace(); }
        mc.stop();
    }
}
