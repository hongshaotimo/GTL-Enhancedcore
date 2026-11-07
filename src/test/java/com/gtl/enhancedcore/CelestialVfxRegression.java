package com.gtl.enhancedcore;

import com.google.gson.JsonParser;
import com.gtl.enhancedcore.client.renderer.celestial.CelestialVfxMesh;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/** Headless mesh and material contracts; rendered appearance still requires the real client. */
final class CelestialVfxRegression {
    private static int assertions;

    static int run() throws Exception {
        assertions = 0;
        check(CelestialVfxMesh.MAX_RADIUS == 12.65 && CelestialVfxMesh.MAX_RADIUS * 2.75 < 36,
                "New celestial geometry fits the approved parent-scaled R36 chamber");
        List<Vertex> eye = null, astro = null;
        for (boolean astrograph : new boolean[]{false, true}) {
            var mesh = new CelestialVfxMesh(astrograph);
            int count = -1;
            for (double time : new double[]{0, 10000, 10040, -123.125, 24000000.25, 0x1.0p40 + .25, Double.MAX_VALUE}) {
                var vertices = snapshot(mesh, time);
                check(vertices.size() == mesh.vertexCount() && vertices.size() > 0 && vertices.size() <= 32768,
                        "Emitted vertices fit the fixed mesh budget");
                if (count == -1) count = vertices.size();
                check(vertices.size() == count && count % 4 == 0, "Animation retains complete quad topology and fixed capacity");
                var colors = new HashSet<Integer>();
                int visible = 0;
                for (var vertex : vertices) {
                    check(Float.isFinite(vertex.x) && Float.isFinite(vertex.y) && Float.isFinite(vertex.z)
                            && Float.isFinite(vertex.u) && Float.isFinite(vertex.v), "All vertex attributes remain finite");
                    double radius = Math.sqrt(vertex.x * (double) vertex.x + vertex.y * (double) vertex.y
                            + vertex.z * (double) vertex.z);
                    check(radius <= CelestialVfxMesh.MAX_RADIUS && radius * 2.75 < 36,
                            "Every complete vertex remains inside the declared and scaled bounds");
                    check(vertex.u >= 0 && vertex.u <= 3 && vertex.v >= 0 && vertex.v <= 1,
                            "UVs select a valid strip or soft-disc material region");
                    check((vertex.rgba >>> 8) != 0, "RGBA retains a nonblack emissive color");
                    colors.add(vertex.rgba >>> 8);
                    if ((vertex.rgba & 255) > 0) visible++;
                }
                check(visible > count / 2 && colors.size() >= 3, "Mesh has visible geometry and multiple light colors");
                for (int i = 0; i < count; i += 4) {
                    var a = vertices.get(i); var b = vertices.get(i + 1);
                    var c = vertices.get(i + 2); var d = vertices.get(i + 3);
                    check(areaSquared(a, b, c) > 1E-16 && areaSquared(a, c, d) > 1E-16,
                            "Each quad contains two nondegenerate triangles");
                    check(a.v == 0 && b.v == 0 && c.v == 1 && d.v == 1,
                            "Quad width coordinates retain a complete material surface");
                }
                check(vertices.equals(snapshot(mesh, time)), "Repeated time reproduces identical mesh data");
                if (time == 0) { if (astrograph) astro = vertices; else eye = vertices; }
            }
            check(!snapshot(mesh, 10000).equals(snapshot(mesh, 10040)), "Forty ticks visibly advance the effect");
            for (double time : new double[]{0, 10000, -123.125, 0x1.0p40 + .25}) {
                var before = snapshot(mesh, time); var after = snapshot(mesh, time + .125);
                double movement = 0; int alphaChange = 0;
                for (int i = 0; i < before.size(); i++) {
                    movement = Math.max(movement, distance(before.get(i), after.get(i)));
                    alphaChange = Math.max(alphaChange, Math.abs((before.get(i).rgba & 255) - (after.get(i).rgba & 255)));
                }
                check(movement > 1E-5 && movement < .1 && alphaChange < 16,
                        "Partial ticks animate continuously at ordinary, negative and huge world times");
            }
        }
        check(!eye.equals(astro), "Eye and Astrograph retain distinct effect geometry");
        materials();
        return assertions;
    }

