package com.gtl.enhancedcore.common.world.plot;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Pure vanilla garden sampling; callers reserve the whole footprint, including crowns and lamps. */
public final class CampusLandscape {
    private static final int RADIUS = 12, HEIGHT = 18, BELT_HALF_WIDTH = 5, TREE_PITCH = 16;
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
    private static final String HEDGE = "minecraft:azalea_leaves[persistent=true]";
    private static final String FLOWER = "minecraft:flowering_azalea_leaves[persistent=true]";
    private static final String WHITE_FLOWER = "minecraft:oxeye_daisy";
    private static final String PURPLE_FLOWER = "minecraft:allium";
    private static final String WATER = "minecraft:water";
    private static final String SAND = "minecraft:sand", GRAVEL = "minecraft:gravel", CLAY = "minecraft:clay";
    private static final String LILY_PAD = "minecraft:lily_pad", SEAGRASS = "minecraft:seagrass";
    private static final String TALL_SEAGRASS_LOWER = "minecraft:tall_seagrass[half=lower]";
    private static final String TALL_SEAGRASS_UPPER = "minecraft:tall_seagrass[half=upper]";
    private static final String LIGHT = "minecraft:sea_lantern";
    private static final String WALL = "minecraft:polished_blackstone_brick_wall";
    private static final String CHAIN = "minecraft:chain[axis=y]";
    private static final String LANTERN = "minecraft:lantern[hanging=true]";
    private static final Set<String> MATERIALS;

    static {
        Set<String> palette = new LinkedHashSet<>(List.of(PALE, GRASS, SOIL, QUARTZ, PILLAR, SLAB,
                STONE, STONE_SLAB, WOOD_TOP, WOOD_SLAB, HEDGE, FLOWER, WHITE_FLOWER,
                PURPLE_FLOWER, WATER, SAND, GRAVEL, CLAY, LILY_PAD, SEAGRASS,
                TALL_SEAGRASS_LOWER, TALL_SEAGRASS_UPPER, LIGHT, WALL, CHAIN, LANTERN));
        palette.addAll(CampusTreeShapes.materials());
        palette.addAll(CampusGroundCover.materials());
        for (String facing : List.of("north", "south", "east", "west")) {
            palette.add(seat(facing));
            palette.add(lampShoulder(facing));
        }
        MATERIALS = Set.copyOf(palette);
    }

    private CampusLandscape() {}

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

    /** Small ornamental pools have three water layers above a sealed y=-3 floor. */
    public static String foundation(int dx, int y, int dz, int variant) {
        if (y >= 0 || y < -3 || !contains(dx, dz) || Math.floorMod(variant, 4) != 0) return null;
        int x = Math.abs(dx), z = Math.abs(dz);
        if (!poolEnvelope(x, z)) return null;
        if (!poolWater(x, z)) return QUARTZ;
        if (y > -3) return WATER;
        return z == 0 && x % 4 == 0 ? LIGHT : QUARTZ;
    }

