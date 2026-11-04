package com.gtl.enhancedcore.common.world.plot;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Pure vanilla garden sampling; callers reserve the whole footprint, including crowns and lamps. */
public final class DenseCampusLandscape {
    private static final int RADIUS = 12, HEIGHT = 13, BELT_HALF_WIDTH = 5, TREE_PITCH = 16;
    private static final int COURT_X_PITCH = 40, COURT_Z_PITCH = 48, PARK_MIN_HALF_EXTENT = 32;
    private static final String PALE = "minecraft:light_gray_concrete";
    private static final String GRASS = "minecraft:grass_block";
    private static final String SOIL = "minecraft:coarse_dirt";
    private static final String QUARTZ = "minecraft:smooth_quartz";
    private static final String PILLAR = "minecraft:quartz_pillar[axis=y]";
    private static final String SLAB = "minecraft:smooth_quartz_slab[type=bottom]";
    private static final String STONE = "minecraft:polished_andesite";
    private static final String STONE_SLAB = "minecraft:polished_andesite_slab[type=bottom]";
    private static final String WOOD_TOP = "minecraft:dark_oak_slab[type=top]";
    private static final String WOOD_SLAB = "minecraft:dark_oak_slab[type=bottom]";
    private static final String LOG = "minecraft:stripped_birch_log[axis=y]";
    private static final String LEAVES = "minecraft:birch_leaves[persistent=true]";
    private static final String HEDGE = "minecraft:azalea_leaves[persistent=true]";
    private static final String FLOWER = "minecraft:flowering_azalea_leaves[persistent=true]";
    private static final String WHITE_FLOWER = "minecraft:oxeye_daisy";
    private static final String PURPLE_FLOWER = "minecraft:allium";
    private static final String WATER = "minecraft:water";
    private static final String LIGHT = "minecraft:sea_lantern";
    private static final String WALL = "minecraft:polished_blackstone_brick_wall";
    private static final String CHAIN = "minecraft:chain[axis=y]";
    private static final String LANTERN = "minecraft:lantern[hanging=true]";
    private static final Set<String> MATERIALS;

    static {
        Set<String> palette = new LinkedHashSet<>(List.of(PALE, GRASS, SOIL, QUARTZ, PILLAR, SLAB,
                STONE, STONE_SLAB, WOOD_TOP, WOOD_SLAB, LOG, LEAVES, HEDGE, FLOWER, WHITE_FLOWER,
                PURPLE_FLOWER, WATER, LIGHT, WALL, CHAIN, LANTERN));
        for (String facing : List.of("north", "south", "east", "west")) {
            palette.add(seat(facing));
            palette.add(lampShoulder(facing));
        }
        MATERIALS = Set.copyOf(palette);
    }

    private DenseCampusLandscape() {}

    public static Set<String> materials() { return MATERIALS; }
    public static int radius() { return RADIUS; }
    /** Inclusive relative Y: includes all tree crowns, lamp caps and pergola roofs. */
    public static int height() { return HEIGHT; }
    public static int beltHalfWidth() { return BELT_HALF_WIDTH; }
    public static int treePitch() { return TREE_PITCH; }
    public static int parkMinHalfExtent() { return PARK_MIN_HALF_EXTENT; }
    public static int parkHeight() { return HEIGHT; }
    public static int roadsideHeight(int halfWidth) { return halfWidth < 1 || halfWidth > 5 ? 0 : halfWidth >= 3 ? HEIGHT : 7; }

    /** Ground is y=0. Reserve 25 by 25; flush paving reaches the edges without a grass frame. */
    public static String surface(int dx, int dz, int variant) {
        if (!contains(dx, dz)) return null;
        int x = Math.abs(dx), z = Math.abs(dz);
        return switch (Math.floorMod(variant, 4)) {
            case 0 -> {
                if (poolWater(x, z)) yield WATER;
                if (poolEnvelope(x, z)) yield QUARTZ;
                if (cornerTree(dx, dz)) yield treeSurface(x - 9, z - 9);
                if (z == 11 && x <= 5) yield SOIL;
                yield z >= 6 && z <= 8 || x >= 10 || x <= 1 ? STONE : PALE;
            }
            case 1 -> {
                int treeZ = groveTreeZ(dz);
                if (x >= 5 && x <= 9 && Math.abs(dz - treeZ) <= 2)
                    yield treeSurface(x - 7, dz - treeZ);
                if (x == 11 && z >= 3 && z <= 9) yield SOIL;
                yield x <= 2 || z <= 1 || z == 11 ? STONE : PALE;
            }
            case 2 -> {
                if (pergolaTree(dx, dz)) yield treeSurface(x - 10, z - 9);
                if (z == 10 && x >= 2 && x <= 5) yield SOIL;
                yield x <= 2 || z <= 1 || x == 8 || z == 12 ? STONE : PALE;
            }
            default -> {
                int tx = parterreTreeX(dx, dz), tz = parterreTreeZ(dx, dz);
                if (Math.abs(dx - tx) <= 2 && Math.abs(dz - tz) <= 2)
                    yield treeSurface(dx - tx, dz - tz);
                if (flowerBed(x, z)) yield SOIL;
                yield x <= 1 || z <= 1 || x == 5 || z == 5 || x == 11 || z == 11 ? STONE : PALE;
            }
        };
    }

