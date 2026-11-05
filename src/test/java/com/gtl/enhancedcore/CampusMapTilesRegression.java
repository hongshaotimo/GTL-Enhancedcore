package com.gtl.enhancedcore;

import com.gtl.enhancedcore.client.ftbchunks.CampusMapTiles;

/** Checks signed region boundaries and seam-free screen coverage while dragging and zooming. */
final class CampusMapTilesRegression {
    private static int checks;

    static int run() {
        checks = 0;
        check(CampusMapTiles.visible(-512, -512, 512, 512).equals(new CampusMapTiles.Regions(-1, -1, -1, -1)),
                "A negative half-open region excludes zero and the next region");
        check(CampusMapTiles.visible(0, 0, 512, 512).equals(new CampusMapTiles.Regions(0, 0, 0, 0)),
                "A full region does not spuriously request the positive border");
        check(CampusMapTiles.visible(-.5, -.5, 1, 1).equals(new CampusMapTiles.Regions(-1, -1, 0, 0)),
                "Fractional panning across zero includes both neighboring regions");
        for (int step = 0; step < 32; step++) {
            int width = 37 + step * 3, height = 29 + step * 2;
            double minX = -1000 + step * 37.25, minZ = -850 + step * 29.125;
            double worldWidth = 2000.0 / Math.pow(1.25, step % 12), worldDepth = worldWidth * .85;
            var range = CampusMapTiles.visible(minX, minZ, worldWidth, worldDepth);
            int[] coverage = new int[width * height];
            for (int z = range.minZ(); z <= range.maxZ(); z++) for (int x = range.minX(); x <= range.maxX(); x++) {
                var tile = CampusMapTiles.clip(x, z, width, height, minX, minZ, worldWidth, worldDepth);
                check(tile.left() >= 0 && tile.top() >= 0 && tile.right() <= width && tile.bottom() <= height,
                        "Clipped region remains within its integer GUI viewport");
                check(tile.u0() >= 0 && tile.v0() >= 0 && tile.u1() <= 1 && tile.v1() <= 1,
                        "Clipped UVs remain within their own FTB region texture");
                for (int py = tile.top(); py < tile.bottom(); py++) for (int px = tile.left(); px < tile.right(); px++)
                    coverage[py * width + px]++;
            }
            check(java.util.Arrays.stream(coverage).allMatch(count -> count == 1),
                    "Fractional panning and zoom cover every pixel once without gaps or tile overlap");
        }
        return checks;
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        checks++;
    }
}
