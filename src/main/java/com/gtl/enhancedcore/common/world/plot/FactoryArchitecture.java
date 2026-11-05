package com.gtl.enhancedcore.common.world.plot;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Deterministic architectural shell for the independent Quartz Works preset. */
public final class FactoryArchitecture {
    public static final String RESOURCE = "/data/gtl_enhancedcore/worldgen/factory_campus_architecture.json";
    public record Hall(String id, String department, String stage, int x, int z, int width, int depth,
                       int clearHeight, int variant, boolean outdoor, String entranceFacing) {
        public Hall {
            if (width < 40 || depth < 48 || clearHeight < 20)
                throw new IllegalArgumentException("Factory hall is too small: " + id);
            if (!Set.of("south", "north", "east", "west").contains(entranceFacing))
                throw new IllegalArgumentException("Unknown factory entrance facing: " + entranceFacing);
            if ((entranceFacing.equals("east") || entranceFacing.equals("west")) && width != depth)
                throw new IllegalArgumentException("Rotated factory hall must be square: " + id);
        }
        public Hall(String id, String department, String stage, int x, int z, int width, int depth,
                    int clearHeight, int variant) {
            this(id, department, stage, x, z, width, depth, clearHeight, variant, false, "north");
        }
        public Hall(String id, String department, String stage, int x, int z, int width, int depth,
                    int clearHeight, int variant, boolean outdoor) {
            this(id, department, stage, x, z, width, depth, clearHeight, variant, outdoor, "north");
        }
        public int maxX() { return x + width - 1; }
        public int maxZ() { return z + depth - 1; }
        public boolean contains(int px, int pz) { return px >= x && px <= maxX() && pz >= z && pz <= maxZ(); }
        public PlannedCampus.Bounds bounds() { return new PlannedCampus.Bounds(x, z, maxX(), maxZ()); }
        public PlannedCampus.Bounds productionBounds() {
            return switch (entranceFacing) {
                case "south" -> new PlannedCampus.Bounds(x + 12, z + 12, maxX() - 12, maxZ() - 22);
                case "east" -> new PlannedCampus.Bounds(x + 12, z + 12, maxX() - 22, maxZ() - 12);
                case "west" -> new PlannedCampus.Bounds(x + 22, z + 12, maxX() - 12, maxZ() - 12);
                default -> new PlannedCampus.Bounds(x + 12, z + 22, maxX() - 12, maxZ() - 12);
            };
        }
        /** Exact door axis is between the two center blocks for an even-width hall. */
        public double entranceX() { return entranceFacing.equals("east") ? maxX() + 0.5
                : entranceFacing.equals("west") ? x - 0.5 : x + (width - 1) / 2.0; }
        public double entranceZ() { return entranceFacing.equals("south") ? maxZ() + 0.5
                : entranceFacing.equals("north") ? z - 0.5 : z + (depth - 1) / 2.0; }
        public double arrivalX() { return entranceX() + (entranceFacing.equals("east") ? 3.5 : entranceFacing.equals("west") ? -3.5 : 0); }
        public double arrivalZ() { return entranceZ() + (entranceFacing.equals("south") ? 3.5 : entranceFacing.equals("north") ? -3.5 : 0); }
        public int signX() { return entranceFacing.equals("east") ? maxX() + 2
                : entranceFacing.equals("west") ? x - 2 : x + width / 2 + 16; }
        public int signZ() { return entranceFacing.equals("south") ? maxZ() + 2
                : entranceFacing.equals("north") ? z - 2 : z + depth / 2 + 16; }
    }
    private record Design(Map<String, String> palette, Map<String, String> accents, Set<String> materials,
                          int facadeBay, int windowStorey, int sawtoothBay, int roofRib, int ventX, int ventZ,
                          int tankSpacing, int chimneySpacing, int fenceSpacing, int landscapePeriod) {}
    private static final Map<String, Renderer> RENDERERS = new java.util.concurrent.ConcurrentHashMap<>();
    private static final Renderer DEFAULT = forResource(RESOURCE);
    private FactoryArchitecture() {}

