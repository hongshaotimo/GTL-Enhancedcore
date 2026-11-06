package com.gtl.enhancedcore.audit;

import com.gregtechceu.gtceu.api.registry.GTRegistries;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.glfw.GLFW;

/** Nine real menu screenshots in one title-screen JVM; only detached fixture worlds are used. */
public final class CoreRemovalUiChecks {
    private record Viewport(int width, int height, int scale) {
        String id() { return width + "x" + height + "-gui" + scale; }
        int logicalWidth() { return (width + scale - 1) / scale; }
        int logicalHeight() { return (height + scale - 1) / scale; }
    }
    private static final List<Viewport> VIEWPORTS = List.of(
            new Viewport(960, 600, 2), new Viewport(1280, 720, 3), new Viewport(640, 480, 2));
    private static final Map<String, Viewport> SCENES = new LinkedHashMap<>();
    private static String preparing;
    private static int preparationTicks, stableTicks;
    private CoreRemovalUiChecks() {}

    public static List<GuiStyleClientChecks.Scene> scenes() {
        verifyRegistry();
        var originals = new ArrayList<GuiStyleClientChecks.Scene>();
        GuiRollbackChecks.scenes().stream().filter(scene -> scene.id().equals("restored-ordinary-buffer")
                || scene.id().equals("restored-super-buffer")).forEach(originals::add);
        AeStyleChecks.scenes().stream().filter(scene -> scene.id().equals("ae-generator-main")).forEach(originals::add);
        GuiStyleClientChecks.require(originals.size() == 3, "Three existing production-menu scene factories selected");
        var result = new ArrayList<GuiStyleClientChecks.Scene>();
        for (Viewport viewport : VIEWPORTS) for (var original : originals) {
            String id = "withdrawal-" + viewport.id() + "-" + original.id();
            SCENES.put(id, viewport);
            result.add(new GuiStyleClientChecks.Scene(id, () -> {
                verifyNoWorld();
                return original.factory().create();
            }, screen -> {
                verifyViewport(screen, viewport);
                original.verify().verify(screen);
                verifyRegistry();
                verifyNoWorld();
            }));
        }
        return List.copyOf(result);
    }

    public static boolean prepareScene(String id) {
        Viewport viewport = SCENES.get(id);
        if (viewport == null) return true;
        var mc = Minecraft.getInstance();
        verifyNoWorld();
        if (!id.equals(preparing)) {
            // Release the preceding detached menu before resizeDisplay reinitializes a screen.
            mc.setScreen(new TitleScreen());
            mc.options.guiScale().set(viewport.scale);
            mc.options.forceUnicodeFont().set(false);
            GLFW.glfwSetWindowSize(mc.getWindow().getWindow(), viewport.width, viewport.height);
            mc.resizeDisplay();
            preparing = id; preparationTicks = 0; stableTicks = 0;
            return false;
        }
        GuiStyleClientChecks.require(++preparationTicks <= 120, "Viewport resize completes: " + id);
        if (mc.getWindow().getWidth() != viewport.width || mc.getWindow().getHeight() != viewport.height) {
            stableTicks = 0;
            mc.resizeDisplay();
            return false;
        }
        if (++stableTicks < 2) return false;
        verifyViewport(null, viewport);
        return true;
    }

    private static void verifyViewport(Screen screen, Viewport viewport) {
        var window = Minecraft.getInstance().getWindow();
        GuiStyleClientChecks.require(window.getWidth() == viewport.width && window.getHeight() == viewport.height
                        && window.getGuiScaledWidth() == viewport.logicalWidth()
                        && window.getGuiScaledHeight() == viewport.logicalHeight()
                        && Minecraft.getInstance().options.guiScale().get() == viewport.scale,
                "Actual framebuffer/logical viewport " + viewport.id() + " = "
                        + viewport.logicalWidth() + "x" + viewport.logicalHeight());
        if (screen != null) GuiStyleClientChecks.require(screen.width == viewport.logicalWidth()
                        && screen.height == viewport.logicalHeight(), "Real menu initializes at requested logical viewport");
    }

    private static void verifyNoWorld() {
        var mc = Minecraft.getInstance();
        GuiStyleClientChecks.require(mc.level == null && mc.player == null && mc.getSingleplayerServer() == null,
                "No client world, player world or integrated server opened");
    }

    private static void verifyRegistry() {
        for (String path : List.of("cloud_pattern_library", "cloud_pattern_buffer")) {
            var id = new ResourceLocation("gtl_enhancedcore", path);
            GuiStyleClientChecks.require(GTRegistries.MACHINES.get(id) == null
                            && !BuiltInRegistries.ITEM.containsKey(id) && !BuiltInRegistries.BLOCK.containsKey(id),
                    "Withdrawn machine/item/block absent from real registries: " + id);
        }
        for (String id : List.of("gtceu:me_final_pattern_buffer", "gtladditions:me_super_pattern_buffer"))
            GuiStyleClientChecks.require(GTRegistries.MACHINES.get(new ResourceLocation(id)) != null,
                    "Ordinary assembly remains registered: " + id);
        GuiStyleClientChecks.require(BuiltInRegistries.ITEM.containsKey(new ResourceLocation("gtl_enhancedcore", "pattern_generator")),
                "Pattern generator remains registered");
    }
}
