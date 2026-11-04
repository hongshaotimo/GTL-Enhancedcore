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

/** One finite, authored industrial garden; every preview and generated column samples this plan. */
public final class LegacyPlannedCampus {
    public static final String CATALOG = "/data/gtl_enhancedcore/worldgen/planned_campus_machines_v1.json";
    private static final String WHITE = "minecraft:white_concrete", PALE = "minecraft:light_gray_concrete";
    private static final String GRAY = "minecraft:gray_concrete", BLACK = "minecraft:black_concrete";
    private static final String GREEN = "minecraft:green_concrete", YELLOW = "minecraft:yellow_concrete";
    private static final String CYAN = "minecraft:cyan_concrete", BLUE = "minecraft:light_blue_stained_glass";
    private static final String LIGHT = "minecraft:sea_lantern", WOOD = "minecraft:oak_log", LEAF = "minecraft:oak_leaves";
    private static final String QUARTZ = "minecraft:smooth_quartz";
    private static final String SLAB = "minecraft:smooth_quartz_slab";
    private static final String WARM = "minecraft:terracotta";
    private static final int TILE = 64, COURT = 7;
    private static final Bounds AE_BOUNDS = new Bounds(-80, -80, 79, 79);
    private static final Set<String> MATERIALS = Set.of(WHITE, PALE, GRAY, BLACK, GREEN, YELLOW, CYAN, BLUE, LIGHT, WOOD, LEAF, QUARTZ, SLAB, WARM);
    private static final List<Garden> AE_GARDENS = List.of(new Garden(-176, -64), new Garden(175, -64),
            new Garden(-176, 48), new Garden(175, 48));

    public record Bounds(int minX, int minZ, int maxX, int maxZ) {
        public int width() { return maxX - minX + 1; }
        public int depth() { return maxZ - minZ + 1; }
        public boolean contains(int x, int z) { return x >= minX && x <= maxX && z >= minZ && z <= maxZ; }
    }
    /** x/z are the exact northwest/bottom corner of the north-facing machine bounding box. */
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
    private record Machine(String id, String stage, int width, int height, int depth, int cx, int cy, int cz, String pattern, int slotIndex, String theme) {}
    private record Street(boolean vertical, int axis, int start, int end, int halfWidth) {
        int perpendicular(int x, int z) { return vertical ? x : z; }
        int along(int x, int z) { return vertical ? z : x; }
        int distance(int x, int z) { int d = perpendicular(x, z) - axis; return d >= 0 ? d : -1 - d; }
        boolean contains(int x, int z) { int along = along(x, z); return along >= start && along <= end && distance(x, z) < halfWidth; }
    }
    private record Tree(int x, int z) {}
    private record Lamp(int x, int z) {}
    private record Garden(int x, int z) {
        Bounds bounds() { return new Bounds(x - 56, z - 28, x + 55, z + 27); }
        int dx(int px) { int offset = px - x; return offset >= 0 ? offset : -1 - offset; }
        int dz(int pz) { int offset = pz - z; return offset >= 0 ? offset : -1 - offset; }
        boolean contains(int px, int pz) { return dx(px) < 56 && dz(pz) < 28 && dx(px) + dz(pz) < 76; }
        boolean pond(int px, int pz) { return dx(px) <= 31 && dz(pz) <= 11 && dx(px) + dz(pz) <= 37; }
        boolean rim(int px, int pz) { return pond(px, pz) && (dx(px) == 31 || dz(pz) == 11 || dx(px) + dz(pz) >= 36); }
    }
    private record Local(List<Plot> plots, List<Street> streets, List<Tree> trees, List<Lamp> lamps, List<Bounds> islands) {}
    private record Plan(List<Plot> plots, List<Street> streets, List<Workshop> workshops,
                        List<District> districts, Map<Long, Local> index, Bounds bounds, int height) {}
    private static final class Holder { private static final Plan PLAN = build(); }

