package com.gtl.enhancedcore.audit;

import com.gregtechceu.gtceu.api.registry.GTRegistries;
import com.gregtechceu.gtceu.integration.jei.multipage.MultiblockInfoCategory;
import com.gtl.enhancedcore.GTLEnhancedcore;
import org.gtlcore.gtlcore.client.preview.PreviewLifecycle;
import com.gtl.enhancedcore.client.preview.PreviewEffectAnchors;
import org.gtlcore.gtlcore.client.preview.PreviewRendererAccess;
import com.lowdragmc.lowdraglib.gui.widget.SceneWidget;
import com.lowdragmc.lowdraglib.jei.ModularWrapper;
import com.lowdragmc.lowdraglib.gui.util.ClickData;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.resources.ResourceLocation;
import org.gtlcore.gtlcore.api.gui.PatternPreviewWidget;

final class PreviewLifecycleChecks {
    private static int phase;
    private static int ticks;
    private static long started;
    private static SceneWidget scene;
    private static PatternPreviewWidget widget;
    private static CompletableFuture<Void> reload;

    static boolean tick() throws Exception {
        var mc = Minecraft.getInstance();
        if (phase == 0) {
            started = System.nanoTime();
            checkAnchors();
            var definition = GTRegistries.MACHINES.get(new ResourceLocation("gtceu:qft"));
            var manager = PreviewJeiAudit.runtime.getRecipeManager();
            var wrapper = manager.createRecipeLookup(MultiblockInfoCategory.RECIPE_TYPE).get()
                    .filter(recipe -> recipe.definition == definition).findFirst().orElseThrow();
            widget = (PatternPreviewWidget) ((ModularWrapper<?>) wrapper).getWidget();
            var field = PatternPreviewWidget.class.getDeclaredField("sceneWidget");
            field.setAccessible(true);
            scene = (SceneWidget) field.get(widget);
            widget.setPage(0, null);
            scene.useCacheBuffer();
            PreviewJeiAudit.runtime.getRecipesGui().showRecipes(
                    manager.getRecipeCategory(MultiblockInfoCategory.RECIPE_TYPE), List.of(wrapper), List.of());
            phase++;
        } else if (phase == 1 && ++ticks >= 2) {
            var updateLayer = PatternPreviewWidget.class.getDeclaredMethod("updateLayer", ClickData.class);
            updateLayer.setAccessible(true);
            var constructor = ClickData.class.getDeclaredConstructor(int.class, boolean.class, boolean.class, boolean.class);
            constructor.setAccessible(true);
            var click = constructor.newInstance(0, false, false, false);
            updateLayer.invoke(widget, click);
            updateLayer.invoke(widget, click);
            ticks = 0;
            phase++;
        } else if (phase == 2 && ++ticks > 20 && !scene.getRenderer().isCompiling()) {
            var key = ((PreviewRendererAccess) scene.getRenderer()).gtlcore$previewKey();
            if (key.layer() != 1 || scene.getRenderer().renderedBlocksMap.size() != 1) {
                throw new AssertionError("Layer switch retained stale rendered-map entries");
            }
            Screenshot.grab(mc.gameDirectory, "lifecycle-layer.png", mc.getMainRenderTarget(), message -> {});
            widget.setPage(0, null);
            phase++;
            ticks = 0;
        } else if (phase == 3 && ++ticks > 20 && !scene.getRenderer().isCompiling()) {
            reload = mc.reloadResourcePacks();
            assertNoMeshes();
            phase++;
            ticks = 0;
        } else if (phase == 4 && reload.isDone() && ++ticks > 40 && !scene.getRenderer().isCompiling()) {
            reload.join();
            Screenshot.grab(mc.gameDirectory, "lifecycle-reload.png", mc.getMainRenderTarget(), message -> {});
            mc.setScreen(null);
            phase++;
            ticks = 0;
        } else if (phase == 5 && ++ticks > 10) {
            PreviewLifecycle.clear();
            assertNoMeshes();
            GTLEnhancedcore.LOGGER.info("[PREVIEW_CLIENT] LIFECYCLE layers/cancellation/reload/cleanup passed");
            phase++;
        }
        if (System.nanoTime() - started > 180_000_000_000L) throw new AssertionError("Lifecycle timeout phase=" + phase);
        return phase >= 6;
    }

    private static void assertNoMeshes() throws Exception {
        var field = Class.forName("org.gtlcore.gtlcore.client.preview.PreviewMesh").getDeclaredField("LIVE");
        field.setAccessible(true);
        if (!((java.util.Set<?>) field.get(null)).isEmpty()) throw new AssertionError("Stale live preview meshes");
    }

    private static void checkAnchors() {
        var world = new com.lowdragmc.lowdraglib.utils.TrackedDummyWorld();
        var pos = net.minecraft.core.BlockPos.ZERO;
        var magic = net.minecraftforge.registries.ForgeRegistries.BLOCKS.getValue(new ResourceLocation("kubejs:magic_core"));
        world.setBlock(pos, magic.defaultBlockState(), 0, 0);
        if (PreviewEffectAnchors.positions(world).length != 1) throw new AssertionError("Missing inserted effect anchor");
        world.setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 0, 0);
        if (PreviewEffectAnchors.positions(world).length != 0) throw new AssertionError("Stale replaced effect anchor");
        world.addBlock(pos, com.lowdragmc.lowdraglib.utils.BlockInfo.fromBlockState(magic.defaultBlockState()));
        world.removeBlock(pos);
        if (PreviewEffectAnchors.positions(world).length != 0) throw new AssertionError("Stale removed effect anchor");
        world.addBlock(pos, com.lowdragmc.lowdraglib.utils.BlockInfo.fromBlockState(magic.defaultBlockState()));
        world.clear();
        if (PreviewEffectAnchors.positions(world).length != 0) throw new AssertionError("Stale cleared effect anchor");
    }
}