    /** All source water has a solid y=-1 floor and solid walls at y=0. */
    public static String foundation(int dx, int y, int dz, int variant) {
        if (y != -1 || !contains(dx, dz) || Math.floorMod(variant, 4) != 0) return null;
        int x = Math.abs(dx), z = Math.abs(dz);
        if (!poolEnvelope(x, z)) return null;
        return poolWater(x, z) && z == 0 && x % 4 == 0 ? LIGHT : QUARTZ;
    }

    /** Empty cells return null. No sampler touches a world or places functional equipment. */
    public static String above(int dx, int y, int dz, int variant) {
        if (y <= 0 || y > HEIGHT || !contains(dx, dz)) return null;
        return switch (Math.floorMod(variant, 4)) {
            case 0 -> poolAbove(dx, y, dz);
            case 1 -> groveAbove(dx, y, dz);
            case 2 -> pergolaAbove(dx, y, dz);
            default -> parterreAbove(dx, y, dz);
        };
    }

    /** Exact occupied column height; avoids fixed generation caps cutting off crowns or roofs. */
    public static int top(int dx, int dz, int variant) {
        if (!contains(dx, dz)) return 0;
        for (int y = HEIGHT; y > 0; y--) if (above(dx, y, dz, variant) != null) return y;
        return 0;
    }

    /**
     * Continuous 11-wide boulevard garden. Along is [-halfLength,halfLength], across is [-5,5].
     * Map the axes in the caller and reserve the full rectangle. Min halfLength is 6.
     * 0/2: twin tree rows; 1/3: one tree row plus an enclosed source-water rill.
     * End setbacks contain complete crowns; seats and lamps fill the bays between trees.
     */
    public static String beltSurface(int along, int across, int halfLength, int variant) {
        if (!beltContains(along, across, halfLength)) return null;
        int a = Math.abs(across), tree = beltTreeAlong(along, halfLength);
        if (beltWater(along, across, halfLength, variant)) return WATER;
        if (beltBasin(along, across, halfLength, variant)) return QUARTZ;
        if (!waterSide(across, variant) && a >= 2 && Math.abs(along - tree) <= 2)
            return treeSurface(along - tree, a - 3);
        if (a == 5 && Math.abs(along) < halfLength - 1) return SOIL;
        return a <= 1 || Math.abs(along) >= halfLength - 1 ? STONE : PALE;
    }

    public static String beltFoundation(int along, int y, int across, int halfLength, int variant) {
        if (y != -1 || !beltContains(along, across, halfLength)
                || !beltBasin(along, across, halfLength, variant)) return null;
        return beltWater(along, across, halfLength, variant) && Math.floorMod(along, 8) == 0 ? LIGHT : QUARTZ;
    }

    public static String beltAbove(int along, int y, int across, int halfLength, int variant) {
        if (y <= 0 || y > HEIGHT || !beltContains(along, across, halfLength)) return null;
        int a = Math.abs(across), tree = beltTreeAlong(along, halfLength);
        if (!waterSide(across, variant) && (y > 1 || a >= 2)) {
            String block = treeAbove(along - tree, y, a - 3);
            if (block != null) return block;
        }
        if (y == 1 && beltBasin(along, across, halfLength, variant)
                && !beltWater(along, across, halfLength, variant)) return SLAB;
        int bay = beltBayAlong(along, halfLength), distance = along - bay;
        if (a == 2 && Math.abs(distance) <= 1 && bay <= halfLength - 3 && y == 1)
            return seat(across > 0 ? "south" : "north");
        if (a == 3 && Math.abs(distance) == 2 && bay <= halfLength - 3 && y == 1) return STONE_SLAB;
        if (a == 4 && distance == 0 && bay <= halfLength - 3 && !waterSide(across, variant)) return lamp(y);
        if (y == 1 && a == 5 && Math.abs(along) < halfLength - 1)
            return Math.floorMod(along + halfLength, 4) == 0 ? FLOWER : HEDGE;
        return null;
    }

