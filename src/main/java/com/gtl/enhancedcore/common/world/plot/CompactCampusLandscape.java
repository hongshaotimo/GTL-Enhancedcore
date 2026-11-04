package com.gtl.enhancedcore.common.world.plot;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Small, non-functional vanilla garden modules sampled relative to their centre. */
public final class CompactCampusLandscape {
    private static final int RADIUS = 7, HEIGHT = 5;
    private static final String PALE = "minecraft:light_gray_concrete";
    private static final String GRASS = "minecraft:grass_block";
    private static final String QUARTZ = "minecraft:smooth_quartz";
    private static final String PILLAR = "minecraft:quartz_pillar[axis=y]";
    private static final String SLAB = "minecraft:smooth_quartz_slab[type=bottom]";
    private static final String STONE = "minecraft:polished_andesite";
    private static final String STONE_SLAB = "minecraft:polished_andesite_slab[type=bottom]";
    private static final String OAK_SLAB = "minecraft:oak_slab[type=bottom]";
    private static final String LOG = "minecraft:oak_log[axis=y]";
    private static final String HEDGE = "minecraft:azalea_leaves[persistent=true]";
    private static final String FLOWER = "minecraft:flowering_azalea_leaves[persistent=true]";
    private static final String WATER = "minecraft:water";
    private static final String LIGHT = "minecraft:sea_lantern";
    private static final Set<String> MATERIALS;

    static {
        Set<String> palette = new LinkedHashSet<>(List.of(PALE, GRASS, QUARTZ, PILLAR, SLAB, STONE,
                STONE_SLAB, OAK_SLAB, LOG, HEDGE, FLOWER, WATER, LIGHT));
        for (String facing : List.of("north", "south", "east", "west")) palette.add(stairs(facing));
        MATERIALS = Set.copyOf(palette);
    }

    private CompactCampusLandscape() {}

    public static Set<String> materials() { return MATERIALS; }
    public static int radius() { return RADIUS; }
    /** Highest relative block coordinate, including the pergola's slab roof. */
    public static int height() { return HEIGHT; }

    /** Ground is y=0; reserve the complete 15 by 15 footprint before placing a module. */
    public static String surface(int dx, int dz, int variant) {
        if (!contains(dx, dz)) return null;
        int x = Math.abs(dx), z = Math.abs(dz), edge = Math.max(x, z);
        if (edge == RADIUS) return GRASS;
        return switch (Math.floorMod(variant, 4)) {
            case 0 -> {
                if (poolWater(x, z)) yield WATER;
                if (poolEnvelope(x, z)) yield QUARTZ;
                if (x >= 5 && z >= 5) yield x == 5 && z == 5 ? LIGHT : GRASS;
                yield edge == 5 ? STONE : PALE;
            }
            case 1 -> {
                if (cornerPlanter(x, z)) yield x == 5 && z == 5 ? LIGHT : QUARTZ;
                if (x <= 1 || z <= 1) yield STONE;
                yield edge == 3 ? QUARTZ : PALE;
            }
            case 2 -> {
                if (x >= 4 && z >= 5) yield x == 5 && z == 6 ? LIGHT : GRASS;
                if (x <= 1 || z == 0) yield STONE;
                yield x == 6 || z == 4 ? QUARTZ : PALE;
            }
            default -> {
                if (x <= 1 || z <= 1) yield STONE;
                if (treePlanter(x, z)) yield QUARTZ;
                yield PALE;
            }
        };
    }

    /** The reflecting pool has a full solid floor at y=-1, never a floating water sheet. */
    public static String foundation(int dx, int y, int dz, int variant) {
        if (y != -1 || !contains(dx, dz) || Math.floorMod(variant, 4) != 0) return null;
        int x = Math.abs(dx), z = Math.abs(dz);
        if (!poolEnvelope(x, z)) return null;
        return poolWater(x, z) && (x == 2 && z == 0 || x == 0 && z == 2) ? LIGHT : QUARTZ;
    }