    /** Immutable renderers are isolated by their authored architecture resource. */
    public static Renderer forResource(String resource) {
        return RENDERERS.computeIfAbsent(resource, path -> new Renderer(load(path)));
    }
    public static Set<String> materials() { return DEFAULT.materials(); }
    public static int maxHeight(Hall hall) { return DEFAULT.maxHeight(hall); }
    public static String surface(Hall hall, int x, int z) { return DEFAULT.surface(hall, x, z); }
    public static String above(Hall hall, int x, int y, int z) { return DEFAULT.above(hall, x, y, z); }
    public static int top(Hall hall, int x, int z) { return DEFAULT.top(hall, x, z); }
    static String block(String key) { return DEFAULT.block(key); }
    static String accent(Hall hall) { return DEFAULT.accent(hall); }

    /** Reusable industrial geometry engine, with a resource-local palette and rhythms. */
    public static final class Renderer {
        private final Design design;
        private Renderer(Design design) { this.design = design; }
        public Set<String> materials() { return design.materials; }
        int landscapePeriod() { return design.landscapePeriod; }
        String gardenSurface(int u, int v) {
            int edge=Math.max(Math.abs(u),Math.abs(v));
            if (edge>11) return null;
            if (edge==11) return block("base");
            return Math.abs(v)<=2 || Math.abs(u)<=2 ? block("floor") : block("grass");
        }
        String gardenAbove(int u,int y,int v) {
            if (Math.abs(u)>11 || Math.abs(v)>11 || y>7) return null;
            for (int sign:new int[]{-1,1}) {
                int dx=u-sign*6,dz=v-sign*6;
                if (dx==0 && dz==0 && y<=4) return block("trunk");
                if (y>=4 && y<=6 && Math.abs(dx)<=2 && Math.abs(dz)<=2
                        && Math.abs(dx)+Math.abs(dz)<= (y==6?2:3)) return block("treeLeaves");
            }
            if (Math.abs(u)>=4 && Math.abs(u)<=8 && Math.abs(v)>=9) {
                if (y==1) return block("quartz");
                if (y==2) return Math.abs(u)%2==0 ? block("flowers") : block("hedge");
            }
            if (Math.abs(u)>=4 && Math.abs(u)<=8 && Math.abs(v)==3 && y==1) return block("seat");
            if (Math.abs(u)==9 && Math.abs(v)==3) return y<=2 ? block("steel") : y==3 ? block("light") : null;
            return null;
        }
        public int maxHeight(Hall hall) { return hall.outdoor ? Math.max(hall.clearHeight, 18) : hall.clearHeight + 18; }
        String block(String key) { return design.palette.get(key); }
        String accent(Hall hall) {
            return design.accents.getOrDefault(hall.department, design.accents.get("default"));
        }

        /** The machine floor is continuous; all visual subdivision is flush with this floor. */
        public String surface(Hall hall, int x, int z) {
            if (!hall.contains(x, z)) return null;
            if (hall.outdoor) return FactoryOutdoor.surface(this, hall, x, z);
            int[] local = oriented(hall, x - hall.x, z - hall.z);
            int u = local[0], v = local[1];
            int freight = Math.min(Math.abs(u - hall.width / 4), Math.abs(u - 3 * hall.width / 4));
            if (v >= 3 && v <= 20 && freight <= 8)
                return v == 3 || v == 20 || freight == 8 ? block("yellow") : block("dock");
            int side = Math.min(u, hall.width - 1 - u);
            if (u == 0 || v == 0 || u == hall.width - 1 || v == hall.depth - 1) return block("dark");
            if (v <= 12) {
                if (Math.abs(2 * u - hall.width + 1) <= 11) return block("floor");
                if (v == 2 || v == 11) return accent(hall);
                return block("quartz");
            }
            if (side <= 5 || v >= hall.depth - 6) return block("quartz");
            if (side == 11 || v == 21 || v == hall.depth - 12) return accent(hall);
            if (u % 24 == 0 || v % 24 == 0) return block("quartz");
            return block("floor");
        }