    public static int beltTop(int along, int across, int halfLength, int variant) {
        if (!beltContains(along, across, halfLength)) return 0;
        for (int y = HEIGHT; y > 0; y--)
            if (beltAbove(along, y, across, halfLength, variant) != null) return y;
        return 0;
    }

    /**
     * Adaptable 3/5/7/9/11-wide road edge. Positive across faces the planted side.
     * halfWidth 1/2 has only low flower hedges, paving, seats and seven-high stepped lamps;
     * halfWidth 3..5 adds one complete tree row with a two-block crown radius.
     * Trees and lights repeat at 16 blocks; reserve [-halfWidth,halfWidth] in full.
     */
    public static String roadsideSurface(int along, int across, int halfLength, int halfWidth, int variant) {
        if (!roadsideContains(along, across, halfLength, halfWidth)) return null;
        int tree = beltTreeAlong(along, halfLength), treeAcross = halfWidth - 2;
        if (halfWidth >= 3 && Math.abs(along - tree) <= 2 && Math.abs(across - treeAcross) <= 2)
            return treeSurface(along - tree, across - treeAcross);
        if (across == halfWidth && Math.abs(along) < halfLength - 1) return SOIL;
        return across <= halfWidth - 3 || Math.abs(along) >= halfLength - 1 ? STONE : PALE;
    }

    /** Roadside strips never excavate; all features stand on the caller's ordinary foundation. */
    public static String roadsideFoundation(int along, int y, int across, int halfLength, int halfWidth, int variant) {
        return null;
    }

    public static String roadsideAbove(int along, int y, int across, int halfLength, int halfWidth, int variant) {
        if (y <= 0 || y > roadsideHeight(halfWidth)
                || !roadsideContains(along, across, halfLength, halfWidth)) return null;
        if (halfWidth >= 3) {
            String tree = treeAbove(along - beltTreeAlong(along, halfLength), y, across - (halfWidth - 2));
            if (tree != null) return tree;
        }
        int bay = beltBayAlong(along, halfLength), distance = along - bay;
        if (bay <= halfLength - 3) {
            if (across == halfWidth && distance == 0) return lamp(y);
            if (halfWidth >= 2 && across == halfWidth - 1 && Math.abs(distance) <= 1 && y == 1)
                return seat("south");
            if (halfWidth >= 2 && across == halfWidth - 1 && Math.abs(distance) == 2 && y == 1)
                return STONE_SLAB;
        }
        if (y == 1 && across == halfWidth && Math.abs(along) < halfLength - 1)
            return Math.floorMod(along + variant * 3, 6) == 0 ? FLOWER : HEDGE;
        return null;
    }

    public static int roadsideTop(int along, int across, int halfLength, int halfWidth, int variant) {
        if (!roadsideContains(along, across, halfLength, halfWidth)) return 0;
        for (int y = roadsideHeight(halfWidth); y > 0; y--)
            if (roadsideAbove(along, y, across, halfLength, halfWidth, variant) != null) return y;
        return 0;
    }

    /**
     * Small public court; local z is the long axis, both half extents >=12.
     * Complete gardens have 40/48 spacing with generous meadow gaps and three-wide paths.
     * Large reservations should use park sampling, which designs one whole park instead of tiles.
     * Remaining edge strips receive complete linear gardens, never partial trees or roofs.
     * Place only on reserved landscape ground, never on player production floors.
     */
    public static String courtyardSurface(int dx, int dz, int halfWidth, int halfDepth, int variant) {
        if (!courtContains(dx, dz, halfWidth, halfDepth)) return null;
        if (courtFringe(dx, dz, halfWidth, halfDepth))
            return courtFringeBlock(dx, 0, dz, halfWidth, halfDepth, variant, 0);
        int x = courtLocal(dx, halfWidth, COURT_X_PITCH), z = courtLocal(dz, halfDepth, COURT_Z_PITCH);
        if (contains(x, z)) return surface(x, z, courtTheme(dx, dz, halfWidth, halfDepth, variant));
        return courtGapPath(x, z) ? STONE : GRASS;
    }

    public static String courtyardFoundation(int dx, int y, int dz, int halfWidth, int halfDepth, int variant) {
        if (!courtContains(dx, dz, halfWidth, halfDepth)) return null;
        if (courtFringe(dx, dz, halfWidth, halfDepth))
            return courtFringeBlock(dx, y, dz, halfWidth, halfDepth, variant, 1);
        int x = courtLocal(dx, halfWidth, COURT_X_PITCH), z = courtLocal(dz, halfDepth, COURT_Z_PITCH);
        return contains(x, z) ? foundation(x, y, z, courtTheme(dx, dz, halfWidth, halfDepth, variant)) : null;
    }

