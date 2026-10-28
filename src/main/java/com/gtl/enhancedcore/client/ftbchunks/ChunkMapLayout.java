package com.gtl.enhancedcore.client.ftbchunks;

/**
 * Fixed 31x31 chunk claim map (upstream FTB Chunks: 15x15). The chunk count never changes;
 * only the on-screen tile size shrinks when the scaled window cannot fit 31 tiles of 16 px.
 */
public final class ChunkMapLayout {
    public static final int CHUNKS = 31;
    public static final int RADIUS = CHUNKS / 2;
    static final int MIN_TILE = 4;
    static final int MAX_TILE = 16;
    static final int MARGIN = 40;
    private static int tileSize = MAX_TILE;

    private ChunkMapLayout() {
    }

    public static int tileFor(int screenWidth, int screenHeight) {
        int fit = Math.min((screenWidth - MARGIN) / CHUNKS, (screenHeight - MARGIN) / CHUNKS);
        return Math.max(MIN_TILE, Math.min(MAX_TILE, fit));
    }

    public static void configure(int screenWidth, int screenHeight) {
        tileSize = tileFor(screenWidth, screenHeight);
    }

    public static int tileSize() {
        return tileSize;
    }

    public static int mapSize() {
        return CHUNKS * tileSize;
    }

    /** Pixel offset of the player's chunk origin inside the map. */
    public static int center() {
        return RADIUS * tileSize;
    }

    public static double pixelScale() {
        return tileSize / 16.0;
    }
}
