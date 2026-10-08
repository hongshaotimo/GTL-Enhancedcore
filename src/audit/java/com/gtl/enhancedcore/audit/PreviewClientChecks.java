package com.gtl.enhancedcore.audit;

import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import com.gtl.enhancedcore.GTLEnhancedcore;
import com.gregtechceu.gtceu.integration.jei.multipage.MultiblockInfoCategory;
import com.lowdragmc.lowdraglib.jei.ModularWrapper;
import com.lowdragmc.lowdraglib.gui.widget.SceneWidget;
import java.lang.reflect.Field;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.gtlcore.gtlcore.api.gui.PatternPreviewWidget;

/** Real OpenGL/JEI widget audit in a separate client and isolated server, opt-in only. */
@Mod.EventBusSubscriber(modid = "enhancedcore_audit", value = Dist.CLIENT)
public final class PreviewClientChecks {
    private static final String[] IDS = {
            "gtl_enhancedcore:infinity_singularity_compressor", "gtceu:gravitation_shockburst",
            "gtceu:qft", "gtl_enhancedcore:causality_terminal",
            "gtladditions:nexus_satellite_factory_mk1", "gtceu:luv_compressed_fusion_reactor", "gtceu:coke_oven",
            "gtladditions:light_hunter_space_station"
    };
    private static boolean connecting;
    private static int settle;
    private static int index;
    private static int readyFrames;
    private static SceneWidget scene;
    private static long start;
    private static long lastFrame;
    private static long maxFrame;
    private static long frames;
    private static int wait;
    private static boolean done;
    private static boolean scopeChecked;

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (done || !Boolean.getBoolean("gtl.enhancedcore.previewAudit") || event.phase != TickEvent.Phase.END) return;
        var mc = Minecraft.getInstance();
        try {
            mc.options.pauseOnLostFocus = false;
            if (!connecting && mc.screen instanceof TitleScreen) {
                connecting = true;
                org.lwjgl.glfw.GLFW.glfwHideWindow(mc.getWindow().getWindow());
                ConnectScreen.startConnecting(mc.screen, mc, ServerAddress.parseString("127.0.0.1:25654"),
                        new ServerData("Preview audit", "127.0.0.1:25654", false), false);
            }
            if (mc.level == null || mc.player == null || PreviewJeiAudit.runtime == null || ++settle < 100) return;
            if (!scopeChecked) {
                PreviewScopeChecks.definitions();
                scopeChecked = true;
            }
            if (index >= IDS.length * 2) {
                if (PreviewWorldChecks.tick()) {
                    if (!PreviewLifecycleChecks.tick()) return;
                    log("COMPLETE");
                    done = true;
                    mc.stop();
                }
                return;
            }
            if (wait > 0) {
                if (--wait == 0) {
                    if (++index < IDS.length * 2) open();
                }
            } else if (scene == null && index == 0) open();
            if (scene != null && System.nanoTime() - start > 180_000_000_000L) throw new IllegalStateException("Preview timeout " + IDS[index / 2]);
        } catch (Throwable error) {
            GTLEnhancedcore.LOGGER.error("[PREVIEW_CLIENT] FAIL", error);
            mc.stop();
        }
    }

    private static void open() throws Exception {
        var mc = Minecraft.getInstance();
        var id = IDS[index / 2];
        var definition = (MultiblockMachineDefinition) GTRegistries.MACHINES.get(new ResourceLocation(id));
        start = System.nanoTime();
        var manager = PreviewJeiAudit.runtime.getRecipeManager();
        var wrapper = manager.createRecipeLookup(MultiblockInfoCategory.RECIPE_TYPE).get()
                .filter(recipe -> recipe.definition == definition).findFirst().orElseThrow();
        var widget = (PatternPreviewWidget) ((ModularWrapper<?>) wrapper).getWidget();
        if (id.equals("gtladditions:light_hunter_space_station") && index % 2 == 1) {
            var toggle = PatternPreviewWidget.class.getDeclaredMethod("toggleModules");
            toggle.setAccessible(true);
            toggle.invoke(widget);
        }
        Field field = PatternPreviewWidget.class.getDeclaredField("sceneWidget");
        field.setAccessible(true);
        scene = (SceneWidget) field.get(widget);
        scene.useCacheBuffer();
        // Same external camera for both versions, independent of the upstream sqrt-size default.
        if (index / 2 < 4) scene.setZoom(510);
        PreviewJeiAudit.runtime.getRecipesGui().showRecipes(
                manager.getRecipeCategory(MultiblockInfoCategory.RECIPE_TYPE), List.of(wrapper), List.of());
        lastFrame = 0;
        maxFrame = 0;
        frames = 0;
        readyFrames = 0;
        log("OPEN id=" + id + " pass=" + index % 2 + " setup_ms=" + (System.nanoTime() - start) / 1_000_000.0);
    }

    @SubscribeEvent
    public static void frame(TickEvent.RenderTickEvent event) {
        if (!Boolean.getBoolean("gtl.enhancedcore.previewAudit") || event.phase != TickEvent.Phase.END) return;
        if (index >= IDS.length * 2) {
            try {
                PreviewWorldChecks.frame();
            } catch (Throwable error) {
                GTLEnhancedcore.LOGGER.error("[PREVIEW_CLIENT] FAIL", error);
                Minecraft.getInstance().stop();
            }
            return;
        }
        if (scene == null) return;
        long now = System.nanoTime();
        if (lastFrame != 0) maxFrame = Math.max(maxFrame, now - lastFrame);
        lastFrame = now;
        frames++;
        if (scene.getRenderer().isCompiling() || frames < 10) return;
        if (++readyFrames < 20) return;
        var mc = Minecraft.getInstance();
        log("READY id=" + IDS[index / 2] + " pass=" + index % 2 + " elapsed_ms=" + (now - start) / 1_000_000.0
                + " frames=" + frames + " max_frame_ms=" + maxFrame / 1_000_000.0);
        stats("before_close");
        if (index % 2 == 0 || IDS[index / 2].equals("gtladditions:light_hunter_space_station")) {
            try {
                if (PreviewScopeChecks.rendered(scene, IDS[index / 2])) PreviewPickingChecks.check(scene);
            } catch (Exception error) {
                GTLEnhancedcore.LOGGER.error("[PREVIEW_CLIENT] FAIL picking", error);
                mc.stop();
                return;
            }
        }
        Screenshot.grab(mc.gameDirectory, "preview-" + index + ".png", mc.getMainRenderTarget(), message -> {});
        scene.getRenderer().deleteCacheBuffer();
        stats("after_close");
        mc.setScreen(null);
        scene = null;
        wait = 40;
    }

    private static void log(String message) { GTLEnhancedcore.LOGGER.info("[PREVIEW_CLIENT] {}", message); }

    private static void stats(String phase) {
        try {
            var meshes = Class.forName("org.gtlcore.gtlcore.client.preview.PreviewMesh");
            var live = meshes.getDeclaredField("LIVE");
            live.setAccessible(true);
            var values = (java.util.Set<?>) live.get(null);
            long bytes = 0;
            for (var value : values) bytes += (long) meshes.getMethod("bytes").invoke(value);
            var idle = Class.forName("org.gtlcore.gtlcore.client.preview.PreviewScenes").getDeclaredField("idle");
            idle.setAccessible(true);
            Object cache = idle.get(null);
            long cached = cache == null ? 0 : (long) cache.getClass().getMethod("weight").invoke(cache);
            log("MEMORY phase=" + phase + " meshes=" + values.size() + " estimated_bytes=" + bytes + " idle_bytes=" + cached);
        } catch (ReflectiveOperationException error) {
            GTLEnhancedcore.LOGGER.error("[PREVIEW_CLIENT] FAIL upstream memory inspection", error);
            Minecraft.getInstance().stop();
            throw new IllegalStateException("Missing GTLCore preview memory inspection", error);
        }
    }
}
