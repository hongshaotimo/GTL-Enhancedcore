package com.gtl.enhancedcore.client.ftbchunks;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.ftb.mods.ftbchunks.client.map.MapDimension;
import dev.ftb.mods.ftbchunks.client.map.MapManager;
import dev.ftb.mods.ftbchunks.client.map.MapRegion;
import dev.ftb.mods.ftblibrary.icon.Color4I;
import dev.ftb.mods.ftblibrary.math.XZ;
import dev.ftb.mods.ftblibrary.ui.GuiHelper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.lwjgl.opengl.GL11;

/** Screen-owned view of FTB's live textures; FTB retains ownership of all map data and GL textures. */
public final class CampusLiveMapBackground implements AutoCloseable {
    public int draw(GuiGraphics graphics, ResourceKey<Level> dimension, int x, int y, int width, int height,
                    double minX, double minZ, double worldWidth, double worldDepth) {
        if (width <= 0 || height <= 0 || !(worldWidth > 0) || !(worldDepth > 0)) return 0;
        MapManager manager = MapManager.getInstance().orElse(null);
        if (manager == null || manager.isInvalid()) return 0;
        // This only creates FTB's map-dimension wrapper. It never loads a world chunk or a region.
        MapDimension mapDimension = manager.getDimension(dimension);

        var tiles = CampusMapTiles.visible(minX, minZ, worldWidth, worldDepth);
        double pixelsPerBlock = Math.min(width / worldWidth, height / worldDepth)
                * Minecraft.getInstance().getWindow().getGuiScale();
        int filter = pixelsPerBlock >= 1 ? GL11.GL_NEAREST : GL11.GL_LINEAR;
        List<MapRegion> visibleRegions = new ArrayList<>();
        synchronized (manager.lock) {
            if (manager.isInvalid() || MapManager.getInstance().orElse(null) != manager) return 0;
            if (manager.getDimensions().get(dimension) != mapDimension) return 0;
            // FTB 2001.3.8 lazily reads its saved region index on the first call only.
            // Keep no registry reference so a purge/reload or manager change is visible next frame.
            Map<XZ, MapRegion> regions = mapDimension.getRegions();
            for (int regionZ = tiles.minZ(); regionZ <= tiles.maxZ(); regionZ++) for (int regionX = tiles.minX(); regionX <= tiles.maxX(); regionX++) {
                MapRegion region = regions.get(XZ.of(regionX, regionZ));
                if (region != null) visibleRegions.add(region);
            }
        }
        // FTB draws immediately through the tessellator; finish GuiGraphics' queued background first.
        graphics.flush();
        int rendered = 0;
        for (MapRegion region : visibleRegions) {
            XZ position = region.pos;
            int regionX = position.x(), regionZ = position.z();
            int texture = region.getRenderedMapImageTextureId();
            if (!region.isMapImageLoaded()) continue;

            var tile = CampusMapTiles.clip(regionX, regionZ, width, height, minX, minZ, worldWidth, worldDepth);
            if (tile.width() <= 0 || tile.height() <= 0) continue;
            RenderSystem.bindTextureForSetup(texture);
            RenderSystem.texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, filter);
            RenderSystem.texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, filter);
            RenderSystem.setShaderTexture(0, texture);
            GuiHelper.drawTexturedRect(graphics,
                    x + tile.left(), y + tile.top(), tile.width(), tile.height(), Color4I.WHITE,
                    tile.u0(), tile.v0(), tile.u1(), tile.v1());
            rendered++;
        }
        return rendered;
    }

    /** There are no retained world/manager/region references or resources owned by this view. */
    @Override public void close() {}
}