    /** Empty cells return null; the caller owns safety checks and any other vegetation. */
    public static String above(int dx, int y, int dz, int variant) {
        if (y <= 0 || y > HEIGHT || !contains(dx, dz)) return null;
        return switch (Math.floorMod(variant, 4)) {
            case 0 -> poolAbove(dx, y, dz);
            case 1 -> terraceAbove(dx, y, dz);
            case 2 -> pergolaAbove(dx, y, dz);
            default -> flowerTreesAbove(dx, y, dz);
        };
    }

    private static String poolAbove(int dx, int y, int dz) {
        int x = Math.abs(dx), z = Math.abs(dz);
        if (y != 1) return null;
        if (poolEnvelope(x, z) && !poolWater(x, z)) return SLAB;
        if (x == 6 && z <= 2 || z == 6 && x <= 2) return SLAB;
        if (x == 6 && z == 3 || z == 6 && x == 3) return QUARTZ;
        if (x >= 5 && x <= 6 && z >= 5 && z <= 6) return x == z ? FLOWER : HEDGE;
        return null;
    }

    private static String terraceAbove(int dx, int y, int dz) {
        int x = Math.abs(dx), z = Math.abs(dz);
        if (cornerPlanter(x, z)) {
            if (x == 5 && z == 5) return y == 1 ? HEDGE : y == 2 ? FLOWER : null;
            return y == 1 ? SLAB : null;
        }
        if (y != 1) return null;
        // Eight short seats leave the three-block-wide cardinal approaches open.
        if (x == 4 && z >= 2 && z <= 3) return stairs(dx > 0 ? "east" : "west");
        if (z == 4 && x >= 2 && x <= 3) return stairs(dz > 0 ? "south" : "north");
        if (x == 3 && z == 3) return STONE_SLAB;
        return null;
    }

    private static String pergolaAbove(int dx, int y, int dz) {
        int x = Math.abs(dx), z = Math.abs(dz);
        // Two side columns carry a cross beam and short cantilever arms.
        if (x == 5 && z == 0 && y <= 3) return PILLAR;
        if (y == 4 && (z == 0 && x <= 5 || x == 5 && z <= 4)) return QUARTZ;
        if (y == 5) {
            if (x == 5 && z <= 4 || z == 4 && x <= 5) return SLAB;
            if (x <= 4 && z <= 3 && x % 2 == 0) return OAK_SLAB;
            return null;
        }
        if (y != 1) return null;
        if (z == 5 && x >= 3 && x <= 5) return SLAB;
        if (z == 5 && x == 6) return QUARTZ;
        if (z == 6 && x >= 4 && x <= 6) return x == 5 ? FLOWER : HEDGE;
        return null;
    }

    private static String flowerTreesAbove(int dx, int y, int dz) {
        int x = Math.abs(dx), z = Math.abs(dz);
        if (!treePlanter(x, z)) return null;
        int a = Math.abs(x - 4), b = Math.abs(z - 4), distance = a + b;
        if (a == 0 && b == 0 && y <= 3) return LOG;
        if (y == 1) {
            if (Math.max(a, b) == 2) return SLAB;
            return distance == 1 ? LIGHT : GRASS;
        }
        if (y == 2 && Math.max(a, b) <= 1) return distance == 2 ? FLOWER : HEDGE;
        if (y == 3 && distance <= 2) return HEDGE;
        if (y == 4 && distance <= 3) return distance >= 2 ? FLOWER : HEDGE;
        if (y == 5 && distance <= 1) return FLOWER;
        return null;
    }

    private static boolean contains(int x, int z) {
        return x >= -RADIUS && x <= RADIUS && z >= -RADIUS && z <= RADIUS;
    }
    private static boolean poolWater(int x, int z) { return x <= 3 && z <= 3 && x + z <= 5; }
    private static boolean poolEnvelope(int x, int z) { return x <= 4 && z <= 4 && x + z <= 6; }
    private static boolean cornerPlanter(int x, int z) { return x >= 4 && x <= 6 && z >= 4 && z <= 6; }
    private static boolean treePlanter(int x, int z) { return x >= 2 && x <= 6 && z >= 2 && z <= 6; }
    private static String stairs(String facing) { return "minecraft:quartz_stairs[facing=" + facing + "]"; }
}
