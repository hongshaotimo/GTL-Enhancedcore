package com.gtl.enhancedcore.client.plot;

import com.gtl.enhancedcore.common.world.plot.PlotPreviewRaster;
import com.gtl.enhancedcore.common.world.plot.PlotSettings;
import com.gtl.enhancedcore.common.world.plot.CampusMapImage;
import com.gtl.enhancedcore.common.world.plot.PlannedCampus;
import com.gtl.enhancedcore.common.world.campus.CampusPlanAccess;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.MapColor;

/** Top-down previews share one exact, coverage-aware raster with the terrain sampler. */
public final class PlotPreviewRenderer {
    private PlotPreviewRenderer() {}

    public static void draw(GuiGraphics g, Font font, PlotSettings settings, int left, int top,
                            int pixels, int plotsPerSide, boolean chunkGrid) {
        int actual = Math.max(1, pixels), size = Math.min(actual, PlotPreviewRaster.MAX_PIXELS);
        int[] raster = PlotPreviewRaster.render(settings, size, plotsPerSide, palette(settings));
        // Compatibility path for the read-only world information screen. Merge equal horizontal runs.
        for (int z = 0; z < size; z++) {
            for (int x = 0; x < size;) {
                int start = x, rgb = raster[z * size + x++];
                while (x < size && raster[z * size + x] == rgb) x++;
                g.fill(left + start * actual / size, top + z * actual / size,
                        left + x * actual / size, top + (z + 1) * actual / size, rgb);
            }
        }
        finish(g, settings, left, top, actual, plotsPerSide, chunkGrid);
        g.drawCenteredString(font, Component.translatable(plotsPerSide == 1
                        ? "gtl_enhancedcore.create_world_plot.preview_single" : "gtl_enhancedcore.create_world_plot.preview_four"),
                left + actual / 2, top + actual + 5, 0xffdce3e8);
    }

    private static void finish(GuiGraphics g, PlotSettings settings, int left, int top,
                               int pixels, int plots, boolean grid) {
        if (settings.isCampus()) {
            g.renderOutline(left - 1, top - 1, pixels + 2, pixels + 2, 0xff8ad6df);
            return;
        }
        int cells = settings.plotBlocks() * plots;
        if (grid) for (int i = 16; i < cells; i += 16) {
            int offset = i * pixels / cells;
            g.fill(left + offset, top, left + offset + 1, top + pixels, 0x304a6373);
            g.fill(left, top + offset, left + pixels, top + offset + 1, 0x304a6373);
        }
        for (int i = 1; i < plots; i++) {
            int offset = i * pixels / plots;
            g.fill(left + offset - 1, top, left + offset + 1, top + pixels, 0xfff2e6bd);
            g.fill(left, top + offset - 1, left + pixels, top + offset + 1, 0xfff2e6bd);
        }
        g.renderOutline(left - 1, top - 1, pixels + 2, pixels + 2, 0xfff2e6bd);
    }

    /** Screen-owned texture; no global settings, world references, or unbounded texture cache. */
    public static final class Cached implements AutoCloseable {
        private PlotSettings previous;
        private int count, rasterSize;
        private ResourceLocation location;
        private DynamicTexture texture;
        private PlotSettings requested;
        private int requestedCount, requestedSize, requestGeneration;
        private CompletableFuture<int[]> pendingRaster;
        private AtomicBoolean pendingCancelled;
        private int[] completedRaster;
        private Throwable rasterFailure;

        public void draw(GuiGraphics g, PlotSettings settings, int left, int top, int pixels, int plots, boolean grid) {
            int actual = Math.max(1, pixels), size = Math.min(actual, PlotPreviewRaster.MAX_PIXELS);
            var manager = Minecraft.getInstance().getTextureManager();
            if (location != null && manager.getTexture(location) != texture) close();
            if (settings.isCampus()) {
                drawCampus(g, settings, left, top, actual, size, plots, grid);
                return;
            }
            if (pendingRaster != null || requested != null) {
                requestGeneration++;
                cancelPendingRaster();
                requested = null; completedRaster = null; rasterFailure = null;
            }
            if (!settings.equals(previous) || plots != count || size != rasterSize || location == null) {
                int[] raster = PlotPreviewRaster.render(settings, size, plots, palette(settings));
                upload(manager, settings, plots, size, raster);
            }
            // Explicit source and target sizes: the full texture is used even at the safety bound.
            g.blit(location, left, top, actual, actual, 0.0f, 0.0f, size, size, size, size);
            finish(g, settings, left, top, actual, plots, grid);
        }