        public String above(Hall hall, int x, int y, int z) {
            if (!hall.contains(x, z) || y < 1 || y > maxHeight(hall)) return null;
            if (hall.outdoor) return FactoryOutdoor.above(this, hall, x, y, z);
            int rawU = x - hall.x, rawV = z - hall.z;
            int[] local = oriented(hall, rawU, rawV);
            int u = local[0], v = local[1], height = hall.clearHeight;
            // Machine envelopes and their ten-block headroom never contain authored decoration.
            if (u >= 12 && u <= hall.width - 13 && v >= 22 && v <= hall.depth - 13 && y < height)
                return null;
            String front = frontAnnex(hall, u, y, v);
            if (front != null) return front;
            String service = serviceBand(hall, u, y, v);
            if (service != null) return service;
            if (!roofFootprint(hall, u, v)) return null;
            int roof = roofY(hall, u, v);
            boolean rib = Math.floorMod(v - 12, design.roofRib) == 0;
            boolean rim = roofRim(hall, u, v);
            if (ventColumn(u, v) && y > roof && y <= roof + 4)
                return y == roof + 4 ? block("grate") : y == roof + 1 ? block("dark") : block("vent");
            if (rim && y == roof + 1) return block("slab");
            if (y == roof) {
                if (rim) return eave(hall, u, v);
                if (rib) return block("dark");
                if (roofGlazing(hall, u, v)) return block("glass");
                return switch (Math.floorMod(hall.variant, 4)) {
                    case 0 -> Math.floorMod(v - 12, design.sawtoothBay) % 2 == 1 ? block("stairSouth") : block("quartz");
                    case 1 -> Math.abs(2 * u - hall.width + 1) < 7 ? block("quartz")
                            : u < hall.width / 2 ? block("stairEast") : block("stairWest");
                    case 2 -> block("quartz");
                    default -> u % gableWidth(hall) < gableWidth(hall) / 2 ? block("stairEast") : block("stairWest");
                };
            }
            if (y >= height) {
                String screen = roofScreen(hall, u, y, v);
                if (screen != null) return screen;
                if (wallFootprint(hall, u, v) && y < roof)
                    return u % 8 == 0 || v % 8 == 0 ? block("steel") : block("glass");
                return null;
            }
            if (y == height - 1 && (rib || rim)) return block("quartz");
            if (!wallFootprint(hall, u, v)) return null;
            boolean side = u <= 6 || u >= hall.width - 7;
            int along = side ? v - 13 : u - 5;
            boolean pier = Math.floorMod(along, design.facadeBay) < 2;
            boolean frontGate = v <= 14 && frontGate(hall, u, y);
            if (frontGate) return null;
            if (y <= 2) return block("base");
            if (pier) return y % 18 == 0 ? block("dark") : block("steel");
            if (y == 4 || y == height - 7) return y == 4 ? block("yellow") : accent(hall);
            if (y == 3 || y == height - 1 || Math.floorMod(y, design.windowStorey) == 2) return block("topSlab");
            boolean lightStrip = y == 5 && Math.floorMod(along, design.facadeBay) == 6;
            if (lightStrip) return block("light");
            int windowPhase = Math.floorMod(y, design.windowStorey * 2);
            boolean glazing = y >= height - 4 || windowPhase >= design.windowStorey - 2
                    && windowPhase <= design.windowStorey;
            if (glazing) return Math.floorMod(along, 4) == 0 ? block("dark") : block("glass");
            return block("cladding");
        }

        /** Highest occupied block in this column, without scanning a tall machine hall. */
        public int top(Hall hall, int x, int z) {
            if (!hall.contains(x, z)) return 0;
            if (hall.outdoor) return FactoryOutdoor.top(this, hall, x, z);
            int[] local = oriented(hall, x - hall.x, z - hall.z);
            int u = local[0], v = local[1];
            if (roofFootprint(hall, u, v)) {
                int roof = roofY(hall, u, v);
                return roof + (ventColumn(u, v) ? 4 : roofRim(hall, u, v) ? 1 : 0);
            }
            for (int y = 30; y > 0; y--) if (above(hall, x, y, z) != null) return y;
            return 0;
        }

        private int[] oriented(Hall hall, int u, int v) {
            return switch (hall.entranceFacing()) {
                case "south" -> new int[] {u, hall.depth - 1 - v};
                case "east" -> new int[] {v, hall.width - 1 - u};
                case "west" -> new int[] {hall.depth - 1 - v, u};
                default -> new int[] {u, v};
            };
        }