    public static String courtyardAbove(int dx, int y, int dz, int halfWidth, int halfDepth, int variant) {
        if (y <= 0 || y > HEIGHT || !courtContains(dx, dz, halfWidth, halfDepth)) return null;
        if (courtFringe(dx, dz, halfWidth, halfDepth))
            return courtFringeBlock(dx, y, dz, halfWidth, halfDepth, variant, 2);
        int x = courtLocal(dx, halfWidth, COURT_X_PITCH), z = courtLocal(dz, halfDepth, COURT_Z_PITCH);
        if (contains(x, z)) return above(x, y, z, courtTheme(dx, dz, halfWidth, halfDepth, variant));
        return y == 1 && !courtGapPath(x, z) ? meadowFlower(dx, dz, variant) : null;
    }

    public static int courtyardTop(int dx, int dz, int halfWidth, int halfDepth, int variant) {
        if (!courtContains(dx, dz, halfWidth, halfDepth)) return 0;
        for (int y = HEIGHT; y > 0; y--)
            if (courtyardAbove(dx, y, dz, halfWidth, halfDepth, variant) != null) return y;
        return 0;
    }

    /**
     * One coherent park for a complete rectangle, not repeated small garden cells.
     * Local z is the long axis; minimum full dimensions are 65 by 65.
     * Themes: 0 elongated real lake/shore promenade/bridge; 1 woodland and open meadow;
     * 2 leisure garden with one central pergola, long flower borders and peripheral groves.
     * All paths have at least three clear blocks; tree spacing is 10/12, never four.
     */
    public static String parkSurface(int dx, int dz, int halfWidth, int halfDepth, int variant) {
        if (!parkContains(dx, dz, halfWidth, halfDepth)) return null;
        int theme = Math.floorMod(variant, 3), x = Math.abs(dx), z = Math.abs(dz);
        if (theme == 0) {
            int wx = lakeHalfWidth(halfWidth), wz = lakeHalfDepth(halfDepth);
            if (lakeInside(x, z, wx, wz, 0))
                return z == 2 && Math.floorMod(dx, 12) == 0 ? QUARTZ : WATER;
            if (lakeInside(x, z, wx, wz, 1)) return QUARTZ;
        }
        if (theme == 2 && contains(dx, dz)) return surface(dx, dz, 2);
        if (parkFlowerBed(x, z, halfDepth, theme)) return SOIL;
        if (parkPath(dx, dz, halfWidth, halfDepth, theme))
            return parkEdge(x, z, halfWidth, halfDepth) == 5 && Math.floorMod(x + z, 32) == 16 ? LIGHT : STONE;
        return GRASS;
    }

    /** Source water is enclosed by a continuous quartz wall and a solid floor at y=-1. */
    public static String parkFoundation(int dx, int y, int dz, int halfWidth, int halfDepth, int variant) {
        if (y != -1 || !parkContains(dx, dz, halfWidth, halfDepth) || Math.floorMod(variant, 3) != 0) return null;
        int x = Math.abs(dx), z = Math.abs(dz), wx = lakeHalfWidth(halfWidth), wz = lakeHalfDepth(halfDepth);
        if (!lakeInside(x, z, wx, wz, 1)) return null;
        return lakeInside(x, z, wx, wz, 0) && Math.floorMod(dx, 16) == 0 && Math.floorMod(dz, 16) == 0 ? LIGHT : QUARTZ;
    }

