package com.gtl.enhancedcore.audit;

import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine;
import com.gregtechceu.gtceu.client.renderer.MultiblockInWorldPreviewRenderer;
import com.gtl.enhancedcore.GTLEnhancedcore;
import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;

final class PreviewWorldChecks {
    private static final BlockPos[] CENTERS = {
            new BlockPos(0, 150, 0), new BlockPos(8, 150, 0), new BlockPos(16, 150, 0)
    };
    private static int pass;
    private static long started;
    private static long lastFrame;
    private static long maxFrame;
    private static int readyFrames;
    private static int idle;
    private static boolean active;
    private static int positioning;
    private static MultiblockControllerMachine controller;
    private static boolean optimized;

    static boolean tick() throws Exception {
        if (pass >= CENTERS.length * 2) return true;
        if (idle > 0) {
            idle--;
            return false;
        }
        if (!active && positioning == 0) {
            var mc = Minecraft.getInstance();
            controller = (MultiblockControllerMachine) MetaMachine.getMachine(mc.level, CENTERS[pass / 2]);
            if (controller == null) throw new IllegalStateException("World preview fixture controller missing");
            mc.player.getAbilities().flying = true;
            mc.player.onUpdateAbilities();
            mc.player.setYRot(-45);
            mc.player.setXRot(20);
            MultiblockInWorldPreviewRenderer.cleanPreview();
            positioning = 20;
            return false;
        }
        if (positioning > 0) {
            if (--positioning > 0) return false;
            var mc = Minecraft.getInstance();
            if (mc.player.getY() < 175) throw new AssertionError("Audit camera fell before world preview");
            Screenshot.grab(mc.gameDirectory, "world-empty-" + pass + ".png", mc.getMainRenderTarget(), message -> {});
            started = System.nanoTime();
            MultiblockInWorldPreviewRenderer.showPreview(CENTERS[pass / 2], controller, 1200);
            optimized = org.gtlcore.gtlcore.client.preview.WorldPreview.active();
            if (optimized != (pass < 4)) throw new AssertionError("Unexpected world preview route " + controller.getDefinition().getId());
            log("WORLD_OPEN pass=" + pass + " id=" + controller.getDefinition().getId()
                    + " path=" + (optimized ? "bounded_parallel" : "native_small")
                    + " setup_ms=" + (System.nanoTime() - started) / 1_000_000.0);
            active = true;
            lastFrame = 0;
            maxFrame = 0;
            readyFrames = 0;
        }
        if (active && System.nanoTime() - started > 120_000_000_000L) throw new IllegalStateException("World preview timeout");
        return false;
    }

    static void frame() throws Exception {
        if (!active) return;
        long now = System.nanoTime();
        if (lastFrame != 0) maxFrame = Math.max(maxFrame, now - lastFrame);
        lastFrame = now;
        if (!ready() || ++readyFrames < 10) return;
        log("WORLD_READY pass=" + pass + " elapsed_ms=" + (now - started) / 1_000_000.0
                + " max_frame_ms=" + maxFrame / 1_000_000.0);
        var mc = Minecraft.getInstance();
        Screenshot.grab(mc.gameDirectory, "world-preview-" + pass + ".png", mc.getMainRenderTarget(), message -> {});
        MultiblockInWorldPreviewRenderer.cleanPreview();
        active = false;
        idle = 40;
        pass++;
    }

    private static boolean ready() throws Exception {
        if (optimized) {
            Class<?> type = Class.forName("org.gtlcore.gtlcore.client.preview.WorldPreview");
            Field field = type.getDeclaredField("mesh");
            field.setAccessible(true);
            Object mesh = field.get(null);
            return mesh != null && (boolean) mesh.getClass().getMethod("complete").invoke(mesh);
        } else {
            Field field = MultiblockInWorldPreviewRenderer.class.getDeclaredField("CACHE_STATE");
            field.setAccessible(true);
            return ((AtomicReference<?>) field.get(null)).get().toString().equals("COMPILED");
        }
    }

    private static void log(String message) { GTLEnhancedcore.LOGGER.info("[PREVIEW_CLIENT] {}", message); }
}
