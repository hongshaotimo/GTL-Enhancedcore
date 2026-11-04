package com.gtl.enhancedcore;

import com.gtl.enhancedcore.common.world.plot.CampusMapImage;
import java.util.concurrent.CancellationException;

/** Verifies a fixed world sample grid and mip colors independently of viewport size, motion or frame timing. */
final class CampusMapImageRegression {
    static int run() {
        int checks = 0;
        int[] visits = new int[15];
        var map = CampusMapImage.sample(-3, -2, 5, 3, (x, z) -> {
            check(x >= -3 && x <= 1 && z >= -2 && z <= 0, "Source sampling remains within inclusive world bounds");
            visits[(z + 2) * 5 + x + 3]++;
            return (x + 3) * 0x200000 | (z + 2) * 0x002000 | 0x01000000;
        });
        check(map.width() == 5 && map.depth() == 3, "Non-square source dimensions are preserved"); checks++;
        for (int z = 0; z < 3; z++) for (int x = 0; x < 5; x++) {
            check(visits[z * 5 + x] == 1, "Each signed world coordinate is sampled exactly once"); checks++;
            check(map.levels().getFirst().pixel(x, z) == (0xff000000 | x * 0x200000 | z * 0x002000),
                    "Source colors use exact block coordinates and opaque ARGB"); checks++;
        }
        int[][] sizes = {{5, 3}, {3, 2}, {2, 1}, {1, 1}};
        check(map.levels().size() == sizes.length, "Mip chain ends at the first one-by-one level"); checks++;
        for (int i = 0; i < sizes.length; i++) {
            var level = map.levels().get(i);
            check(level.width() == sizes[i][0] && level.height() == sizes[i][1],
                    "Odd dimensions round up independently along each axis"); checks++;
        }
        var colors = CampusMapImage.sample(0, 0, 2, 2, (x, z) -> new int[] {0, 0xff0000, 0x00ff00, 0x0000ff}[z * 2 + x]);
        check(colors.levels().getLast().pixel(0, 0) == 0xff404040,
                "Box average rounds RGB channels without alpha leakage or packed-channel carries"); checks++;
        var odd = CampusMapImage.sample(-1, -1, 3, 3, (x, z) -> ((z + 1) * 3 + x + 1) * 10 * 0x010101);
        int[] expectedFirstMip = {20, 35, 65, 80};
        for (int i = 0; i < expectedFirstMip.length; i++) {
            check(odd.levels().get(1).pixel(i % 2, i / 2) == (0xff000000 | expectedFirstMip[i] * 0x010101),
                    "Partial edge boxes average existing cells without padding, wrapping or duplicating samples"); checks++;
        }
        check(odd.levels().getLast().pixel(0, 0) == 0xff323232, "Successive mips are averaged from the preceding level"); checks++;
        var line = CampusMapImage.sample(7, -9, 1, 3, (x, z) -> (z + 9) * 100);
        check(line.levels().get(1).width() == 1 && line.levels().get(1).height() == 2
                        && line.levels().get(1).pixel(0, 0) == 0xff000032
                        && line.levels().get(1).pixel(0, 1) == 0xff0000c8
                        && line.levels().getLast().pixel(0, 0) == 0xff00007d,
                "One-pixel-wide maps downsample without reading a nonexistent neighbor"); checks++;
        var single = CampusMapImage.sample(-20, 21, 1, 1, (x, z) -> 0x123456);
        check(single.levels().size() == 1 && single.levels().getFirst().pixel(0, 0) == 0xff123456,
                "One source pixel requires no artificial extra mip"); checks++;
        for (double[] choice : new double[][] {{.1, 0}, {1, 0}, {1.999, 0}, {2, 1}, {3.999, 1}, {4, 2}, {8, 3}, {1e9, 3}}) {
            check(map.levelFor(choice[0]) == (int) choice[1],
                    "Selected mip never has a coarser power-of-two footprint than the physical output pixel"); checks++;
        }
        try {
            map.levels().clear();
            throw new AssertionError("Callers cannot mutate the level list");
        } catch (UnsupportedOperationException expected) { checks++; }
        for (int[] point : new int[][] {{-1, 0}, {0, -1}, {5, 0}, {0, 3}}) {
            try {
                map.levels().getFirst().pixel(point[0], point[1]);
                throw new AssertionError("Out-of-bounds pixels must not alias the adjacent row");
            } catch (IndexOutOfBoundsException expected) { checks++; }
        }
        int[] calls = {0};
        try {
            Thread.currentThread().interrupt();
            CampusMapImage.sample(0, 0, 2, 2, (x, z) -> ++calls[0]);
            throw new AssertionError("Pre-interrupted image work must be cancelled");
        } catch (CancellationException expected) {
            check(calls[0] == 0 && Thread.currentThread().isInterrupted(),
                    "Cancellation preserves the interrupt flag and does not sample a source pixel"); checks++;
        } finally { Thread.interrupted(); }
        try {
            CampusMapImage.sample(0, 0, 3, 3, (x, z) -> {
                calls[0]++;
                Thread.currentThread().interrupt();
                return 0;
            });
            throw new AssertionError("Capture stops when interrupted while sampling");
        } catch (CancellationException expected) {
            check(calls[0] == 3, "Interrupt cancellation is bounded to the current source row"); checks++;
        } finally { Thread.interrupted(); }
        for (int[] dimensions : new int[][] {{0, 1}, {1, 0}, {-1, 2}, {Integer.MAX_VALUE, 2}}) {
            try {
                CampusMapImage.sample(0, 0, dimensions[0], dimensions[1], (x, z) -> 0);
                throw new AssertionError("Invalid dimensions cannot allocate an image");
            } catch (IllegalArgumentException expected) { checks++; }
        }
        try {
            CampusMapImage.sample(Integer.MAX_VALUE, 0, 2, 1, (x, z) -> 0);
            throw new AssertionError("World coordinate overflow cannot wrap sampling into negative coordinates");
        } catch (IllegalArgumentException expected) { checks++; }
        return checks;
    }

    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
