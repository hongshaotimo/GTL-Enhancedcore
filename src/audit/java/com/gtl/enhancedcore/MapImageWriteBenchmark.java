package com.gtl.enhancedcore;

import com.gtl.enhancedcore.client.performance.map.PackedMapImage;
import com.sun.management.ThreadMXBean;
import java.awt.image.BufferedImage;
import java.lang.management.ManagementFactory;
import java.util.Arrays;
import java.util.Random;

/** Standalone synthetic map-save capture benchmark. Does not start Minecraft or write a world. */
public final class MapImageWriteBenchmark {
    private static volatile BufferedImage[] sink;
    private static final short[] HEIGHT = new short[512 * 512];
    private static final short[] WATER_LIGHT = new short[512 * 512];
    private static final int[][] COLORS = new int[3][512 * 512];

    public static void main(String[] args) {
        Random random = new Random(8192);
        for (int i = 0; i < HEIGHT.length; i++) {
            HEIGHT[i] = (short) random.nextInt();
            WATER_LIGHT[i] = (short) random.nextInt();
            for (int[] colors : COLORS) colors[i] = random.nextInt();
        }
        ThreadMXBean memory = ManagementFactory.getPlatformMXBean(ThreadMXBean.class);
        boolean allocations = memory != null && memory.isThreadAllocatedMemorySupported();
        if (allocations) memory.setThreadAllocatedMemoryEnabled(true);
        for (int i = 0; i < 30; i++) { capture(false); capture(true); }
        int samples = 30;
        long[][] times = new long[2][samples];
        long[][] bytes = new long[2][samples];
        for (int i = 0; i < samples; i++) {
            // Alternate order so later variants do not systematically get the warmer JVM.
            for (int order = 0; order < 2; order++) {
                int variant = (i + order) & 1;
                long beforeBytes = allocations ? memory.getThreadAllocatedBytes(Thread.currentThread().threadId()) : 0;
                long beforeTime = System.nanoTime();
                capture(variant == 1);
                times[variant][i] = System.nanoTime() - beforeTime;
                bytes[variant][i] = allocations
                        ? memory.getThreadAllocatedBytes(Thread.currentThread().threadId()) - beforeBytes : -1;
            }
        }
        System.out.println("Synthetic 512x512 five-image FTB write capture; no PNG/zip/disk; 30 warmups + 30 samples");
        System.out.println("java=" + System.getProperty("java.version"));
        for (int variant = 0; variant < 2; variant++) {
            Arrays.sort(times[variant]);
            Arrays.sort(bytes[variant]);
            System.out.printf("%s median_ms=%.3f p90_ms=%.3f allocated_bytes=%d%n",
                    variant == 0 ? "JDK_setRGB" : "PackedMapImage", times[variant][15] / 1e6,
                    times[variant][26] / 1e6, bytes[variant][15]);
        }
    }

    private static void capture(boolean packed) {
        BufferedImage[] images = new BufferedImage[5];
        for (int i = 0; i < images.length; i++) {
            int type = i == 0 ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB;
            images[i] = packed ? PackedMapImage.create(512, 512, type) : new BufferedImage(512, 512, type);
        }
        for (int y = 0; y < 512; y++) {
            for (int x = 0; x < 512; x++) {
                int index = x + y * 512;
                images[0].setRGB(x, y, (HEIGHT[index] & 65535) << 16 | (WATER_LIGHT[index] & 65535));
                images[1].setRGB(x, y, 0xff000000 | (COLORS[0][index] & 0xffffff));
                images[2].setRGB(x, y, 0xff000000 | (COLORS[1][index] & 0xffffff));
                images[3].setRGB(x, y, 0xff000000 | (COLORS[2][index] & 0xffffff));
                int blockIndex = (COLORS[0][index] >>> 24) << 16
                        | (COLORS[1][index] >>> 24) << 8 | (COLORS[2][index] >>> 24);
                images[4].setRGB(x, y, 0xff000000 | blockIndex);
            }
        }
        sink = images;
    }
}