    public static String parkAbove(int dx, int y, int dz, int halfWidth, int halfDepth, int variant) {
        if (y <= 0 || y > HEIGHT || !parkContains(dx, dz, halfWidth, halfDepth)) return null;
        int theme = Math.floorMod(variant, 3), x = Math.abs(dx), z = Math.abs(dz);
        if (theme == 0) {
            int wx = lakeHalfWidth(halfWidth), wz = lakeHalfDepth(halfDepth);
            if (x <= wx + 1 && z <= 2) {
                if (y == 1) return z == 2 ? QUARTZ : WOOD_SLAB;
                return y == 2 && z == 2 ? SLAB : null;
            }
            if (x <= wx + 4 && z <= 1 && y == 1) return SLAB;
            String pavilion = parkPavilion(dx - wx - 10, y, dz);
            if (pavilion != null) return pavilion;
            if (lakeInside(x, z, wx, wz, 1)) return null;
            if (y == 1 && x == wx + 5 && z <= wz - 4 && Math.floorMod(z, 32) >= 10 && Math.floorMod(z, 32) <= 14)
                return seat(dx > 0 ? "east" : "west");
        } else if (theme == 1) {
            String pavilion = parkPavilion(dx, y, dz);
            if (pavilion != null) return pavilion;
        } else if (contains(dx, dz)) {
            return above(dx, y, dz, 2);
        }
        if (parkFlowerBed(x, z, halfDepth, theme) && y == 1) {
            if (x == 6 || x == 10 || z == 14 || z == halfDepth - 10) return SLAB;
            return Math.floorMod(dx + dz, 3) == 0 ? PURPLE_FLOWER : WHITE_FLOWER;
        }
        int pitch = theme == 1 ? 10 : 12;
        int tx = parkTreeCentre(dx, halfWidth, pitch), tz = parkTreeCentre(dz, halfDepth, pitch);
        if (parkTreeAllowed(tx, tz, halfWidth, halfDepth, theme, variant)) {
            String tree = treeAbove(dx - tx, y, dz - tz);
            if (tree != null) return tree;
        }
        if (parkPath(dx, dz, halfWidth, halfDepth, theme)) return null;
        if (y == 1 && theme != 0 && x == halfWidth - 7 && Math.floorMod(z, 48) >= 22 && Math.floorMod(z, 48) <= 24)
            return seat(dx > 0 ? "east" : "west");
        if (y == 1 && parkEdge(x, z, halfWidth, halfDepth) == 0 && x > 2 && z > 2
                && Math.floorMod(x + z, 24) < 15) return Math.floorMod(x + z, 4) == 0 ? FLOWER : HEDGE;
        return y == 1 ? meadowFlower(dx, dz, variant) : null;
    }

    public static int parkTop(int dx, int dz, int halfWidth, int halfDepth, int variant) {
        if (!parkContains(dx, dz, halfWidth, halfDepth)) return 0;
        for (int y = HEIGHT; y > 0; y--)
            if (parkAbove(dx, y, dz, halfWidth, halfDepth, variant) != null) return y;
        return 0;
    }

    private static boolean parkContains(int x, int z, int halfWidth, int halfDepth) {
        return halfWidth >= PARK_MIN_HALF_EXTENT && halfDepth >= PARK_MIN_HALF_EXTENT
                && Math.abs(x) <= halfWidth && Math.abs(z) <= halfDepth;
    }
    private static int parkEdge(int x, int z, int halfWidth, int halfDepth) {
        return Math.min(halfWidth - x, halfDepth - z);
    }
    private static int lakeHalfWidth(int halfWidth) { return Math.max(10, Math.min(halfWidth - 14, halfWidth * 2 / 5)); }
    private static int lakeHalfDepth(int halfDepth) { return Math.max(12, Math.min(halfDepth - 14, halfDepth * 2 / 3)); }
    private static boolean lakeInside(int x, int z, int halfWidth, int halfDepth, int expand) {
        return x <= halfWidth + expand && z <= halfDepth + expand
                && x + z <= halfWidth + halfDepth - 4 + 2 * expand;
    }
    private static boolean parkPath(int dx, int dz, int halfWidth, int halfDepth, int theme) {
        int x = Math.abs(dx), z = Math.abs(dz), edge = parkEdge(x, z, halfWidth, halfDepth);
        if (edge >= 4 && edge <= 6 || z <= 1) return true;
        if (theme != 0) return x <= 1;
        int wx = lakeHalfWidth(halfWidth), wz = lakeHalfDepth(halfDepth);
        return x <= 1 && z >= wz + 2 || lakeInside(x, z, wx, wz, 4) && !lakeInside(x, z, wx, wz, 1);
    }
    private static boolean parkFlowerBed(int x, int z, int halfDepth, int theme) {
        return theme == 2 && x >= 6 && x <= 10 && z >= 14 && z <= halfDepth - 10;
    }
    /** Symmetric paired rows, with nine-block boundary clearance and complete two-radius crowns. */
    private static int parkTreeCentre(int coordinate, int halfExtent, int pitch) {
        int index = Math.max(0, Math.min((halfExtent - 19) / pitch,
                Math.floorDiv(Math.abs(coordinate) - 10 + pitch / 2, pitch)));
        int centre = 10 + index * pitch;
        return coordinate < 0 ? -centre : centre;
    }
    private static boolean parkTreeAllowed(int tx, int tz, int halfWidth, int halfDepth, int theme, int variant) {
        int x = Math.abs(tx), z = Math.abs(tz);
        if (x <= 3 || z <= 3 || parkEdge(x, z, halfWidth, halfDepth) < 9) return false;
        if (theme == 0 && lakeInside(x, z, lakeHalfWidth(halfWidth), lakeHalfDepth(halfDepth), 7)) return false;
        if (theme == 1 && x <= Math.max(8, halfWidth / 4) && z <= Math.max(10, halfDepth / 5)) return false;
        if (theme == 2 && (x <= 14 && z <= 14 || x >= 4 && x <= 12 && z >= 12 && z <= halfDepth - 8)) return false;
        if (theme != 0 && x >= halfWidth - 9 && Math.floorMod(z, 48) >= 20 && Math.floorMod(z, 48) <= 26) return false;
        int group = Math.floorMod(Math.floorDiv(tx, 30) * 3 + Math.floorDiv(tz, 40) * 5 + variant, 7);
        return theme == 1 ? group != 0 && group != 3 : group != 0;
    }
    private static String meadowFlower(int x, int z, int variant) {
        int hash = x * 73428767 ^ z * 912931 ^ variant * 1237;
        return (hash & 127) == 0 ? (hash & 128) == 0 ? WHITE_FLOWER : PURPLE_FLOWER : null;
    }
    /** A single airy 13-by-9 pavilion; cardinal paths remain three blocks clear below the roof. */
    private static String parkPavilion(int dx, int y, int dz) {
        int x = Math.abs(dx), z = Math.abs(dz);
        if (x > 6 || z > 4) return null;
        if (x == 5 && z == 3 && y <= 6) return y == 1 ? QUARTZ : PILLAR;
        if (y == 7 && (x == 5 && z <= 3 || z == 3 && x <= 5)) return QUARTZ;
        if (y == 8 && (x == 6 || z == 4)) return SLAB;
        if (y == 8 && x <= 5 && z <= 3 && dx % 2 == 0) return WOOD_TOP;
        if (x == 0 && z == 0) return y == 7 ? CHAIN : y == 6 ? LANTERN : null;
        if (y == 1 && z == 3 && x >= 2 && x <= 3) return seat(dz > 0 ? "south" : "north");
        return null;
    }