    private LegacyPlannedCampus() {}
    public static Set<String> materials() { return MATERIALS; }
    public static List<Plot> plots() { return Holder.PLAN.plots; }
    public static List<Workshop> workshops() { return Holder.PLAN.workshops; }
    public static List<District> districts() { return Holder.PLAN.districts; }
    public static Bounds bounds() { return Holder.PLAN.bounds; }
    public static Bounds previewBounds(boolean overview) { return overview ? bounds() : new Bounds(-256, -256, 255, 255); }
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
        // Rooted tree islands include the full canopy projection, so no leaf hangs above missing terrain.
        for (Tree tree : local.trees) if (Math.abs(x - tree.x) <= 3 && Math.abs(z - tree.z) <= 3) return true;
        return false;
    }
    private static boolean onIsland(Local local, int x, int z) {
        for (Bounds island : local.islands) if (island.contains(x, z)) {
            int corner = Math.min(24, Math.min(island.width(), island.depth()) / 8);
            int edgeX = Math.min(x - island.minX, island.maxX - x), edgeZ = Math.min(z - island.minZ, island.maxZ - z);
            if (edgeX + edgeZ >= corner) return true;
        }
        return false;
    }
    public static Plot plotAt(int x, int z) {
        Local local = local(x, z);
        if (local != null) for (Plot plot : local.plots) if (plot.contains(x, z)) return plot;
        return null;
    }

    public static String surfaceBlock(PlotSettings settings, int x, int z) {
        if (!hasTerrain(x, z)) return "minecraft:air";
        Plan plan = Holder.PLAN;
        Local local = local(x, z);
        if (AE_BOUNDS.contains(x, z)) return aeSurface(x, z);
        if (local != null) for (Plot plot : local.plots) {
            if (frameEdge(plot, x, z)) return QUARTZ;
            if (plot.contains(x, z)) {
                boolean edge = x == plot.x || x == plot.maxX() || z == plot.z || z == plot.maxZ();
                if (edge) return WHITE;
                if (x == plot.controllerX && z == plot.controllerZ) return LIGHT;
                return PALE;
            }
            if (x >= plot.x - COURT && x <= plot.maxX() + COURT && z >= plot.z - COURT && z <= plot.maxZ() + COURT) {
                if (z < plot.z && arrow(plot, x, z)) return CYAN;
                if (plot.theme.equals("cosmic") && plot.width >= 8 && x >= plot.x + 2 && x <= plot.maxX() - 2
                        && z >= plot.maxZ() + 3 && z <= plot.maxZ() + 5)
                    return z == plot.maxZ() + 4 ? BLUE : QUARTZ;
                if (planted(plot) && z >= plot.maxZ() + 2 && z <= plot.maxZ() + 6
                        && (Math.abs(x - (plot.x - 4)) <= 2 || Math.abs(x - (plot.maxX() + 4)) <= 2)) return GREEN;
                if ((x == plot.x - 6 || x == plot.maxX() + 6) && z >= plot.z && z <= plot.maxZ()
                        || z == plot.maxZ() + 6 && x >= plot.x && x <= plot.maxX()) return accent(plot.theme);
                return x == plot.x - COURT || x == plot.maxX() + COURT || z == plot.maxZ() + COURT ? GRAY : PALE;
            }
        }
        for (Workshop pad : plan.workshops) if (pad.contains(x, z)) {
            int dx = x - pad.x, dz = z - pad.z;
            if (dx == 0 || dz == 0 || dx == pad.width - 1 || dz == pad.depth - 1) return WHITE;
            return PALE;
        }
        if (local != null) {
            Street chosen = null;
            for (Street street : local.streets) if (street.contains(x, z)
                    && (chosen == null || street.distance(x, z) < chosen.distance(x, z))) chosen = street;
            if (chosen != null) return streetSurface(chosen, x, z, local.streets);
        }
        String garden = gardenSurface(x, z);
        if (garden != null) return garden;
        for (Workshop pad : plan.workshops) if (workshopPlanter(pad, x, z)) return GREEN;
        if (edgeColumn(x, z)) return QUARTZ;
        // Paired reflecting gardens frame the southern approach without flowing water.
        if (z >= 224 && z <= 320 && Math.abs(x) >= 20 && Math.abs(x) <= 28) {
            return Math.abs(x) == 20 || Math.abs(x) == 28 || z == 224 || z == 320 ? QUARTZ : BLUE;
        }
        return GREEN;
    }

    private static String gardenSurface(int x, int z) {
        // Four gardens flank the AE reserve; all water is sealed, decorative glass over a lit floor.
        for (Garden garden : AE_GARDENS) if (garden.contains(x, z)) {
            int dx = garden.dx(x), dz = garden.dz(z);
            if (garden.pond(x, z)) return garden.rim(x, z) ? QUARTZ : BLUE;
            if (dx >= 35 && dx <= 39 && dz <= 21 || dz >= 16 && dz <= 20 && dx <= 39) return PALE;
            if (dx >= 53 || dz >= 25 || dx + dz >= 73) return GRAY;
            if (dz <= 1) return PALE;
            return GREEN;
        }
        // Paths join the AE court to each garden's inner bank and the public roadside promenade.
        int ax = x >= 0 ? x : -1 - x;
        if (ax >= 80 && ax <= 143 && (Math.abs(z + 64) <= 1 || Math.abs(z - 48) <= 1)) return PALE;
        if (ax >= 222 && ax <= 225 && z >= -112 && z <= 80) return PALE;
        return null;
    }

    private static boolean workshopPlanter(Workshop pad, int x, int z) {
        boolean cornerX = Math.abs(x - (pad.x - 4)) <= 2 || Math.abs(x - (pad.x + pad.width + 3)) <= 2;
        boolean cornerZ = Math.abs(z - (pad.z - 4)) <= 2 || Math.abs(z - (pad.z + pad.depth + 3)) <= 2;
        return cornerX && cornerZ;
    }

    /** Solid facade accents only. The caller retains bedrock and its existing underground gallery. */
    public static String foundationBlockAt(int x, int relativeY, int z) {
        if (relativeY >= 0 || !hasTerrain(x, z)) return null;
        if (relativeY == -1) for (Garden garden : AE_GARDENS) if (garden.pond(x, z))
            return Math.floorMod(x + z, 8) == 0 ? LIGHT : QUARTZ;
        if (!edgeColumn(x, z)) return null;
        return relativeY == -1 || relativeY == -4 || relativeY == -7 ? QUARTZ : GRAY;
    }

    private static boolean edgeColumn(int x, int z) {
        Local local = local(x, z);
        if (local == null) return false;
        boolean possibleEdge = !onIsland(local, x, z);
        for (Bounds island : local.islands) if (island.contains(x, z)) {
            int ex = Math.min(x - island.minX, island.maxX - x), ez = Math.min(z - island.minZ, island.maxZ - z);
            int corner = Math.min(24, Math.min(island.width(), island.depth()) / 8);
            if (ex <= 1 || ez <= 1 || ex + ez <= corner + 1) possibleEdge = true;
        }
        return possibleEdge && hasTerrain(x, z) && (!hasTerrain(x - 1, z) || !hasTerrain(x + 1, z)
                || !hasTerrain(x, z - 1) || !hasTerrain(x, z + 1));
    }

    private static boolean bridgeRail(Local local, int x, int z) {
        if (onIsland(local, x, z)) return false;
        for (Street street : local.streets) if (street.contains(x, z) && street.distance(x, z) == street.halfWidth - 1) return true;
        return false;
    }

    private static String landscapeAbove(int x, int y, int z, Local local) {
        if (AE_BOUNDS.contains(x, z)) return null;
        if ((z == 80 || z == 81 || z == -81 || z == -82) && x >= -20 && x <= 19) {
            if ((x <= -19 || x >= 18) && y <= 8) return QUARTZ;
            if (y == 9) return SLAB;
        }
        if (y == 1) {
            for (Garden garden : AE_GARDENS) if (garden.rim(x, z)) return SLAB;
            for (Workshop pad : Holder.PLAN.workshops) if (workshopPlanter(pad, x, z)) {
                int cx = x < pad.x ? pad.x - 4 : pad.x + pad.width + 3;
                int cz = z < pad.z ? pad.z - 4 : pad.z + pad.depth + 3;
                if (Math.abs(x - cx) == 2 || Math.abs(z - cz) == 2) return SLAB;
            }
        }
        if (y <= 2 && bridgeRail(local, x, z)) return y == 1 ? QUARTZ : SLAB;
        return null;
    }

    private static boolean planted(Plot plot) { return !plot.theme.equals("cosmic") && !plot.theme.equals("energy"); }
    private static String accent(String theme) {
        return switch (theme) {
            case "chemistry", "computation" -> CYAN;
            case "metallurgy" -> WARM;
            case "biology" -> GREEN;
            case "cosmic", "energy", "logistics" -> GRAY;
            default -> QUARTZ;
        };
    }

    private static String aeSurface(int x, int z) {
        int ax = x < 0 ? -1 - x : x, az = z < 0 ? -1 - z : z, r = Math.max(ax, az);
        if (Math.abs(ax - 24) <= 1 && az <= 1 || Math.abs(az - 24) <= 1 && ax <= 1)
            return ax == 24 && az == 0 || az == 24 && ax == 0 ? LIGHT : GRAY;
        if (r == 78 || r == 79) return QUARTZ;
        // A cross-shaped promenade joins all four districts; the central square is deliberately empty.
        if (ax <= 7 || az <= 7) return ax == 7 || az == 7 ? WHITE : PALE;
        if (r <= 15) return r == 15 ? QUARTZ : PALE;
        if (r >= 67) return GREEN;
        // Four spacious, interchangeable AE courts. Their fine inlay suggests expansion, not device slots.
        if (ax >= 20 && ax <= 61 && az >= 20 && az <= 61)
            return ax == 20 || ax == 61 || az == 20 || az == 61 ? QUARTZ : PALE;
        if ((ax == 16 || az == 16) && r < 64) return CYAN;
        if (ax >= 64 && ax <= 65 || az >= 64 && az <= 65) return GRAY;
        return PALE;
    }

    private static boolean frameEdge(Plot plot, int x, int z) {
        return x >= plot.x - 1 && x <= plot.maxX() + 1 && z >= plot.z - 1 && z <= plot.maxZ() + 1
                && (x == plot.x - 1 || x == plot.maxX() + 1 || z == plot.z - 1 || z == plot.maxZ() + 1);
    }

    private static boolean arrow(Plot plot, int x, int z) {
        int center = plot.signX(), back = plot.z - z;
        return back >= 2 && back <= 6 && (x == center || back == 4 && Math.abs(x - center) == 1
                || back == 3 && Math.abs(x - center) == 2);
    }

    private static String streetSurface(Street street, int x, int z, List<Street> nearby) {
        int d = street.distance(x, z), half = street.halfWidth, carriage = half - 7;
        if (d >= half - 3) return GREEN;
        if (d >= half - 6) return PALE;
        if (d == half - 7) return GRAY;
        if (d == carriage - 1) return WHITE;
        boolean junction = false, crosswalk = false;
        for (Street other : nearby) if (other.vertical != street.vertical) {
            int distance = other.distance(x, z);
            if (distance < other.halfWidth - 7) junction = true;
            if (distance >= other.halfWidth - 6 && distance < other.halfWidth - 3) crosswalk = true;
        }
        if (crosswalk && d < carriage - 1) return Math.floorMod(street.perpendicular(x, z), 2) == 0 ? WHITE : BLACK;
        if (!junction && d == 1 && half >= 16) return YELLOW;
        if (!junction && d == 0 && half < 16 && Math.floorMod(street.along(x, z), 10) < 5) return WHITE;
        return BLACK;
    }

    /** relativeY=1 is the first block above the paved surface. null means air. */
    public static String blockAboveAt(PlotSettings settings, int x, int relativeY, int z) {
        if (relativeY <= 0) return null;
        Local local = local(x, z);
        if (local == null) return null;
        for (Plot plot : local.plots) if (plot.contains(x, z) || frameEdge(plot, x, z)) {
            if (x == plot.controllerX && z == plot.controllerZ && relativeY == plot.controllerY) return LIGHT;
            if (relativeY > plot.height + 1) return null;
            boolean xEdge = x == plot.x - 1 || x == plot.maxX() + 1, zEdge = z == plot.z - 1 || z == plot.maxZ() + 1;
            if (xEdge && zEdge || relativeY == plot.height + 1 && (xEdge || zEdge))
                return relativeY == 1 || relativeY == plot.height + 1 ? QUARTZ : PALE;
            return null;
        }
        String landscape = landscapeAbove(x, relativeY, z, local);
        if (landscape != null) return landscape;
        // Decoration stays outside the complete service court, including every machine entrance.
        for (Lamp lamp : local.lamps) if (lamp.x == x && lamp.z == z) {
            if (relativeY <= 5) return GRAY;
            if (relativeY == 6) return LIGHT;
            if (relativeY == 7) return WHITE;
        }
        for (Tree tree : local.trees) {
            int dx = Math.abs(x - tree.x), dz = Math.abs(z - tree.z);
            if (dx == 0 && dz == 0 && relativeY <= 4) return WOOD;
            if (relativeY >= 4 && relativeY <= 6 && dx <= (relativeY == 6 ? 1 : 2)
                    && dz <= (relativeY == 6 ? 1 : 2) && dx + dz <= 3) return LEAF;
        }
        return null;
    }

    /** Sparse column ceiling, used to avoid traversing every giant's height over ordinary terrain. */
    public static int maxDecorationYAt(int x, int z) {
        Local local = local(x, z);
        if (local == null) return 0;
        for (Plot plot : local.plots) if (plot.contains(x, z) || frameEdge(plot, x, z)) {
            if (frameEdge(plot, x, z)) return plot.height + 1;
            return x == plot.controllerX && z == plot.controllerZ ? plot.controllerY : 0;
        }
        for (Lamp lamp : local.lamps) if (lamp.x == x && lamp.z == z) return 7;
        for (Tree tree : local.trees) if (Math.abs(x - tree.x) <= 2 && Math.abs(z - tree.z) <= 2
                && Math.abs(x - tree.x) + Math.abs(z - tree.z) <= 3) return 6;
        if (landscapeAbove(x, 9, z, local) != null) return 9;
        if (landscapeAbove(x, 2, z, local) != null) return 2;
        if (landscapeAbove(x, 1, z, local) != null) return 1;
        return 0;
    }

    public static int previewColorAt(int x, int z) {
        String block = surfaceBlock(null, x, z);
        return switch (block) {
            case WHITE -> 0xffd8dad5;
            case PALE -> 0xffa5aaa8;
            case GRAY -> 0xff606668;
            case BLACK -> 0xff252a2d;
            case GREEN -> 0xff66784e;
            case YELLOW -> 0xffc7ae57;
            case CYAN -> 0xff4d8d93;
            case BLUE -> 0xff6299ae;
            case LIGHT -> 0xffcee5df;
            case QUARTZ -> 0xffe1dfd7;
            case WARM -> 0xffa77862;
            case "minecraft:air" -> 0xff162431;
            default -> 0xff66784e;
        };
    }

    private static Local local(int x, int z) { return Holder.PLAN.index.get(key(Math.floorDiv(x, TILE), Math.floorDiv(z, TILE))); }
    private static long key(int x, int z) {
        // Packed coordinates have a heavily colliding Long.hashCode on square grids. MapN's
        // linear probing needs a mixed key to keep map previews and chunk sampling bounded.
        long value = (long) x << 32 ^ (z & 0xffffffffL);
        value = (value ^ (value >>> 33)) * 0xff51afd7ed558ccdL;
        value = (value ^ (value >>> 33)) * 0xc4ceb9fe1a85ec53L;
        return value ^ (value >>> 33);
    }

    private static List<Machine> machines() {
        var stream = LegacyPlannedCampus.class.getResourceAsStream(CATALOG);
        if (stream == null) throw new IllegalStateException("Missing planned campus machine catalog");
        try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            if (root.get("schemaVersion").getAsInt() != 1) throw new IllegalArgumentException("Unsupported campus schema");
            List<Machine> result = new ArrayList<>();
            Set<String> ids = new LinkedHashSet<>();
            for (var element : root.getAsJsonArray("machines")) {
                JsonObject json = element.getAsJsonObject();
                var size = json.getAsJsonArray("size"); var anchor = json.getAsJsonArray("groundOffset");
                String id = json.get("id").getAsString(), stage = json.get("stage").getAsString();
                int w = size.get(0).getAsInt(), h = size.get(1).getAsInt(), d = size.get(2).getAsInt();
                int cx = anchor.get(0).getAsInt(), cy = anchor.get(1).getAsInt(), cz = anchor.get(2).getAsInt();
                if (!ids.add(id) || !Set.of("early", "mid", "late").contains(stage) || w < 1 || h < 1 || d < 1
                        || cx < 0 || cx >= w || cy < 0 || cy >= h || cz < 0 || cz >= d
                        || !json.get("frontFacing").getAsString().equals("north"))
                    throw new IllegalArgumentException("Invalid planned machine " + id);
                int copies = json.has("copies") ? json.get("copies").getAsInt() : 1;
                String theme = json.has("theme") ? json.get("theme").getAsString() : "assembly";
                if (copies < 1 || copies > 16) throw new IllegalArgumentException("Invalid campus copies: " + id);
                for (int slot = 0; slot < copies; slot++) result.add(new Machine(id, stage, w, h, d, cx, cy, cz,
                        json.get("patternResource").getAsString(), slot, theme));
            }
            if (result.isEmpty()) throw new IllegalArgumentException("Empty campus machine catalog");
            return List.copyOf(result);
        } catch (java.io.IOException error) { throw new IllegalStateException("Cannot load campus catalog", error); }
    }

    private static Plan build() {
        List<Machine> catalog = machines();
        int wing = Math.max(512, ((catalog.stream().mapToInt(machine -> machine.width + COURT * 2).max().orElseThrow() + 63) / 64) * 64);
        List<Plot> plots = new ArrayList<>(); List<Street> streets = new ArrayList<>(); List<District> districts = new ArrayList<>();
        List<Bounds> islands = new ArrayList<>();
        islands.add(new Bounds(-320, -128, 319, 447));
        List<Workshop> pads = List.of(new Workshop("early", -288, 112, 192, 128), new Workshop("early", 96, 112, 192, 128),
                new Workshop("mid", -288, 272, 192, 128), new Workshop("late", 96, 272, 192, 128));
        streets.add(new Street(false, 96, -312, 311, 12));
        streets.add(new Street(false, 256, -312, 311, 12));
        streets.add(new Street(false, 416, -312, 311, 12));
        int furthest = 448, northBoundary = 480;
        for (String sector : List.of("early", "mid", "late_north", "late_south")) {
            boolean late = sector.startsWith("late");
            String stage = late ? "late" : sector;
            List<Machine> all = catalog.stream().filter(machine -> machine.stage.equals(stage))
                    .sorted(Comparator.comparingInt(Machine::depth).reversed().thenComparingInt(Machine::height).thenComparing(Machine::id)).toList();
            List<Machine> group = new ArrayList<>();
            Map<String, Integer> familySide = new HashMap<>();
            for (Machine machine : all) {
                int family = familySide.computeIfAbsent(machine.id, ignored -> familySide.size());
                if (!late || family % 2 == (sector.equals("late_south") ? 1 : 0)) group.add(machine);
            }
            List<Plot> relative = new ArrayList<>(); List<Integer> rowRoads = new ArrayList<>();
            int nextZ = 0, cursor = 0;
            long area = group.stream().mapToLong(machine -> (long) (machine.width + COURT * 2 + 8) * (machine.depth + COURT * 2 + 40)).sum();
            int balanced = ((int) Math.sqrt(area / (late ? 2.0 : 1.0)) + 63) / 64 * 64;
            int packingWing = Math.max(late ? 768 : wing, balanced);
            while (cursor < group.size()) {
                int rowDepth = 0;
                rowRoads.add(nextZ - 12);
                for (int side : late ? new int[] {1, -1} : new int[] {1}) {
                    int used = 0;
                    while (cursor < group.size()) {
                        Machine machine = group.get(cursor); int footprint = machine.width + COURT * 2;
                        if (used > 0 && used + footprint > packingWing) break;
                        int x = side > 0 ? 32 + used + COURT : -32 - used - COURT - machine.width;
                        int z = nextZ + COURT;
                        relative.add(new Plot(machine.id, stage, x, z, machine.width, machine.height, machine.depth,
                                x + machine.cx, machine.cy + 1, z + machine.cz, machine.pattern, machine.slotIndex, machine.theme));
                        used += footprint + 8; rowDepth = Math.max(rowDepth, machine.depth + COURT * 2); cursor++;
                    }
                }
                nextZ += ((rowDepth + 23) / 8) * 8 + 24;
            }
            int shiftX = stage.equals("early") ? -packingWing - 320 : stage.equals("mid") ? 256 : 0;
            int shiftZ = late ? (sector.equals("late_south") ? northBoundary : -northBoundary - nextZ) : -((nextZ / 2 + 7) / 8) * 8;
            if (!late) northBoundary = Math.max(northBoundary, -shiftZ + 88);
            for (Plot plot : relative) plots.add(new Plot(plot.id, plot.stage, plot.x + shiftX, plot.z + shiftZ,
                    plot.width, plot.height, plot.depth, plot.controllerX + shiftX, plot.controllerY, plot.controllerZ + shiftZ, plot.patternResource, plot.slotIndex, plot.theme));
            int minX = late ? -packingWing - 48 : 16 + shiftX, maxX = late ? packingWing + 47 : packingWing + 47 + shiftX;
            for (int roadZ : rowRoads) streets.add(new Street(false, roadZ + shiftZ, minX, maxX, 12));
            streets.add(new Street(false, nextZ - 12 + shiftZ, minX, maxX, 12));
            streets.add(new Street(true, minX + 12, shiftZ - 24, shiftZ + nextZ - 1, 12));
            streets.add(new Street(true, maxX - 11, shiftZ - 24, shiftZ + nextZ - 1, 12));
            districts.add(new District(stage, shiftZ - 24, shiftZ + nextZ));
            islands.add(new Bounds(minX - 16, shiftZ - 40, maxX + 16, shiftZ + nextZ + 16));
            furthest = Math.max(furthest, Math.max(Math.max(Math.abs(minX), Math.abs(maxX)), Math.max(Math.abs(shiftZ - 24), Math.abs(shiftZ + nextZ))));
        }
        int outer = ((furthest + 95) / 32) * 32;
        int roadNorth = islands.stream().mapToInt(Bounds::minZ).min().orElse(-128) + 16;
        int roadSouth = islands.stream().mapToInt(Bounds::maxZ).max().orElse(383) - 16;
        streets.add(new Street(true, 0, roadNorth, roadSouth, 16));
        // East/west avenues terminate at the district's inner distributor, never through a machine court.
        streets.add(new Street(false, 0, -288, 287, 16));
        Bounds bounds = new Bounds(-outer, -outer, outer - 1, outer - 1);
        Map<Long, List<Plot>> plotIndex = new HashMap<>(); Map<Long, List<Street>> streetIndex = new HashMap<>();
        Map<Long, List<Bounds>> islandIndex = new HashMap<>();
        for (Bounds island : islands) index(islandIndex, island, island);
        Map<Long, List<Tree>> treeIndex = new HashMap<>(); Map<Long, List<Lamp>> lampIndex = new HashMap<>();
        for (Plot plot : plots) index(plotIndex, new Bounds(plot.x - COURT, plot.z - COURT, plot.maxX() + COURT, plot.maxZ() + COURT), plot);
        List<Tree> trees = new ArrayList<>(); List<Lamp> lamps = new ArrayList<>();
        for (Plot plot : plots) {
            Lamp entryLight = new Lamp(plot.x - 4, plot.z - 4);
            lamps.add(entryLight); index(lampIndex, new Bounds(entryLight.x, entryLight.z, entryLight.x, entryLight.z), entryLight);
            if (planted(plot)) for (int x : new int[] {plot.x - 4, plot.maxX() + 4}) {
                Tree tree = new Tree(x, plot.maxZ() + 4); trees.add(tree);
                index(treeIndex, new Bounds(tree.x - 2, tree.z - 2, tree.x + 2, tree.z + 2), tree);
            }
        }
        for (Garden garden : AE_GARDENS) {
            for (int dx : new int[] {-44, 43}) for (int dz : new int[] {-20, 19}) {
                Tree tree = new Tree(garden.x + dx, garden.z + dz); trees.add(tree);
                index(treeIndex, new Bounds(tree.x - 2, tree.z - 2, tree.x + 2, tree.z + 2), tree);
            }
            for (int dx : new int[] {-50, 49}) {
                Lamp lamp = new Lamp(garden.x + dx, garden.z + 10); lamps.add(lamp);
                index(lampIndex, new Bounds(lamp.x, lamp.z, lamp.x, lamp.z), lamp);
            }
        }
        for (Workshop pad : pads) for (int x : new int[] {pad.x - 4, pad.x + pad.width + 3})
            for (int z : new int[] {pad.z - 4, pad.z + pad.depth + 3}) {
                Tree tree = new Tree(x, z); trees.add(tree);
                index(treeIndex, new Bounds(x - 2, z - 2, x + 2, z + 2), tree);
            }
        for (Street street : streets) {
            Bounds extent = street.vertical ? new Bounds(street.axis - street.halfWidth, street.start, street.axis + street.halfWidth - 1, street.end)
                    : new Bounds(street.start, street.axis - street.halfWidth, street.end, street.axis + street.halfWidth - 1);
            index(streetIndex, extent, street);
            for (int along = ((street.start + 31) / 32) * 32; along <= street.end; along += 32) for (int side : new int[] {-1, 1}) {
                int across = street.axis + (side > 0 ? street.halfWidth - 2 : -street.halfWidth + 1);
                int x = street.vertical ? across : along, z = street.vertical ? along : across;
                if (safeGarden(x, z, plots, pads, streets, street)) {
                    Tree tree = new Tree(x, z); trees.add(tree); index(treeIndex, new Bounds(x - 2, z - 2, x + 2, z + 2), tree);
                }
                int lampAcross = street.axis + (side > 0 ? street.halfWidth - 5 : -street.halfWidth + 4);
                int lx = street.vertical ? lampAcross : along + 16, lz = street.vertical ? along + 16 : lampAcross;
                if (safeGarden(lx, lz, plots, pads, streets, street)) {
                    Lamp lamp = new Lamp(lx, lz); lamps.add(lamp); index(lampIndex, new Bounds(lx, lz, lx, lz), lamp);
                }
            }
        }
        Set<Long> tiles = new LinkedHashSet<>(); tiles.addAll(plotIndex.keySet()); tiles.addAll(streetIndex.keySet()); tiles.addAll(treeIndex.keySet()); tiles.addAll(lampIndex.keySet()); tiles.addAll(islandIndex.keySet());
        Map<Long, Local> index = new HashMap<>();
        for (long tile : tiles) index.put(tile, new Local(List.copyOf(plotIndex.getOrDefault(tile, List.of())),
                List.copyOf(streetIndex.getOrDefault(tile, List.of())), List.copyOf(treeIndex.getOrDefault(tile, List.of())),
                List.copyOf(lampIndex.getOrDefault(tile, List.of())), List.copyOf(islandIndex.getOrDefault(tile, List.of()))));
        return new Plan(List.copyOf(plots), List.copyOf(streets), pads, List.copyOf(districts), Map.copyOf(index), bounds,
                Math.max(9, plots.stream().mapToInt(plot -> plot.height + 2).max().orElseThrow()));
    }

    private static boolean safeGarden(int x, int z, List<Plot> plots, List<Workshop> pads, List<Street> streets, Street owner) {
        if (AE_BOUNDS.contains(x, z)) return false;
        for (Plot plot : plots) if (x >= plot.x - COURT - 2 && x <= plot.maxX() + COURT + 2
                && z >= plot.z - COURT - 2 && z <= plot.maxZ() + COURT + 2) return false;
        for (Workshop pad : pads) if (x >= pad.x - 2 && x <= pad.x + pad.width + 1 && z >= pad.z - 2 && z <= pad.z + pad.depth + 1) return false;
        for (Street street : streets) if (street != owner && street.contains(x, z)) return false;
        return true;
    }
    private static <T> void index(Map<Long, List<T>> map, Bounds bounds, T item) {
        for (int x = Math.floorDiv(bounds.minX, TILE); x <= Math.floorDiv(bounds.maxX, TILE); x++)
            for (int z = Math.floorDiv(bounds.minZ, TILE); z <= Math.floorDiv(bounds.maxZ, TILE); z++)
                map.computeIfAbsent(key(x, z), ignored -> new ArrayList<>()).add(item);
    }
}


