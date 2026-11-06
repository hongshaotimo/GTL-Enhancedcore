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
public final class CompactPlannedCampus {
    public static final String CATALOG = "/data/gtl_enhancedcore/worldgen/planned_campus_machines_v3.json";
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
    private static final int TILE = 64, COURT = 4, CORE_EDGE = 244;
    private static final Bounds AE_BOUNDS = new Bounds(-80, -80, 79, 79);
    private static final List<Garden> AE_GARDENS = List.of(new Garden(-164, -48), new Garden(164, -48),
            new Garden(-164, 48), new Garden(164, 48));
    private static final Set<String> MATERIALS;
    static {
        Set<String> palette = new LinkedHashSet<>(List.of(WHITE, PALE, GRAY, BLACK, GREEN, YELLOW, CYAN,
                WATER, LIGHT, WOOD, LEAF, QUARTZ, SLAB, WARM, STONE, WALL, STONE_SLAB, HEDGE, FLOWER));
        for (String facing : List.of("north", "south", "east", "west"))
            palette.add("minecraft:quartz_stairs[facing=" + facing + "]");
        palette.addAll(CompactCampusLandscape.materials());
        MATERIALS = Set.copyOf(palette);
    }

    public record Bounds(int minX, int minZ, int maxX, int maxZ) {
        public int width() { return maxX - minX + 1; }
        public int depth() { return maxZ - minZ + 1; }
        public boolean contains(int x, int z) { return x >= minX && x <= maxX && z >= minZ && z <= maxZ; }
    }
    /** Exact north-facing machine envelope; the ground border sits one block outside it. */
    public record Plot(String id, String stage, int x, int z, int width, int height, int depth,
                       int controllerX, int controllerY, int controllerZ, String patternResource, int slotIndex, String theme) {
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
                           int cx, int cy, int cz, String pattern, int slotIndex, String theme) {}
    private record Pair(Machine right, Machine left, int width, int depth) {}
    private record Column(List<Pair> pairs, int width) {}
    private record Row(List<Column> columns, int width, int depth) {}
    private record Shelf(Row row, boolean south, int near, int startX) {}
    private record Packing(List<Shelf> shelves, int halfWidth, int halfDepth) {}
    private record Street(boolean vertical, int axis, int start, int end, int halfWidth) {
        int perpendicular(int x, int z) { return vertical ? x : z; }
        int along(int x, int z) { return vertical ? z : x; }
        int distance(int x, int z) { return fold(perpendicular(x, z) - axis); }
        boolean contains(int x, int z) { int along = along(x, z); return along >= start && along <= end && distance(x, z) < halfWidth; }
    }
    private record Tree(int x, int z) {}
    private record Lamp(int x, int z, boolean vertical, int inward) {}
    private record Landscape(int x, int z, int variant) {
        boolean contains(int px, int pz) { return Math.abs(px - x) <= CompactCampusLandscape.radius() && Math.abs(pz - z) <= CompactCampusLandscape.radius(); }
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