    private static String poolAbove(int dx, int y, int dz) {
        int x = Math.abs(dx), z = Math.abs(dz);
        String tree = treeAbove(x - 9, y, z - 9);
        if (tree != null) return tree;
        if (y != 1) return null;
        if (poolEnvelope(x, z) && !poolWater(x, z)) return SLAB;
        if (z == 7 && x <= 5) return seat(dz > 0 ? "south" : "north");
        if (z == 7 && x == 6) return STONE_SLAB;
        if (z == 11 && x <= 5) return x % 4 == 0 ? FLOWER : HEDGE;
        return null;
    }

    private static String groveAbove(int dx, int y, int dz) {
        int x = Math.abs(dx), z = Math.abs(dz);
        String tree = treeAbove(x - 7, y, dz - groveTreeZ(dz));
        if (tree != null) return tree;
        if (x == 4 && z >= 3 && z <= 5 && y == 1) return seat(dx > 0 ? "east" : "west");
        if (x == 4 && (z == 2 || z == 6) && y == 1) return STONE_SLAB;
        if (x == 11 && z >= 3 && z <= 9 && y == 1) return z % 3 == 0 ? FLOWER : HEDGE;
        if (x == 3 && z == 10) return lamp(y);
        return null;
    }

    private static String pergolaAbove(int dx, int y, int dz) {
        int x = Math.abs(dx), z = Math.abs(dz);
        String tree = treeAbove(x - 10, y, z - 9);
        if (tree != null) return tree;
        if (x == 6 && z == 7 && y <= 6) return y == 1 ? QUARTZ : PILLAR;
        if (y == 7 && (x == 6 && z <= 8 || z == 7 && x <= 6)) return QUARTZ;
        if (y == 8 && x <= 7 && z <= 8 && (x == 7 || z == 8)) return SLAB;
        if (y == 8 && x <= 6 && z <= 7 && dx % 2 == 0) return WOOD_TOP;
        if (x == 0 && z == 4) return y == 7 ? CHAIN : y == 6 ? LANTERN : null;
        if (y != 1) return null;
        if (x == 5 && z >= 2 && z <= 4) return seat(dx > 0 ? "east" : "west");
        if (x == 5 && z == 5) return STONE_SLAB;
        if (z == 10 && x >= 2 && x <= 5) return x % 3 == 0 ? FLOWER : HEDGE;
        return null;
    }

