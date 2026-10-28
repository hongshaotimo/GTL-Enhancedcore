package com.gtl.enhancedcore.client.performance.map;

import com.gtl.enhancedcore.common.performance.ClientThreadSettings;
import java.awt.image.BufferedImage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

/** Bounded client-only workers for the pure FTB Chunks map image transform. */
public final class MapRasterWorkers {
    private static final int THREADS = ClientThreadSettings.mapRasterThreads();
    private static final ExecutorService EXECUTOR = createExecutor();
    private static final System.Logger LOG = System.getLogger("GTL-Enhancedcore/MapRaster");

    static {
        LOG.log(System.Logger.Level.INFO, "FTB map raster workers: {0}", THREADS);
    }

    private MapRasterWorkers() {}

    public static void populate(short[] height, short[] waterLightAndBiome, int[] foliage, int[] grass, int[] water,
                                BufferedImage[] images) {
        if (images.length != 5) throw new IllegalArgumentException("Expected five FTB map images");
        int[][] output = new int[5][];
        for (int i = 0; i < images.length; i++) {
            if (!(images[i] instanceof PackedMapImage packed))
                throw new IllegalArgumentException("FTB map image was not packed");
            output[i] = packed.pixelBuffer();
        }
        MapRasterTransform.populate(height, waterLightAndBiome, foliage, grass, water, output, THREADS, EXECUTOR);
    }

    private static ExecutorService createExecutor() {
        if (THREADS <= 1) return null;
        ThreadFactory factory = runnable -> {
            Thread thread = new Thread(runnable, "GTL map raster");
            thread.setDaemon(true);
            return thread;
        };
        return Executors.newFixedThreadPool(THREADS, factory);
    }
}
