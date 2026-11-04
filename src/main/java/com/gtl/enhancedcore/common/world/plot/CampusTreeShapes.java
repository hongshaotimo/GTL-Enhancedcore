package com.gtl.enhancedcore.common.world.plot;

import java.util.LinkedHashSet;
import java.util.Set;

/** Pure tree geometry. Ground is y=0; callers reserve a complete crown before sampling. */
final class CampusTreeShapes {
    private static final int EMPTY = 0, STEM = 1, BRANCH_X = 2, BRANCH_Z = 3, FOLIAGE = 4;
    private static final String[] SPECIES = {"oak", "dark_oak", "birch", "spruce", "acacia", "cherry"};
    private static final String[][] LOGS = new String[SPECIES.length][3];
    private static final String[] LEAVES = new String[SPECIES.length];
    private static final Set<String> MATERIALS;

    static {
        Set<String> palette = new LinkedHashSet<>();
        for (int i = 0; i < SPECIES.length; i++) {
            LOGS[i][0] = "minecraft:" + SPECIES[i] + "_log[axis=y]";
            LOGS[i][1] = "minecraft:" + SPECIES[i] + "_log[axis=x]";
            LOGS[i][2] = "minecraft:" + SPECIES[i] + "_log[axis=z]";
            LEAVES[i] = "minecraft:" + SPECIES[i] + "_leaves[persistent=true]";
            for (String log : LOGS[i]) palette.add(log);
            palette.add(LEAVES[i]);
        }
        MATERIALS = Set.copyOf(palette);
    }

    private CampusTreeShapes() {}

    static Set<String> materials() { return MATERIALS; }

    /** Inclusive relative height, reached by the tallest woodland spruce. */
    static int maxHeight() { return 18; }

    /** Chebyshev radius: formal trees fit the existing 5x5 reservations. */
    static int radius(boolean woodland) { return woodland ? 4 : 2; }

    /** Exact occupied height of this column; the reserved but empty crown margin returns zero. */
    static int top(int dx, int dz, int seed, boolean woodland) {
        int limit = radius(woodland);
        if (dx < -limit || dx > limit || dz < -limit || dz > limit) return 0;
        for (int y = maxHeight(); y > 0; y--)
            if (above(dx, y, dz, seed, woodland) != null) return y;
        return 0;
    }

    /**
     * Empty cells return null. floorMod(seed,6) selects oak, dark oak, birch, spruce, acacia
     * or cherry; a caller may hold that remainder stable to create a planted species group.
     * The remaining seed controls height, crown spread and quarter-turn orientation.
     * Sampling evaluates a few local predicates, never scans a tree or reads a world.
     */
    static String above(int dx, int y, int dz, int seed, boolean woodland) {
        int limit = radius(woodland);
        if (y <= 0 || y > maxHeight() || dx < -limit || dx > limit || dz < -limit || dz > limit)
            return null;
        int kind = Math.floorMod(seed, SPECIES.length), variation = mix(seed);
        int turn = (variation >>> 27) & 3;
        int x = switch (turn) { case 1 -> dz; case 2 -> -dx; case 3 -> -dz; default -> dx; };
        int z = switch (turn) { case 1 -> -dx; case 2 -> -dz; case 3 -> dx; default -> dz; };
        int spread = woodland ? 3 + ((variation >>> 12) & 1) : 2;
        int heightVariation = variation >>> 3;
        int h = switch (kind) {
            case 0 -> woodland ? 9 + heightVariation % 5 : 8 + heightVariation % 4;
            case 1 -> woodland ? 11 + heightVariation % 5 : 9 + heightVariation % 4;
            case 2 -> woodland ? 12 + heightVariation % 5 : 10 + heightVariation % 5;
            case 3 -> woodland ? 14 + heightVariation % 5 : 11 + heightVariation % 5;
            case 4 -> woodland ? 9 + heightVariation % 4 : 8 + heightVariation % 3;
            default -> woodland ? 10 + heightVariation % 4 : 8 + heightVariation % 4;
        };
        if (y > h) return null;
        int block = switch (kind) {
            case 0 -> oak(x, y, z, h, spread);
            case 1 -> darkOak(x, y, z, h, spread, woodland);
            case 2 -> birch(x, y, z, h, woodland ? spread - 1 : 2, variation);
            case 3 -> spruce(x, y, z, h, spread, variation);
            case 4 -> acacia(x, y, z, h, spread);
            default -> cherry(x, y, z, h, spread);
        };
        if (block == EMPTY) return null;
        if (block == FOLIAGE) return LEAVES[kind];
        int axis = block - STEM;
        if ((turn & 1) != 0 && axis != 0) axis = 3 - axis;
        return LOGS[kind][axis];
    }