        private void drawCampus(GuiGraphics g, PlotSettings settings, int left, int top,
                                int actual, int size, int plots, boolean grid) {
            boolean textureReady = settings.equals(previous) && plots == count && size == rasterSize && location != null;
            boolean requestMatches = settings.equals(requested) && plots == requestedCount && size == requestedSize;
            if (!textureReady && !requestMatches) {
                requested = settings; requestedCount = plots; requestedSize = size; completedRaster = null; rasterFailure = null;
                int generation = ++requestGeneration;
                cancelPendingRaster();
                AtomicBoolean cancelled = new AtomicBoolean();
                pendingCancelled = cancelled;
                int[] colors = palette(settings);
                CompletableFuture<int[]> work = CompletableFuture.supplyAsync(
                        () -> PlotPreviewRaster.render(settings, size, plots, colors, cancelled::get), Util.backgroundExecutor());
                pendingRaster = work;
                work.whenComplete((raster, failure) -> Minecraft.getInstance().execute(() -> {
                    if (generation != requestGeneration) return;
                    pendingRaster = null; pendingCancelled = null;
                    if (failure != null) rasterFailure = failure;
                    else completedRaster = raster;
                }));
            }
            if (completedRaster != null && settings.equals(requested) && plots == requestedCount && size == requestedSize) {
                upload(Minecraft.getInstance().getTextureManager(), settings, plots, size, completedRaster);
                completedRaster = null;
                textureReady = true;
            }
            if (textureReady) {
                g.blit(location, left, top, actual, actual, 0.0f, 0.0f, size, size, size, size);
            } else {
                g.fill(left, top, left + actual, top + actual, 0xff242a30);
                Component message = rasterFailure == null
                        ? Component.translatable("gtl_enhancedcore.create_world_plot.preview_loading")
                        : Component.translatable("gtl_enhancedcore.create_world_plot.preview_failed");
                g.drawCenteredString(Minecraft.getInstance().font, message, left + actual / 2, top + actual / 2 - 4,
                        rasterFailure == null ? 0xffdce3e8 : 0xffffa69c);
            }
            finish(g, settings, left, top, actual, plots, grid);
        }

        private void upload(net.minecraft.client.renderer.texture.TextureManager manager, PlotSettings settings,
                            int plots, int size, int[] raster) {
            NativeImage image = new NativeImage(size, size, false);
            for (int z = 0; z < size; z++) for (int x = 0; x < size; x++) {
                int rgb = raster[z * size + x];
                image.setPixelRGBA(x, z, 0xff000000 | ((rgb & 255) << 16) | (rgb & 0xff00) | ((rgb >>> 16) & 255));
            }
            if (texture != null && size == rasterSize) {
                texture.setPixels(image);
                texture.upload();
            } else {
                closeTexture(manager);
                texture = new DynamicTexture(image);
                texture.setFilter(false, false);
                location = manager.register("plot_preview", texture);
            }
            previous = settings; count = plots; rasterSize = size;
        }

        private void closeTexture(net.minecraft.client.renderer.texture.TextureManager manager) {
            if (location != null && manager.getTexture(location) == texture) manager.release(location);
            else if (texture != null) texture.close();
            location = null; texture = null; previous = null; rasterSize = 0; count = 0;
        }

        private void cancelPendingRaster() {
            if (pendingCancelled != null) pendingCancelled.set(true);
            if (pendingRaster != null) pendingRaster.cancel(false);
            pendingRaster = null; pendingCancelled = null;
        }

        @Override public void close() {
            var manager = Minecraft.getInstance().getTextureManager();
            requestGeneration++;
            cancelPendingRaster();
            requested = null; completedRaster = null; rasterFailure = null;
            closeTexture(manager);
        }
    }

    /** Samples immutable terrain once; scrolling only transforms cached, world-aligned images. */
    public static final class AtlasCached implements AutoCloseable {
        private record Layer(ResourceLocation location, DynamicTexture texture) {}
        private String campusKey;
        private PlannedCampus.Bounds bounds;
        private CompletableFuture<CampusMapImage> source;
        private CampusMapImage image;
        private final List<Layer> layers = new ArrayList<>();
        private int sourceBuilds, textureUploads;

