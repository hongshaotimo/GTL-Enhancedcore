package com.gtl.enhancedcore;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.gtl.enhancedcore.common.world.plot.CampusScene;

/** Pure authored geometry checks, independent of a Minecraft registry or world. */
final class CampusSceneRegression {
    private static int checks;
    static int run() {
        checks = 0;
        CampusScene box = scene("""
                {"type":"box","bounds":[-17,-2,-1,-15,2,1],"material":"pink","inner":1}
                """);
        check("minecraft:pink_concrete".equals(box.blockAt(-17, -2, -1)), "Hollow boxes retain their inclusive negative corner");
        check(box.blockAt(-16, 0, 0) == null && box.blockAt(-18, 0, 0) == null,
                "Hollow box interior and coordinates beyond its negative tile boundary stay absent");
        check(box.topAt(-17, 0) == 2 && box.topAt(-14, 0) == 0 && box.maxHeight() == 2,
                "Column ceilings use only intersecting authored bounds");
        CampusScene order = scene("""
                {"type":"box","bounds":[0,-2,0,2,5,2],"material":"pink"},
                {"type":"box","bounds":[1,-1,1,1,6,1],"material":"air"},
                {"type":"box","bounds":[1,2,1,1,2,1],"material":"white"}
                """);
        check("minecraft:air".equals(order.blockAt(1, 0, 1))
                        && "minecraft:white_concrete".equals(order.blockAt(1, 2, 1))
                        && "minecraft:pink_concrete".equals(order.blockAt(0, -2, 0)),
                "Later shapes overwrite earlier shapes including explicit air and nonpositive foundations");
        check(order.topAt(1, 1) == 6 && order.materials().contains("minecraft:air") && order.shapeCount() == 3,
                "Air cutters contribute conservative height and the immutable generation palette");
        CampusScene ellipsoid = scene("""
                {"type":"ellipsoid","bounds":[-3,-3,-3,3,3,3],"material":"pink","inner":1}
                """);
        check(ellipsoid.blockAt(0, 0, 0) == null && ellipsoid.blockAt(3, 0, 0) != null
                        && ellipsoid.blockAt(3, 3, 3) == null,
                "Ellipsoid shells retain their axial surface and omit their center and cuboid corners");
        for (String axis : new String[] {"x", "y", "z"}) {
            CampusScene cylinder = scene("""
                    {"type":"cylinder","bounds":[-3,-3,-3,3,3,3],"material":"pink","inner":1,"axis":"%s"}
                    """.formatted(axis));
            int x = axis.equals("x") ? 3 : 0, y = axis.equals("y") ? 3 : 0, z = axis.equals("z") ? 3 : 0;
            check(cylinder.blockAt(0, 0, 0) == null && cylinder.blockAt(x, y, z) != null,
                    "Cylinders preserve both hollow interiors and closed end caps along " + axis);
            CampusScene cone = scene("""
                    {"type":"cone","bounds":[-3,-3,-3,3,3,3],"material":"pink","axis":"%s"}
                    """.formatted(axis));
            int baseX = axis.equals("x") ? -3 : 2, baseY = axis.equals("y") ? -3 : axis.equals("x") ? 2 : 0,
                    baseZ = axis.equals("z") ? -3 : 0;
            int tipX = axis.equals("x") ? 3 : 2, tipY = axis.equals("y") ? 3 : axis.equals("x") ? 2 : 0,
                    tipZ = axis.equals("z") ? 3 : 0;
            check(cone.blockAt(baseX, baseY, baseZ) != null && cone.blockAt(tipX, tipY, tipZ) == null
                            && cone.blockAt(x, y, z) != null,
                    "Cones taper toward the maximum endpoint along " + axis);
        }
        CampusScene polygon = scene("""
                {"type":"polygon","bounds":[0,1,0,4,3,4],"material":"pink","points":[[0,0],[4,0],[4,4],[2,2],[0,4]]}
                """);
        check(polygon.blockAt(2, 1, 2) != null && polygon.blockAt(2, 2, 1) != null
                        && polygon.blockAt(2, 2, 3) == null && polygon.blockAt(2, 4, 1) == null,
                "Concave polygon extrusion includes its edges while preserving the concave cutout and axis limits");
        for (String axis : new String[] {"x", "z"}) {
            CampusScene extrusion = scene("""
                    {"type":"polygon","bounds":[0,0,0,4,4,4],"material":"pink","axis":"%s","points":[[0,0],[4,0],[0,4]]}
                    """.formatted(axis));
            check(extrusion.blockAt(axis.equals("x") ? 4 : 1, 1, axis.equals("z") ? 4 : 1) != null
                            && extrusion.blockAt(3, 3, 3) == null,
                    "Polygon point coordinates map to the perpendicular plane for " + axis);
        }
        JsonObject authored = architecture("");
        JsonObject s = authored.getAsJsonObject("scene"), templates = new JsonObject();
        templates.add("post", JsonParser.parseString("""
                [{"type":"box","bounds":[1,0,2,2,3,2],"material":"pink"}]
                """));
        s.add("templates", templates);
        JsonArray instances = JsonParser.parseString("""
                [{"template":"post","x":32,"y":10,"z":-32,"rotation":90},
                 {"template":"post","x":-32,"y":0,"z":32,"rotation":180},
                 {"template":"post","x":32,"y":0,"z":32,"rotation":270}]
                """).getAsJsonArray();
        s.add("instances", instances);
        CampusScene rotated = new CampusScene(authored);
        check(rotated.blockAt(30, 13, -31) != null && rotated.blockAt(30, 10, -30) != null
                        && rotated.blockAt(31, 10, -30) == null && rotated.topAt(30, -31) == 13,
                "Quarter-turn template rotation updates its exact geometry and spatial bounds before translation");
        check(rotated.blockAt(-33, 0, 30) != null && rotated.blockAt(34, 0, 31) != null,
                "Half and three-quarter turns preserve authored geometry across positive and negative buckets");
        authored.getAsJsonObject("scene").getAsJsonObject("palette").addProperty("pink", "minecraft:black_concrete");
        check("minecraft:pink_concrete".equals(rotated.blockAt(30, 10, -31)),
                "Mutating authoring JSON cannot alter an instantiated scene");
        reject("{\"type\":\"box\",\"bounds\":[2,0,0,1,1,1],\"material\":\"pink\"}");
        reject("{\"type\":\"box\",\"bounds\":[0,0,0,1.5,1,1],\"material\":\"pink\"}");
        reject("{\"type\":\"box\",\"bounds\":[0,0,0,1000001,1,1],\"material\":\"pink\"}");
        reject("{\"type\":\"box\",\"bounds\":[-1000000,0,-1000000,1000000,0,1000000],\"material\":\"pink\"}");
        reject("{\"type\":\"cone\",\"bounds\":[0,0,0,2,2,2],\"material\":\"pink\",\"inner\":1}");
        reject("{\"type\":\"box\",\"bounds\":[0,0,0,1,1,1],\"material\":\"missing\"}");
        reject("{\"type\":\"polygon\",\"bounds\":[0,0,0,2,2,2],\"material\":\"pink\",\"points\":[[0,0],[1,1],[2,2]]}");
        reject("{\"type\":\"polygon\",\"bounds\":[0,0,0,2,2,2],\"material\":\"pink\",\"points\":[[0,0],[3,1],[0,2]]}");
        JsonObject badRotation = authored.deepCopy();
        badRotation.getAsJsonObject("scene").getAsJsonArray("instances").get(0).getAsJsonObject().addProperty("rotation", 45);
        reject(badRotation);
        JsonObject moved = authored.deepCopy();
        moved.getAsJsonObject("scene").getAsJsonArray("instances").get(0).getAsJsonObject().addProperty("x", 1000000);
        moved.getAsJsonObject("scene").getAsJsonArray("instances").get(0).getAsJsonObject().addProperty("rotation", 0);
        reject(moved);
        return checks;
    }
    private static CampusScene scene(String shapes) { return new CampusScene(architecture(shapes)); }
    private static JsonObject architecture(String shapes) {
        return JsonParser.parseString("""
                {"schemaVersion":1,"scene":{"palette":{"pink":"minecraft:pink_concrete","white":"minecraft:white_concrete","air":"minecraft:air"},"shapes":[%s]}}
                """.formatted(shapes)).getAsJsonObject();
    }
    private static void reject(String shapes) { reject(architecture(shapes)); }
    private static void reject(JsonObject architecture) {
        try { new CampusScene(architecture); } catch (IllegalArgumentException expected) { checks++; return; }
        throw new AssertionError("Malformed or unbounded scene must be rejected");
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); checks++; }
    public static void main(String[] args) { System.out.println("Campus scene geometry checks: " + run()); }
}
