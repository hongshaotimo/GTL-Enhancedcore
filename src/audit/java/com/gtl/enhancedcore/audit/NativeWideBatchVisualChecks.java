package com.gtl.enhancedcore.audit;

import com.google.gson.GsonBuilder;
import com.gtl.enhancedcore.common.recipe.iv.IvMachineDisplay;
import com.gtl.enhancedcore.common.recipe.iv.IvPresentation;
import com.hepdd.gtmthings.data.WirelessEnergySavaedData;
import com.lowdragmc.lowdraglib.gui.modular.ModularUI;
import com.lowdragmc.lowdraglib.gui.modular.ModularUIGuiContainer;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.gui.modular.WidgetUIAccess;
import io.netty.buffer.Unpooled;
import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;
import snownee.jade.api.config.IWailaConfig;
import snownee.jade.api.theme.IThemeHelper;
import snownee.jade.impl.Tooltip;
import snownee.jade.impl.ui.TextElement;
import snownee.jade.overlay.OverlayRenderer;
import snownee.jade.overlay.TooltipRenderer;

/** Real prepared order snapshots, production machine menu and shared presentation in native Jade. */
@Mod("enhancedcore_audit")
public final class NativeWideBatchVisualChecks {
    private static final int[][] SIZES = {{960, 600, 2}, {1280, 720, 3}, {640, 480, 2}};
    private final List<Map<String,Object>> scenes = new ArrayList<>();
    private Path directory;
    private WirelessEnergySavaedData previousData;
    private NativeWideBatchChecks.VisualFixture fixture;
    private WideScreen screen;
    private int scenario, viewport, mode, frames, stable, resizeTicks;
    private boolean done, resizing;

    public NativeWideBatchVisualChecks() {
        MinecraftForge.EVENT_BUS.addListener(this::tick);
        MinecraftForge.EVENT_BUS.addListener(this::renderTick);
    }
    private void renderTick(TickEvent.RenderTickEvent event) {
        if (!done && event.phase == TickEvent.Phase.END && screen != null
                && Minecraft.getInstance().screen == screen && Minecraft.getInstance().getOverlay() == null) frames++;
    }
    private void tick(TickEvent.ClientTickEvent event) {
        if (done || event.phase != TickEvent.Phase.END || !Boolean.getBoolean("gtl.nativeWideVisual.audit")) return;
        var mc = Minecraft.getInstance();
        if (mc.getOverlay() != null) return;
        try {
            if (directory == null) {
                if (mc.screen == null) return;
                directory = mc.gameDirectory.toPath().toAbsolutePath().normalize();
                require(directory.getParent().equals(Path.of("C:/IDEA/GTL-Enhancedcore/_audit/super-buffer-overflow-20261107"))
                        && Files.isRegularFile(directory.resolve("native-wide-visual.marker")), "Explicit disposable visual fixture");
                Files.createDirectories(directory.resolve("screenshots"));
                previousData = WirelessEnergySavaedData.INSTANCE;
                WirelessEnergySavaedData.INSTANCE = new WirelessEnergySavaedData(null);
            }
            require(mc.level == null && mc.player == null && mc.getSingleplayerServer() == null, "No player world opened");
            if (fixture == null) fixture = NativeWideBatchChecks.visual(scenario == 0);
            if (screen == null) {
                int[] size = SIZES[viewport];
                if (!resizing) {
                    mc.setScreen(new TitleScreen()); mc.options.guiScale().set(size[2]);
                    mc.options.forceUnicodeFont().set(false);
                    GLFW.glfwSetWindowSize(mc.getWindow().getWindow(), size[0], size[1]); mc.resizeDisplay();
                    resizing = true; stable = resizeTicks = 0; return;
                }
                require(++resizeTicks < 120, "Window resize finishes");
                if (mc.getWindow().getWidth() != size[0] || mc.getWindow().getHeight() != size[1]) {
                    stable = 0; mc.resizeDisplay(); return;
                }
                if (++stable < 2) return;
                require(mc.getWindow().getGuiScaledWidth() == (size[0] + size[2] - 1) / size[2]
                        && mc.getWindow().getGuiScaledHeight() == (size[1] + size[2] - 1) / size[2], "Requested logical viewport");
                screen = new WideScreen(fixture, mode == 1); mc.setScreen(screen); frames = 0; return;
            }
            if (frames < 16) return;
            require(mc.screen == screen, "Real scene stays active");
            var result = screen.verify();
            String filename = "screenshots/" + (scenario == 0 ? "stellar-three" : "omega-one")
                    + "-" + viewport + (mode == 0 ? "-gui.png" : "-jade.png");
            try (var image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) { image.writeToFile(directory.resolve(filename)); }
            result.put("screenshot", filename); result.put("viewport", Arrays.toString(SIZES[viewport]));
            result.put("language", mc.options.languageCode); result.put("frames", frames);
            result.put("summary", fixture.summary().toString()); result.put("fixtureChecks", fixture.checks());
            scenes.add(result); mc.setScreen(new TitleScreen()); screen = null; resizing = false;
            if (++mode < 2) return; mode = 0;
            if (++viewport < SIZES.length) return; viewport = 0;
            fixture.close(); fixture = null;
            if (++scenario == 2) finish(null);
        } catch (Throwable failure) { failure.printStackTrace(); finish(failure); }
    }
    private void finish(Throwable failure) {
        done = true; var mc = Minecraft.getInstance();
        mc.setScreen(new TitleScreen());
        if (fixture != null) fixture.close();
        if (directory != null) {
            WirelessEnergySavaedData.INSTANCE = previousData;
            var report = new LinkedHashMap<String,Object>();
            report.put("status", failure == null ? "PASS" : "FAIL"); report.put("scenes", scenes);
            report.put("worldOpened", false);
            report.put("scope", "Real native prepare/start/publish and production ModularUI. Jade uses actual IvMachineDisplay/IvPresentation components in its native renderer; complete BlockAccessor provider/network path is not exercised. Native menu overflow is recorded, not compressed.");
            if (failure != null) report.put("failure", failure.toString());
            try { Files.writeString(directory.resolve("native-wide-visual.json"), new GsonBuilder().setPrettyPrinting().create().toJson(report)); }
            catch (Exception error) { error.printStackTrace(); }
        }
        mc.stop();
    }
    private static void require(boolean valid, String description) { if (!valid) throw new AssertionError(description); }

