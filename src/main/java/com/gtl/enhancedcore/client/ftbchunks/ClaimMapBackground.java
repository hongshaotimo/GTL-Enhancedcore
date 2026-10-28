package com.gtl.enhancedcore.client.ftbchunks;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.ftb.mods.ftbchunks.client.map.MapDimension;
import dev.ftb.mods.ftbchunks.client.map.MapRegion;
import dev.ftb.mods.ftblibrary.icon.Color4I;
import dev.ftb.mods.ftblibrary.math.XZ;
import dev.ftb.mods.ftblibrary.ui.GuiHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.level.ChunkPos;
import org.lwjgl.opengl.GL11;

/**
 * Draws the claim map background straight from FTB region textures (512 px = 32 chunks each).
 * The upstream minimap texture only covers 15x15 chunks, so it cannot back the 31x31 map.
 */
public final class ClaimMapBackground {
    private ClaimMapBackground() {
    }

    public static void draw(GuiGraphics graphics, MapDimension dimension, int sx, int sy) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || dimension == null) return;
        ChunkPos center = player.chunkPosition();
        int minX = center.x - ChunkMapLayout.RADIUS;
        int minZ = center.z - ChunkMapLayout.RADIUS;
        int maxX = center.x + ChunkMapLayout.RADIUS;
        int maxZ = center.z + ChunkMapLayout.RADIUS;
        int tile = ChunkMapLayout.tileSize();
        int filter = tile >= 16 ? GL11.GL_NEAREST : GL11.GL_LINEAR;
        for (int rz = minZ >> 5; rz <= maxZ >> 5; rz++) {
            for (int rx = minX >> 5; rx <= maxX >> 5; rx++) {
                MapRegion region = dimension.getRegion(XZ.of(rx, rz));
                int textureId = region.getRenderedMapImageTextureId();
                if (!region.isMapImageLoaded()) continue;
                int originX = rx << 5;
                int originZ = rz << 5;
                int x0 = Math.max(minX, originX);
                int z0 = Math.max(minZ, originZ);
                int x1 = Math.min(maxX, originX + 31);
                int z1 = Math.min(maxZ, originZ + 31);
                RenderSystem.bindTextureForSetup(textureId);
                RenderSystem.texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, filter);
                RenderSystem.texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, filter);
                RenderSystem.setShaderTexture(0, textureId);
                GuiHelper.drawTexturedRect(graphics,
                        sx + (x0 - minX) * tile, sy + (z0 - minZ) * tile,
                        (x1 - x0 + 1) * tile, (z1 - z0 + 1) * tile, Color4I.WHITE,
                        (x0 - originX) / 32F, (z0 - originZ) / 32F,
                        (x1 + 1 - originX) / 32F, (z1 + 1 - originZ) / 32F);
            }
        }
    }
}