    /** Rounded, uneven oak: a high central dome and two lower shoulder lobes. */
    private static int oak(int x, int y, int z, int h, int r) {
        int arm = Math.max(1, r - 2), lobe = r - arm;
        if (x == 0 && z == 0 && y <= h - 3) return STEM;
        if (y == h - 5 && z == 0 && x >= -arm && x <= arm) return BRANCH_X;
        if (y == h - 4 && x == 0 && z >= -arm && z <= 0) return BRANCH_Z;
        if (y == h - 4 && x == arm && z == 0) return STEM;
        return ellipsoid(x, y - (h - 3), z, r - 1, 3, r - 1)
                || ellipsoid(x - arm, y - (h - 4), z, lobe, 2, r - 1)
                || ellipsoid(x + arm, y - (h - 3), z + 1, lobe, 2, r - 1)
                ? FOLIAGE : EMPTY;
    }

    /** A heavier dark oak with a squat, broad crown and a 2x2 woodland trunk. */
    private static int darkOak(int x, int y, int z, int h, int r, boolean woodland) {
        if (x == 0 && z == 0 && y <= h - 3) return STEM;
        if (woodland && x >= 0 && x <= 1 && z >= 0 && z <= 1 && y <= h - 5) return STEM;
        if (y == h - 5 && z == 0 && Math.abs(x) <= r - 1) return BRANCH_X;
        if (y == h - 4 && x == 0 && Math.abs(z) <= r - 1) return BRANCH_Z;
        int shoulder = Math.max(1, r - 2), lobe = r - shoulder;
        return ellipsoid(x, y - (h - 2), z, r - 1, 2, r - 1)
                || ellipsoid(x - shoulder, y - (h - 4), z, lobe, 2, r - 1)
                || ellipsoid(x + shoulder, y - (h - 3), z, lobe, 2, r - 1)
                || ellipsoid(x, y - (h - 4), z - shoulder, r - 1, 2, lobe)
                || ellipsoid(x, y - (h - 3), z + shoulder, r - 1, 2, lobe)
                ? FOLIAGE : EMPTY;
    }

    /** White, tall birch, with a slender crown tapering into a slightly offset leader. */
    private static int birch(int x, int y, int z, int h, int r, int variation) {
        if (x == 0 && z == 0 && y <= h - 2) return STEM;
        if (y == h - 5 && z == 0 && Math.abs(x) <= 1) return BRANCH_X;
        if (y == h - 3 && x == 0 && z >= -1 && z <= 0) return BRANCH_Z;
        int lean = (variation >>> 18) & 1;
        return ellipsoid(x, y - (h - 4), z, r, 3, r)
                || ellipsoid(x - lean, y - (h - 2), z, Math.max(1, r - lean), 2, r - 1)
                || ellipsoid(x, y - (h - 6), z, r - 1, 2, r - 1)
                ? FOLIAGE : EMPTY;
    }

