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
public final class ParkPlannedCampus {
    public static final String CATALOG = "/data/gtl_enhancedcore/worldgen/planned_campus_machines_v2.json";
    private static final String WHITE = "minecraft:white_concrete", PALE = "minecraft:light_gray_concrete";
    private static final String GRAY = "minecraft:gray_concrete", BLACK = "minecraft:black_concrete";
    private static final String GREEN = "minecraft:grass_block", YELLOW = "minecraft:yellow_concrete";
    private static final String CYAN = "minecraft:cyan_concrete", WATER = "minecraft:water";
    private static final String LIGHT = "minecraft:sea_lantern", WOOD = "minecraft:oak_log", LEAF = "minecraft:oak_leaves";
    private static final String QUARTZ = "minecraft:smooth_quartz", SLAB = "minecraft:smooth_quartz_slab";
    private static final String STONE = "minecraft:polished_andesite", WALL = "minecraft:stone_brick_wall[up=true]";
    private static final String STONE_SLAB = "minecraft:polished_andesite_slab";
    private static final String HEDGE = "minecraft:azalea_leaves", FLOWER = "minecraft:flowering_azalea_leaves";
    private static final String WARM = "minecraft:terracotta";
    private static final int TILE = 64, COURT = 7;
    private static final Bounds AE_BOUNDS = new Bounds(-80, -80, 79, 79);
    private static final List<Garden> AE_GARDENS = List.of(new Garden(-176, -64), new Garden(176, -64),
            new Garden(-176, 64), new Garden(176, 64));
    private static final Set<String> MATERIALS;
    static {
        Set<String> palette = new LinkedHashSet<>(List.of(WHITE, PALE, GRAY, BLACK, GREEN, YELLOW, CYAN,
                WATER, LIGHT, WOOD, LEAF, QUARTZ, SLAB, WARM, STONE, WALL, STONE_SLAB, HEDGE, FLOWER));
        for (String facing : List.of("north", "south", "east", "west"))
            palette.add("minecraft:quartz_stairs[facing=" + facing + "]");
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
    private record Row(List<Pair> pairs, int width, int depth) {}
    private record Street(boolean vertical, int axis, int start, int end, int halfWidth) {
        int perpendicular(int x, int z) { return vertical ? x : z; }
        int along(int x, int z) { return vertical ? z : x; }
        int distance(int x, int z) { return fold(perpendicular(x, z) - axis); }
        boolean contains(int x, int z) { int along = along(x, z); return along >= start && along <= end && distance(x, z) < halfWidth; }
    }
    private record Tree(int x, int z) {}
    private record Lamp(int x, int z, boolean vertical, int inward) {}
    private record Garden(int x, int z) {
        Bounds bounds() { return new Bounds(x - 56, z - 28, x + 55, z + 27); }
        int dx(int px) { return fold(px - x); }
        int dz(int pz) { return fold(pz - z); }
        boolean contains(int px, int pz) { return dx(px) < 56 && dz(pz) < 28; }
        boolean pond(int px, int pz) { return dx(px) <= 31 && dz(pz) <= 10; }
        boolean rim(int px, int pz) { return pond(px, pz) && (dx(px) == 31 || dz(pz) == 10); }
    }
    private record Local(List<Plot> plots, List<Street> streets, List<Tree> trees, List<Lamp> lamps, List<Bounds> islands) {}
    private record Plan(List<Plot> plots, List<Street> streets, List<Workshop> workshops,
                        List<District> districts, Map<Long, Local> index, Bounds bounds, int height, int shared,
                        Map<String, Integer> tiers) {}
    private static final class Holder { private static final Plan PLAN = build(); }

    private ParkPlannedCampus() {}
    private static int fold(int coordinate) { return coordinate >= 0 ? coordinate : -1 - coordinate; }
    private static int align(int value) { return ((value + 15) / 16) * 16; }
    public static Set<String> materials() { return MATERIALS; }
    public static List<Plot> plots() { return Holder.PLAN.plots; }
    public static List<Workshop> workshops() { return Holder.PLAN.workshops; }
    public static List<District> districts() { return Holder.PLAN.districts; }
    public static int sharedMachineCount() { return Holder.PLAN.shared; }
    public static int planningTier(String id) { return Holder.PLAN.tiers.get(id); }
    public static Bounds bounds() { return Holder.PLAN.bounds; }
    public static Bounds previewBounds(boolean overview) { return overview ? bounds() : new Bounds(-320, -272, 319, 271); }
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
        String garden = gardenSurface(x, z);
        if (garden != null) return garden;
        if (edgeColumn(x, z)) return QUARTZ;
        if (Math.floorMod(fold(x), 64) >= 29 && Math.floorMod(fold(x), 64) <= 31) return STONE;
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
        if (d >= half - 3) return GREEN;
        if (d >= half - 6) return Math.floorMod(fold(street.along(x, z)), 8) == 0 ? STONE : PALE;
        if (d == half - 7) return STONE;
        boolean junction = false, crosswalk = false, stop = false;
        for (Street other : nearby) if (other.vertical != street.vertical && other.contains(x, z)) {
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
            if (street != null && street.distance(x, z) == street.halfWidth - 2
                    && fold(street.along(x, z)) % 32 >= 7 && fold(street.along(x, z)) % 32 <= 23
                    && awayFromJunction(local, x, z, street, 5))
                return fold(street.along(x, z)) % 8 == 0 ? FLOWER : HEDGE;
        }
        if (!onIsland(local, x, z) && edgeColumn(x, z) && y <= 2) return y == 1 ? QUARTZ : SLAB;
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
        for (Tree tree : local.trees) if (Math.abs(x - tree.x) <= 3 && Math.abs(z - tree.z) <= 3) return 12;
        for (Lamp lamp : local.lamps) if (Math.abs(x - lamp.x) <= 3 && Math.abs(z - lamp.z) <= 3) return 8;
        if (blockAboveAt(settings, x, 2, z) != null) return 2;
        return blockAboveAt(settings, x, 1, z) == null ? 0 : 1;
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
        var stream = ParkPlannedCampus.class.getResourceAsStream(CATALOG);
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
    private static List<Row> rows(List<Machine> group, int wingWidth) {
        List<Pair> pairs = new ArrayList<>();
        for (int i = 0; i < group.size(); i += 2) {
            Machine right = group.get(i), left = i + 1 < group.size() ? group.get(i + 1) : null;
            pairs.add(new Pair(right, left, align(Math.max(right.width, left == null ? 0 : left.width) + 2 * COURT),
                    align(Math.max(right.depth, left == null ? 0 : left.depth) + 2 * COURT)));
        }
        List<Row> rows = new ArrayList<>(); List<Pair> row = new ArrayList<>(); int width = 0, depth = 0;
        for (Pair pair : pairs) {
            if (!row.isEmpty() && width + pair.width + 16 > wingWidth) {
                rows.add(new Row(List.copyOf(row), width, depth)); row.clear(); width = depth = 0;
            }
            row.add(pair); width += pair.width + 16; depth = Math.max(depth, pair.depth);
        }
        if (!row.isEmpty()) rows.add(new Row(List.copyOf(row), width, depth));
        return rows;
    }
    private static void placeRow(Row row, int minZ, List<Plot> plots) {
        if (row == null) return;
        int cursor = 40;
        for (Pair pair : row.pairs) {
            for (int side : new int[] {1, -1}) {
                Machine machine = side > 0 ? pair.right : pair.left;
                if (machine == null) continue;
                int rightX = cursor + (pair.width - machine.width) / 2;
                int x = side > 0 ? rightX : -rightX - machine.width;
                int z = minZ + COURT;
                plots.add(new Plot(machine.id, machine.stage, x, z, machine.width, machine.height, machine.depth,
                        x + machine.cx, machine.cy + 1, z + machine.cz, machine.pattern, machine.slotIndex, machine.theme));
            }
            cursor += pair.width + 16;
        }
    }
    private static Plan build() {
        JsonObject catalog = catalog(); List<Machine> machines = machines(catalog);
        Map<String, Integer> tiers = new HashMap<>();
        for (var element : catalog.getAsJsonArray("machines")) {
            JsonObject machine = element.getAsJsonObject();
            String stage = machine.get("stage").getAsString();
            tiers.put(machine.get("id").getAsString(), machine.has("planningTier") ? machine.get("planningTier").getAsInt()
                    : stage.equals("early") ? 3 : stage.equals("mid") ? 7 : 12);
        }
        int shared = (int) catalog.getAsJsonArray("machines").asList().stream()
                .map(element -> element.getAsJsonObject()).filter(json -> json.has("allocation") && json.get("allocation").getAsString().equals("shared")).count();
        List<Plot> plots = new ArrayList<>(); List<Street> streets = new ArrayList<>(); List<District> districts = new ArrayList<>();
        List<Bounds> islands = new ArrayList<>(); islands.add(new Bounds(-320, -272, 319, 271));
        List<Workshop> pads = List.of(new Workshop("early", -288, -240, 192, 128), new Workshop("early", 96, -240, 192, 128),
                new Workshop("early", -288, 112, 192, 128), new Workshop("early", 96, 112, 192, 128));
        streets.add(new Street(false, 0, -319, 318, 16));
        for (int axis : new int[] {-256, -96, 96, 256}) streets.add(new Street(false, axis, -319, 318, 12));
        int outward = 304, maxWing = 320;
        // Each voltage band advances outwards together on both sides of the AE hub.
        for (int tier = 0; tier <= 14; tier++) {
            final int voltage = tier;
            List<Machine> group = machines.stream().filter(machine -> machine.tier == voltage)
                    .sorted(Comparator.comparingInt(Machine::depth).reversed().thenComparing(Comparator.comparingInt(Machine::width).reversed())
                            .thenComparing(Machine::id).thenComparingInt(Machine::slotIndex)).toList();
            if (group.isEmpty()) continue;
            long area = group.stream().mapToLong(machine -> (long) (machine.width + 30) * (machine.depth + 30)).sum();
            int wing = Math.max(288, Math.min(768, align((int) Math.sqrt(area / 2.0))));
            List<Row> rows = rows(group, wing);
            for (int i = 0; i < rows.size(); i += 2) {
                Row north = rows.get(i), south = i + 1 < rows.size() ? rows.get(i + 1) : null;
                int width = align(Math.max(north.width, south == null ? 0 : south.width)) + 64;
                int depth = Math.max(north.depth, south == null ? 0 : south.depth);
                int near = outward, far = near + depth + 32;
                placeRow(north, -far + 16, plots); placeRow(south, near + 16, plots);
                for (int direction : new int[] {-1, 1}) {
                    int minZ = direction > 0 ? near - 12 : -far - 12, maxZ = direction > 0 ? far + 11 : -near + 11;
                    islands.add(new Bounds(-width, minZ, width - 1, maxZ));
                    for (int axis : new int[] {near, far}) streets.add(new Street(false, direction * axis, -width, width - 1, 12));
                    for (int axis : new int[] {-width + 12, width - 12})
                        streets.add(new Street(true, axis, minZ, maxZ, 12));
                }
                String stage = tier <= 3 ? "early" : tier <= 8 ? "mid" : "late";
                districts.add(new District(stage, near, far)); districts.add(new District(stage, -far, -near));
                outward = far + 48; maxWing = Math.max(maxWing, width);
            }
        }
        streets.add(new Street(true, 0, -outward + 32, outward - 33, 16));
        Bounds bounds = new Bounds(-maxWing - 16, -outward, maxWing + 15, outward - 1);
        Map<Long, List<Plot>> plotIndex = new HashMap<>(); Map<Long, List<Street>> streetIndex = new HashMap<>();
        Map<Long, List<Bounds>> islandIndex = new HashMap<>(); Map<Long, List<Tree>> treeIndex = new HashMap<>();
        Map<Long, List<Lamp>> lampIndex = new HashMap<>();
        for (Bounds island : islands) index(islandIndex, island, island);
        for (Plot plot : plots) index(plotIndex, new Bounds(plot.x - COURT, plot.z - COURT, plot.maxX() + COURT, plot.maxZ() + COURT), plot);
        List<Tree> trees = new ArrayList<>(); List<Lamp> lamps = new ArrayList<>(); Set<Long> treeKeys = new LinkedHashSet<>(), lampKeys = new LinkedHashSet<>();
        for (Street street : streets) {
            Bounds extent = street.vertical ? new Bounds(street.axis - street.halfWidth, street.start, street.axis + street.halfWidth - 1, street.end)
                    : new Bounds(street.start, street.axis - street.halfWidth, street.end, street.axis + street.halfWidth - 1);
            index(streetIndex, extent, street);
            // Center-out phases preserve exact left/right and north/south street rhythms.
            int alongLimit = Math.max(fold(street.start), fold(street.end));
            for (int offset = 16; offset <= alongLimit; offset += 32) for (int direction : new int[] {-1, 1}) for (int side : new int[] {-1, 1}) {
                int along = direction > 0 ? offset : -1 - offset;
                if (along < street.start || along > street.end) continue;
                int across = street.axis + (side > 0 ? street.halfWidth - 2 : -street.halfWidth + 1);
                int x = street.vertical ? across : along, z = street.vertical ? along : across;
                if (supported(islands, x, z, 3) && safeGarden(x, z, 3, plots, pads, streets, street) && treeKeys.add(key(x, z))) trees.add(new Tree(x, z));
                int lampAcross = street.axis + (side > 0 ? street.halfWidth - 5 : -street.halfWidth + 4);
                int lampAlong = direction > 0 ? offset - 16 : -1 - (offset - 16);
                int lx = street.vertical ? lampAcross : lampAlong, lz = street.vertical ? lampAlong : lampAcross;
                if (supported(islands, lx, lz, 3) && safeGarden(lx, lz, 3, plots, pads, streets, street) && lampKeys.add(key(lx, lz)))
                    lamps.add(new Lamp(lx, lz, street.vertical, -side));
            }
        }
        // Regular allées fill the core lawns; production courts and the AE floor remain completely open.
        for (int x = 88; x <= 312; x += 32) for (int z = 16; z <= 256; z += 32) for (int sx : new int[] {-1, 1}) for (int sz : new int[] {-1, 1}) {
            int px = sx > 0 ? x : -1 - x, pz = sz > 0 ? z : -1 - z;
            if (safeGarden(px, pz, 4, plots, pads, streets, null) && !inPondOrPath(px, pz) && treeKeys.add(key(px, pz))) trees.add(new Tree(px, pz));
        }
        for (Garden garden : AE_GARDENS) for (int dx : new int[] {-46, 45}) for (int dz : new int[] {-14, 13}) {
            int x = garden.x + dx, z = garden.z + dz;
            if (treeKeys.add(key(x, z))) trees.add(new Tree(x, z));
        }
        // Long residual lawns become regimented groves, rather than unplanned empty green rectangles.
        for (Bounds island : islands) {
            if (island.minZ >= -272 && island.maxZ <= 271) continue;
            for (int dx = 16; dx <= Math.max(fold(island.minX), fold(island.maxX)); dx += 32)
                for (int dz = Math.max(16, Math.floorDiv(Math.min(fold(island.minZ), fold(island.maxZ)), 32) * 32 + 16);
                        dz <= Math.max(fold(island.minZ), fold(island.maxZ)); dz += 32)
                    for (int sx : new int[] {-1, 1}) {
                        int x = sx > 0 ? dx : -1 - dx, z = island.minZ > 0 ? dz : -1 - dz;
                        if (supported(islands, x, z, 4) && safeGarden(x, z, 4, plots, pads, streets, null)
                                && treeKeys.add(key(x, z))) trees.add(new Tree(x, z));
                    }
        }
        for (Tree tree : trees) index(treeIndex, new Bounds(tree.x - 3, tree.z - 3, tree.x + 3, tree.z + 3), tree);
        for (Lamp lamp : lamps) index(lampIndex, new Bounds(lamp.x - 3, lamp.z - 3, lamp.x + 3, lamp.z + 3), lamp);
        Set<Long> tiles = new LinkedHashSet<>(); tiles.addAll(plotIndex.keySet()); tiles.addAll(streetIndex.keySet());
        tiles.addAll(treeIndex.keySet()); tiles.addAll(lampIndex.keySet()); tiles.addAll(islandIndex.keySet());
        Map<Long, Local> index = new HashMap<>();
        for (long tile : tiles) index.put(tile, new Local(List.copyOf(plotIndex.getOrDefault(tile, List.of())),
                List.copyOf(streetIndex.getOrDefault(tile, List.of())), List.copyOf(treeIndex.getOrDefault(tile, List.of())),
                List.copyOf(lampIndex.getOrDefault(tile, List.of())), List.copyOf(islandIndex.getOrDefault(tile, List.of()))));
        return new Plan(List.copyOf(plots), List.copyOf(streets), pads, List.copyOf(districts), Map.copyOf(index), bounds,
                Math.max(12, plots.stream().mapToInt(plot -> plot.height + 2).max().orElseThrow()), shared, Map.copyOf(tiers));
    }
    private static boolean supported(List<Bounds> islands, int x, int z, int radius) {
        for (Bounds island : islands) if (island.contains(x - radius, z - radius) && island.contains(x + radius, z + radius)) return true;
        return false;
    }
    private static boolean inPondOrPath(int x, int z) {
        for (Garden garden : AE_GARDENS) if (garden.contains(x, z))
            return garden.dx(x) < 43 || garden.dz(z) < 22;
        return false;
    }
    private static boolean awayFromJunction(Local local, int x, int z, Street owner, int margin) {
        for (Street street : local.streets) if (street.vertical != owner.vertical
                && street.distance(x, z) < street.halfWidth + margin && street.along(x, z) >= street.start - margin
                && street.along(x, z) <= street.end + margin) return false;
        return true;
    }
    private static boolean safeGarden(int x, int z, int radius, List<Plot> plots, List<Workshop> pads, List<Street> streets, Street owner) {
        if (AE_BOUNDS.contains(x, z) || AE_BOUNDS.contains(x - radius, z - radius) || AE_BOUNDS.contains(x + radius, z + radius)) return false;
        for (Plot plot : plots) if (x >= plot.x - COURT - radius && x <= plot.maxX() + COURT + radius
                && z >= plot.z - COURT - radius && z <= plot.maxZ() + COURT + radius) return false;
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