        private String frontAnnex(Hall hall, int u, int y, int v) {
            if (v > 12) return null;
            int middle = hall.width / 2, distance = Math.abs(u - middle);
            int loading = Math.min(Math.abs(u - hall.width / 4), Math.abs(u - 3 * hall.width / 4));
            // Two rolling freight doors and their raised loading docks break up the civic facade.
            if (v <= 2 && loading <= 8) {
                if (y == 1) return block("dock");
                if (y == 2 && (loading == 8 || Math.floorMod(v, 2) == 0)) return block("yellow");
                if (y == 3 && loading <= 6) return block("dark");
            }
            // Loading apron: a steel canopy spans the centered personnel gate and two freight doors.
            if (v >= 2 && v <= 12 && distance <= 13) {
                boolean dock = loading <= 5;
                if (distance == 13 && (v == 3 || v == 11) && y <= 8) return block("steel");
                if (y == 9 && v >= 3 && v <= 11 && (distance <= 13))
                    return v == 3 || v == 11 || distance == 13 ? block("dark") : block("cladding");
                if (dock && y == 1 && v >= 4 && v <= 12) return block("base");
                if (dock && y == 2 && v == 4) return block("yellow");
                if (y == 10 && distance == 13 && v == 7) return block("light");
            }
            boolean leftWing = u >= 13 && u <= middle - 14;
            boolean rightWing = u >= middle + 14 && u <= hall.width - 14;
            if ((!leftWing && !rightWing) || v < 4 || v > 10 || y > 8) return null;
            int wingStart = leftWing ? 13 : middle + 14, wingEnd = leftWing ? middle - 14 : hall.width - 14;
            if (y == 8) return v == 4 || u == wingStart || u == wingEnd ? block("slab") : block("cladding");
            boolean wall = v == 4 || v == 10 || u == wingStart || u == wingEnd;
            if (!wall) return null;
            if (v == 4 && Math.abs(u - (wingStart + wingEnd) / 2) <= 1 && y <= 3) return null;
            if (y == 1 || y == 7) return block("base");
            if (u == wingStart || u == wingEnd || u % 6 == 0) return block("steel");
            return y >= 3 && y <= 5 ? block("clearGlass") : block("cladding");
        }

        private String serviceBand(Hall hall, int u, int y, int v) {
            int side = Math.min(u, hall.width - 1 - u);
            if (side > 11 || v < 15 || v >= hall.depth - 9) return null;
            boolean west = u < hall.width / 2;
            int along = Math.floorMod(v - 17, 48);
            int pipeTop = Math.min(28, hall.clearHeight - 2);
            String tank = tankStack(hall, u, y, v, side);
            if (tank != null) return tank;
            // Tall exhaust risers sit in the perimeter service strip, with a cap and a warning band.
            if (side == 1 && Math.floorMod(v - 26, design.chimneySpacing) <= 1 && y <= 20) {
                if (y == 1 || y == 20) return block("dark");
                if (y == 14) return block("yellow");
                return block("chimney");
            }
            // Open maintenance fans punctuate the catwalk without forming a second facade.
            if (side == 6 && Math.floorMod(v - 22, design.chimneySpacing) == 0 && y == 8)
                return block("fan");
            if (side == 1 && along == 4 && y <= pipeTop)
                return y == pipeTop || y % 6 == 0 ? block("dark") : block("pipe");
            if ((side == 10 || side == 11) && Math.floorMod(v - 29, 96) <= 2 && y >= 3 && y <= 18)
                return y == 3 || y == 18 ? block("dark") : block("pipe");
            if (side == 9 && Math.floorMod(v - 30, 96) <= 4 && y == 3) return block("floorSlab");
            if (side == 2 && along >= 2 && along <= 6 && y >= 2 && y <= 7)
                return y == 2 || y == 7 ? block("base") : west ? block("louvreWest") : block("louvreEast");
            if (side >= 3 && side <= 4 && Math.floorMod(v - 13, design.facadeBay) == 0 && y < hall.clearHeight)
                return y == 2 || y == hall.clearHeight - 1 ? block("dark") : block("steel");
            // Narrow perimeter catwalks stay entirely outside the reserved production volume.
            boolean ramp = (side == 8 || side == 9) && v >= 15 && v <= 22;
            if (side >= 7 && side <= 10 && y == 8 && !ramp) return block("floorSlab");
            if (side == 11 && y == 9 && v > 22) return block("barsZ");
            if (side == 11 && v > 22 && Math.floorMod(v - 22, design.fenceSpacing) == 0)
                return y == 1 ? block("steel") : y == 2 ? block("barsZ") : null;
            if (ramp && y == v - 14) return block("accessStair");
            if (side == 7 && v >= 15 && v <= 22 && y <= v - 14) return block("base");
            return null;
        }