    private static final class MachineGui extends ModularUIGuiContainer {
        MachineGui(ModularUI ui) { super(ui, 1); }
        void initialize(Minecraft mc) { minecraft = mc; font = mc.font; width = mc.getWindow().getGuiScaledWidth();
            height = mc.getWindow().getGuiScaledHeight(); super.init(); }
        void menuTick() { super.containerTick(); }
    }
    private static final class WideScreen extends Screen {
        final NativeWideBatchChecks.VisualFixture fixture;
        final boolean jade;
        final List<Component> text = new ArrayList<>();
        ModularUI ui; MachineGui gui; TooltipRenderer renderer;
        float oldScale, oldX, oldY, oldAnchorX, oldAnchorY;
        int draws;
        WideScreen(NativeWideBatchChecks.VisualFixture fixture, boolean jade) throws Exception {
            super(Component.literal("Native wide batch audit")); this.fixture = fixture; this.jade = jade;
            IvMachineDisplay.wrap(fixture.fixture().machine(), ignored -> {}).accept(text);
            require(text.stream().anyMatch(line -> line.getString().contains(IvPresentation.number(fixture.summary().getString("running")))),
                    "Actual production presentation contains exact wide running count");
            if (jade) {
                var tooltip = new Tooltip();
                text.forEach(line -> tooltip.add(new TextElement(line)));
                renderer = new TooltipRenderer(tooltip, false);
            } else {
                ui = fixture.fixture().machine().createUI(fixture.fixture().player()); ui.initWidgets();
                // ComponentPanelWidget formats its client render lines only when
                // receiving initial data; a detached server UI otherwise stays blank.
                var initial = new FriendlyByteBuf(Unpooled.buffer());
                try {
                    ui.mainGroup.writeInitialData(initial); ui.mainGroup.readInitialData(initial);
                    require(initial.readableBytes() == 0, "Actual menu initial state consumed completely");
                } finally { initial.release(); }
                fixture.fixture().player().containerMenu = ui.getModularUIContainer(); gui = new MachineGui(ui);
            }
        }
        @Override protected void init() {
            if (!jade) {
                gui.initialize(minecraft);
                ui.mainGroup.setUiAccess(new WidgetUIAccess() {
                    public boolean attemptMergeStack(ItemStack stack, boolean a, boolean b) { return false; }
                    public void writeUpdateInfo(Widget source, int action, Consumer<FriendlyByteBuf> writer) {}
                    public void writeClientAction(Widget source, int action, Consumer<FriendlyByteBuf> writer) {}
                });
            } else {
                var overlay = IWailaConfig.get().getOverlay();
                oldScale = overlay.getOverlayScale(); oldX = overlay.getOverlayPosX(); oldY = overlay.getOverlayPosY();
                oldAnchorX = overlay.getAnchorX(); oldAnchorY = overlay.getAnchorY();
                overlay.setOverlayScale(1); overlay.setOverlayPosX(0.5f); overlay.setOverlayPosY(0.5f);
                overlay.setAnchorX(0.5f); overlay.setAnchorY(0.5f);
                renderer.setPaddingFromTheme(IThemeHelper.get().theme()); renderer.recalculateRealRect();
            }
        }
        @Override public void tick() { if (gui != null) gui.menuTick(); }
        @Override public void render(GuiGraphics graphics, int x, int y, float partial) {
            graphics.fill(0, 0, width, height, 0xff24313b);
            if (!jade) gui.renderWithTooltip(graphics, -100, -100, partial);
            else {
                renderer.recalculateRealRect(); var rect = renderer.getRealRect(); float scale = renderer.getRealScale();
                OverlayRenderer.theme.setValue(IThemeHelper.get().theme()); float alpha = OverlayRenderer.alpha; OverlayRenderer.alpha = 1;
                graphics.pose().pushPose();
                try {
                    graphics.pose().translate(rect.getX(), rect.getY(), 0); graphics.pose().scale(scale, scale, 1);
                    OverlayRenderer.drawTooltipBox(graphics, 0, 0, (int)Math.ceil(rect.getWidth() / scale),
                            (int)Math.ceil(rect.getHeight() / scale), 1, IWailaConfig.get().getOverlay().getSquare(), renderer);
                    renderer.draw(graphics);
                } finally { graphics.pose().popPose(); OverlayRenderer.alpha = alpha; }
            }
            draws++;
        }
        @Override public void removed() {
            if (jade) {
                var overlay = IWailaConfig.get().getOverlay(); overlay.setOverlayScale(oldScale);
                overlay.setOverlayPosX(oldX); overlay.setOverlayPosY(oldY); overlay.setAnchorX(oldAnchorX); overlay.setAnchorY(oldAnchorY);
            }
        }
        Map<String,Object> verify() {
            require(draws > 0, "Actual screen rendered");
            var result = new LinkedHashMap<String,Object>(); result.put("kind", jade ? "Jade shared presentation" : "Production machine ModularUI");
            result.put("logicalWidth", width); result.put("logicalHeight", height);
            result.put("productionLines", text.stream().map(Component::getString).toList());
            if (jade) {
                var rect = renderer.getRealRect(); result.put("nativeRect", List.of(rect.getX(), rect.getY(), rect.getWidth(), rect.getHeight()));
                result.put("nativeScale", renderer.getRealScale());
                result.put("fitsViewport", rect.getX() >= -1 && rect.getY() >= -1
                        && rect.getX() + rect.getWidth() <= width + 1 && rect.getY() + rect.getHeight() <= height + 1);
            } else {
                result.put("menuWidth", ui.getWidth()); result.put("menuHeight", ui.getHeight());
                result.put("fitsViewport", ui.getWidth() <= width && ui.getHeight() <= height);
            }
            return result;
        }
    }
}
