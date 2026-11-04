package com.gtl.enhancedcore.common.world.plot;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Finite modern garden campus. Geometry, preview and generation share this single plan. */
public final class PlannedCampus {
    public static final String CATALOG = "/data/gtl_enhancedcore/worldgen/planned_campus_machines.json";
    private static final String WHITE = "minecraft:white_concrete", PALE = "minecraft:light_gray_concrete";
    private static final String GRAY = "minecraft:gray_concrete", BLACK = "minecraft:black_concrete";
    private static final String GREEN = "minecraft:grass_block", YELLOW = "minecraft:yellow_concrete";
    private static final String CYAN = "minecraft:cyan_concrete", WATER = "minecraft:water";
    private static final String LIGHT = "minecraft:sea_lantern", WOOD = "minecraft:oak_log", LEAF = "minecraft:oak_leaves[persistent=true]";
    private static final String QUARTZ = "minecraft:smooth_quartz", SLAB = "minecraft:smooth_quartz_slab";
    private static final String STONE = "minecraft:polished_andesite", WALL = "minecraft:stone_brick_wall[up=true]";
    private static final String STONE_SLAB = "minecraft:polished_andesite_slab";
    private static final String HEDGE = "minecraft:azalea_leaves[persistent=true]", FLOWER = "minecraft:flowering_azalea_leaves[persistent=true]";
    private static final String WARM = "minecraft:terracotta";
    private static final int TILE = 64;
    private static final Bounds AE_BOUNDS = new Bounds(-80, -80, 79, 79);
    private static final List<Garden> AE_GARDENS = List.of(new Garden(-164, -48), new Garden(164, -48),
            new Garden(-164, 48), new Garden(164, 48));
    private static final Set<String> MATERIALS;
    static {
        Set<String> palette = new LinkedHashSet<>(List.of(WHITE, PALE, GRAY, BLACK, GREEN, YELLOW, CYAN,
                WATER, LIGHT, WOOD, LEAF, QUARTZ, SLAB, WARM, STONE, WALL, STONE_SLAB, HEDGE, FLOWER));
        for (String facing : List.of("north", "south", "east", "west"))
            palette.add("minecraft:quartz_stairs[facing=" + facing + "]");
        palette.add("minecraft:polished_andesite_slab[type=top]");
        palette.addAll(CampusLandscape.materials());
        MATERIALS = Set.copyOf(palette);
    }

    public record Bounds(int minX, int minZ, int maxX, int maxZ) {
        public int width() { return maxX - minX + 1; }
        public int depth() { return maxZ - minZ + 1; }
        public boolean contains(int x, int z) { return x >= minX && x <= maxX && z >= minZ && z <= maxZ; }
    }
    /** North-facing reservation envelope, including upgrade or full-module capacity where selected. */
    public record Plot(String id, String stage, int x, int z, int width, int height, int depth,
                       int controllerX, int controllerY, int controllerZ, String patternResource, int slotIndex, String theme,
                       String displayNameKey) {
        public Plot(String id, String stage, int x, int z, int width, int height, int depth,
                    int controllerX, int controllerY, int controllerZ, String patternResource, int slotIndex, String theme) {
            this(id, stage, x, z, width, height, depth, controllerX, controllerY, controllerZ,
                    patternResource, slotIndex, theme, "");
        }
        public int maxX() { return x + width - 1; }
        public int maxZ() { return z + depth - 1; }
        public int signX() { return x + width / 2; }
        public int signZ() { return z - 3; }
        public int baseY() { return 1; }
        public boolean contains(int px, int pz) { return px >= x && px <= maxX() && pz >= z && pz <= maxZ(); }
        public Bounds bounds() { return new Bounds(x, z, maxX(), maxZ()); }
    }
    public record Workshop(String stage, int x, int z, int width, int depth) {
        public boolean contains(int px, int pz) { return px >= x && px < x + width && pz >= z && pz < z + depth; }
    }
    public record District(String stage, int minZ, int maxZ) {}
    private record Machine(String id, String stage, int tier, int width, int height, int depth,
                           int cx, int cy, int cz, String pattern, int slotIndex, String theme, String displayNameKey) {}
    private record Placement(Machine machine, Bounds court) {}
    private record Candidate(Bounds court, Bounds free, int radius, int taxi, int slack, int waste) {}
    private record Connection(Street street, int length, int orientation, int edge, int target) {}
    private record Packing(List<Placement> placements, List<Street> streets, List<Bounds> residuals, List<Bounds> greenways, int halfWidth, int halfDepth) {}
    private record Street(boolean vertical, int axis, int start, int end, int halfWidth) {
        int perpendicular(int x, int z) { return vertical ? x : z; }
        int along(int x, int z) { return vertical ? z : x; }
        int distance(int x, int z) { return fold(perpendicular(x, z) - axis); }
        boolean contains(int x, int z) { int along = along(x, z); return along >= start && along <= end && distance(x, z) < halfWidth; }
    }
    private record Tree(int x, int z) {}
    private record Lamp(int x, int z, boolean vertical, int inward) {}
    /** A whole reserved public garden, never a clipped tree or an isolated patch in a lawn. */
    private record Landscape(Bounds bounds, int variant) {
        boolean vertical() { return bounds.depth() >= bounds.width(); }
        int halfWidth() { return (Math.min(bounds.width(), bounds.depth()) - 1) / 2; }
        int halfLength() { return (Math.max(bounds.width(), bounds.depth()) - 1) / 2; }
        int across(int x, int z) { return (vertical() ? x - bounds.minX : z - bounds.minZ) - halfWidth(); }
        int along(int x, int z) { return (vertical() ? z - bounds.minZ : x - bounds.minX) - halfLength(); }
        boolean contains(int x, int z) { return bounds.contains(x, z); }
        boolean park() { return halfWidth() >= CampusLandscape.parkMinHalfExtent() && halfLength() >= CampusLandscape.parkMinHalfExtent(); }
        boolean court() { return halfWidth() >= 12 && halfLength() >= 12; }
        boolean strip() { return halfWidth() >= 1 && halfWidth() <= 5 && halfLength() >= 6; }
        boolean sampleContains(int x, int z) { return Math.abs(across(x, z)) <= halfWidth() && Math.abs(along(x, z)) <= halfLength(); }
        String surface(int x, int z) {
            if (!sampleContains(x, z)) return STONE;
            int a = across(x, z), l = along(x, z);
            if (park()) return CampusLandscape.parkSurface(a, l, halfWidth(), halfLength(), variant);
            if (court()) return CampusLandscape.courtyardSurface(a, l, halfWidth(), halfLength(), variant);
            if (strip()) return CampusLandscape.roadsideSurface(l, a, halfLength(), halfWidth(), variant);
            return Math.abs(l) >= halfLength() - 1 ? STONE : GREEN;
        }
        String foundation(int x, int y, int z) {
            if (park() && sampleContains(x, z))
                return CampusLandscape.parkFoundation(across(x, z), y, along(x, z), halfWidth(), halfLength(), variant);
            return court() && sampleContains(x, z)
                    ? CampusLandscape.courtyardFoundation(across(x, z), y, along(x, z), halfWidth(), halfLength(), variant) : null;
        }
        String above(int x, int y, int z) {
            if (!sampleContains(x, z)) return null;
            int a = across(x, z), l = along(x, z);
            String block = park() ? CampusLandscape.parkAbove(a, y, l, halfWidth(), halfLength(), variant)
                    : court() ? CampusLandscape.courtyardAbove(a, y, l, halfWidth(), halfLength(), variant)
                    : strip() ? CampusLandscape.roadsideAbove(l, y, a, halfLength(), halfWidth(), variant)
                    : y == 1 && Math.abs(l) < halfLength() - 1 ? Math.floorMod(l, 6) == 0 ? FLOWER : HEDGE : null;
            if (court()) return vertical() ? block : transpose(block);
            if (strip()) return vertical() ? transpose(block) : block;
            return block;
        }
        int top(int x, int z) {
            if (!sampleContains(x, z)) return 0;
            if (park()) return CampusLandscape.parkTop(across(x, z), along(x, z), halfWidth(), halfLength(), variant);
            return court() ? CampusLandscape.courtyardTop(across(x, z), along(x, z), halfWidth(), halfLength(), variant)
                    : strip() ? CampusLandscape.roadsideTop(along(x, z), across(x, z), halfLength(), halfWidth(), variant)
                    : Math.abs(along(x, z)) < halfLength() - 1 ? 1 : 0;
        }
    }
    private record Garden(int x, int z) {
        Bounds bounds() { return new Bounds(x - 56, z - 28, x + 55, z + 27); }
        int dx(int px) { return fold(px - x); }
        int dz(int pz) { return fold(pz - z); }
        boolean contains(int px, int pz) { return dx(px) < 56 && dz(pz) < 28; }
        boolean pond(int px, int pz) { return dx(px) <= 31 && dz(pz) <= 10; }
        boolean rim(int px, int pz) { return pond(px, pz) && (dx(px) == 31 || dz(pz) == 10); }
    }
    private record Local(List<Plot> plots, List<Street> streets, List<Tree> trees, List<Lamp> lamps, List<Landscape> landscapes, List<Bounds> islands) {}
    private record Plan(List<Plot> plots, List<Street> streets, List<Workshop> workshops,
                        List<District> districts, Map<Long, Local> index, Bounds bounds, int height, int shared,
                        Map<String, Integer> tiers) {}
    private static final class Holder { private static final Plan PLAN = build(); }