        public void draw(GuiGraphics g, PlotSettings settings, int x, int y, int width, int height,
                         PlotPreviewRaster.CampusViewport region) {
            if (source == null || !settings.campusKey().equals(campusKey)) {
                close();
                campusKey = settings.campusKey(); bounds = CampusPlanAccess.of(settings).bounds();
                var capturedBounds = bounds;
                source = CompletableFuture.supplyAsync(() -> CampusMapImage.capture(settings, capturedBounds), Util.backgroundExecutor());
                sourceBuilds++;
            }
            if (image == null && source.isDone()) {
                image = source.join();
                for (var ignored : image.levels()) layers.add(null);
            }
            if (image == null) {
                g.drawCenteredString(Minecraft.getInstance().font,
                        Component.translatable("gtl_enhancedcore.campus.teleport.map_loading"),
                        x + width / 2, y + height / 2 - 4, 0xffdce3e8);
                return;
            }
            double scale = Minecraft.getInstance().getWindow().getGuiScale();
            int level = image.levelFor(Math.min(region.width() / (width * scale), region.depth() / (height * scale)));
            int maxTexture = RenderSystem.maxSupportedTextureSize();
            while (level + 1 < image.levels().size() && (image.levels().get(level).width() > maxTexture
                    || image.levels().get(level).height() > maxTexture)) level++;
            var pixels = image.levels().get(level);
            var manager = Minecraft.getInstance().getTextureManager();
            Layer layer = layers.get(level);
            if (layer != null && manager.getTexture(layer.location) != layer.texture) {
                layer.texture.close(); layer = null;
            }
            if (layer == null) {
                NativeImage nativeImage = new NativeImage(pixels.width(), pixels.height(), false);
                for (int pz = 0; pz < pixels.height(); pz++) for (int px = 0; px < pixels.width(); px++) {
                    int rgb = pixels.pixel(px, pz);
                    nativeImage.setPixelRGBA(px, pz, 0xff000000 | ((rgb & 255) << 16) | (rgb & 0xff00) | ((rgb >>> 16) & 255));
                }
                var texture = new DynamicTexture(nativeImage);
                texture.setFilter(false, false);
                layer = new Layer(manager.register("campus_atlas", texture), texture);
                layers.set(level, layer); textureUploads++;
            }
            // The caller scissors to the viewport. Mapping the full image keeps hover coordinates exact.
            g.pose().pushPose();
            g.pose().translate(x + (bounds.minX() - region.minX()) * width / region.width(),
                    y + (bounds.minZ() - region.minZ()) * height / region.depth(), 0);
            g.pose().scale((float) (width / region.width()), (float) (height / region.depth()), 1);
            g.blit(layer.location, 0, 0, bounds.width(), bounds.depth(), 0, 0,
                    pixels.width(), pixels.height(), pixels.width(), pixels.height());
            g.pose().popPose();
        }

        public boolean ready() { return image != null && layers.stream().anyMatch(layer -> layer != null); }
        public int sourceBuilds() { return sourceBuilds; }
        public int textureUploads() { return textureUploads; }

        @Override public void close() {
            var manager = Minecraft.getInstance().getTextureManager();
            for (Layer layer : layers) if (layer != null) {
                if (manager.getTexture(layer.location) == layer.texture) manager.release(layer.location);
                else layer.texture.close();
            }
            layers.clear(); image = null; campusKey = null; bounds = null;
            if (source != null) source.cancel(false);
            source = null;
        }
    }

    private static int[] palette(PlotSettings settings) {
        return new int[] {color(settings.groundBlock()), color(settings.plotEdgeBlock()), color(settings.roadBlock()),
                color(settings.borderBlock()), color(settings.markerBlock()), color(settings.lineBlock())};
    }

    private static int color(String id) {
        try {
            Block block = BuiltInRegistries.BLOCK.get(ResourceLocation.tryParse(id));
            MapColor map = block.defaultBlockState().getMapColor(net.minecraft.world.level.EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
            if (map == null) return 0x777777;
            // Minecraft map colors / NativeImage use ABGR; the raster and GuiGraphics use ARGB.
            int abgr = map.calculateRGBColor(MapColor.Brightness.NORMAL);
            return ((abgr & 0xff) << 16) | (abgr & 0xff00) | ((abgr >>> 16) & 0xff);
        } catch (RuntimeException ignored) { return 0x777777; }
    }
}
