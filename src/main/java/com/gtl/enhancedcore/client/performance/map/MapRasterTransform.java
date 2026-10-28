package com.gtl.enhancedcore.client.performance.map;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

/** Pure pixel conversion; parallel workers only read private snapshots and write distinct planes. */
public final class MapRasterTransform {
    private MapRasterTransform() {}

    public static void populate(short[] height, short[] waterLightAndBiome, int[] foliage, int[] grass, int[] water,
                                int[][] output, int workers, ExecutorService executor) {
        int pixels = height.length;
        if (pixels != waterLightAndBiome.length || pixels != foliage.length || pixels != grass.length
                || pixels != water.length || output.length != 5) throw new IllegalArgumentException("Invalid map planes");
        for (int[] plane : output) if (plane.length != pixels) throw new IllegalArgumentException("Invalid output plane");
        if (workers <= 1 || executor == null) {
            fill(height, waterLightAndBiome, foliage, grass, water, output);
            return;
        }

        short[] heightSnapshot = height.clone();
        short[] waterLightSnapshot = waterLightAndBiome.clone();
        int[] foliageSnapshot = foliage.clone();
        int[] grassSnapshot = grass.clone();
        int[] waterColorSnapshot = water.clone();
        List<java.util.concurrent.Callable<Void>> tasks = new ArrayList<>(5);
        for (int plane = 0; plane < 5; plane++) {
            final int index = plane;
            tasks.add(() -> {
                fillPlane(heightSnapshot, waterLightSnapshot, foliageSnapshot, grassSnapshot,
                        waterColorSnapshot, output[index], index);
                return null;
            });
        }
        try {
            for (var future : executor.invokeAll(tasks)) future.get();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while building FTB map image", interrupted);
        } catch (ExecutionException failed) {
            fill(height, waterLightAndBiome, foliage, grass, water, output);
        }
    }

    private static void fill(short[] height, short[] waterLightAndBiome, int[] foliage, int[] grass, int[] water,
                             int[][] output) {
        for (int plane = 0; plane < output.length; plane++)
            fillPlane(height, waterLightAndBiome, foliage, grass, water, output[plane], plane);
    }

    private static void fillPlane(short[] height, short[] waterLightAndBiome, int[] foliage, int[] grass, int[] water,
                                  int[] target, int plane) {
        for (int i = 0; i < target.length; i++) {
            target[i] = switch (plane) {
                case 0 -> (height[i] & 0xffff) << 16 | (waterLightAndBiome[i] & 0xffff);
                case 1 -> foliage[i] & 0x00ffffff;
                case 2 -> grass[i] & 0x00ffffff;
                case 3 -> water[i] & 0x00ffffff;
                case 4 -> ((foliage[i] >>> 24 & 0xff) << 16)
                        | ((grass[i] >>> 24 & 0xff) << 8) | (water[i] >>> 24 & 0xff);
                default -> throw new IllegalArgumentException("Unknown image plane: " + plane);
            };
        }
    }
}