    /** Layered spruce, with visible gaps between skirts and a continuous central leader. */
    private static int spruce(int x, int y, int z, int h, int r, int variation) {
        if (x == 0 && z == 0 && y < h) return STEM;
        int below = h - y;
        if (y < 4 + ((variation >>> 16) & 1)) return EMPTY;
        int tier = below / 3, phase = below % 3;
        int width = Math.min(r, 1 + tier);
        int skirt = phase == 1 ? width : Math.max(0, width - 1);
        if (below >= 4 && phase == 1) {
            int branch = Math.max(1, skirt - 1);
            if (z == 0 && Math.abs(x) <= branch) return BRANCH_X;
            if (x == 0 && Math.abs(z) <= branch) return BRANCH_Z;
        }
        if (below == 0) return x == 0 && z == 0 ? FOLIAGE : EMPTY;
        return disc(x, z, skirt, skirt) ? FOLIAGE : EMPTY;
    }

    /** Acacia forks into a raised, offset umbrella and a lower opposing bough. */
    private static int acacia(int x, int y, int z, int h, int r) {
        int fork = h - 4, arm = Math.max(1, r - 2), lobe = r - arm;
        if (x == 0 && z == 0 && y <= fork) return STEM;
        if (y == fork && z == 0 && x >= 0 && x <= 1) return BRANCH_X;
        if (x == 1 && z == 0 && y > fork && y <= h - 2) return STEM;
        if (y == fork - 1 && z == 0 && x >= -arm && x <= 0) return BRANCH_X;
        if (x == -arm && z == 0 && y >= fork && y <= fork + 1) return STEM;
        if (ellipsoid(x + arm, y - (h - 3), z, lobe, 1, r - 1)) return FOLIAGE;
        int main = r - 1;
        return (y == h - 3 && disc(x - 1, z, Math.max(1, main - 1), r - 1))
                || ((y == h - 2 || y == h - 1) && disc(x - 1, z, main, r))
                || (y == h && disc(x - 1, z, Math.max(1, main - 1), r - 1))
                ? FOLIAGE : EMPTY;
    }

    /** Pink cherry canopy, with a domed centre and low, rounded overhanging shoulders. */
    private static int cherry(int x, int y, int z, int h, int r) {
        int arm = Math.max(1, r - 2), lobe = r - arm;
        if (x == 0 && z == 0 && y <= h - 3) return STEM;
        if (y == h - 5 && z == 0 && Math.abs(x) <= arm) return BRANCH_X;
        if (y == h - 4 && x == 0 && Math.abs(z) <= arm) return BRANCH_Z;
        if (Math.abs(x) == arm && z == 0 && y >= h - 4 && y <= h - 3) return STEM;
        return ellipsoid(x, y - (h - 2), z, r - 1, 2, r - 1)
                || ellipsoid(x - arm, y - (h - 4), z, lobe, 2, r - 1)
                || ellipsoid(x + arm, y - (h - 3), z, lobe, 2, r - 1)
                || ellipsoid(x, y - (h - 3), z - arm, r - 1, 2, lobe)
                ? FOLIAGE : EMPTY;
    }

    private static boolean ellipsoid(int x, int y, int z, int rx, int ry, int rz) {
        if (x < -rx || x > rx || y < -ry || y > ry || z < -rz || z > rz) return false;
        int xx = rx * rx, yy = ry * ry, zz = rz * rz;
        return x * x * yy * zz + y * y * xx * zz + z * z * xx * yy <= xx * yy * zz;
    }

    private static boolean disc(int x, int z, int rx, int rz) {
        if (rx == 0 || rz == 0) return x == 0 && z == 0;
        if (x < -rx || x > rx || z < -rz || z > rz) return false;
        return x * x * rz * rz + z * z * rx * rx <= rx * rx * rz * rz + Math.min(rx, rz);
    }

    private static int mix(int seed) {
        int value = seed ^ 0x6D2B79F5;
        value = (value ^ (value >>> 16)) * 0x7FEB352D;
        value = (value ^ (value >>> 15)) * 0x846CA68B;
        return value ^ (value >>> 16);
    }
}
