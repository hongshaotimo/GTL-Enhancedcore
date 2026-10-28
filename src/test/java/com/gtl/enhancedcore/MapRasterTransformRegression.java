package com.gtl.enhancedcore;

import com.gtl.enhancedcore.client.performance.map.MapRasterTransform;
import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.Executors;

final class MapRasterTransformRegression {
    private static int assertions;

    static int run() {
        assertions = 0;
        for (int length : new int[]{1, 17, 512 * 512}) compare(length);
        return assertions;
    }

    private static void compare(int length) {
        short[] height = new short[length], waterLight = new short[length];
        int[] foliage = new int[length], grass = new int[length], water = new int[length];
        Random random = new Random(0x4d415052415354L + length);
        for (int i = 0; i < length; i++) {
            height[i] = (short) random.nextInt(); waterLight[i] = (short) random.nextInt();
            foliage[i] = random.nextInt(); grass[i] = random.nextInt(); water[i] = random.nextInt();
        }
        int[][] serial = planes(length), parallel = planes(length);
        MapRasterTransform.populate(height, waterLight, foliage, grass, water, serial, 1, null);
        var executor = Executors.newFixedThreadPool(4);
        try { MapRasterTransform.populate(height, waterLight, foliage, grass, water, parallel, 4, executor); }
        finally { executor.shutdownNow(); }
        for (int i = 0; i < serial.length; i++) {
            check(Arrays.equals(serial[i], parallel[i]), "Serial and worker pixels match for plane " + i + " length " + length);
        }
    }

    private static int[][] planes(int length) {
        return new int[][]{new int[length], new int[length], new int[length], new int[length], new int[length]};
    }

    private static void check(boolean value, String message) {
        assertions++;
        if (!value) throw new AssertionError(message);
    }
}
