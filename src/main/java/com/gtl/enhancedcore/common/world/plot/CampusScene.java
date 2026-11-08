package com.gtl.enhancedcore.common.world.plot;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Immutable, block-sampled authored scenery. Shapes and instances retain their file ordering. */
public final class CampusScene {
    private static final int TILE = 16;
    private static final int COORDINATE_LIMIT = 1_000_000;
    private static final long MAX_TILE_REFERENCES = 1_000_000;
    private final Map<Long, List<PlacedShape>> tiles;
    private final Set<String> materials;
    private final int maxHeight;
    private final int shapeCount;

    public CampusScene(JsonObject architecture) {
        if (integer(architecture.get("schemaVersion"), "schemaVersion") != 1)
            throw new IllegalArgumentException("Unsupported scene architecture schema");
        JsonObject scene = object(architecture.get("scene"), "scene");
        JsonObject paletteJson = object(scene.get("palette"), "scene.palette");
        Map<String, String> palette = new HashMap<>();
        for (var entry : paletteJson.entrySet()) {
            JsonElement value = entry.getValue();
            if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()
                    || !value.getAsString().matches("[a-z0-9_.-]+:[a-z0-9_./-]+(?:\\[[a-z0-9_=,]+\\])?"))
                throw new IllegalArgumentException("Invalid scene material: " + entry.getKey());
            palette.put(entry.getKey(), value.getAsString());
        }
        Set<String> used = new HashSet<>(palette.values());
        used.add("minecraft:air");
        materials = Set.copyOf(used);
        Map<String, List<Shape>> templates = new HashMap<>();
        if (scene.has("templates")) for (var entry : object(scene.get("templates"), "scene.templates").entrySet())
            templates.put(entry.getKey(), shapes(array(entry.getValue(), "template " + entry.getKey()), palette));
        List<PlacedShape> placed = new ArrayList<>();
        // Instances are the reusable background; direct shapes are authored overrides.
        if (scene.has("instances")) for (var element : array(scene.get("instances"), "scene.instances")) {
            JsonObject instance = object(element, "instance");
            String name = string(instance.get("template"), "instance.template");
            List<Shape> template = templates.get(name);
            if (template == null) throw new IllegalArgumentException("Unknown scene template: " + name);
            int x = coordinate(instance.get("x")), y = coordinate(instance.get("y")), z = coordinate(instance.get("z"));
            int rotation = instance.has("rotation") ? integer(instance.get("rotation"), "rotation") : 0;
            if (rotation != 0 && rotation != 90 && rotation != 180 && rotation != 270)
                throw new IllegalArgumentException("Scene rotation must be 0, 90, 180 or 270");
            for (Shape shape : template) placed.add(new PlacedShape(shape, x, y, z, rotation));
        }
        if (scene.has("shapes")) for (Shape shape : shapes(array(scene.get("shapes"), "scene.shapes"), palette))
            placed.add(new PlacedShape(shape, 0, 0, 0, 0));
        Map<Long, List<PlacedShape>> buckets = new HashMap<>();
        int highest = 0;
        long references = 0;
        for (PlacedShape shape : placed) {
            highest = Math.max(highest, shape.bounds.maxY);
            int minX = Math.floorDiv(shape.bounds.minX, TILE), maxX = Math.floorDiv(shape.bounds.maxX, TILE);
            int minZ = Math.floorDiv(shape.bounds.minZ, TILE), maxZ = Math.floorDiv(shape.bounds.maxZ, TILE);
            references += (long) (maxX - minX + 1) * (maxZ - minZ + 1);
            if (references > MAX_TILE_REFERENCES)
                throw new IllegalArgumentException("Scene exceeds horizontal tile reference limit");
            for (int x = minX; x <= maxX; x++) for (int z = minZ; z <= maxZ; z++)
                buckets.computeIfAbsent(key(x, z), unused -> new ArrayList<>()).add(shape);
        }
        buckets.replaceAll((key, value) -> List.copyOf(value));
        // Packed X/Z keys share many Long hashes. Map.copyOf uses linear probing,
        // turning large scene construction and every voxel lookup into long scans.
        // The backing map is private and complete; its lists are already immutable.
        tiles = java.util.Collections.unmodifiableMap(buckets);
        maxHeight = highest;
        shapeCount = placed.size();
    }

    public Set<String> materials() { return materials; }
    public int maxHeight() { return maxHeight; }
    public int shapeCount() { return shapeCount; }

    /** Null means no authored shape; an explicit air shape remains minecraft:air. */
    public String blockAt(int x, int y, int z) {
        List<PlacedShape> nearby = nearby(x, z);
        for (int i = nearby.size() - 1; i >= 0; i--) {
            PlacedShape shape = nearby.get(i);
            if (shape.contains(x, y, z)) return shape.shape.material;
        }
        return null;
    }

    /** Conservative column ceiling from intersecting AABBs, including authored air cutters. */
    public int topAt(int x, int z) {
        int result = 0;
        for (PlacedShape shape : nearby(x, z)) if (shape.bounds.containsColumn(x, z))
            result = Math.max(result, shape.bounds.maxY);
        return result;
    }

    private List<PlacedShape> nearby(int x, int z) {
        return tiles.getOrDefault(key(Math.floorDiv(x, TILE), Math.floorDiv(z, TILE)), List.of());
    }
    private static long key(int x, int z) { return (long) x << 32 ^ z & 0xffffffffL; }

    private record Bounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        Bounds {
            if (minX > maxX || minY > maxY || minZ > maxZ
                    || Math.abs((long) minX) > COORDINATE_LIMIT || Math.abs((long) maxX) > COORDINATE_LIMIT
                    || Math.abs((long) minY) > COORDINATE_LIMIT || Math.abs((long) maxY) > COORDINATE_LIMIT
                    || Math.abs((long) minZ) > COORDINATE_LIMIT || Math.abs((long) maxZ) > COORDINATE_LIMIT)
                throw new IllegalArgumentException("Invalid scene bounds");
        }
        boolean containsColumn(int x, int z) { return x >= minX && x <= maxX && z >= minZ && z <= maxZ; }
        boolean contains(int x, int y, int z) { return containsColumn(x, z) && y >= minY && y <= maxY; }
        int min(int axis) { return axis == 0 ? minX : axis == 1 ? minY : minZ; }
        int max(int axis) { return axis == 0 ? maxX : axis == 1 ? maxY : maxZ; }
    }

    private record Point(double u, double v) {}
    private record Shape(String type, Bounds bounds, String material, int inner, int axis, List<Point> points) {
        boolean contains(int x, int y, int z) {
            if (!bounds.contains(x, y, z) || !volume(x, y, z, 0)) return false;
            return inner == 0 || !volume(x, y, z, inner);
        }
        private boolean volume(int x, int y, int z, int inset) {
            int minX = bounds.minX + inset, minY = bounds.minY + inset, minZ = bounds.minZ + inset;
            int maxX = bounds.maxX - inset, maxY = bounds.maxY - inset, maxZ = bounds.maxZ - inset;
            if (x < minX || x > maxX || y < minY || y > maxY || z < minZ || z > maxZ) return false;
            if (type.equals("box")) return true;
            if (type.equals("ellipsoid")) return normalized(x, minX, maxX) + normalized(y, minY, maxY)
                    + normalized(z, minZ, maxZ) <= 1.0 + 1.0e-10;
            int u = axis == 0 ? y : x, v = axis == 2 ? y : z;
            int minU = axis == 0 ? minY : minX, maxU = axis == 0 ? maxY : maxX;
            int minV = axis == 2 ? minY : minZ, maxV = axis == 2 ? maxY : maxZ;
            if (type.equals("polygon")) return inPolygon(u, v, points);
            double radius = 1;
            if (type.equals("cone")) {
                int along = axis == 0 ? x : axis == 1 ? y : z;
                radius = (bounds.max(axis) - along + 0.5) / (bounds.max(axis) - bounds.min(axis) + 1.0);
            }
            return normalized(u, minU, maxU) + normalized(v, minV, maxV) <= radius * radius + 1.0e-10;
        }
    }

    private static double normalized(int value, int min, int max) {
        double distance = (2.0 * value - min - max) / (max - min + 1.0);
        return distance * distance;
    }
    private static boolean inPolygon(double u, double v, List<Point> points) {
        boolean inside = false;
        Point previous = points.getLast();
        for (Point point : points) {
            double cross = (u - previous.u) * (point.v - previous.v) - (v - previous.v) * (point.u - previous.u);
            if (Math.abs(cross) < 1.0e-9 && u >= Math.min(previous.u, point.u) && u <= Math.max(previous.u, point.u)
                    && v >= Math.min(previous.v, point.v) && v <= Math.max(previous.v, point.v)) return true;
            if ((point.v > v) != (previous.v > v)
                    && u < (previous.u - point.u) * (v - point.v) / (previous.v - point.v) + point.u) inside = !inside;
            previous = point;
        }
        return inside;
    }

    private static final class PlacedShape {
        final Shape shape;
        final int x, y, z, rotation;
        final Bounds bounds;
        PlacedShape(Shape shape, int x, int y, int z, int rotation) {
            this.shape = shape; this.x = x; this.y = y; this.z = z; this.rotation = rotation;
            Bounds b = shape.bounds;
            int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
            for (int px : new int[] {b.minX, b.maxX}) for (int pz : new int[] {b.minZ, b.maxZ}) {
                int rx = rotatedX(px, pz, rotation) + x, rz = rotatedZ(px, pz, rotation) + z;
                minX = Math.min(minX, rx); maxX = Math.max(maxX, rx);
                minZ = Math.min(minZ, rz); maxZ = Math.max(maxZ, rz);
            }
            bounds = new Bounds(minX, b.minY + y, minZ, maxX, b.maxY + y, maxZ);
        }
        boolean contains(int px, int py, int pz) {
            if (!bounds.contains(px, py, pz)) return false;
            int localX = px - x, localZ = pz - z, inverse = (360 - rotation) % 360;
            return shape.contains(rotatedX(localX, localZ, inverse), py - y, rotatedZ(localX, localZ, inverse));
        }
    }
    private static int rotatedX(int x, int z, int rotation) {
        return switch (rotation) { case 90 -> -z; case 180 -> -x; case 270 -> z; default -> x; };
    }
    private static int rotatedZ(int x, int z, int rotation) {
        return switch (rotation) { case 90 -> x; case 180 -> -z; case 270 -> -x; default -> z; };
    }

    private static List<Shape> shapes(JsonArray array, Map<String, String> palette) {
        List<Shape> result = new ArrayList<>();
        for (JsonElement element : array) {
            JsonObject json = object(element, "shape");
            String type = string(json.get("type"), "shape.type");
            if (!Set.of("box", "ellipsoid", "cylinder", "cone", "polygon").contains(type))
                throw new IllegalArgumentException("Unknown scene shape: " + type);
            JsonArray b = array(json.get("bounds"), "shape.bounds");
            if (b.size() != 6) throw new IllegalArgumentException("Scene bounds require six coordinates");
            Bounds bounds = new Bounds(coordinate(b.get(0)), coordinate(b.get(1)), coordinate(b.get(2)),
                    coordinate(b.get(3)), coordinate(b.get(4)), coordinate(b.get(5)));
            String material = palette.get(string(json.get("material"), "shape.material"));
            if (material == null) throw new IllegalArgumentException("Unknown scene palette material");
            int inner = json.has("inner") ? integer(json.get("inner"), "shape.inner") : 0;
            if (inner < 0 || inner > COORDINATE_LIMIT || inner > 0 && !Set.of("box", "ellipsoid", "cylinder").contains(type))
                throw new IllegalArgumentException("Unsupported scene hollow shape or shell thickness");
            String axisName = json.has("axis") ? string(json.get("axis"), "shape.axis") : "y";
            int axis = switch (axisName) { case "x" -> 0; case "y" -> 1; case "z" -> 2;
                default -> throw new IllegalArgumentException("Invalid scene shape axis"); };
            List<Point> points = new ArrayList<>();
            if (type.equals("polygon")) {
                JsonArray p = array(json.get("points"), "shape.points");
                if (p.size() < 3) throw new IllegalArgumentException("Scene polygon requires at least three points");
                int minU = axis == 0 ? bounds.minY : bounds.minX, maxU = axis == 0 ? bounds.maxY : bounds.maxX;
                int minV = axis == 2 ? bounds.minY : bounds.minZ, maxV = axis == 2 ? bounds.maxY : bounds.maxZ;
                double area = 0;
                for (JsonElement point : p) {
                    JsonArray pair = array(point, "polygon point");
                    if (pair.size() != 2) throw new IllegalArgumentException("Scene polygon point requires two coordinates");
                    double u = number(pair.get(0)), v = number(pair.get(1));
                    if (u < minU || u > maxU || v < minV || v > maxV)
                        throw new IllegalArgumentException("Scene polygon points must fit authored bounds");
                    points.add(new Point(u, v));
                }
                Point previous = points.getLast();
                for (Point point : points) { area += previous.u * point.v - point.u * previous.v; previous = point; }
                if (Math.abs(area) < 1.0e-9) throw new IllegalArgumentException("Scene polygon must have nonzero area");
            }
            result.add(new Shape(type, bounds, material, inner, axis, List.copyOf(points)));
        }
        return List.copyOf(result);
    }
    private static JsonObject object(JsonElement value, String name) {
        if (value == null || !value.isJsonObject()) throw new IllegalArgumentException("Missing or invalid " + name);
        return value.getAsJsonObject();
    }
    private static JsonArray array(JsonElement value, String name) {
        if (value == null || !value.isJsonArray()) throw new IllegalArgumentException("Missing or invalid " + name);
        return value.getAsJsonArray();
    }
    private static String string(JsonElement value, String name) {
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString() || value.getAsString().isBlank())
            throw new IllegalArgumentException("Missing or invalid " + name);
        return value.getAsString();
    }
    private static int coordinate(JsonElement value) {
        int result = integer(value, "scene coordinate");
        if (Math.abs((long) result) > COORDINATE_LIMIT) throw new IllegalArgumentException("Scene coordinate exceeds limit");
        return result;
    }
    private static int integer(JsonElement value, String name) {
        double number = number(value);
        if (number < Integer.MIN_VALUE || number > Integer.MAX_VALUE || number != Math.rint(number))
            throw new IllegalArgumentException("Expected integral " + name);
        return (int) number;
    }
    private static double number(JsonElement value) {
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber())
            throw new IllegalArgumentException("Expected finite scene number");
        double number = value.getAsDouble();
        if (!Double.isFinite(number)) throw new IllegalArgumentException("Expected finite scene number");
        return number;
    }
}
