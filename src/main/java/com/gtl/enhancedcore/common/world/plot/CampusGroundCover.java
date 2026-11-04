package com.gtl.enhancedcore.common.world.plot;

import java.util.Set;

/** Pure ground-cover sampling above a reserved grass-block surface at relative y=0. */
final class CampusGroundCover {
    private static final String GRASS = "minecraft:grass";
    private static final String FERN = "minecraft:fern";
    private static final String TALL_GRASS_LOWER = "minecraft:tall_grass[half=lower]";
    private static final String TALL_GRASS_UPPER = "minecraft:tall_grass[half=upper]";
    private static final String LARGE_FERN_LOWER = "minecraft:large_fern[half=lower]";
    private static final String LARGE_FERN_UPPER = "minecraft:large_fern[half=upper]";
    private static final String DANDELION = "minecraft:dandelion";
    private static final String POPPY = "minecraft:poppy";
    private static final String DAISY = "minecraft:oxeye_daisy";
    private static final String BLUET = "minecraft:azure_bluet";
    private static final String ALLIUM = "minecraft:allium";
    private static final String CORNFLOWER = "minecraft:cornflower";
    private static final String LILY = "minecraft:lily_of_the_valley";
    private static final String ORCHID = "minecraft:blue_orchid";
    private static final Set<String> MATERIALS = Set.of(GRASS, FERN, TALL_GRASS_LOWER, TALL_GRASS_UPPER,
            LARGE_FERN_LOWER, LARGE_FERN_UPPER, DANDELION, POPPY, DAISY, BLUET, ALLIUM, CORNFLOWER,
            LILY, ORCHID);

    private CampusGroundCover() {}

    static Set<String> materials() { return MATERIALS; }

    /** Inclusive relative height; single plants use y=1, double plants use y=1 and y=2. */
    static int maxHeight() { return 2; }

    /**
     * Habitat 0 is open meadow, 1 is woodland floor, and 2 is dry lake shore.
     * Callers keep paths/furniture clear and reserve both above-ground cells for double plants.
     * Every column decision excludes y, so independently sampled halves always agree.
     */
    static String above(int x, int y, int z, int seed, int habitat) {
        if (y < 1 || y > 2 || habitat < 0 || habitat > 2) return null;
        int salt = seed ^ habitat * 0x68bc21eb;
        double cover = field(x, z, salt ^ 0x2c1b3c6d, 24);
        double density = switch (habitat) {
            case 0 -> 0.24 + 0.48 * cover;
            case 1 -> 0.56 + 0.30 * cover;
            default -> 0.50 + 0.34 * cover;
        };
        if (unit(hash(x, z, salt ^ 0x297a2d39)) >= density) return null;

        // Smooth patches retain grassy breathing spaces between the flower-rich pockets.
        double patch = field(x, z, salt ^ 0x165667b1, 12);
        patch = smooth(Math.max(0.0, Math.min(1.0, (patch - 0.30) / 0.40)));
        double flowers = switch (habitat) {
            case 0 -> 0.07 + 0.48 * patch;
            case 1 -> 0.035 + 0.14 * patch;
            default -> 0.14 + 0.46 * patch;
        };
        double kind = unit(hash(x, z, salt ^ 0x9e3779b9));
        if (kind < flowers) return y == 1 ? flower(x, z, salt, habitat) : null;

        double tall = switch (habitat) {
            case 0 -> 0.05 + 0.04 * cover;
            case 1 -> 0.07 + 0.04 * cover;
            default -> 0.08 + 0.06 * cover;
        };
        if (kind < flowers + tall) {
            boolean fern = habitat == 1 && unit(hash(x, z, salt ^ 0x51ed270b)) < 0.80;
            if (fern) return y == 1 ? LARGE_FERN_LOWER : LARGE_FERN_UPPER;
            return y == 1 ? TALL_GRASS_LOWER : TALL_GRASS_UPPER;
        }
        if (y != 1) return null;
        if (habitat == 1 && unit(hash(x, z, salt ^ 0x7f4a7c15)) < 0.55 + 0.25 * cover) return FERN;
        if (habitat == 2 && unit(hash(x, z, salt ^ 0x7f4a7c15)) < 0.14) return FERN;
        return GRASS;
    }

    /** Larger coherent color districts: each flower pocket has one 82% dominant species. */
    private static String flower(int x, int z, int seed, int habitat) {
        int theme = (int) (field(x, z, seed ^ 0x27d4eb2f, 48) * 4.0);
        boolean main = unit(hash(x, z, seed ^ 0x85ebca6b)) < 0.82;
        return switch (habitat) {
            case 0 -> switch (theme) {
                case 0 -> main ? DAISY : DANDELION;
                case 1 -> main ? CORNFLOWER : BLUET;
                case 2 -> main ? DANDELION : POPPY;
                default -> main ? ALLIUM : DAISY;
            };
            case 1 -> switch (theme) {
                case 0 -> main ? LILY : BLUET;
                case 1 -> main ? BLUET : LILY;
                case 2 -> main ? ALLIUM : LILY;
                default -> main ? LILY : ORCHID;
            };
            default -> switch (theme) {
                case 0 -> main ? ORCHID : BLUET;
                case 1 -> main ? ALLIUM : ORCHID;
                case 2 -> main ? DAISY : BLUET;
                default -> main ? CORNFLOWER : ORCHID;
            };
        };
    }

    /** Smooth value noise; lattice corners affect density, never fixed plant positions. */
    private static double field(int x, int z, int seed, int scale) {
        int cellX = Math.floorDiv(x, scale), cellZ = Math.floorDiv(z, scale);
        double u = smooth(Math.floorMod(x, scale) / (double) scale);
        double v = smooth(Math.floorMod(z, scale) / (double) scale);
        double north = lerp(unit(hash(cellX, cellZ, seed)), unit(hash(cellX + 1, cellZ, seed)), u);
        double south = lerp(unit(hash(cellX, cellZ + 1, seed)), unit(hash(cellX + 1, cellZ + 1, seed)), u);
        return lerp(north, south, v);
    }

    private static double smooth(double value) { return value * value * (3.0 - 2.0 * value); }

    private static double lerp(double a, double b, double amount) { return a + (b - a) * amount; }

    private static double unit(int value) { return (value >>> 8) * 0x1.0p-24; }

    private static int hash(int x, int z, int seed) {
        int value = x * 0x1f123bb5 ^ z * 0x5f356495 ^ seed;
        value ^= value >>> 16;
        value *= 0x7feb352d;
        value ^= value >>> 15;
        value *= 0x846ca68b;
        return value ^ value >>> 16;
    }
}