    private static void materials() throws Exception {
        Path shaders = Path.of("src/main/resources/assets/gtl_enhancedcore/shaders");
        for (String material : List.of("celestial_light", "celestial_core", "heart/celestial_black_hole")) {
            var json = JsonParser.parseString(Files.readString(shaders.resolve("core/" + material + ".json"))).getAsJsonObject();
            var declarations = new HashSet<String>();
            for (String stage : List.of("vertex", "fragment")) {
                String id = json.get(stage).getAsString();
                check(id.startsWith("gtl_enhancedcore:"), "New shader stage uses the mod namespace");
                Path source = shaders.resolve("core/" + id.substring(id.indexOf(':') + 1) + (stage.equals("vertex") ? ".vsh" : ".fsh"));
                check(Files.isRegularFile(source), "Every declared shader stage exists");
                String code = Files.readString(source);
                var declared = java.util.regex.Pattern.compile("\\buniform\\s+\\w+\\s+(\\w+)\\s*;").matcher(code);
                while (declared.find()) declarations.add(declared.group(1));
                var imports = java.util.regex.Pattern.compile("#moj_import\\s*<([^>]+)>").matcher(code);
                while (imports.find()) {
                    String include = imports.group(1);
                    if (include.startsWith("gtl_enhancedcore:"))
                        check(Files.isRegularFile(shaders.resolve("include/" + include.substring(include.indexOf(':') + 1))),
                                "Every self-namespaced shader include resolves");
                }
            }
            var uniforms = new HashSet<String>();
            json.getAsJsonArray("uniforms").forEach(u -> uniforms.add(u.getAsJsonObject().get("name").getAsString()));
            check(uniforms.containsAll(declarations), "Material JSON supplies every directly declared shader uniform");
            check(uniforms.containsAll(List.of(material.startsWith("celestial_") ? "FieldViewMat" : "ModelViewMat", "ProjMat")),
                    "Material retains its own camera matrix contract");
            if (material.startsWith("celestial_"))
                check(uniforms.containsAll(List.of("PhaseTime", "Profile", "Kind", "LocalEye", "CoreCenter", "CoreRadius")),
                        "Celestial material retains animation, profile and analytic sphere inputs");
            if (material.equals("celestial_core")) check(!json.has("blend"), "Opaque sphere material does not apply additive blending");
            if (material.equals("celestial_light")) check(!json.has("blend"),
                    "Light blending belongs to RenderType and does not leave the vanilla shader blend cache additive");
            if (material.startsWith("heart/"))
                check(uniforms.containsAll(List.of("Time", "Center", "CameraPosition", "FacingAxis", "PassMode",
                        "BlackHoleAndDiskRadius", "SpaceSolidRadius", "SpaceFadeRadius", "RotationSpeed", "BlackHoleRotation")),
                        "Heart material retains every upstream volume/compositor uniform");
        }
    }

    private static List<Vertex> snapshot(CelestialVfxMesh mesh, double time) {
        mesh.update(time);
        var vertices = new ArrayList<Vertex>(mesh.vertexCount());
        mesh.emit((x,y,z,u,v,rgba) -> vertices.add(new Vertex(x,y,z,u,v,rgba)));
        return vertices;
    }
    private static double distance(Vertex a, Vertex b) {
        return Math.sqrt(Math.pow(a.x-b.x, 2) + Math.pow(a.y-b.y, 2) + Math.pow(a.z-b.z, 2));
    }
    private static double areaSquared(Vertex a, Vertex b, Vertex c) {
        double ux=b.x-(double)a.x, uy=b.y-(double)a.y, uz=b.z-(double)a.z;
        double vx=c.x-(double)a.x, vy=c.y-(double)a.y, vz=c.z-(double)a.z;
        return Math.pow(uy*vz-uz*vy,2)+Math.pow(uz*vx-ux*vz,2)+Math.pow(ux*vy-uy*vx,2);
    }
    private record Vertex(float x, float y, float z, float u, float v, int rgba) {}
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        assertions++;
    }
}