    private PlannedCampus() {}
    private static int fold(int coordinate) { return coordinate >= 0 ? coordinate : -1 - coordinate; }
    public static Set<String> materials() { return MATERIALS; }
    public static List<Plot> plots() { return Holder.PLAN.plots; }
    public static List<Workshop> workshops() { return Holder.PLAN.workshops; }
    public static List<District> districts() { return Holder.PLAN.districts; }
    public static int sharedMachineCount() { return Holder.PLAN.shared; }
    public static int planningTier(String id) { return Holder.PLAN.tiers.get(id); }
    public static Bounds bounds() { return Holder.PLAN.bounds; }
    public static Bounds previewBounds(boolean overview) { return overview ? bounds() : new Bounds(-320, -256, 319, 255); }
    public static Bounds aeCoreBounds() { return AE_BOUNDS; }
    public static List<Bounds> aeGardenBounds() { return AE_GARDENS.stream().map(Garden::bounds).toList(); }
    public static int maxHeight() { return Holder.PLAN.height; }
    public static int machineBaseY() { return 1; }
    public static boolean fitsHeight(PlotSettings settings) { return settings.surfaceY() + maxHeight() <= 319; }
    public static boolean hasTerrain(int x, int z) {
        Local local = local(x, z);
        if (local == null) return false;
        if (onIsland(local, x, z)) return true;
        for (Street street : local.streets) if (street.contains(x, z)) return true;
        return false;
    }
    private static boolean onIsland(Local local, int x, int z) {
        for (Bounds island : local.islands) if (island.contains(x, z)) return true;
        return false;
    }
    public static Plot plotAt(int x, int z) {
        Local local = local(x, z);
        if (local != null) for (Plot plot : local.plots) if (plot.contains(x, z)) return plot;
        return null;
    }
    public static String surfaceBlock(PlotSettings settings, int x, int z) {
        if (!hasTerrain(x, z)) return "minecraft:air";
        Local local = local(x, z);
        if (AE_BOUNDS.contains(x, z)) return aeSurface(x, z);
        Street access = streetAt(local, x, z);
        if (access != null && access.halfWidth <= 4) return streetSurface(access, x, z, local.streets);
        for (Plot plot : local.plots) {
            if (frameEdge(plot, x, z)) return QUARTZ;
            if (plot.contains(x, z)) return PALE;
            if (courtBounds(plot).contains(x, z)) {
                if (z < plot.z && Math.abs(x - plot.signX()) <= 2) return PALE;
                if (x == plot.x - 2 || x == plot.maxX() + 2 || z == plot.maxZ() + 2) return accent(plot.theme);
                return GREEN;
            }
        }
        for (Workshop pad : Holder.PLAN.workshops) if (pad.contains(x, z)) {
            int dx = x - pad.x, dz = z - pad.z;
            if (dx == 0 || dz == 0 || dx == pad.width - 1 || dz == pad.depth - 1) return QUARTZ;
            // Flush paving bands, with no above-ground subdivision of the player's production floor.
            if (dx % 32 == 0 || dz % 32 == 0) return STONE;
            return PALE;
        }
        Street street = streetAt(local, x, z);
        if (street != null) return streetSurface(street, x, z, local.streets);
        for (Landscape landscape : local.landscapes) if (landscape.contains(x, z)) {
            String surface = landscape.surface(x, z);
            if (surface != null) return surface;
        }
        String garden = gardenSurface(x, z);
        if (garden != null) return garden;
        if (edgeColumn(x, z)) return QUARTZ;
        return GREEN;
    }
    private static Street streetAt(Local local, int x, int z) {
        Street chosen = null;
        for (Street street : local.streets) if (street.contains(x, z)
                && (chosen == null || street.distance(x, z) - (street.halfWidth - 7)
                        < chosen.distance(x, z) - (chosen.halfWidth - 7))) chosen = street;
        return chosen;
    }
    private static String gardenSurface(int x, int z) {
        for (Garden garden : AE_GARDENS) if (garden.contains(x, z)) {
            int dx = garden.dx(x), dz = garden.dz(z);
            if (garden.pond(x, z)) return garden.rim(x, z) ? QUARTZ : WATER;
            if (dx >= 35 && dx <= 39 || dz >= 15 && dz <= 18) return STONE;
            if (dx >= 53 || dz >= 25) return PALE;
            return GREEN;
        }
        int ax = fold(x), az = fold(z);
        if (ax >= 80 && ax <= 143 && Math.abs(az - 64) <= 1) return STONE;
        return null;
    }
    public static String foundationBlockAt(int x, int y, int z) {
        if (y >= 0 || !hasTerrain(x, z)) return null;
        if (y >= -4) for (Garden garden : AE_GARDENS) if (garden.pond(x, z)) {
            if (garden.rim(x, z)) return QUARTZ;
            int dx = garden.dx(x), dz = garden.dz(z);
            if (y == -4) return dx % 16 == 8 && dz <= 1 ? LIGHT : "minecraft:clay";
            if (y == -3 && dz >= 6 && Math.floorMod(x * 31 + z * 17, 13) < 3) return "minecraft:seagrass";
            return WATER;
        }
        for (Landscape landscape : local(x, z).landscapes) if (landscape.contains(x, z)) {
            String block = landscape.foundation(x, y, z);
            if (block != null) return block;
        }
        if (!edgeColumn(x, z)) return null;
        return y == -1 || y == -7 ? QUARTZ : GRAY;
    }
    private static boolean edgeColumn(int x, int z) {
        return hasTerrain(x, z) && (!hasTerrain(x - 1, z) || !hasTerrain(x + 1, z)
                || !hasTerrain(x, z - 1) || !hasTerrain(x, z + 1));
    }
    private static String accent(String theme) {
        return switch (theme) {
            case "chemistry", "computation" -> CYAN;
            case "metallurgy" -> WARM;
            default -> STONE;
        };
    }
    private static String aeSurface(int x, int z) {
        int ax = fold(x), az = fold(z), r = Math.max(ax, az);
        if (Math.abs(ax - 24) <= 1 && az <= 1 || Math.abs(az - 24) <= 1 && ax <= 1)
            return ax == 24 && az == 0 || az == 24 && ax == 0 ? LIGHT : GRAY;
        if (r == 78 || r == 79) return QUARTZ;
        if (ax <= 7 || az <= 7) return ax == 7 || az == 7 ? STONE : PALE;
        if (r <= 15) return r == 15 ? QUARTZ : PALE;
        if (r >= 67) return GREEN;
        if (ax >= 20 && ax <= 61 && az >= 20 && az <= 61)
            return ax == 20 || ax == 61 || az == 20 || az == 61 ? QUARTZ : PALE;
        if ((ax == 16 || az == 16) && r < 64) return CYAN;
        if (ax >= 64 && ax <= 65 || az >= 64 && az <= 65) return STONE;
        return PALE;
    }
    private static Bounds courtBounds(Plot plot) {
        return new Bounds(plot.x - 8, plot.z - 12, plot.maxX() + 8, plot.maxZ() + 8);
    }
    /** Reflect local garden axes when a whole rectangular reservation runs east-west. */
    private static String transpose(String state) {
        if (state == null) return null;
        state = CampusLandscape.swapHorizontalAxis(state);
        int start = state.indexOf("facing=");
        if (start < 0) return state;
        start += 7; int end = state.indexOf(',', start);
        if (end < 0) end = state.indexOf(']', start);
        String facing = switch (state.substring(start, end)) {
            case "north" -> "west"; case "south" -> "east";
            case "east" -> "south"; case "west" -> "north";
            default -> state.substring(start, end);
        };
        return state.substring(0, start) + facing + state.substring(end);
    }
    private static boolean frameEdge(Plot plot, int x, int z) {
        return x >= plot.x - 1 && x <= plot.maxX() + 1 && z >= plot.z - 1 && z <= plot.maxZ() + 1
                && (x == plot.x - 1 || x == plot.maxX() + 1 || z == plot.z - 1 || z == plot.maxZ() + 1);
    }
    private static String streetSurface(Street street, int x, int z, List<Street> nearby) {
        int d = street.distance(x, z), half = street.halfWidth, carriage = half - 7;
        if (half <= 4) return d == half - 1 ? STONE : PALE;
        if (d >= half - 3) return GREEN;
        if (d >= half - 6) return Math.floorMod(fold(street.along(x, z)), 8) == 0 ? STONE : PALE;
        if (d == half - 7) return STONE;
        boolean junction = false, crosswalk = false, stop = false;
        for (Street other : nearby) if (other.halfWidth > 4 && other.vertical != street.vertical && other.contains(x, z)) {
            int distance = other.distance(x, z), otherCarriage = other.halfWidth - 7;
            if (distance < otherCarriage) junction = true;
            if (distance >= otherCarriage + 1 && distance <= otherCarriage + 3) crosswalk = true;
            if (distance == otherCarriage + 5) stop = true;
        }
        if (junction) return BLACK;
        if (crosswalk && d < carriage - 1) return (d & 1) == 0 ? WHITE : BLACK;
        if (stop && d >= 2 && d < carriage - 1) return WHITE;
        if (d == carriage - 1) return WHITE;
        if (d == 1 && half >= 16) return YELLOW;
        if (d == 0 && half < 16 && fold(street.along(x, z)) % 12 < 6) return WHITE;
        return BLACK;
    }
    public static String blockAboveAt(PlotSettings settings, int x, int y, int z) {
        if (y <= 0) return null;
        Local local = local(x, z);
        if (local == null || !hasTerrain(x, z)) return null;
        for (Plot plot : local.plots) if (plot.contains(x, z) || frameEdge(plot, x, z)) {
            if (x == plot.controllerX && z == plot.controllerZ && y == plot.controllerY) return LIGHT;
            if (settings != null && settings.showMachineFrames() && y <= plot.height + 1) {
                boolean xe = x == plot.x - 1 || x == plot.maxX() + 1, ze = z == plot.z - 1 || z == plot.maxZ() + 1;
                if (xe && ze || y == plot.height + 1 && (xe || ze)) return y == 1 || y == plot.height + 1 ? QUARTZ : PALE;
            }
            return null;
        }
        for (Plot plot : local.plots) if (courtBounds(plot).contains(x, z)) return courtyardDecoration(plot, x, y, z);
        if (AE_BOUNDS.contains(x, z)) return null;
        for (Workshop pad : Holder.PLAN.workshops) if (pad.contains(x, z)) return null;
        for (Landscape landscape : local.landscapes) if (landscape.contains(x, z))
            return landscape.above(x, y, z);
        for (Lamp lamp : local.lamps) {
            int dx = x - lamp.x, dz = z - lamp.z;
            int along = lamp.vertical ? dz : dx, across = (lamp.vertical ? dx : dz) * lamp.inward;
            if (dx == 0 && dz == 0) {
                if (y == 1) return STONE;
                if (y == 2) return QUARTZ;
                if (y <= 7) return "minecraft:polished_blackstone_brick_wall";
                if (y == 8) return SLAB;
            }
            if (y == 1 && Math.abs(dx) + Math.abs(dz) == 1) return STONE_SLAB;
            if (along == 0 && across >= 1 && across <= 3) {
                if (y == 8) return SLAB;
                if (across == 1 && y == 7) return stairs(lamp.vertical ? (lamp.inward > 0 ? "east" : "west") : (lamp.inward > 0 ? "south" : "north"));
            }
            // Slim three-panel luminaire with a stepped canopy and lower shade.
            if (across == 3 && Math.abs(along) <= 1) {
                if (y == 8) return STONE_SLAB;
                if (y == 7) return LIGHT;
                if (y == 6) return "minecraft:polished_andesite_slab[type=top]";
            }
        }
        for (Tree tree : local.trees) {
            String block = treeBlock(x - tree.x, y, z - tree.z, CampusLandscape.treeSeed(tree.x, tree.z, 31));
            if (block != null) return block;
        }
        if (y == 1) {
            for (Garden garden : AE_GARDENS) {
                if (garden.rim(x, z)) return SLAB;
                int dx = garden.dx(x), dz = garden.dz(z);
                if (dx >= 10 && dx <= 17 && dz == 21) return SLAB;
                if (dx == 9 && dz == 21) return stairs(x < garden.x ? "east" : "west");
                if (dx == 18 && dz == 21) return stairs(x < garden.x ? "west" : "east");
            }
            // A low flowering hedge gives empty lawns an intentional edge, never a rockery.
            Street street = streetAt(local, x, z);
            if (street != null && street.halfWidth > 4 && street.distance(x, z) == street.halfWidth - 2
                    && fold(street.along(x, z)) % 32 >= 7 && fold(street.along(x, z)) % 32 <= 23
                    && awayFromJunction(local, x, z, street, 5))
                return fold(street.along(x, z)) % 8 == 0 ? FLOWER : HEDGE;
        }
        if (edgeColumn(x, z) && y <= 2) return y == 1 ? QUARTZ : SLAB;
        if (y <= CampusGroundCover.maxHeight()) for (Garden garden : AE_GARDENS) {
            if (!garden.contains(x, z)) continue;
            if (garden.pond(x, z)) {
                return !garden.rim(x, z) && y == 1 && garden.dz(z) >= 7
                        && Math.floorMod(x * 19 + z * 29, 41) == 0 ? "minecraft:lily_pad" : null;
            }
            int dx = garden.dx(x), dz = garden.dz(z);
            if (dz >= 20 && dz <= 22 && dx >= 8 && dx <= 19) return null;
            if (!GREEN.equals(gardenSurface(x, z))) return null;
            for (Tree tree : local.trees) if (treeBlock(x - tree.x, 1, z - tree.z,
                    CampusLandscape.treeSeed(tree.x, tree.z, 31)) != null
                    || treeBlock(x - tree.x, 2, z - tree.z, CampusLandscape.treeSeed(tree.x, tree.z, 31)) != null) return null;
            return CampusGroundCover.above(x, y, z, garden.x ^ garden.z, 2);
        }
        return null;
    }
    private static String treeBlock(int dx, int y, int dz, int seed) {
        return CampusTreeShapes.above(dx, y, dz, seed, false);
    }
    private static boolean frontTree(Plot plot, int x, int z) {
        return plot.width >= 96 && Math.abs(z - (plot.z - 6)) <= 2
                && (Math.abs(x - (plot.x + 6)) <= 2 || Math.abs(x - (plot.maxX() - 6)) <= 2);
    }
    /** Low planting frames a generous entrance without consuming the player's machine envelope. */
    private static String courtyardDecoration(Plot plot, int x, int y, int z) {
        if (frontTree(plot, x, z)) {
            int treeX = x < plot.signX() ? plot.x + 6 : plot.maxX() - 6;
            return treeBlock(x - treeX, y, z - (plot.z - 6), CampusLandscape.treeSeed(treeX, plot.z - 6, 17));
        }
        if (y != 1) return null;
        if ((x == plot.x - 3 || x == plot.maxX() + 3) && z >= plot.z + 3 && z <= plot.maxZ() - 3
                && Math.floorMod(z - plot.z, 16) < 10) return Math.floorMod(z - plot.z, 4) == 0 ? FLOWER : HEDGE;
        if (z == plot.maxZ() + 3 && x >= plot.x + 3 && x <= plot.maxX() - 3
                && Math.floorMod(x - plot.x, 16) < 10) return Math.floorMod(x - plot.x, 4) == 0 ? FLOWER : HEDGE;
        if (Math.abs(x - plot.signX()) <= 4) return null;
        if (z == plot.z - 6 && x >= plot.x + 2 && x <= plot.maxX() - 2
                && Math.floorMod(x - plot.x, 16) < 10) return Math.floorMod(x - plot.x, 4) == 0 ? FLOWER : HEDGE;
        return null;
    }
    private static String stairs(String facing) { return "minecraft:quartz_stairs[facing=" + facing + "]"; }
    public static int maxDecorationYAt(int x, int z) { return maxDecorationYAt(null, x, z); }
    public static int maxDecorationYAt(PlotSettings settings, int x, int z) {
        Local local = local(x, z);
        if (local == null || !hasTerrain(x, z)) return 0;
        for (Plot plot : local.plots) if (plot.contains(x, z) || frameEdge(plot, x, z))
            return frameEdge(plot, x, z) ? settings != null && settings.showMachineFrames() ? plot.height + 1 : 0
                    : x == plot.controllerX && z == plot.controllerZ ? plot.controllerY : 0;
        if (AE_BOUNDS.contains(x, z)) return 0;
        for (Workshop pad : Holder.PLAN.workshops) if (pad.contains(x, z)) return 0;
        int top = 0;
        for (Plot plot : local.plots) if (courtBounds(plot).contains(x, z)) {
            if (frontTree(plot, x, z)) top = Math.max(top, CampusTreeShapes.maxHeight());
            else if (courtyardDecoration(plot, x, 1, z) != null) top = Math.max(top, 1);
        }
        for (Landscape landscape : local.landscapes) if (landscape.contains(x, z)) top = Math.max(top, landscape.top(x, z));
        for (Tree tree : local.trees) if (Math.abs(x - tree.x) <= 2 && Math.abs(z - tree.z) <= 2)
            top = Math.max(top, CampusTreeShapes.top(x - tree.x, z - tree.z, CampusLandscape.treeSeed(tree.x, tree.z, 31), false));
        for (Lamp lamp : local.lamps) if (Math.abs(x - lamp.x) <= 3 && Math.abs(z - lamp.z) <= 3) top = Math.max(top, 8);
        if (blockAboveAt(settings, x, 2, z) != null) top = Math.max(top, 2);
        if (blockAboveAt(settings, x, 1, z) != null) top = Math.max(top, 1);
        return top;
    }
    public static int previewColorAt(int x, int z) {
        return switch (surfaceBlock(null, x, z)) {
            case WHITE -> 0xffd8dad5;
            case PALE -> 0xffa5aaa8;
            case GRAY -> 0xff606668;
            case BLACK -> 0xff252a2d;
            case GREEN -> 0xff728354;
            case YELLOW -> 0xffc7ae57;
            case CYAN -> 0xff4d8d93;
            case WATER -> 0xff477d9b;
            case LIGHT -> 0xffcee5df;
            case QUARTZ -> 0xffe1dfd7;
            case STONE -> 0xff7f8585;
            case WARM -> 0xffa77862;
            case "minecraft:air" -> 0xff162431;
            default -> 0xff66784e;
        };
    }
    private static Local local(int x, int z) { return Holder.PLAN.index.get(key(Math.floorDiv(x, TILE), Math.floorDiv(z, TILE))); }
    private static long key(int x, int z) {
        long value = (long) x << 32 ^ (z & 0xffffffffL);
        value = (value ^ (value >>> 33)) * 0xff51afd7ed558ccdL;
        value = (value ^ (value >>> 33)) * 0xc4ceb9fe1a85ec53L;
        return value ^ (value >>> 33);
    }
    private static JsonObject catalog() {
        var stream = PlannedCampus.class.getResourceAsStream(CATALOG);
        if (stream == null) throw new IllegalStateException("Missing planned campus machine catalog");
        try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        } catch (java.io.IOException error) { throw new IllegalStateException("Cannot load campus catalog", error); }
    }
    private static List<Machine> machines(JsonObject catalog) {
        List<Machine> result = new ArrayList<>();
        Set<String> ids = new LinkedHashSet<>();
        for (var element : catalog.getAsJsonArray("reservations")) {
            JsonObject json = element.getAsJsonObject();
            var size = json.getAsJsonArray("size"); var anchor = json.getAsJsonArray("groundOffset");
            String id = json.get("id").getAsString(), stage = json.get("stage").getAsString();
            int w = size.get(0).getAsInt(), h = size.get(1).getAsInt(), d = size.get(2).getAsInt();
            int cx = anchor.get(0).getAsInt(), cy = anchor.get(1).getAsInt(), cz = anchor.get(2).getAsInt();
            if (!ids.add(id) || w < 1 || h < 1 || d < 1 || cx < 0 || cx >= w || cy < 0 || cy >= h || cz < 0 || cz >= d
                    || !json.get("frontFacing").getAsString().equals("north")) throw new IllegalArgumentException("Invalid planned machine " + id);
            int tier = json.get("placementTier").getAsInt();
            int copies = json.get("copies").getAsInt();
            if (copies < 1 || copies > 16 || tier < 0 || tier > 14) throw new IllegalArgumentException("Invalid campus allocation " + id);
            for (int slot = 0; slot < copies; slot++) result.add(new Machine(id, stage, tier, w, h, d, cx, cy, cz,
                    json.get("patternResource").getAsString(), slot, json.get("theme").getAsString(),
                    json.has("displayNameKey") ? json.get("displayNameKey").getAsString() : ""));
        }
        return List.copyOf(result);
    }
    private static Bounds expand(Bounds bounds, int margin) {
        return new Bounds(bounds.minX - margin, bounds.minZ - margin, bounds.maxX + margin, bounds.maxZ + margin);
    }
    private static Bounds streetBounds(Street street) {
        return street.vertical ? new Bounds(street.axis - street.halfWidth, street.start, street.axis + street.halfWidth - 1, street.end)
                : new Bounds(street.start, street.axis - street.halfWidth, street.end, street.axis + street.halfWidth - 1);
    }
    private static boolean intersects(Bounds a, Bounds b) {
        return a.minX <= b.maxX && a.maxX >= b.minX && a.minZ <= b.maxZ && a.maxZ >= b.minZ;
    }
    private static boolean encloses(Bounds outer, Bounds inner) {
        return outer.minX <= inner.minX && outer.minZ <= inner.minZ && outer.maxX >= inner.maxX && outer.maxZ >= inner.maxZ;
    }
    /** Maximal empty rectangles retain both dimensions; no machine borrows another machine's depth. */
    private static List<Bounds> subtract(List<Bounds> free, Bounds used) {
        Set<Bounds> fragments = new LinkedHashSet<>();
        for (Bounds bounds : free) {
            if (!intersects(bounds, used)) { fragments.add(bounds); continue; }
            if (used.minX > bounds.minX) fragments.add(new Bounds(bounds.minX, bounds.minZ, used.minX - 1, bounds.maxZ));
            if (used.maxX < bounds.maxX) fragments.add(new Bounds(used.maxX + 1, bounds.minZ, bounds.maxX, bounds.maxZ));
            if (used.minZ > bounds.minZ) fragments.add(new Bounds(bounds.minX, bounds.minZ, bounds.maxX, used.minZ - 1));
            if (used.maxZ < bounds.maxZ) fragments.add(new Bounds(bounds.minX, used.maxZ + 1, bounds.maxX, bounds.maxZ));
        }
        List<Bounds> result = new ArrayList<>(fragments);
        result.removeIf(inner -> fragments.stream().anyMatch(outer -> !outer.equals(inner) && encloses(outer, inner)));
        return result;
    }
    private static void serviceRing(List<Street> streets, Bounds court, int north, int side, int south) {
        addStreet(streets, new Street(false, court.minZ + north / 2, court.minX, court.maxX, north / 2));
        addStreet(streets, new Street(true, court.minX + side / 2, court.minZ, court.maxZ, side / 2));
        addStreet(streets, new Street(true, court.maxX + 1 - side / 2, court.minZ, court.maxZ, side / 2));
        addStreet(streets, new Street(false, court.maxZ + 1 - south / 2, court.minX, court.maxX, south / 2));
    }
    /** Largest coherent gardens first; unlike maximal packing spaces, these rectangles never overlap. */
    private static List<Bounds> gardenRectangles(List<Bounds> free) {
        List<Bounds> pending = new ArrayList<>(free), gardens = new ArrayList<>();
        Comparator<Bounds> order = Comparator.comparingInt((Bounds bounds) -> bounds.width() * bounds.depth()).reversed()
                .thenComparing(Comparator.comparingInt((Bounds bounds) -> Math.min(bounds.width(), bounds.depth())).reversed())
                .thenComparingInt(Bounds::minX).thenComparingInt(Bounds::minZ);
        while (!pending.isEmpty()) {
            Bounds garden = pending.stream().min(order).orElseThrow();
            gardens.add(garden); pending = subtract(pending, garden);
        }
        return List.copyOf(gardens);
    }
    private static List<Street> backbone(int half, List<Workshop> pads) {
        List<Street> streets = new ArrayList<>();
        // Every boulevard is a 64-block public reservation: road, planted belts and four-block outer walks.
        for (int axis : new int[] {0, -400, 400}) {
            int roadHalf = axis == 0 ? 16 : 12;
            addStreet(streets, new Street(true, axis, -half, half - 1, roadHalf));
            addStreet(streets, new Street(false, axis, -half, half - 1, roadHalf));
            for (int edge : new int[] {axis - 30, axis + 30}) {
                addStreet(streets, new Street(true, edge, -half, half - 1, 2));
                addStreet(streets, new Street(false, edge, -half, half - 1, 2));
            }
        }
        for (int axis : new int[] {-half + 12, half - 12}) {
            addStreet(streets, new Street(true, axis, -half, half - 1, 12));
            addStreet(streets, new Street(false, axis, -half, half - 1, 12));
        }
        for (Workshop pad : pads) {
            int axis = pad.z < 0 ? pad.z + pad.depth + 2 : pad.z - 2;
            addStreet(streets, new Street(false, axis, Math.min(0, pad.x - 4), Math.max(0, pad.x + pad.width + 3), 2));
        }
        serviceRing(streets, expand(AE_BOUNDS, 4), 4, 4, 4);
        for (Workshop pad : pads) serviceRing(streets, expand(new Bounds(pad.x, pad.z, pad.x + pad.width - 1, pad.z + pad.depth - 1), 4), 4, 4, 4);
        for (Garden garden : AE_GARDENS) serviceRing(streets, expand(garden.bounds(), 4), 4, 4, 4);
        return streets;
    }
    private static List<Bounds> greenBuffers(int half) {
        List<Bounds> buffers = new ArrayList<>();
        for (int axis : new int[] {0, -400, 400}) {
            buffers.add(new Bounds(axis - 32, -half + 24, axis + 31, half - 25));
            buffers.add(new Bounds(-half + 24, axis - 32, half - 25, axis + 31));
        }
        return buffers;
    }
    private static List<Bounds> coreReservations(List<Workshop> pads) {
        List<Bounds> core = new ArrayList<>(); core.add(expand(AE_BOUNDS, 4));
        for (Workshop pad : pads) core.add(expand(new Bounds(pad.x, pad.z, pad.x + pad.width - 1, pad.z + pad.depth - 1), 4));
        for (Garden garden : AE_GARDENS) core.add(expand(garden.bounds(), 4));
        return core;
    }
    /** Progression changes the actual allowed radius, rather than leaving all machines next to AE. */
    private static int minimumRadius(int tier) {
        return switch (tier) {
            case 4 -> 160; case 5 -> 180; case 6 -> 200; case 7 -> 220; case 8 -> 240;
            case 9 -> 400; case 10 -> 450; case 11 -> 500; case 12 -> 570; case 13 -> 640; case 14 -> 700;
            default -> 0;
        };
    }
    private static boolean better(Candidate a, Candidate b) {
        if (b == null) return true;
        if (a.radius != b.radius) return a.radius < b.radius;
        if (a.taxi != b.taxi) return a.taxi < b.taxi;
        if (a.slack != b.slack) return a.slack < b.slack;
        if (a.waste != b.waste) return a.waste < b.waste;
        return a.court.minX != b.court.minX ? a.court.minX < b.court.minX : a.court.minZ < b.court.minZ;
    }
    private static boolean better(Connection a, Connection b) {
        if (b == null) return true;
        if (a.length != b.length) return a.length < b.length;
        if (a.orientation != b.orientation) return a.orientation < b.orientation;
        if (a.edge != b.edge) return a.edge < b.edge;
        return a.target < b.target;
    }
    /** A free rectangle edge meets a previously connected court or road; join only the nearest such contact. */
    private static Connection connect(Bounds court, Bounds free, List<Street> streets) {
        Connection best = null;
        for (int side = 0; side < 4; side++) {
            boolean vertical = side < 2, low = side == 0 || side == 2;
            int edge = vertical ? (low ? court.minX : court.maxX) : low ? court.minZ : court.maxZ;
            int freeEdge = vertical ? (low ? free.minX : free.maxX) : low ? free.minZ : free.maxZ;
            if (edge != freeEdge) continue;
            int courtStart = vertical ? court.minZ : court.minX, courtEnd = vertical ? court.maxZ : court.maxX;
            int freeStart = vertical ? free.minZ : free.minX, freeEnd = vertical ? free.maxZ : free.maxX;
            for (Street street : streets) {
                Bounds road = streetBounds(street);
                int contact = vertical ? (low ? road.maxX : road.minX) : low ? road.maxZ : road.minZ;
                if (contact != edge + (low ? -1 : 1)) continue;
                int start = Math.max(freeStart, vertical ? road.minZ : road.minX);
                int end = Math.min(freeEnd, vertical ? road.maxZ : road.maxX);
                if (start > end) continue;
                int target = Math.max(start, Math.min(end, courtStart));
                int origin = Math.max(courtStart, Math.min(courtEnd, target)), length = Math.abs(target - origin);
                Street branch = length == 0 ? null : new Street(vertical, edge + (low ? 2 : -1), Math.min(target, origin), Math.max(target, origin), 2);
                Connection candidate = new Connection(branch, length, vertical ? 0 : 1, edge, target);
                if (better(candidate, best)) best = candidate;
            }
        }
        if (best == null) throw new IllegalStateException("Campus court has no connected packing edge");
        return best;
    }
    private static Packing pack(List<Machine> machines, List<Workshop> pads, int half) {
        List<Street> streets = backbone(half, pads);
        List<Bounds> buffers = greenBuffers(half), core = coreReservations(pads);
        List<Bounds> free = new ArrayList<>(List.of(new Bounds(-half + 24, -half + 24, half - 25, half - 25)));
        for (Bounds buffer : buffers) free = subtract(free, buffer);
        for (Street street : streets) free = subtract(free, streetBounds(street));
        for (Bounds reservation : core) free = subtract(free, reservation);
        List<Bounds> publicGreen = new ArrayList<>(buffers);
        for (Street street : streets) publicGreen = subtract(publicGreen, streetBounds(street));
        for (Bounds reservation : core) publicGreen = subtract(publicGreen, reservation);
        List<Placement> placements = new ArrayList<>();
        for (Machine machine : machines) {
            int width = machine.width + 16, depth = machine.depth + 20; Candidate best = null;
            for (Bounds space : free) {
                if (width > space.width() || depth > space.depth()) continue;
                for (int x : new int[] {space.minX, space.maxX - width + 1}) for (int z : new int[] {space.minZ, space.maxZ - depth + 1}) {
                    int cx = Math.abs(2 * x + width - 1), cz = Math.abs(2 * z + depth - 1);
                    if (Math.max(cx, cz) < 2 * minimumRadius(machine.tier)) continue;
                    Candidate candidate = new Candidate(new Bounds(x, z, x + width - 1, z + depth - 1), space, Math.max(cx, cz), cx + cz,
                            Math.min(space.width() - width, space.depth() - depth), space.width() * space.depth() - width * depth);
                    if (better(candidate, best)) best = candidate;
                }
            }
            if (best == null) return null;
            Connection connection = connect(best.court, best.free, streets);
            free = subtract(free, best.court);
            if (connection.street != null) { addStreet(streets, connection.street); free = subtract(free, streetBounds(connection.street)); }
            serviceRing(streets, best.court, 4, 4, 4);
            placements.add(new Placement(machine, best.court));
        }
        List<Bounds> greenways = gardenRectangles(publicGreen), residuals = new ArrayList<>(greenways);
        residuals.addAll(gardenRectangles(free));
        return new Packing(List.copyOf(placements), List.copyOf(streets), List.copyOf(residuals), greenways, half, half);
    }
    private static Packing packing(List<Machine> machines, List<Workshop> pads) {
        List<Machine> ordered = machines.stream().sorted(Comparator.comparingInt(Machine::tier)
                .thenComparing(Comparator.comparingInt((Machine machine) -> (machine.width + 16) * (machine.depth + 20)).reversed())
                .thenComparing(Comparator.comparingInt((Machine machine) -> Math.max(machine.width, machine.depth)).reversed())
                .thenComparing(Machine::id).thenComparingInt(Machine::slotIndex)).toList();
        Packing packing = pack(ordered, pads, 1000);
        if (packing != null) return packing;
        throw new IllegalStateException("Campus catalog cannot fit the 2000-block park island");
    }
    private static void placeCourtyard(Placement placement, List<Plot> plots) {
        Machine machine = placement.machine; int x = placement.court.minX + 8, z = placement.court.minZ + 12;
        plots.add(new Plot(machine.id, machine.stage, x, z, machine.width, machine.height, machine.depth,
                x + machine.cx, machine.cy + 1, z + machine.cz, machine.pattern, machine.slotIndex, machine.theme,
                machine.displayNameKey));
    }
    private static void addStreet(List<Street> streets, Street street) { if (!streets.contains(street)) streets.add(street); }
    private static Plan build() {
        JsonObject catalog = catalog(); List<Machine> machines = machines(catalog);
        Map<String, Integer> tiers = new HashMap<>();
        for (var element : catalog.getAsJsonArray("machines")) {
            JsonObject machine = element.getAsJsonObject();
            String stage = machine.get("stage").getAsString();
            tiers.put(machine.get("id").getAsString(), machine.has("planningTier") ? machine.get("planningTier").getAsInt()
                    : stage.equals("early") ? 3 : stage.equals("mid") ? 7 : 12);
        }
        int shared = catalog.getAsJsonArray("effectiveSharedIds").size();
        List<Workshop> pads = List.of(new Workshop("early", -216, -216, 128, 128), new Workshop("early", 88, -216, 128, 128),
                new Workshop("early", -216, 88, 128, 128), new Workshop("early", 88, 88, 128, 128));
        Packing packing = packing(machines, pads);
        Bounds bounds = new Bounds(-packing.halfWidth, -packing.halfDepth, packing.halfWidth - 1, packing.halfDepth - 1);
        // Every interior column belongs to this one continuous island. Only its outside is void.
        List<Bounds> islands = List.of(bounds);
        List<Plot> plots = new ArrayList<>(); List<Street> streets = new ArrayList<>(packing.streets); List<District> districts = new ArrayList<>();
        for (Placement placement : packing.placements) placeCourtyard(placement, plots);
        for (String stage : List.of("early", "mid", "late")) {
            int minZ = plots.stream().filter(plot -> plot.stage.equals(stage)).mapToInt(Plot::z).min().orElseThrow();
            int maxZ = plots.stream().filter(plot -> plot.stage.equals(stage)).mapToInt(Plot::maxZ).max().orElseThrow();
            districts.add(new District(stage, minZ, maxZ));
        }
        Map<Long, List<Plot>> plotIndex = new HashMap<>(); Map<Long, List<Street>> streetIndex = new HashMap<>();
        Map<Long, List<Bounds>> islandIndex = new HashMap<>(); Map<Long, List<Tree>> treeIndex = new HashMap<>();
        Map<Long, List<Lamp>> lampIndex = new HashMap<>(); Map<Long, List<Landscape>> landscapeIndex = new HashMap<>();
        index(islandIndex, bounds, bounds);
        for (Plot plot : plots) index(plotIndex, courtBounds(plot), plot);
        List<Tree> trees = new ArrayList<>(); List<Lamp> lamps = new ArrayList<>(); List<Landscape> landscapes = new ArrayList<>();
        Set<Long> treeKeys = new LinkedHashSet<>(), lampKeys = new LinkedHashSet<>();
        for (Street street : streets) {
            Bounds extent = street.vertical ? new Bounds(street.axis - street.halfWidth, street.start, street.axis + street.halfWidth - 1, street.end)
                    : new Bounds(street.start, street.axis - street.halfWidth, street.end, street.axis + street.halfWidth - 1);
            index(streetIndex, extent, street);
            if (street.halfWidth <= 4) continue;
            int alongLimit = Math.max(fold(street.start), fold(street.end));
            for (int offset = 12; offset <= alongLimit; offset += 24) for (int direction : new int[] {-1, 1}) for (int side : new int[] {-1, 1}) {
                int along = direction > 0 ? offset : -1 - offset;
                if (along < street.start || along > street.end) continue;
                int across = street.axis + (side > 0 ? street.halfWidth - 3 : -street.halfWidth + 2);
                int x = street.vertical ? across : along, z = street.vertical ? along : across;
                if (supported(islands, x, z, 2) && safeGarden(x, z, 2, plots, pads, streets, street) && treeKeys.add(key(x, z))) trees.add(new Tree(x, z));
                int lampAcross = street.axis + (side > 0 ? street.halfWidth - 5 : -street.halfWidth + 4);
                int lampAlong = direction > 0 ? offset - 12 : -1 - (offset - 12);
                int lx = street.vertical ? lampAcross : lampAlong, lz = street.vertical ? lampAlong : lampAcross;
                if (supported(islands, lx, lz, 3) && safeGarden(lx, lz, 3, plots, pads, streets, street) && lampKeys.add(key(lx, lz)))
                    lamps.add(new Lamp(lx, lz, street.vertical, -side));
            }
        }
        for (Garden garden : AE_GARDENS) for (int dx : new int[] {-46, 45}) for (int dz : new int[] {-14, 13}) {
            int x = garden.x + dx, z = garden.z + dz;
            if (safeGarden(x, z, 3, plots, pads, streets, null) && treeKeys.add(key(x, z))) trees.add(new Tree(x, z));
        }
        // The packing residuals are disjoint and already exclude every full machine/service court.
        // Fill their complete area: large connected gardens and narrower continuous planted galleries.
        for (Bounds residual : packing.residuals) reserveLandscape(landscapes, residual, gardenTheme(residual, plots));
        for (Tree tree : trees) index(treeIndex, new Bounds(tree.x - 2, tree.z - 2, tree.x + 2, tree.z + 2), tree);
        for (Lamp lamp : lamps) index(lampIndex, new Bounds(lamp.x - 3, lamp.z - 3, lamp.x + 3, lamp.z + 3), lamp);
        for (Landscape landscape : landscapes) index(landscapeIndex, landscape.bounds, landscape);
        Set<Long> tiles = new LinkedHashSet<>(); tiles.addAll(plotIndex.keySet()); tiles.addAll(streetIndex.keySet());
        tiles.addAll(treeIndex.keySet()); tiles.addAll(lampIndex.keySet()); tiles.addAll(landscapeIndex.keySet()); tiles.addAll(islandIndex.keySet());
        Map<Long, Local> index = new HashMap<>();
        for (long tile : tiles) index.put(tile, new Local(List.copyOf(plotIndex.getOrDefault(tile, List.of())),
                List.copyOf(streetIndex.getOrDefault(tile, List.of())), List.copyOf(treeIndex.getOrDefault(tile, List.of())),
                List.copyOf(lampIndex.getOrDefault(tile, List.of())), List.copyOf(landscapeIndex.getOrDefault(tile, List.of())),
                List.copyOf(islandIndex.getOrDefault(tile, List.of()))));
        return new Plan(List.copyOf(plots), List.copyOf(streets), pads, List.copyOf(districts), Map.copyOf(index), bounds,
                Math.max(CampusLandscape.height(), plots.stream().mapToInt(plot -> plot.height + 2).max().orElseThrow()), shared, Map.copyOf(tiers));
    }
    private static int gardenTheme(Bounds garden, List<Plot> plots) {
        int x = (garden.minX + garden.maxX) / 2, z = (garden.minZ + garden.maxZ) / 2;
        Plot nearest = null; long best = Long.MAX_VALUE;
        for (Plot plot : plots) {
            long dx = Math.max(0, Math.max(plot.x - x, x - plot.maxX()));
            long dz = Math.max(0, Math.max(plot.z - z, z - plot.maxZ()));
            long distance = dx * dx + dz * dz;
            if (distance < best) { best = distance; nearest = plot; }
        }
        return switch (nearest.theme) {
            case "chemistry" -> 0;
            case "computation" -> 2;
            case "metallurgy" -> 3;
            default -> 1;
        };
    }
    private static void reserveLandscape(List<Landscape> gardens, Bounds bounds, int variant) {
        int shortSide = Math.min(bounds.width(), bounds.depth());
        // 12..24-wide gaps become adjacent complete narrow galleries, never a single clipped canopy.
        if (shortSide > 11 && shortSide < 25) {
            if (bounds.width() <= bounds.depth()) {
                int split = bounds.minX + bounds.width() / 2;
                reserveLandscape(gardens, new Bounds(bounds.minX, bounds.minZ, split - 1, bounds.maxZ), variant);
                reserveLandscape(gardens, new Bounds(split, bounds.minZ, bounds.maxX, bounds.maxZ), variant);
            } else {
                int split = bounds.minZ + bounds.depth() / 2;
                reserveLandscape(gardens, new Bounds(bounds.minX, bounds.minZ, bounds.maxX, split - 1), variant);
                reserveLandscape(gardens, new Bounds(bounds.minX, split, bounds.maxX, bounds.maxZ), variant);
            }
            return;
        }
        gardens.add(new Landscape(bounds, variant));
    }
    private static boolean supported(List<Bounds> islands, int x, int z, int radius) {
        for (Bounds island : islands) if (island.contains(x - radius, z - radius) && island.contains(x + radius, z + radius)) return true;
        return false;
    }
    private static boolean awayFromJunction(Local local, int x, int z, Street owner, int margin) {
        for (Street street : local.streets) if (street.vertical != owner.vertical
                && street.distance(x, z) < street.halfWidth + margin && street.along(x, z) >= street.start - margin
                && street.along(x, z) <= street.end + margin) return false;
        return true;
    }
    private static boolean safeGarden(int x, int z, int radius, List<Plot> plots, List<Workshop> pads, List<Street> streets, Street owner) {
        if (x + radius >= AE_BOUNDS.minX && x - radius <= AE_BOUNDS.maxX
                && z + radius >= AE_BOUNDS.minZ && z - radius <= AE_BOUNDS.maxZ) return false;
        for (Plot plot : plots) {
            Bounds court = courtBounds(plot);
            if (x >= court.minX - radius && x <= court.maxX + radius
                    && z >= court.minZ - radius && z <= court.maxZ + radius) return false;
        }
        for (Workshop pad : pads) if (x >= pad.x - radius && x <= pad.x + pad.width - 1 + radius && z >= pad.z - radius && z <= pad.z + pad.depth - 1 + radius) return false;
        for (Street street : streets) if (street != owner && street.distance(x, z) < street.halfWidth + radius
                && street.along(x, z) >= street.start - radius && street.along(x, z) <= street.end + radius) return false;
        return true;
    }
    private static <T> void index(Map<Long, List<T>> map, Bounds bounds, T item) {
        for (int x = Math.floorDiv(bounds.minX, TILE); x <= Math.floorDiv(bounds.maxX, TILE); x++)
            for (int z = Math.floorDiv(bounds.minZ, TILE); z <= Math.floorDiv(bounds.maxZ, TILE); z++)
                map.computeIfAbsent(key(x, z), ignored -> new ArrayList<>()).add(item);
    }
}
