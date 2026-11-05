package com.gtl.enhancedcore.client.ftbchunks;

/** Half-open 512-block FTB regions mapped to shared integer GUI edges. */
public final class CampusMapTiles {
    public record Regions(int minX, int minZ, int maxX, int maxZ) {}
    public record Tile(int left, int top, int right, int bottom, float u0, float v0, float u1, float v1) {
        public int width() { return right - left; }
        public int height() { return bottom - top; }
    }
    private CampusMapTiles() {}

    public static Regions visible(double minX, double minZ, double width, double depth) {
        return new Regions((int) Math.floor(minX / 512), (int) Math.floor(minZ / 512),
                (int) Math.ceil((minX + width) / 512) - 1,
                (int) Math.ceil((minZ + depth) / 512) - 1);
    }

    public static Tile clip(int regionX, int regionZ, int width, int height,
                            double minX, double minZ, double worldWidth, double worldDepth) {
        double originX = regionX * 512.0, originZ = regionZ * 512.0;
        double left = Math.max(minX, originX), top = Math.max(minZ, originZ);
        double right = Math.min(minX + worldWidth, originX + 512), bottom = Math.min(minZ + worldDepth, originZ + 512);
        return new Tile(edge(left, minX, worldWidth, width), edge(top, minZ, worldDepth, height),
                edge(right, minX, worldWidth, width), edge(bottom, minZ, worldDepth, height),
                (float) ((left - originX) / 512), (float) ((top - originZ) / 512),
                (float) ((right - originX) / 512), (float) ((bottom - originZ) / 512));
    }

    private static int edge(double coordinate, double origin, double worldSize, int pixels) {
        return (int) Math.round((coordinate - origin) * pixels / worldSize);
    }
}
