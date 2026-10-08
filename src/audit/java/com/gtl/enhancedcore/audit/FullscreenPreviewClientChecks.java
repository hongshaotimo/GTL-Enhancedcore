package com.gtl.enhancedcore.audit;

import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import com.gregtechceu.gtceu.integration.jei.multipage.MultiblockInfoCategory;
import com.gtl.enhancedcore.GTLEnhancedcore;
import org.gtlcore.gtlcore.client.preview.FullscreenPreviewScreen;
import org.gtlcore.gtlcore.client.preview.PreviewControls;
import org.gtlcore.gtlcore.client.preview.PreviewHeight;
import com.lowdragmc.lowdraglib.gui.widget.ButtonWidget;
import com.lowdragmc.lowdraglib.gui.widget.SceneWidget;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.jei.ModularWrapper;
import com.lowdragmc.lowdraglib.utils.BlockInfo;
import com.lowdragmc.lowdraglib.utils.Position;
import com.lowdragmc.lowdraglib.utils.Size;
import java.lang.reflect.Field;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.gtlcore.gtlcore.api.gui.PatternPreviewWidget;
import org.lwjgl.glfw.GLFW;

/** Isolated real-JEI interaction and OpenGL checks, not included in the release JAR. */
@Mod.EventBusSubscriber(modid = "enhancedcore_audit", value = Dist.CLIENT)
public final class FullscreenPreviewClientChecks {
    private static final String[] DEFAULT_IDS = {
            "gtl_enhancedcore:hyperstructural_chemical_distorter",
            "gtl_enhancedcore:infinity_singularity_compressor",
            "gtl_enhancedcore:dragon_field_proliferation_core",
            "gtladditions:light_hunter_space_station",
            "gtceu:coke_oven", "gtceu:electric_blast_furnace"
    };
    private static final boolean PALETTE_AUDIT = System.getProperty("gtl.enhancedcore.fullscreenMachines") != null;
    private static final String[] IDS = PALETTE_AUDIT
            ? System.getProperty("gtl.enhancedcore.fullscreenMachines").split(",") : DEFAULT_IDS;
    private static boolean connecting, done;
    private static int settle, index, stage, ready, frames, checks;
    private static long start;
    private static PatternPreviewWidget widget;
    private static PreviewControls controls;
    private static SceneWidget scene;
    private static Screen parent;
    private static Object renderer, mesh;
    private static Position originalPosition;
    private static Size originalSize, sceneSize;
    private static String materials;
    private static ButtonWidget pageButton, layerButton, moduleButton;
    private static java.util.concurrent.CompletableFuture<Void> reload;
    private static float zoom, yaw, pitch;
    private static int height;
    private static PreviewHeight.Dimensions dimensions;
    private static org.joml.Vector3f center;

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (done || !Boolean.getBoolean("gtl.enhancedcore.fullscreenAudit")
                || event.phase != TickEvent.Phase.END) return;
        var mc = Minecraft.getInstance();
        try {
            mc.options.pauseOnLostFocus = false;
            if (!connecting && mc.screen instanceof TitleScreen) {
                connecting = true;
                GLFW.glfwHideWindow(mc.getWindow().getWindow());
                ConnectScreen.startConnecting(mc.screen, mc, ServerAddress.parseString("127.0.0.1:25654"),
                        new ServerData("Fullscreen audit", "127.0.0.1:25654", false), false);
            }
            if (mc.level == null || mc.player == null || PreviewJeiAudit.runtime == null || ++settle < 100) return;
            if (index == IDS.length) {
                log("COMPLETE checks=" + checks);
                done = true;
                mc.stop();
                return;
            }
            if (widget == null) open();
            if (System.nanoTime() - start > 240_000_000_000L) throw new IllegalStateException("Timed out at " + IDS[index] + " stage " + stage);
        } catch (Throwable error) {
            fail(error);
        }
    }

    private static void open() throws Exception {
        start = System.nanoTime();
        if (index == 0) log("SETTINGS source=config/gtlcore.yaml enabled=" + org.gtlcore.gtlcore.client.preview.PreviewSettings.enabled()
                + " minPositions=" + org.gtlcore.gtlcore.client.preview.PreviewSettings.minPositions());
        var mc = Minecraft.getInstance();
        mc.getToasts().clear();
        var definition = (MultiblockMachineDefinition) GTRegistries.MACHINES.get(new ResourceLocation(IDS[index]));
        var manager = PreviewJeiAudit.runtime.getRecipeManager();
        var wrapper = manager.createRecipeLookup(MultiblockInfoCategory.RECIPE_TYPE).get()
                .filter(recipe -> recipe.definition == definition).findFirst().orElseThrow();
        widget = (PatternPreviewWidget) ((ModularWrapper<?>) wrapper).getWidget();
        controls = widget.getPreviewControls();
        scene = controls.scene();
        scene.useCacheBuffer();
        check(controls.controllerHeight() >= 0, "Controller height missing");
        checkPatterns();
        pageButton = buttonAt(30);
        layerButton = buttonAt(50);
        moduleButton = widget.widgets.stream().filter(w -> w instanceof ButtonWidget && w.getSelfPositionY() == 70)
                .map(w -> (ButtonWidget) w).findFirst().orElse(null);
        PreviewJeiAudit.runtime.getRecipesGui().showRecipes(
                manager.getRecipeCategory(MultiblockInfoCategory.RECIPE_TYPE), List.of(wrapper), List.of());
        parent = mc.screen;
        originalPosition = widget.getSelfPosition();
        originalSize = widget.getSize();
        sceneSize = scene.getSize();
        renderer = scene.getRenderer();
        materials = materials();
        height = controls.controllerHeight();
        dimensions = controls.dimensions();
        check(dimensions.known(), "Structure dimensions missing");
        stage = 0;
        frames = ready = 0;
        PreviewHighlightClientChecks.queue(widget, index);
        log("OPEN id=" + IDS[index] + " height=" + height + " dimensions=" + dimensions);
    }

    @SubscribeEvent
    public static void frame(TickEvent.RenderTickEvent event) {
        if (done || !Boolean.getBoolean("gtl.enhancedcore.fullscreenAudit")
                || event.phase != TickEvent.Phase.END || widget == null) return;
        var mc = Minecraft.getInstance();
        try {
            frames++;
            if (reload != null && !reload.isDone()) return;
            if (scene.getRenderer().isCompiling() || frames < 8) { ready = 0; return; }
            if (++ready < 12) return;
            ready = frames = 0;
            switch (stage++) {
                case 0 -> {
                    checks += PreviewHighlightClientChecks.result();
                    screenshot("window");
                    mesh = mesh();
                    click(controls.expandButton());
                    check(mc.screen instanceof FullscreenPreviewScreen, "Button did not open fullscreen");
                    check(scene.getRenderer() == renderer, "Fullscreen replaced renderer");
                    check(mesh() == mesh, "Fullscreen discarded prepared mesh");
                    PreviewHighlightClientChecks.queue(widget, index);
                }
                case 1 -> {
                    checkLayout();
                    check(materials().equals(materials), "Fullscreen changed material contents/NBT");
                    screenshot("full");
                    checks += PreviewHighlightClientChecks.result();
                    Screen full = mc.screen;
                    double x = mc.screen.width * 0.55, y = mc.screen.height * 0.5;
                    float oldZoom = scene.getZoom(), oldYaw = scene.getRotationYaw();
                    full.mouseScrolled(x, y, 1);
                    full.mouseClicked(x, y, 0);
                    full.mouseDragged(x + 12, y + 8, 0, 12, 8);
                    full.mouseReleased(x + 12, y + 8, 0);
                    check(scene.getZoom() != oldZoom && scene.getRotationYaw() != oldYaw, "Camera interaction failed");
                    check(controls.controllerHeight() == height, "Camera changed height");
                    checkPan(full, x, y);
                    zoom = scene.getZoom(); yaw = scene.getRotationYaw(); pitch = scene.getRotationPitch();
                    center = new org.joml.Vector3f(scene.getCenter());
                }
                case 2 -> {
                    screenshot("panned");
                    mc.screen.keyPressed(GLFW.GLFW_KEY_E, 0, 0);
                    check(mc.screen == parent, "E did not return to the same JEI screen");
                    checkRestored();
                    check(scene.getZoom() == zoom && scene.getRotationYaw() == yaw && scene.getRotationPitch() == pitch,
                            "Windowed return lost camera");
                    check(scene.getCenter().equals(center), "Windowed return lost panning");
                }
                case 3 -> {
                    screenshot("returned");
                    click(controls.expandButton());
                    click(layerButton);
                    check(controls.controllerHeight() == height, "Layer filter changed height");
                    check(controls.dimensions().equals(dimensions), "Layer filter changed dimensions");
                }
                case 4 -> {
                    screenshot("layer");
                    widget.setPage(0, null);
                    if (moduleButton != null) {
                        click(moduleButton);
                        check(controls.controllerHeight() == patternHeight(patterns()[0], true),
                                "Module height not updated");
                        check(controls.dimensions().equals(patternDimensions(patterns()[0], true)),
                                "Module dimensions not updated");
                    }
                    checkLayout();
                    if (patterns().length > 1) {
                        click(pageButton);
                        check((int) field(widget, "index") == 1, "Structure page button failed");
                        check(controls.controllerHeight() == patternHeight(patterns()[1], (boolean) field(widget, "showModules")), "Structure page height stale");
                        check(controls.dimensions().equals(patternDimensions(patterns()[1], (boolean) field(widget, "showModules"))), "Structure page dimensions stale");
                        widget.setPage(0, null);
                    }
                }
                case 5 -> {
                    screenshot("controls");
                    if (index == 0) {
                        mc.options.guiScale().set(2);
                        GLFW.glfwSetWindowSize(mc.getWindow().getWindow(), 960, 720);
                        mc.resizeDisplay();
                    }
                }
                case 6 -> {
                    checkLayout();
                    if (index == 0) {
                        screenshot("960x720-scale2");
                        mc.options.guiScale().set(4);
                        GLFW.glfwSetWindowSize(mc.getWindow().getWindow(), 1920, 1080);
                        mc.resizeDisplay();
                    }
                }
                case 7 -> {
                    checkLayout();
                    if (index == 0) {
                        screenshot("1920x1080-scale4");
                        mc.options.guiScale().set(4);
                        GLFW.glfwSetWindowSize(mc.getWindow().getWindow(), 800, 600);
                        mc.resizeDisplay();
                    }
                }
                case 8 -> {
                    checkLayout();
                    if (index == 0) {
                        screenshot("800x600-scale4");
                        mc.options.guiScale().set(3);
                        GLFW.glfwSetWindowSize(mc.getWindow().getWindow(), 1280, 800);
                        mc.resizeDisplay();
                    }
                    if (index == 1) reload = mc.reloadResourcePacks();
                }
                case 9 -> {
                    checkLayout();
                    if (index == 1) {
                        reload.join();
                        reload = null;
                        screenshot("reloaded");
                    }
                    click(controls.expandButton());
                    check(mc.screen == parent, "Restore icon did not return to JEI");
                    checkRestored();
                }
                case 10 -> {
                    for (int i = 0; i < 4; i++) {
                        click(controls.expandButton());
                        check(mc.screen instanceof FullscreenPreviewScreen, "Repeated open failed");
                        if (i == 0) mc.screen.keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0);
                        else if (i == 1) mc.screen.keyPressed(GLFW.GLFW_KEY_E, 0, 0);
                        else if (i == 2) {
                            var binding = mc.options.keyInventory.getKey();
                            try {
                                mc.options.keyInventory.setKey(com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM
                                        .getOrCreate(GLFW.GLFW_KEY_I));
                                mc.screen.keyPressed(GLFW.GLFW_KEY_I, 0, 0);
                            } finally {
                                mc.options.keyInventory.setKey(binding);
                            }
                        } else mc.screen.onClose();
                        check(mc.screen == parent, "Repeated close did not return to JEI");
                        checkRestored();
                    }
                    if (moduleButton != null) click(moduleButton);
                    widget.setPage(0, null);
                    check(materials().equals(materials), "Roundtrip changed lamp/material stacks");
                    check(controls.controllerHeight() == height, "Roundtrip changed height");
                    check(controls.dimensions().equals(dimensions), "Roundtrip changed dimensions");
                    if (PALETTE_AUDIT) {
                        click(controls.expandButton());
                        var controller = ((IMultiController) field(patterns()[0], "controllerBase")).self();
                        var p = controller.getPos();
                        scene.setCenter(new org.joml.Vector3f(p.getX() + 0.5f, p.getY() + 0.5f, p.getZ() + 0.5f));
                        scene.setZoom(18);
                        var front = controller.getFrontFacing();
                        scene.setCameraYawAndPitch(12, (float) Math.toDegrees(Math.atan2(front.getStepZ(), front.getStepX())));
                    } else finishMachine();
                }
                case 11 -> {
                    screenshot("panel");
                    finishMachine();
                }
                default -> throw new IllegalStateException("Bad stage");
            }
        } catch (Throwable error) {
            fail(error);
        }
    }

    private static void finishMachine() {
        log("PASS id=" + IDS[index] + " height=" + height + " dimensions=" + dimensions + " checks=" + checks);
        scene.getRenderer().deleteCacheBuffer();
        Minecraft.getInstance().setScreen(null);
        widget = null;
        index++;
    }

    private static void checkLayout() {
        var mc = Minecraft.getInstance();
        check(mc.screen instanceof FullscreenPreviewScreen && controls.fullscreen(), "Fullscreen state lost");
        check(scene.getSizeWidth() == mc.screen.width - 8, "Scene did not use available width");
        check(scene.getPositionY() >= controls.headerHeight() && scene.getPositionY() + scene.getSizeHeight() <= mc.screen.height - 28,
                "Scene overlaps header/materials");
        for (var child : widget.widgets) {
            check(child.getPositionX() >= 0 && child.getPositionY() >= 0
                    && child.getPositionX() + child.getSizeWidth() <= mc.screen.width
                    && child.getPositionY() + child.getSizeHeight() <= mc.screen.height, "Widget outside screen: " + child.getClass());
        }
        check(scene.getRenderer() == renderer, "Layout rebuilt renderer");
    }

    private static void checkRestored() {
        check(!controls.fullscreen() && widget.getSelfPosition().equals(originalPosition)
                && widget.getSize().equals(originalSize) && scene.getSize().equals(sceneSize), "Windowed geometry not restored");
        check(pageButton.getSelfPosition().equals(new Position(138, 30)), "Page button not restored");
        check(layerButton.getSelfPosition().equals(new Position(138, 50)), "Layer button not restored");
    }

    @SuppressWarnings("unchecked")
    private static void checkPatterns() throws Exception {
        for (Object pattern : patterns()) {
            var blocks = (Map<BlockPos, BlockInfo>) field(pattern, "blockMap");
            var modules = (Set<BlockPos>) field(pattern, "moduleOnlyBlocks");
            int y = ((IMultiController) field(pattern, "controllerBase")).self().getPos().getY();
            for (boolean enabled : new boolean[]{false, true}) {
                int min = blocks.entrySet().stream().filter(e -> !e.getValue().getBlockState().isAir())
                        .filter(e -> enabled || !modules.contains(e.getKey())).mapToInt(e -> e.getKey().getY()).min().orElseThrow();
                check(patternHeight(pattern, enabled) == y - min, "Occupied bounds differ from cached height");
                var positions = blocks.entrySet().stream().filter(e -> !e.getValue().getBlockState().isAir())
                        .filter(e -> enabled || !modules.contains(e.getKey())).map(Map.Entry::getKey).toList();
                var xs = positions.stream().mapToInt(BlockPos::getX).summaryStatistics();
                var ys = positions.stream().mapToInt(BlockPos::getY).summaryStatistics();
                var zs = positions.stream().mapToInt(BlockPos::getZ).summaryStatistics();
                check(patternDimensions(pattern, enabled).equals(new PreviewHeight.Dimensions(
                        (long) xs.getMax() - xs.getMin() + 1, (long) zs.getMax() - zs.getMin() + 1,
                        (long) ys.getMax() - ys.getMin() + 1)), "Occupied bounds differ from cached dimensions");
            }
        }
    }

    private static int patternHeight(Object pattern, boolean modules) throws Exception {
        int controllerY = ((IMultiController) field(pattern, "controllerBase")).self().getPos().getY();
        return ((PreviewHeight) field(pattern, "bounds")).aboveBottom(controllerY, modules);
    }

    private static PreviewHeight.Dimensions patternDimensions(Object pattern, boolean modules) throws Exception {
        return ((PreviewHeight) field(pattern, "bounds")).dimensions(modules);
    }

    private static void checkPan(Screen full, double x, double y) throws Exception {
        var before = new org.joml.Vector3f(scene.getCenter());
        float oldYaw = scene.getRotationYaw(), oldPitch = scene.getRotationPitch(), oldZoom = scene.getZoom();
        var selected = scene.getSelectedPosFace();
        Object oldMesh = mesh();
        check(!full.mouseClicked(2, 2, 2), "Header started pan");
        check(!full.mouseDragged(x, y, 2, 10, 10), "Pan started without scene press");
        check(!full.mouseClicked(pageButton.getPositionX() + 8, pageButton.getPositionY() + 8, 2),
                "Overlay button started pan");
        check(full.mouseClicked(x, y, 2), "Middle press not handled");
        check(full.mouseDragged(x + 24, y + 16, 2, 24, 16), "Middle drag not handled");
        check(full.mouseDragged(-1, -1, 2, 0, 0), "Dragging outside viewport lost capture");
        check(full.mouseReleased(-1, -1, 2), "Middle release not handled");
        check(scene.getCenter().distance(before) > 0.001, "Middle drag did not pan");
        check(scene.getRotationYaw() == oldYaw && scene.getRotationPitch() == oldPitch && scene.getZoom() == oldZoom,
                "Middle pan rotated or zoomed");
        check(scene.getSelectedPosFace() == selected, "Middle pan selected a block");
        check(scene.getRenderer() == renderer && mesh() == oldMesh, "Middle pan rebuilt renderer/mesh");
        var after = new org.joml.Vector3f(scene.getCenter());
        check(!full.mouseDragged(x, y, 2, 10, 10) && scene.getCenter().equals(after), "Pan stuck after release");
        check(controls.dimensions().equals(dimensions), "Pan changed structure dimensions");
    }

    private static ButtonWidget buttonAt(int y) {
        return widget.widgets.stream().filter(w -> w instanceof ButtonWidget && w.getSelfPositionY() == y)
                .map(w -> (ButtonWidget) w).findFirst().orElseThrow();
    }

    private static void click(ButtonWidget button) {
        check(widget.mouseClicked(button.getPositionX() + 8, button.getPositionY() + 8, 0), "Click not consumed");
    }

    private static Object[] patterns() throws Exception { return (Object[]) field(widget, "patterns"); }

    @SuppressWarnings("unchecked")
    private static String materials() throws Exception {
        var parts = (List<List<ItemStack>>) field(patterns()[(int) field(widget, "index")], "parts");
        return parts.stream().flatMap(Collection::stream).map(s -> s.save(new net.minecraft.nbt.CompoundTag()).toString())
                .sorted().toList().toString();
    }

    private static Object mesh() throws Exception {
        var state = field(scene.getRenderer(), "gTLCore$previewScene");
        return state == null ? null : field(state, "mesh");
    }

    private static Object field(Object object, String name) throws Exception {
        for (Class<?> type = object.getClass(); type != null; type = type.getSuperclass()) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(object);
            } catch (NoSuchFieldException ignored) {}
        }
        throw new NoSuchFieldException(name);
    }

    private static void screenshot(String phase) {
        var mc = Minecraft.getInstance();
        Screenshot.grab(mc.gameDirectory, "fullscreen-" + index + "-" + phase + ".png", mc.getMainRenderTarget(), message -> {});
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
        checks++;
    }

    private static void fail(Throwable error) {
        done = true;
        GTLEnhancedcore.LOGGER.error("[FULLSCREEN_CLIENT] FAIL id=" + index + " stage=" + stage, error);
        Minecraft.getInstance().stop();
    }

    private static void log(String message) { GTLEnhancedcore.LOGGER.info("[FULLSCREEN_CLIENT] {}", message); }
}
