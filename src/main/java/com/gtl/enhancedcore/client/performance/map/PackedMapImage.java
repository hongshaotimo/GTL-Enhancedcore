package com.gtl.enhancedcore.client.performance.map;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;

/** Fresh, privately owned map-save image; keeps upstream PNG types and pixel semantics. */
public final class PackedMapImage extends BufferedImage {
    private final int[] pixels;
    private final int width;
    private final int height;
    private final int colorMask;

    private PackedMapImage(int width, int height, int type) {
        super(width, height, type);
        this.width = width;
        this.height = height;
        this.colorMask = type == TYPE_INT_RGB ? 0x00ffffff : -1;
        // The standard constructor owns a zero-offset, width-stride packed-int raster.
        // Accessing its buffer also marks it untrackable for Java2D, so direct writes
        // cannot leave a stale accelerated image. Never use a shared/subimage raster.
        this.pixels = ((DataBufferInt) getRaster().getDataBuffer()).getData();
    }

    public static BufferedImage create(int width, int height, int type) {
        return type == TYPE_INT_RGB || type == TYPE_INT_ARGB
                ? new PackedMapImage(width, height, type)
                : new BufferedImage(width, height, type);
    }

    int[] pixelBuffer() { return pixels; }

    @Override
    public void setRGB(int x, int y, int rgb) {
        // Checking each axis preserves setRGB's failure instead of aliasing a later row.
        if (x < 0 || y < 0 || x >= width || y >= height) {
            throw new ArrayIndexOutOfBoundsException("Coordinate out of bounds!");
        }
        pixels[y * width + x] = rgb & colorMask;
    }
}
