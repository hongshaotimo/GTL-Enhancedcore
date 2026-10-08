package com.gtl.enhancedcore.audit;

import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.pattern.MultiblockShapeInfo;
import com.gtl.enhancedcore.GTLEnhancedcore;
import org.gtlcore.gtlcore.client.preview.PreviewRendererAccess;
import org.gtlcore.gtlcore.client.preview.PreviewScenes;
import org.gtlcore.gtlcore.client.preview.PreviewSettings;
import org.gtlcore.gtlcore.client.preview.PreviewShapeCache;
import com.lowdragmc.lowdraglib.gui.widget.SceneWidget;
import com.lowdragmc.lowdraglib.utils.BlockInfo;
import com.lowdragmc.lowdraglib.utils.TrackedDummyWorld;
import java.lang.reflect.Field;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.resources.ResourceLocation;

/** Unknown namespace, cache invalidation and real renderer-path checks; test helper only. */
final class PreviewScopeChecks {
    private static int hostPositions;

    static void definitions() throws Exception {
        var discovery = org.gtlcore.gtlcore.api.gui.PatternPreviewWidget.class.getDeclaredMethod(
                "previewController", BlockInfo.class, net.minecraft.core.BlockPos.class);
        discovery.setAccessible(true);
        var hatch = net.minecraftforge.registries.ForgeRegistries.BLOCKS.getValue(
                new ResourceLocation("gtceu:lv_energy_input_hatch"));
        var controller = net.minecraftforge.registries.ForgeRegistries.BLOCKS.getValue(
                new ResourceLocation("gtceu:coke_oven"));
        if (hatch == null || controller == null
                || discovery.invoke(null, BlockInfo.fromBlockState(hatch.defaultBlockState()), net.minecraft.core.BlockPos.ZERO) != null
                || !(discovery.invoke(null, BlockInfo.fromBlockState(controller.defaultBlockState()), net.minecraft.core.BlockPos.ZERO)
                instanceof com.gregtechceu.gtceu.api.machine.IMachineBlockEntity)) {
            throw new AssertionError("Controller discovery skipped a controller or constructed a throwaway hatch");
        }
        GTLEnhancedcore.LOGGER.info("[PREVIEW_CLIENT] SCOPE_DISCOVERY noncontroller_skipped/controller_preserved passed");
        var definition = MultiblockMachineDefinition.createDefinition(new ResourceLocation("future_addon", "new_giant"));
        int minimum = PreviewSettings.minPositions();
        GTLEnhancedcore.LOGGER.info("[PREVIEW_CLIENT] SETTINGS source=config/gtlcore.yaml enabled={} minPositions={} workers={} frameBudgetMs={} cacheMb={}",
                PreviewSettings.enabled(), minimum, PreviewSettings.workers(), PreviewSettings.frameBudgetMs(), PreviewSettings.cacheMb());
        if (!PreviewSettings.enabled()) throw new AssertionError("Upstream preview audit requires enabled multiblockPreview config");
        var small = new MultiblockShapeInfo(new BlockInfo[1][1][minimum - 1]);
        var large = new MultiblockShapeInfo(new BlockInfo[1][1][minimum]);
        int generation = PreviewScenes.generation();
        definition.setShapes(() -> List.of(small));
        if (PreviewScenes.generation() == generation) {
            throw new AssertionError("Uncached shape replacement failed to invalidate mesh generation");
        }
        if (PreviewShapeCache.isLarge(small) || !PreviewShapeCache.isLarge(large) || PreviewShapeCache.isLarge(null)) {
            throw new AssertionError("Automatic size threshold boundary");
        }
        var calls = new AtomicInteger();
        definition.setShapes(() -> {
            calls.incrementAndGet();
            return java.util.Arrays.asList(large, null);
        });
        definition.getMatchingShapes();
        if (definition.getMatchingShapes().get(1) != null || calls.get() != 1) {
            throw new AssertionError("Future namespace missed shape cache or null page");
        }
        definition.setShapes(() -> {
            calls.incrementAndGet();
            return List.of(small);
        });
        if (definition.getMatchingShapes().getFirst() != small || calls.get() != 2) {
            throw new AssertionError("Replaced shape supplier retained old geometry");
        }
        definition.setPatternFactory(() -> null);
        definition.getMatchingShapes();
        if (calls.get() != 3) throw new AssertionError("Replaced pattern retained old geometry");
        var scene = new SceneWidget(0, 0, 160, 160, new TrackedDummyWorld());
        PreviewScenes.configure(scene, definition, 0, -1, false);
        var access = (PreviewRendererAccess) scene.getRenderer();
        if (access.gtlcore$previewKey() == null
                || !access.gtlcore$previewKey().machine().equals(definition.getId())) {
            throw new AssertionError("Future namespace was not attached to the shared preview renderer");
        }
        var options = org.gtlcore.gtlcore.config.ConfigHolder.INSTANCE.multiblockPreview;
        boolean enabled = options.enabled;
        try {
            options.enabled = false;
            PreviewScenes.configure(scene, definition, 0, -1, false);
            if (access.gtlcore$previewKey() != null) throw new AssertionError("Disabled preview retained active key");
            definition.getMatchingShapes();
            definition.getMatchingShapes();
            if (calls.get() != 5) throw new AssertionError("Disabled shape cache intercepted supplier");
        } finally {
            options.enabled = enabled;
            scene.getRenderer().deleteCacheBuffer();
            PreviewShapeCache.invalidate(definition);
        }
        GTLEnhancedcore.LOGGER.info("[PREVIEW_CLIENT] SCOPE_DEFINITIONS future_namespace/threshold/invalidation/disabled passed");
        GTLEnhancedcore.LOGGER.info("[PREVIEW_CLIENT] SCOPE_NULL_PAGES preserved");
    }

    static boolean rendered(SceneWidget scene, String id) throws Exception {
        var renderer = scene.getRenderer();
        Object state = field(renderer, "gTLCore$previewScene");
        if (state == null) throw new AssertionError("No common preview state for " + id);
        Object mesh = field(state, "mesh");
        boolean fallback = (boolean) field(state, "fallback");
        int positions = renderer.renderedBlocksMap.keySet().stream().mapToInt(java.util.Collection::size).sum();
        boolean large = positions >= PreviewSettings.minPositions();
        if (large && (mesh == null || fallback) || !large && (mesh != null || !fallback)) {
            throw new AssertionError("Unexpected renderer route for " + id + ", positions=" + positions);
        }
        if (id.equals("gtladditions:light_hunter_space_station")) {
            var key = ((PreviewRendererAccess) renderer).gtlcore$previewKey();
            if (!key.modules()) hostPositions = positions;
            else {
                if (positions <= hostPositions) throw new AssertionError("Module toggle retained host-only geometry");
                GTLEnhancedcore.LOGGER.info("[PREVIEW_CLIENT] MODULES host={} off={} on={}", id, hostPositions, positions);
            }
        }
        GTLEnhancedcore.LOGGER.info("[PREVIEW_CLIENT] SCOPE id={} path={} positions={}",
                id, large ? "bounded_parallel" : "native_small", positions);
        return large;
    }

    private static Object field(Object owner, String name) throws Exception {
        Class<?> type = owner.getClass();
        while (type != null) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(owner);
            } catch (NoSuchFieldException missing) {
                type = type.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }
}