        private String tankStack(Hall hall, int u, int y, int v, int side) {
            if (side > 6 || v < 24 || v >= hall.depth - 18) return null;
            int centerU = u < hall.width / 2 ? 4 : hall.width - 5;
            int centerV = 30 + (int) Math.round((v - 30) / (double) design.tankSpacing) * design.tankSpacing;
            int radialU = Math.abs(u - centerU), radialV = Math.abs(v - centerV);
            if (radialU > 2 || radialV > 2) return null;
            int radius = Math.max(radialU, radialV);
            if (y == 1) return block("base");
            if (y >= 2 && y <= 9 && radius == 2)
                return y == 4 || y == 8 ? block("tankBand") : block("tank");
            if (y == 10 && radius <= 1) return block("tankBand");
            if (y == 11 && radialU == 0 && radialV == 0) return block("chimney");
            return null;
        }

        private boolean frontGate(Hall hall, int u, int y) {
            int distance = Math.abs(2 * u - hall.width + 1);
            if (distance <= 11 && y <= 12) return true;
            if (hall.width < 100) return false;
            int loading = Math.min(Math.abs(u - hall.width / 4), Math.abs(u - 3 * hall.width / 4));
            return loading <= 4 && y <= 9;
        }
        private boolean footprint(int u, int v, int minU, int minV, int maxU, int maxV, int chamfer) {
            if (u < minU || u > maxU || v < minV || v > maxV) return false;
            return Math.min(u - minU, maxU - u) + Math.min(v - minV, maxV - v) >= chamfer;
        }
        private boolean roofFootprint(Hall hall, int u, int v) {
            return footprint(u, v, 3, 11, hall.width - 4, hall.depth - 4, 4);
        }
        private boolean wallFootprint(Hall hall, int u, int v) {
            if (!footprint(u, v, 5, 13, hall.width - 6, hall.depth - 6, 4)) return false;
            return !footprint(u, v, 7, 15, hall.width - 8, hall.depth - 8, 3);
        }
        private boolean roofRim(Hall hall, int u, int v) {
            return !footprint(u, v, 4, 12, hall.width - 5, hall.depth - 5, 4);
        }
        private int gableWidth(Hall hall) {
            int span = hall.width - 6, bays = Math.max(1, (span + 39) / 40);
            return (span + bays - 1) / bays;
        }
        private int monitorDistance(Hall hall, int u) {
            return Math.min(Math.abs(u - hall.width / 4), Math.abs(u - 3 * hall.width / 4));
        }
        private int roofY(Hall hall, int u, int v) {
            int rise = switch (Math.floorMod(hall.variant, 4)) {
                case 0 -> Math.floorMod(v - 12, design.sawtoothBay) / 2;
                case 1 -> 12 - Math.min(12, Math.abs(2 * u - hall.width + 1) * 12 / (hall.width - 1));
                case 2 -> Math.max(0, 10 - Math.max(0, monitorDistance(hall, u) - 5) * 2);
                default -> {
                    int period = gableWidth(hall), phase = Math.floorMod(u - 3, period);
                    yield Math.min(phase, period - 1 - phase) / 2;
                }
            };
            return hall.clearHeight + rise;
        }
        private boolean roofGlazing(Hall hall, int u, int v) {
            if (hall.department.equals("space") && Math.floorMod(u, 16) >= 3 && Math.floorMod(u, 16) <= 12)
                return true;
            return switch (Math.floorMod(hall.variant, 4)) {
                case 0 -> Math.floorMod(v - 12, design.sawtoothBay) >= design.sawtoothBay - 3;
                case 1 -> Math.floorMod(v, 26) >= 11 && Math.floorMod(v, 26) <= 15;
                case 2 -> monitorDistance(hall, u) <= 5 && Math.floorMod(v, 16) >= 2;
                default -> Math.floorMod(v, 24) >= 10 && Math.floorMod(v, 24) <= 14;
            };
        }
        private String roofScreen(Hall hall, int u, int y, int v) {
            if (Math.floorMod(hall.variant, 4) == 0
                    && Math.floorMod(v - 12, design.sawtoothBay) == design.sawtoothBay - 1
                    && y <= hall.clearHeight + 6)
                return u % 8 == 0 ? block("quartz") : block("glass");
            if (Math.floorMod(hall.variant, 4) == 2 && monitorDistance(hall, u) == 6
                    && y <= hall.clearHeight + 8)
                return v % 8 == 0 ? block("quartz") : block("glass");
            return null;
        }
        private boolean ventColumn(int u, int v) {
            int a = Math.floorMod(u, design.ventX), b = Math.floorMod(v, design.ventZ);
            return a >= 15 && a <= 17 && b >= 23 && b <= 25;
        }
        private String eave(Hall hall, int u, int v) {
            if (u <= 4) return block("stairEast");
            if (u >= hall.width - 5) return block("stairWest");
            return v <= 12 ? block("stairSouth") : block("stairNorth");
        }
    }

