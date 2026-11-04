package com.gtl.enhancedcore.common.world.plot;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.function.IntBinaryOperator;

/** Immutable block-aligned map pixels and box-filtered levels; independent of any Minecraft world. */
public final class CampusMapImage {
    public static final class Level {
        private final int width, height;
        private final int[] pixels;

        private Level(int width, int height, int[] pixels) {
            this.width = width;
            this.height = height;
            this.pixels = pixels;
        }
        public int width() { return width; }
        public int height() { return height; }
        public int pixel(int x, int z) {
            if (x < 0 || z < 0 || x >= width || z >= height) throw new IndexOutOfBoundsException();
            return pixels[z * width + x];
        }
    }

    private final int width, depth;
    private final List<Level> levels;

    private CampusMapImage(int width, int depth, List<Level> levels) {
        this.width = width;
        this.depth = depth;
        this.levels = List.copyOf(levels);
    }

    /** Samples only authored terrain data. The caller initializes its campus plan before background capture. */
    public static CampusMapImage capture(PlotSettings settings, PlannedCampus.Bounds bounds) {
        IntBinaryOperator sampler = switch (settings.roadStyle()) {
            case CAMPUS -> LegacyPlannedCampus::previewColorAt;
            case CAMPUS_PARK -> ParkPlannedCampus::previewColorAt;
            case CAMPUS_COMPACT -> CompactPlannedCampus::previewColorAt;
            case CAMPUS_DENSE -> DensePlannedCampus::previewColorAt;
            case CAMPUS_WOODLAND -> PlannedCampus::previewColorAt;
            default -> throw new IllegalArgumentException("Not a campus preset");
        };
        return sample(bounds.minX(), bounds.minZ(), bounds.width(), bounds.depth(), sampler);
    }

    /** Each source block is visited exactly once; negative world coordinates do not shift the sample grid. */
    public static CampusMapImage sample(int minX, int minZ, int width, int depth, IntBinaryOperator sampler) {
        if (width < 1 || depth < 1 || (long) width * depth > Integer.MAX_VALUE
                || (long) minX + width - 1 > Integer.MAX_VALUE || (long) minZ + depth - 1 > Integer.MAX_VALUE)
            throw new IllegalArgumentException("Invalid campus image bounds");
        checkInterrupted();
        int[] pixels = new int[width * depth];
        for (int z = 0; z < depth; z++) {
            checkInterrupted();
            for (int x = 0; x < width; x++)
                pixels[z * width + x] = 0xff000000 | (sampler.applyAsInt(minX + x, minZ + z) & 0xffffff);
        }
        List<Level> result = new ArrayList<>();
        Level current = new Level(width, depth, pixels);
        result.add(current);
        while (current.width > 1 || current.height > 1) {
            int nextWidth = (current.width + 1) / 2, nextHeight = (current.height + 1) / 2;
            int[] next = new int[nextWidth * nextHeight];
            for (int z = 0; z < nextHeight; z++) {
                checkInterrupted();
                for (int x = 0; x < nextWidth; x++) {
                    int red = 0, green = 0, blue = 0, count = 0;
                    for (int sz = z * 2; sz < Math.min(z * 2 + 2, current.height); sz++) {
                        for (int sx = x * 2; sx < Math.min(x * 2 + 2, current.width); sx++) {
                            int color = current.pixels[sz * current.width + sx];
                            red += (color >>> 16) & 255;
                            green += (color >>> 8) & 255;
                            blue += color & 255;
                            count++;
                        }
                    }
                    next[z * nextWidth + x] = 0xff000000 | ((red + count / 2) / count << 16)
                            | ((green + count / 2) / count << 8) | (blue + count / 2) / count;
                }
            }
            current = new Level(nextWidth, nextHeight, next);
            result.add(current);
        }
        return new CampusMapImage(width, depth, result);
    }

    public int width() { return width; }
    public int depth() { return depth; }
    public List<Level> levels() { return levels; }

    /** Uses the finest power-of-two footprint no larger than the requested physical pixel footprint. */
    public int levelFor(double blocksPerPhysicalPixel) {
        int level = 0;
        double footprint = 2;
        while (level + 1 < levels.size() && footprint <= blocksPerPhysicalPixel) {
            level++;
            footprint *= 2;
        }
        return level;
    }

    private static void checkInterrupted() {
        if (Thread.currentThread().isInterrupted()) throw new CancellationException("Campus image capture interrupted");
    }
}
