package com.gtl.enhancedcore.mixin.ftbchunks.client;

import com.gtl.enhancedcore.client.performance.map.PackedMapImage;
import com.gtl.enhancedcore.client.performance.map.MapRasterWorkers;
import dev.ftb.mods.ftbchunks.client.FTBChunksClient;
import dev.ftb.mods.ftbchunks.client.map.MapChunk;
import dev.ftb.mods.ftbchunks.client.map.MapRegionData;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.Collection;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.gen.Invoker;

/** FTB Chunks 2001.3.8: optionally parallelize only the pure map pixel conversion. */
@Mixin(value = MapRegionData.class, remap = false)
public abstract class MapRegionPackedImageMixin {
    @Shadow @Final public dev.ftb.mods.ftbchunks.client.map.MapRegion region;
    @Shadow @Final public short[] height;
    @Shadow @Final public short[] waterLightAndBiome;
    @Shadow @Final public int[] foliage;
    @Shadow @Final public int[] grass;
    @Shadow @Final public int[] water;

    @Invoker("writeData")
    protected abstract void enhanced$writeData(Collection<MapChunk> modified, BufferedImage data,
            BufferedImage foliageImage, BufferedImage grassImage, BufferedImage waterImage, BufferedImage blocksImage)
            throws IOException;

    @Inject(method = "write()V", at = @At("HEAD"), cancellable = true, require = 1)
    private void enhanced$parallelMapRaster(CallbackInfo ci) {
        Collection<MapChunk> modified;
        synchronized (region.dimension.getManager().lock) {
            modified = region.getModifiedChunks();
        }
        if (modified.isEmpty()) {
            ci.cancel();
            return;
        }

        BufferedImage data = PackedMapImage.create(512, 512, BufferedImage.TYPE_INT_ARGB);
        BufferedImage foliageImage = PackedMapImage.create(512, 512, BufferedImage.TYPE_INT_RGB);
        BufferedImage grassImage = PackedMapImage.create(512, 512, BufferedImage.TYPE_INT_RGB);
        BufferedImage waterImage = PackedMapImage.create(512, 512, BufferedImage.TYPE_INT_RGB);
        BufferedImage blocksImage = PackedMapImage.create(512, 512, BufferedImage.TYPE_INT_RGB);
        MapRasterWorkers.populate(height, waterLightAndBiome, foliage, grass, water,
                new BufferedImage[]{data, foliageImage, grassImage, waterImage, blocksImage});
        FTBChunksClient.MAP_EXECUTOR.execute(() -> {
            try {
                enhanced$writeData(modified, data, foliageImage, grassImage, waterImage, blocksImage);
            } catch (Exception error) {
                System.getLogger("FTB Chunks").log(System.Logger.Level.ERROR,
                        "Failed saving map region " + region, error);
                error.printStackTrace();
            }
        });
        ci.cancel();
    }
}