    private static String parterreAbove(int dx, int y, int dz) {
        int x = Math.abs(dx), z = Math.abs(dz);
        String tree = treeAbove(dx - parterreTreeX(dx, dz), y, dz - parterreTreeZ(dx, dz));
        if (tree != null) return tree;
        if (y == 1 && flowerBed(x, z)) {
            if (x == 3 || x == 7 || z == 3 || z == 7) return SLAB;
            return (x + z) % 2 == 0 ? WHITE_FLOWER : PURPLE_FLOWER;
        }
        if (y == 1 && z == 10 && x >= 2 && x <= 5) return seat(dz > 0 ? "south" : "north");
        if (x == 10 && z == 3) return lamp(y);
        return null;
    }

    /** Pruned 13-block birch: clear trunk, compact high crown, low planted stone surround. */
    private static String treeAbove(int dx, int y, int dz) {
        int x = Math.abs(dx), z = Math.abs(dz), edge = Math.max(x, z), distance = x + z;
        if (edge > 2) return null;
        if (x == 0 && z == 0 && y <= 9) return LOG;
        if (y == 1) return edge == 2 ? SLAB : HEDGE;
        if (y == 7 && distance <= 1 || y >= 8 && y <= 10 && distance <= 3
                || y == 11 && distance <= 2 || y == 12 && edge <= 1 || y == 13 && distance == 0)
            return LEAVES;
        return null;
    }