    private CompactPlannedCampus() {}
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
            if (x >= plot.x - COURT && x <= plot.maxX() + COURT && z >= plot.z - COURT && z <= plot.maxZ() + COURT) {
                if (z < plot.z && Math.abs(x - plot.signX()) <= 2) return PALE;
                if (x < plot.x - 2 || x > plot.maxX() + 2 || z > plot.maxZ() + 2) return GREEN;
                return (x == plot.x - 2 || x == plot.maxX() + 2 || z == plot.maxZ() + 2) ? accent(plot.theme) : PALE;
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
            String surface = CompactCampusLandscape.surface(x - landscape.x, z - landscape.z, landscape.variant);
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
        if (y == -1) for (Garden garden : AE_GARDENS) if (garden.pond(x, z))
            return (garden.dx(x) % 16 == 8 && garden.dz(z) <= 1) ? LIGHT : QUARTZ;
        for (Landscape landscape : local(x, z).landscapes) if (landscape.contains(x, z)) {
            String block = CompactCampusLandscape.foundation(x - landscape.x, y, z - landscape.z, landscape.variant);
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
        if (AE_BOUNDS.contains(x, z)) return null;
        for (Workshop pad : Holder.PLAN.workshops) if (pad.contains(x, z)) return null;
        for (Landscape landscape : local.landscapes) if (landscape.contains(x, z))
            return CompactCampusLandscape.above(x - landscape.x, y, z - landscape.z, landscape.variant);
        for (Lamp lamp : local.lamps) {
            int dx = x - lamp.x, dz = z - lamp.z;
            int along = lamp.vertical ? dz : dx, across = (lamp.vertical ? dx : dz) * lamp.inward;
            if (dx == 0 && dz == 0) {
                if (y == 1) return STONE;
                if (y <= 7) return WALL;
                if (y == 8) return SLAB;
            }
            if (y == 1 && Math.abs(dx) + Math.abs(dz) == 1) return STONE_SLAB;
            if (along == 0 && across >= 1 && across <= 3) {
                if (y == 8) return SLAB;
                if (across == 1 && y == 7) return stairs(lamp.vertical ? (lamp.inward > 0 ? "east" : "west") : (lamp.inward > 0 ? "south" : "north"));
                if (across == 3 && y == 7) return LIGHT;
            }
        }
        for (Tree tree : local.trees) {
            int dx = Math.abs(x - tree.x), dz = Math.abs(z - tree.z);
            if (dx == 0 && dz == 0 && y <= 9) return WOOD;
            int radius = y == 12 ? 1 : y == 7 || y == 11 ? 2 : 3;
            if (y >= 7 && y <= 12 && dx <= radius && dz <= radius && dx + dz <= radius + 1) return LEAF;
            if (y == 1 && Math.max(dx, dz) == 2) return SLAB;
            if (y == 1 && Math.max(dx, dz) < 2 && dx + dz > 0) return HEDGE;
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
        for (Landscape landscape : local.landscapes) if (landscape.contains(x, z)) top = Math.max(top, CompactCampusLandscape.height());
        for (Tree tree : local.trees) if (Math.abs(x - tree.x) <= 3 && Math.abs(z - tree.z) <= 3) top = Math.max(top, 12);
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
        var stream = CompactPlannedCampus.class.getResourceAsStream(CATALOG);
        if (stream == null) throw new IllegalStateException("Missing planned campus machine catalog");
        try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        } catch (java.io.IOException error) { throw new IllegalStateException("Cannot load campus catalog", error); }
    }
    private static List<Machine> machines(JsonObject catalog) {
        List<Machine> result = new ArrayList<>();
        Set<String> ids = new LinkedHashSet<>();
        for (var element : catalog.getAsJsonArray("machines")) {
            JsonObject json = element.getAsJsonObject();
            var size = json.getAsJsonArray("size"); var anchor = json.getAsJsonArray("groundOffset");
            String id = json.get("id").getAsString(), stage = json.get("stage").getAsString();
            int w = size.get(0).getAsInt(), h = size.get(1).getAsInt(), d = size.get(2).getAsInt();
            int cx = anchor.get(0).getAsInt(), cy = anchor.get(1).getAsInt(), cz = anchor.get(2).getAsInt();
            if (!ids.add(id) || w < 1 || h < 1 || d < 1 || cx < 0 || cx >= w || cy < 0 || cy >= h || cz < 0 || cz >= d
                    || !json.get("frontFacing").getAsString().equals("north")) throw new IllegalArgumentException("Invalid planned machine " + id);
            if (json.has("allocation") && json.get("allocation").getAsString().equals("shared")) continue;
            int tier = json.has("planningTier") ? json.get("planningTier").getAsInt() : stage.equals("early") ? 3 : stage.equals("mid") ? 7 : 12;
            int copies = json.get("copies").getAsInt();
            if (copies < 1 || copies > 16 || tier < 0 || tier > 14) throw new IllegalArgumentException("Invalid campus allocation " + id);
            for (int slot = 0; slot < copies; slot++) result.add(new Machine(id, stage, tier, w, h, d, cx, cy, cz,
                    json.get("patternResource").getAsString(), slot, json.get("theme").getAsString()));
        }
        return List.copyOf(result);
    }
    private static int phase(Machine machine) {
        return machine.tier / 2;
    }
    private static int pairTier(Pair pair) { return Math.min(pair.right.tier, pair.left == null ? pair.right.tier : pair.left.tier); }
    private static int courtSize(int size) { return (size + 2 * COURT + 3) / 4 * 4; }
    private static List<Pair> pairs(List<Machine> machines) {
        List<Pair> result = new ArrayList<>();
        // Only adjacent voltage levels can share a mirrored pair; dimensions never promote an IV pad before ULV.
        for (int phase = 0; phase < 8; phase++) {
            final int group = phase;
            List<Machine> ordered = machines.stream().filter(machine -> phase(machine) == group)
                    .sorted(Comparator.comparingInt(Machine::depth).reversed()
                            .thenComparing(Comparator.comparingInt(Machine::width).reversed())
                            .thenComparingInt(Machine::tier).thenComparing(Machine::id).thenComparingInt(Machine::slotIndex)).toList();
            for (int i = 0; i < ordered.size(); i += 2) {
                Machine right = ordered.get(i), left = i + 1 < ordered.size() ? ordered.get(i + 1) : null;
                result.add(new Pair(right, left, courtSize(Math.max(right.width, left == null ? 0 : left.width)),
                        courtSize(Math.max(right.depth, left == null ? 0 : left.depth))));
            }
        }
        result.sort(Comparator.comparingInt(CompactPlannedCampus::pairTier)
                .thenComparing(Comparator.comparingInt(Pair::depth).reversed()).thenComparing(Comparator.comparingInt(Pair::width).reversed())
                .thenComparing(pair -> pair.right.id).thenComparingInt(pair -> pair.right.slotIndex));
        return List.copyOf(result);
    }
    private static Packing pack(List<Pair> pairs, int halfWidth) {
        List<Pair> pending = new ArrayList<>(pairs); List<Shelf> shelves = new ArrayList<>();
        int north = 16, south = 16;
        while (!pending.isEmpty()) {
            boolean toSouth = south < north;
            int near = toSouth ? south : north, startX = near < CORE_EDGE ? CORE_EDGE : 28;
            int available = halfWidth - 24 - startX, width = 0, depth = pending.get(0).depth, firstTier = pairTier(pending.get(0));
            List<Pair> row = new ArrayList<>();
            while (true) {
                int chosen = -1;
                for (int i = 0; i < pending.size(); i++) {
                    Pair pair = pending.get(i);
                    // Do not let a deeper next-stage machine turn a shallow populated row into a large empty lawn.
                    if (pairTier(pair) <= firstTier + 3 && pair.depth <= depth && width + pair.width <= available) {
                        chosen = i; break;
                    }
                }
                if (chosen < 0) break;
                Pair pair = pending.remove(chosen); width += pair.width; row.add(pair);
            }
            if (row.isEmpty()) throw new IllegalArgumentException("Campus wing cannot fit its largest machine");
            List<Column> columns = new ArrayList<>();
            for (Pair seed : row) {
                List<Pair> stack = new ArrayList<>(); stack.add(seed); int used = seed.depth;
                while (true) {
                    int chosen = -1;
                    for (int i = 0; i < pending.size(); i++) {
                        Pair candidate = pending.get(i);
                        if (pairTier(candidate) <= firstTier + 3 && candidate.width <= seed.width && used + candidate.depth <= depth
                                && (chosen < 0 || pairTier(candidate) < pairTier(pending.get(chosen))
                                || pairTier(candidate) == pairTier(pending.get(chosen))
                                && candidate.width * candidate.depth > pending.get(chosen).width * pending.get(chosen).depth)) chosen = i;
                    }
                    if (chosen < 0) break;
                    Pair next = pending.remove(chosen); stack.add(next); used += next.depth;
                }
                stack.sort(Comparator.comparingInt(CompactPlannedCampus::pairTier));
                columns.add(new Column(List.copyOf(stack), seed.width));
            }
            columns.sort(Comparator.comparingInt(column -> pairTier(column.pairs.get(0))));
            shelves.add(new Shelf(new Row(List.copyOf(columns), width, depth), toSouth, near, startX));
            if (toSouth) south = near + depth + 24; else north = near + depth + 24;
        }
        return new Packing(List.copyOf(shelves), halfWidth, Math.max(CORE_EDGE, Math.max(north, south)));
    }
    private static Packing packing(List<Machine> machines) {
        List<Pair> pairs = pairs(machines); Packing best = null; double bestScore = Double.MAX_VALUE;
        // A single width applies to the whole campus; empty tier strips never dictate the outer shape.
        for (int halfWidth = 640; halfWidth <= 944; halfWidth += 16) {
            Packing candidate = pack(pairs, halfWidth);
            double ratio = (double) Math.max(candidate.halfWidth, candidate.halfDepth) / Math.min(candidate.halfWidth, candidate.halfDepth);
            double score = ratio + (double) candidate.halfWidth * candidate.halfDepth * 4 / 1_000_000 * 0.015;
            if (score < bestScore) { best = candidate; bestScore = score; }
        }
        return best;
    }
    private static void placeRow(Shelf shelf, List<Plot> plots, List<Street> streets) {
        Row row = shelf.row; int cursor = shelf.startX, rowMinZ = shelf.south ? shelf.near : -shelf.near - row.depth;
        for (Column column : row.columns) {
            int occupiedDepth = column.pairs.stream().mapToInt(Pair::depth).sum();
            int minZ = shelf.south ? rowMinZ : rowMinZ + row.depth - occupiedDepth;
            List<Pair> stack = new ArrayList<>(column.pairs);
            if (!shelf.south) java.util.Collections.reverse(stack);
            for (Pair pair : stack) {
                for (int side : new int[] {1, -1}) {
                    Machine machine = side > 0 ? pair.right : pair.left;
                    if (machine == null) continue;
                    int rightX = cursor + (column.width - machine.width) / 2;
                    int x = side > 0 ? rightX : -rightX - machine.width, z = minZ + COURT;
                    plots.add(new Plot(machine.id, machine.stage, x, z, machine.width, machine.height, machine.depth,
                            x + machine.cx, machine.cy + 1, z + machine.cz, machine.pattern, machine.slotIndex, machine.theme));
                    addStreet(streets, new Street(false, minZ, side > 0 ? cursor : -cursor - column.width,
                            side > 0 ? cursor + column.width - 1 : -cursor - 1, 3));
                }
                minZ += pair.depth;
            }
            // COURT already supplies an eight-block gap; six blocks are a shared service walk, with no extra setback.
            for (int boundary : new int[] {cursor, cursor + column.width}) for (int side : new int[] {-1, 1})
                addStreet(streets, new Street(true, side * boundary, rowMinZ, rowMinZ + row.depth - 1, 3));
            cursor += column.width;
        }
    }
    private static void addStreet(List<Street> streets, Street street) { if (!streets.contains(street)) streets.add(street); }
    private static void crossStreet(List<Street> streets, int axis, int halfWidth) {
        if (Math.abs(axis) >= 228) addStreet(streets, new Street(false, axis, -halfWidth, halfWidth - 1, 12));
        else {
            addStreet(streets, new Street(false, axis, 220, halfWidth - 1, 12));
            addStreet(streets, new Street(false, axis, -halfWidth, -221, 12));
        }
    }
    private static Plan build() {
        Plan frozen = FrozenCampusLayouts.load("campus_compact_layout.json.gz", CATALOG, Plan.class);
        Map<Long, Local> index = new HashMap<>();
        frozen.index.forEach((tile, local) -> index.put(tile, new Local(List.copyOf(local.plots), List.copyOf(local.streets), List.copyOf(local.trees),
                List.copyOf(local.lamps), List.copyOf(local.landscapes), List.copyOf(local.islands))));
        return new Plan(List.copyOf(frozen.plots), List.copyOf(frozen.streets), List.copyOf(frozen.workshops),
                List.copyOf(frozen.districts), Map.copyOf(index), frozen.bounds, frozen.height, frozen.shared, Map.copyOf(frozen.tiers));
    }
    private static boolean clearOfGardens(int x, int z, int radius) {
        for (Garden garden : AE_GARDENS) {
            Bounds bounds = garden.bounds();
            if (x >= bounds.minX - radius && x <= bounds.maxX + radius && z >= bounds.minZ - radius && z <= bounds.maxZ + radius) return false;
        }
        return true;
    }
    private static boolean clearOfTreesAndLamps(int x, int z, int radius, List<Tree> trees, List<Lamp> lamps) {
        for (Tree tree : trees) if (Math.abs(x - tree.x) <= radius + 3 && Math.abs(z - tree.z) <= radius + 3) return false;
        for (Lamp lamp : lamps) if (Math.abs(x - lamp.x) <= radius + 3 && Math.abs(z - lamp.z) <= radius + 3) return false;
        return true;
    }
    private static boolean clearOfLandscapes(int x, int z, int radius, List<Landscape> landscapes) {
        for (Landscape landscape : landscapes) if (Math.abs(x - landscape.x) <= radius + CompactCampusLandscape.radius()
                && Math.abs(z - landscape.z) <= radius + CompactCampusLandscape.radius()) return false;
        return true;
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
        for (Plot plot : plots) if (x >= plot.x - 2 - radius && x <= plot.maxX() + 2 + radius
                && z >= plot.z - 4 - radius && z <= plot.maxZ() + 2 + radius) return false;
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
