package com.gtl.enhancedcore.client.plot;

import com.gtl.enhancedcore.common.world.plot.PlotPreviewRaster;
import com.gtl.enhancedcore.common.world.plot.PlotSettings;
import com.mojang.blaze3d.platform.NativeImage;
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

        public void draw(GuiGraphics g, PlotSettings settings, int left, int top, int pixels, int plots, boolean grid) {
            int actual = Math.max(1, pixels), size = Math.min(actual, PlotPreviewRaster.MAX_PIXELS);
            var manager = Minecraft.getInstance().getTextureManager();
            if (location != null && manager.getTexture(location) != texture) close();
            if (!settings.equals(previous) || plots != count || size != rasterSize || location == null) {
                int[] raster = PlotPreviewRaster.render(settings, size, plots, palette(settings));
                NativeImage image = new NativeImage(size, size, false);
                for (int z = 0; z < size; z++) for (int x = 0; x < size; x++) {
                    int rgb = raster[z * size + x];
                    image.setPixelRGBA(x, z, 0xff000000 | ((rgb & 255) << 16) | (rgb & 0xff00) | ((rgb >>> 16) & 255));
                }
                if (texture != null && size == rasterSize) {
                    texture.setPixels(image); // closes the previous NativeImage
                    texture.upload();
                } else {
                    close();
                    texture = new DynamicTexture(image);
                    texture.setFilter(false, false);
                    location = manager.register("plot_preview", texture);
                }
                previous = settings; count = plots; rasterSize = size;
            }
            // Explicit source and target sizes: the full texture is used even at the safety bound.
            g.blit(location, left, top, actual, actual, 0.0f, 0.0f, size, size, size, size);
            finish(g, settings, left, top, actual, plots, grid);
        }

        @Override public void close() {
            var manager = Minecraft.getInstance().getTextureManager();
            if (location != null && manager.getTexture(location) == texture) manager.release(location);
            else if (texture != null) texture.close();
            location = null; texture = null; previous = null; rasterSize = 0; count = 0;
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