    private static String lamp(int y) {
        if (y == 1) return QUARTZ;
        if (y >= 2 && y <= 4) return WALL;
        if (y == 5) return lampShoulder("north");
        if (y == 6) return LIGHT;
        return y == 7 ? STONE_SLAB : null;
    }
    private static String treeSurface(int dx, int dz) {
        return Math.max(Math.abs(dx), Math.abs(dz)) == 2 ? QUARTZ : SOIL;
    }
    private static boolean contains(int x, int z) { return Math.abs(x) <= RADIUS && Math.abs(z) <= RADIUS; }
    private static boolean poolWater(int x, int z) { return x <= 8 && z <= 4; }
    private static boolean poolEnvelope(int x, int z) { return x <= 9 && z <= 5; }
    private static boolean cornerTree(int x, int z) { return Math.abs(Math.abs(x) - 9) <= 2 && Math.abs(Math.abs(z) - 9) <= 2; }
    private static boolean pergolaTree(int x, int z) { return Math.abs(Math.abs(x) - 10) <= 2 && Math.abs(Math.abs(z) - 9) <= 2; }
    private static int groveTreeZ(int z) { return z < -3 ? -7 : z > 3 ? 7 : 0; }
    private static int parterreTreeX(int x, int z) { return Math.abs(x) <= 2 && Math.abs(z) <= 2 ? 0 : x < 0 ? -9 : 9; }
    private static int parterreTreeZ(int x, int z) { return Math.abs(x) <= 2 && Math.abs(z) <= 2 ? 0 : z < 0 ? -9 : 9; }
    private static boolean flowerBed(int x, int z) { return x >= 3 && x <= 7 && z >= 3 && z <= 7; }
    private static boolean beltContains(int along, int across, int halfLength) {
        return halfLength >= 6 && Math.abs(along) <= halfLength && Math.abs(across) <= BELT_HALF_WIDTH;
    }
    private static boolean waterSide(int across, int variant) {
        return Math.floorMod(variant, 4) == 1 && across > 0 || Math.floorMod(variant, 4) == 3 && across < 0;
    }
    private static boolean beltBasin(int along, int across, int halfLength, int variant) {
        return waterSide(across, variant) && Math.abs(across) >= 2 && Math.abs(along) <= halfLength - 2;
    }
    private static boolean beltWater(int along, int across, int halfLength, int variant) {
        return waterSide(across, variant) && Math.abs(across) >= 3 && Math.abs(across) <= 4
                && Math.abs(along) <= halfLength - 3;
    }
    private static int beltTreeAlong(int along, int halfLength) {
        int first = -halfLength + 4, last = halfLength - 4;
        int index = Math.floorDiv(along - first + TREE_PITCH / 2, TREE_PITCH);
        return first + Math.max(0, Math.min(index, Math.floorDiv(last - first, TREE_PITCH))) * TREE_PITCH;
    }
    private static int beltBayAlong(int along, int halfLength) {
        int first = -halfLength + 4 + TREE_PITCH / 2;
        return first + Math.max(0, Math.floorDiv(along - first + TREE_PITCH / 2, TREE_PITCH)) * TREE_PITCH;
    }
    private static boolean roadsideContains(int along, int across, int halfLength, int halfWidth) {
        return halfWidth >= 1 && halfWidth <= 5 && halfLength >= 6
                && Math.abs(along) <= halfLength && Math.abs(across) <= halfWidth;
    }
    private static boolean courtContains(int x, int z, int halfWidth, int halfDepth) {
        return halfWidth >= 12 && halfDepth >= 12 && Math.abs(x) <= halfWidth && Math.abs(z) <= halfDepth;
    }
    private static int courtCount(int halfExtent, int pitch) { return (2 * halfExtent + pitch - 24) / pitch; }
    private static int courtGridHalf(int halfExtent, int pitch) {
        return pitch * (courtCount(halfExtent, pitch) - 1) / 2 + RADIUS;
    }
    private static int courtIndex(int coordinate, int halfExtent, int pitch) {
        int count = courtCount(halfExtent, pitch), first = -pitch * (count - 1) / 2;
        return Math.max(0, Math.min(count - 1, Math.floorDiv(coordinate - first + pitch / 2, pitch)));
    }
    private static int courtLocal(int coordinate, int halfExtent, int pitch) {
        int first = -pitch * (courtCount(halfExtent, pitch) - 1) / 2;
        return coordinate - first - courtIndex(coordinate, halfExtent, pitch) * pitch;
    }
    private static int courtTheme(int x, int z, int halfWidth, int halfDepth, int variant) {
        return Math.floorMod(variant + courtIndex(x, halfWidth, COURT_X_PITCH) / 2
                + courtIndex(z, halfDepth, COURT_Z_PITCH) / 3, 4);
    }
    private static boolean courtGapPath(int x, int z) {
        return Math.abs(x) >= COURT_X_PITCH / 2 - 1 || Math.abs(z) >= COURT_Z_PITCH / 2 - 1;
    }
    private static boolean courtFringe(int x, int z, int halfWidth, int halfDepth) {
        return Math.abs(x) > courtGridHalf(halfWidth, COURT_X_PITCH) || Math.abs(z) > courtGridHalf(halfDepth, COURT_Z_PITCH);
    }
    /** Layer 0/1/2 is surface/foundation/above. Each edge is its own complete reservation. */
    private static String courtFringeBlock(int x, int y, int z, int halfWidth, int halfDepth, int variant, int layer) {
        int gridDepth = courtGridHalf(halfDepth, COURT_Z_PITCH);
        boolean end = Math.abs(z) > gridDepth;
        int edge = end ? halfDepth : halfWidth;
        int inner = end ? gridDepth : courtGridHalf(halfWidth, COURT_X_PITCH);
        int stripHalfWidth = Math.min(5, (edge - inner - 1) / 2);
        if (stripHalfWidth < 1) return layer == 0 ? GRASS : null;
        int along = end ? x : z, across = Math.abs(end ? z : x) - (edge - stripHalfWidth);
        int length = end ? halfWidth : gridDepth;
        String block;
        if (!end && stripHalfWidth == 5) {
            block = switch (layer) {
                case 0 -> beltSurface(along, across, length, variant);
                case 1 -> beltFoundation(along, y, across, length, variant);
                default -> beltAbove(along, y, across, length, variant);
            };
        } else {
            block = switch (layer) {
                case 0 -> roadsideSurface(along, across, length, stripHalfWidth, variant);
                case 1 -> roadsideFoundation(along, y, across, length, stripHalfWidth, variant);
                default -> roadsideAbove(along, y, across, length, stripHalfWidth, variant);
            };
        }
        return block == null && layer == 0 ? GRASS : courtFringeFacing(block, end, (end ? z : x) > 0);
    }
    /** Strip states use along=+X/across=+Z; the court also swaps axes and mirrors negative edges. */
    private static String courtFringeFacing(String block, boolean end, boolean positive) {
        if (block == null) return null;
        int start = block.indexOf("facing=");
        if (start < 0) return block;
        start += 7;
        int stop = block.indexOf(',', start);
        if (stop < 0) stop = block.indexOf(']', start);
        String facing = block.substring(start, stop);
        String transformed = switch (facing) {
            case "north" -> end ? positive ? "north" : "south" : positive ? "west" : "east";
            case "south" -> end ? positive ? "south" : "north" : positive ? "east" : "west";
            case "east" -> end ? "east" : "south";
            case "west" -> end ? "west" : "north";
            default -> facing;
        };
        return block.substring(0, start) + transformed + block.substring(stop);
    }
    private static String seat(String facing) {
        return "minecraft:dark_oak_stairs[facing=" + facing + ",half=bottom,shape=straight]";
    }
    private static String lampShoulder(String facing) {
        return "minecraft:smooth_quartz_stairs[facing=" + facing + ",half=top,shape=straight]";
    }
}