    private static Design load(String resource) {
        var stream = FactoryArchitecture.class.getResourceAsStream(resource);
        if (stream == null) throw new IllegalStateException("Missing factory architecture resource " + resource);
        try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
            if (json.get("schemaVersion").getAsInt() != 1) throw new IllegalArgumentException("Unsupported factory architecture");
            Map<String, String> palette = strings(json.getAsJsonObject("palette"));
            palette.putIfAbsent("cladding", "minecraft:gray_concrete");
            palette.putIfAbsent("steel", "minecraft:polished_basalt[axis=y]");
            palette.putIfAbsent("yellow", "minecraft:yellow_concrete");
            palette.putIfAbsent("dock", "minecraft:deepslate_tiles");
            palette.putIfAbsent("tank", "minecraft:iron_block");
            palette.putIfAbsent("tankBand", "minecraft:orange_concrete");
            palette.putIfAbsent("chimney", "minecraft:cut_copper");
            palette.putIfAbsent("fan", "minecraft:iron_trapdoor[open=false,half=top,facing=north]");
            palette.putIfAbsent("grass", "minecraft:grass_block");
            palette.putIfAbsent("trunk", "minecraft:oak_log[axis=y]");
            palette.putIfAbsent("treeLeaves", "minecraft:oak_leaves[persistent=true]");
            palette.putIfAbsent("flowers", "minecraft:flowering_azalea_leaves[persistent=true]");
            palette.putIfAbsent("seat", "minecraft:spruce_slab[type=bottom]");
            Map<String, String> accents = strings(json.getAsJsonObject("accents"));
            Set<String> materials = new HashSet<>(palette.values()); materials.addAll(accents.values());
            JsonObject rhythm = json.getAsJsonObject("rhythm");
            JsonObject industrial = json.has("industrial") ? json.getAsJsonObject("industrial") : new JsonObject();
            return new Design(Map.copyOf(palette), Map.copyOf(accents), Set.copyOf(materials),
                    rhythm.get("facadeBay").getAsInt(), rhythm.get("windowStorey").getAsInt(),
                    rhythm.get("sawtoothBay").getAsInt(), rhythm.get("roofRib").getAsInt(),
                    rhythm.get("roofVentX").getAsInt(), rhythm.get("roofVentZ").getAsInt(),
                    intValue(industrial, "tankSpacing", 96), intValue(industrial, "chimneySpacing", 72),
                    intValue(industrial, "fenceSpacing", 16), intValue(industrial, "landscapePeriod", 192));
        } catch (IOException error) { throw new IllegalStateException("Cannot load factory architecture", error); }
    }
    private static Map<String, String> strings(JsonObject object) {
        Map<String, String> result = new HashMap<>();
        object.entrySet().forEach(entry -> result.put(entry.getKey(), entry.getValue().getAsString()));
        return result;
    }
    private static int intValue(JsonObject object, String key, int fallback) {
        return object.has(key) ? object.get(key).getAsInt() : fallback;
    }
}