    /** Empty cells return null. No sampler touches a world or places functional equipment. */
    public static String above(int dx, int y, int dz, int variant) {
        if (y <= 0 || y > HEIGHT || !contains(dx, dz)) return null;
        return switch (Math.floorMod(variant, 4)) {
            case 0 -> poolAbove(dx, y, dz, variant);
            case 1 -> groveAbove(dx, y, dz, variant);
            case 2 -> pergolaAbove(dx, y, dz, variant);
            default -> parterreAbove(dx, y, dz, variant);
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
        if (y >= 0 || y < -2 || !beltContains(along, across, halfLength)
                || !beltBasin(along, across, halfLength, variant)) return null;
        if (y == -1 && beltWater(along, across, halfLength, variant)) return WATER;
        return beltWater(along, across, halfLength, variant) && Math.floorMod(along, 8) == 0 ? LIGHT : QUARTZ;
    }

    public static String beltAbove(int along, int y, int across, int halfLength, int variant) {
        if (y <= 0 || y > HEIGHT || !beltContains(along, across, halfLength)) return null;
        int a = Math.abs(across), tree = beltTreeAlong(along, halfLength);
        if (!waterSide(across, variant) && (y > CampusGroundCover.maxHeight() || a >= 2)) {
            String block = treeAbove(along - tree, y, a - 3, treeSeed(tree, across > 0 ? 3 : -3, variant));
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
            int centre = beltTreeAlong(along, halfLength);
            String tree = treeAbove(along - centre, y, across - (halfWidth - 2), treeSeed(centre, halfWidth, variant));
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
        return !courtGapPath(x, z) ? CampusGroundCover.above(dx, y, dz, variant, 0) : null;
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
     * Themes: 0 terraced lake with planted shore, decks and bridge; 1 mixed woodland/meadow;
     * 2 flower garden with a central pergola. Woodland is staggered, jittered and clustered;
     * formal paths stay at least three blocks clear and every complete crown stays in its park.
     */
    public static String parkSurface(int dx, int dz, int halfWidth, int halfDepth, int variant) {
        if (!parkContains(dx, dz, halfWidth, halfDepth)) return null;
        int theme = Math.floorMod(variant, 3), x = Math.abs(dx), z = Math.abs(dz);
        if (theme == 0) {
            int wx = lakeHalfWidth(halfWidth), wz = lakeHalfDepth(halfDepth);
            if (lakeInside(x, z, wx, wz, 0))
                return z == 2 && Math.floorMod(dx, 12) == 0 ? QUARTZ : WATER;
            if (lakeInside(x, z, wx, wz, 1)) return QUARTZ;
            if (lakeDeck(x, z, wz)) return WOOD_TOP;
            if (shoreFlowerBed(x, z, wx, wz)) return SOIL;
            if (Math.abs(x - (wx + 8)) <= 6 && z <= 4) return STONE;
        }
        if (theme == 1 && x <= 6 && z <= 4) return STONE;
        if (theme == 2 && contains(dx, dz)) return surface(dx, dz, 2);
        if (parkFlowerBed(x, z, halfDepth, theme)) return SOIL;
        if (parkLampSite(x, z, halfWidth, halfDepth, theme)) return STONE;
        if (parkPath(dx, dz, halfWidth, halfDepth, theme))
            return parkEdge(x, z, halfWidth, halfDepth) == 5 && Math.floorMod(x + z, 32) == 16 ? LIGHT : STONE;
        return GRASS;
    }

    /** Three-to-six source-water layers, sealed side walls, and a solid terraced mineral bed. */
    public static String parkFoundation(int dx, int y, int dz, int halfWidth, int halfDepth, int variant) {
        if (y >= 0 || y < -6 || !parkContains(dx, dz, halfWidth, halfDepth)
                || Math.floorMod(variant, 3) != 0) return null;
        int x = Math.abs(dx), z = Math.abs(dz), wx = lakeHalfWidth(halfWidth), wz = lakeHalfDepth(halfDepth);
        if (!lakeInside(x, z, wx, wz, 1)) return null;
        if (!lakeInside(x, z, wx, wz, 0)) return QUARTZ;
        int depth = lakeDepth(x, z, wx, wz);
        if (y < -depth) return null;
        if (z == 2 && Math.floorMod(dx, 12) == 0) return QUARTZ;
        if (y == -depth) return lakeBed(dx, dz, variant);
        int plant = Math.floorMod(plantHash(dx, dz, variant), 19);
        if (plant == 0 && z > 5) {
            if (y == 1 - depth) return TALL_SEAGRASS_LOWER;
            if (y == 2 - depth) return TALL_SEAGRASS_UPPER;
        }
        if (plant <= 4 && z > 5 && y == 1 - depth) return SEAGRASS;
        return WATER;
    }

    public static String parkAbove(int dx, int y, int dz, int halfWidth, int halfDepth, int variant) {
        if (y <= 0 || y > HEIGHT || !parkContains(dx, dz, halfWidth, halfDepth)) return null;
        String fixture = parkFixture(dx, y, dz, halfWidth, halfDepth, variant);
        if (fixture != null) return fixture;
        String tree = parkWoodland(dx, y, dz, halfWidth, halfDepth, variant);
        if (tree != null) return tree;
        return parkGroundCover(dx, y, dz, halfWidth, halfDepth, variant);
    }

    public static int parkTop(int dx, int dz, int halfWidth, int halfDepth, int variant) {
        if (!parkContains(dx, dz, halfWidth, halfDepth)) return 0;
        int top = parkWoodlandTop(dx, dz, halfWidth, halfDepth, variant);
        if (Math.floorMod(variant, 3) == 2 && contains(dx, dz)) top = Math.max(top, top(dx, dz, 2));
        for (int y = 8; y > top; y--)
            if (parkFixture(dx, y, dz, halfWidth, halfDepth, variant) != null) return y;
        for (int y = 2; y > top; y--)
            if (parkGroundCover(dx, y, dz, halfWidth, halfDepth, variant) != null) return y;
        return top;
    }

    private static String parkFixture(int dx, int y, int dz, int halfWidth, int halfDepth, int variant) {
        int theme = Math.floorMod(variant, 3), x = Math.abs(dx), z = Math.abs(dz);
        if (theme == 0) {
            int wx = lakeHalfWidth(halfWidth), wz = lakeHalfDepth(halfDepth);
            if (x <= wx + 1 && z <= 2) {
                if (y == 1) return z == 2 ? QUARTZ : WOOD_SLAB;
                return y == 2 && z == 2 ? SLAB : null;
            }
            if (x <= wx + 4 && z <= 1 && y == 1) return SLAB;
            if (lakeInside(x, z, wx, wz, 0)) {
                return y == 1 && z > 7 && !lakeInside(x, z, wx, wz, -3)
                        && Math.floorMod(plantHash(dx, dz, variant), 23) == 0 ? LILY_PAD : null;
            }
            if (lakeInside(x, z, wx, wz, 1)) return null;
            String pavilion = parkPavilion(x - wx - 8, y, dz);
            if (pavilion != null) return pavilion;
            if (y == 1 && x == 4 && z == wz + 7) return seat(dz > 0 ? "south" : "north");
            if (y == 1 && x == wx + 5 && z > 7 && z <= wz - 4
                    && Math.floorMod(z, 32) >= 10 && Math.floorMod(z, 32) <= 14)
                return seat(dx > 0 ? "east" : "west");
            if (shoreFlowerBed(x, z, wx, wz) && y == 1) {
                int patch = Math.floorMod(plantHash(Math.floorDiv(dx, 4), Math.floorDiv(dz, 5), variant), 5);
                return patch == 0 ? FLOWER : patch == 1 ? HEDGE : patch == 2 ? PURPLE_FLOWER : WHITE_FLOWER;
            }
        } else if (theme == 1) {
            String pavilion = parkPavilion(dx, y, dz);
            if (pavilion != null) return pavilion;
        } else if (contains(dx, dz)) {
            return above(dx, y, dz, 2);
        }
        if (parkFlowerBed(x, z, halfDepth, theme) && y == 1) {
            if (x == 6 || x == 10 || z == 14 || z == halfDepth - 10) return SLAB;
            return Math.floorMod(plantHash(dx / 3, dz / 4, variant), 4) == 0 ? FLOWER
                    : Math.floorMod(dx + dz, 3) == 0 ? PURPLE_FLOWER : WHITE_FLOWER;
        }
        if (parkLampSite(x, z, halfWidth, halfDepth, theme)) return lamp(y);
        if (y == 1 && theme != 0 && x == halfWidth - 7 && z > 5
                && Math.floorMod(z, 48) >= 22 && Math.floorMod(z, 48) <= 24)
            return seat(dx > 0 ? "east" : "west");
        if (y == 1 && parkEdge(x, z, halfWidth, halfDepth) == 0 && x > 2 && z > 2
                && Math.floorMod(x + z, 24) < 15) return Math.floorMod(x + z, 4) == 0 ? FLOWER : HEDGE;
        return null;
    }

    /** No ground cover can straddle paving, a stem, a seat, or the upper half of another fixture. */
    private static String parkGroundCover(int dx, int y, int dz, int halfWidth, int halfDepth, int variant) {
        if (y < 1 || y > 2 || !GRASS.equals(parkSurface(dx, dz, halfWidth, halfDepth, variant))) return null;
        if (parkFixture(dx, 1, dz, halfWidth, halfDepth, variant) != null
                || parkFixture(dx, 2, dz, halfWidth, halfDepth, variant) != null
                || parkWoodland(dx, 1, dz, halfWidth, halfDepth, variant) != null
                || parkWoodland(dx, 2, dz, halfWidth, halfDepth, variant) != null) return null;
        int theme = Math.floorMod(variant, 3), habitat = 1;
        int x = Math.abs(dx), z = Math.abs(dz);
        if (theme == 0 && lakeInside(x, z, lakeHalfWidth(halfWidth), lakeHalfDepth(halfDepth), 14)) habitat = 2;
        else if (theme == 1 && x <= Math.max(10, halfWidth / 3) && z <= Math.max(12, halfDepth / 4)) habitat = 0;
        else if (theme == 2 && x <= 18) habitat = 0;
        return CampusGroundCover.above(dx, y, dz, variant, habitat);
    }

    /** A bounded 3x3 neighbourhood finds complete jittered crowns without scanning a park. */
    private static String parkWoodland(int dx, int y, int dz, int halfWidth, int halfDepth, int variant) {
        if (y > CampusTreeShapes.maxHeight()) return null;
        int cellX = Math.floorDiv(dx, 10), cellZ = Math.floorDiv(dz, 11);
        int theme = Math.floorMod(variant, 3);
        for (int iz = cellZ - 1; iz <= cellZ + 1; iz++) for (int ix = cellX - 1; ix <= cellX + 1; ix++) {
            int tx = woodlandX(ix, iz, variant), tz = woodlandZ(ix, iz, variant);
            if (Math.abs(dx - tx) > 4 || Math.abs(dz - tz) > 4
                    || !parkTreeAllowed(tx, tz, halfWidth, halfDepth, theme, variant)) continue;
            String block = CampusTreeShapes.above(dx - tx, y, dz - tz, treeSeed(tx, tz, variant), true);
            if (block != null) return block;
        }
        return null;
    }

    private static int parkWoodlandTop(int dx, int dz, int halfWidth, int halfDepth, int variant) {
        int cellX = Math.floorDiv(dx, 10), cellZ = Math.floorDiv(dz, 11), top = 0;
        int theme = Math.floorMod(variant, 3);
        for (int iz = cellZ - 1; iz <= cellZ + 1; iz++) for (int ix = cellX - 1; ix <= cellX + 1; ix++) {
            int tx = woodlandX(ix, iz, variant), tz = woodlandZ(ix, iz, variant);
            if (Math.abs(dx - tx) <= 4 && Math.abs(dz - tz) <= 4
                    && parkTreeAllowed(tx, tz, halfWidth, halfDepth, theme, variant))
                top = Math.max(top, CampusTreeShapes.top(dx - tx, dz - tz, treeSeed(tx, tz, variant), true));
        }
        return top;
    }
    private static int woodlandX(int x, int z, int variant) {
        return x * 10 + (z & 1) * 5 + Math.floorMod(plantHash(x, z, variant + 19), 5) - 2;
    }
    private static int woodlandZ(int x, int z, int variant) {
        return z * 11 + Math.floorMod(plantHash(x, z, variant + 41), 5) - 2;
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
        int radius = Math.max(4, Math.min(halfWidth, halfDepth) / 2);
        int cornerX = Math.max(0, x - (halfWidth - radius));
        int cornerZ = Math.max(0, z - (halfDepth - radius));
        int expandedRadius = radius + expand;
        return expandedRadius > 0 && x <= halfWidth + expand && z <= halfDepth + expand
                && cornerX * cornerX + cornerZ * cornerZ <= expandedRadius * expandedRadius;
    }
    private static int lakeDepth(int x, int z, int halfWidth, int halfDepth) {
        if (!lakeInside(x, z, halfWidth, halfDepth, -2)) return 3;
        if (!lakeInside(x, z, halfWidth, halfDepth, -5)) return 4;
        return !lakeInside(x, z, halfWidth, halfDepth, -9) ? 5 : 6;
    }
    private static String lakeBed(int x, int z, int variant) {
        int patch = Math.floorMod(plantHash(Math.floorDiv(x, 6), Math.floorDiv(z, 7), variant), 10);
        return patch < 5 ? GRAVEL : patch < 8 ? CLAY : SAND;
    }
    private static boolean lakeDeck(int x, int z, int halfDepth) {
        return x <= 5 && z >= halfDepth + 3 && z <= halfDepth + 7;
    }
    private static boolean shoreFlowerBed(int x, int z, int halfWidth, int halfDepth) {
        int phase = Math.floorMod(z, 32);
        return x >= halfWidth + 7 && x <= halfWidth + 9 && z >= 9 && z <= halfDepth - 5
                && phase >= 9 && phase <= 19 && Math.abs(phase - 14) + Math.abs(x - halfWidth - 8) <= 5;
    }
    private static boolean parkLampSite(int x, int z, int halfWidth, int halfDepth, int theme) {
        return theme == 0 ? x == lakeHalfWidth(halfWidth) + 6 && z > 7
                && z < lakeHalfDepth(halfDepth) - 4 && Math.floorMod(z, 32) == 8
                : x == halfWidth - 8 && z > 6 && z < halfDepth - 8 && Math.floorMod(z, 40) == 15;
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
    /** Denser organic groves surround a clear meadow; full crowns keep one block off every path. */
    private static boolean parkTreeAllowed(int tx, int tz, int halfWidth, int halfDepth, int theme, int variant) {
        int x = Math.abs(tx), z = Math.abs(tz);
        if (x <= 6 || z <= 6 || parkEdge(x, z, halfWidth, halfDepth) < 11) return false;
        if (theme == 0) {
            int wx = lakeHalfWidth(halfWidth);
            if (lakeInside(x, z, wx, lakeHalfDepth(halfDepth), 10)
                    || Math.abs(x - wx - 8) <= 11 && z <= 9) return false;
        }
        if (theme == 1 && x <= Math.max(10, halfWidth / 3) + 3 && z <= Math.max(12, halfDepth / 4) + 3) return false;
        if (theme == 2 && (x <= 17 && z <= 17 || x >= 2 && x <= 15 && z >= 9 && z <= halfDepth - 5)) return false;
        if (theme != 0 && x >= halfWidth - 13) return false;
        int opening = Math.floorMod(plantHash(Math.floorDiv(tx, 34), Math.floorDiv(tz, 39), variant), 9);
        int density = opening == 0 ? 28 : theme == 1 ? 92 : 78;
        return Math.floorMod(plantHash(tx, tz, variant + 91), 100) < density;
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

    private static String poolAbove(int dx, int y, int dz, int variant) {
        int x = Math.abs(dx), z = Math.abs(dz);
        String tree = treeAbove(x - 9, y, z - 9, treeSeed(dx < 0 ? -9 : 9, dz < 0 ? -9 : 9, variant));
        if (tree != null) return tree;
        if (y != 1) return null;
        if (poolEnvelope(x, z) && !poolWater(x, z)) return SLAB;
        if (z == 7 && x <= 5) return seat(dz > 0 ? "south" : "north");
        if (z == 7 && x == 6) return STONE_SLAB;
        if (z == 11 && x <= 5) return x % 4 == 0 ? FLOWER : HEDGE;
        return null;
    }

    private static String groveAbove(int dx, int y, int dz, int variant) {
        int x = Math.abs(dx), z = Math.abs(dz);
        String tree = treeAbove(x - 7, y, dz - groveTreeZ(dz), treeSeed(dx < 0 ? -7 : 7, groveTreeZ(dz), variant));
        if (tree != null) return tree;
        if (x == 4 && z >= 3 && z <= 5 && y == 1) return seat(dx > 0 ? "east" : "west");
        if (x == 4 && (z == 2 || z == 6) && y == 1) return STONE_SLAB;
        if (x == 11 && z >= 3 && z <= 9 && y == 1) return z % 3 == 0 ? FLOWER : HEDGE;
        if (x == 3 && z == 10) return lamp(y);
        return null;
    }

    private static String pergolaAbove(int dx, int y, int dz, int variant) {
        int x = Math.abs(dx), z = Math.abs(dz);
        String tree = treeAbove(x - 10, y, z - 9, treeSeed(dx < 0 ? -10 : 10, dz < 0 ? -9 : 9, variant));
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

    private static String parterreAbove(int dx, int y, int dz, int variant) {
        int x = Math.abs(dx), z = Math.abs(dz);
        int tx = parterreTreeX(dx, dz), tz = parterreTreeZ(dx, dz);
        String tree = treeAbove(dx - tx, y, dz - tz, treeSeed(tx, tz, variant));
        if (tree != null) return tree;
        if (y == 1 && flowerBed(x, z)) {
            if (x == 3 || x == 7 || z == 3 || z == 7) return SLAB;
            return (x + z) % 2 == 0 ? WHITE_FLOWER : PURPLE_FLOWER;
        }
        if (y == 1 && z == 10 && x >= 2 && x <= 5) return seat(dz > 0 ? "south" : "north");
        if (x == 10 && z == 3) return lamp(y);
        return null;
    }

    /** Street trees keep their complete 5x5 reservation; planting grows directly from grass. */
    private static String treeAbove(int dx, int y, int dz, int seed) {
        String tree = CampusTreeShapes.above(dx, y, dz, seed, false);
        if (tree != null) return tree;
        if (Math.abs(dx) > 2 || Math.abs(dz) > 2
                || CampusTreeShapes.above(dx, 1, dz, seed, false) != null
                || CampusTreeShapes.above(dx, 2, dz, seed, false) != null) return null;
        return CampusGroundCover.above(dx, y, dz, seed, 1);
    }
    /** Adjacent trees vary in height; nearby planted groups retain a coherent dominant species. */
    static int treeSeed(int x, int z, int variant) {
        int group = Math.floorMod(plantHash(Math.floorDiv(x, 48), Math.floorDiv(z, 56), variant), 6);
        int detail = plantHash(x, z, variant + 73) & 0x3fffffff;
        int species = Math.floorMod(detail, 5) == 0 ? Math.floorMod(detail / 5, 6) : group;
        return detail / 6 * 6 + species;
    }
    private static int plantHash(int x, int z, int seed) {
        int value = x * 0x1f123bb5 ^ z * 0x5f356495 ^ seed * 0x68bc21eb;
        value = (value ^ value >>> 16) * 0x7feb352d;
        value = (value ^ value >>> 15) * 0x846ca68b;
        return value ^ value >>> 16;
    }

    private static String lamp(int y) {
        if (y == 1) return QUARTZ;
        if (y >= 2 && y <= 4) return WALL;
        if (y == 5) return lampShoulder("north");
        if (y == 6) return LIGHT;
        return y == 7 ? STONE_SLAB : null;
    }
    private static String treeSurface(int dx, int dz) {
        return GRASS;
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
        if (!end) block = swapHorizontalAxis(block);
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
    static String swapHorizontalAxis(String block) {
        int axis = block.indexOf("axis=");
        if (axis < 0) return block;
        int index = axis + 5;
        char value = block.charAt(index);
        return value == 'x' || value == 'z'
                ? block.substring(0, index) + (value == 'x' ? 'z' : 'x') + block.substring(index + 1) : block;
    }
    private static String seat(String facing) {
        return "minecraft:dark_oak_stairs[facing=" + facing + ",half=bottom,shape=straight]";
    }
    private static String lampShoulder(String facing) {
        return "minecraft:smooth_quartz_stairs[facing=" + facing + ",half=top,shape=straight]";
    }
}
