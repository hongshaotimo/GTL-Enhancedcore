package com.gtl.enhancedcore;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.gtl.enhancedcore.common.config.ForeignMachineMixinPolicy;
import com.gtl.enhancedcore.common.config.MachineOverrideConfig.Settings;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipFile;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/** Actual dependency meshes, whole-cube clearance and compiled injector contracts; no game bootstrap. */
final class CelestialEffectAnchorRegression {
    private static int assertions;
    private static final String ADD = "com/gtladd/gtladditions/", CORE = "org/gtlcore/gtlcore/";
    private static final String MAIN = "com/gtl/enhancedcore/";
    private static final Path STRUCTURES = Path.of("src/main/resources/data/gtl_enhancedcore/structures/gtl");

    static int run() throws Exception {
        assertions = 0;
        var sizing = mainClass("common/structure/CelestialEffectSizing");
        double eyeScale = number(sizing, "EYE_SCALE"), astroScale = number(sizing, "ASTROGRAPH_SCALE");
        double starScale = number(sizing, "STAR_SCALE"), heartRadius = number(sizing, "HEART_RADIUS");
        double heartFade = number(sizing, "HEART_FADE_RADIUS"), heartCull = number(sizing, "HEART_CULL_RADIUS");
        check(eyeScale == 2.75 && astroScale == 2.75 && Math.abs(starScale - 1.45) < 1E-6, "Approved outer and star scales");
        check(heartRadius == 32 && heartFade == 1.25 && heartCull == 44 && number(sizing, "VIEW_DISTANCE") == 512,
                "Approved Heart shader/culling and renderer distance");
        var spec = JsonParser.parseString(Files.readString(STRUCTURES.resolve("upgrades_20261102.json"))).getAsJsonObject();
        cavity(spec, "eye_of_harmony", new int[]{95,95,95}, new int[]{0,-55,-91}, 36);
        cavity(spec, "arcanic_astrograph", new int[]{145,145,73}, new int[]{0,103,-75}, 36);
        double clearance = cavity(spec, "heart_of_the_universe", new int[]{120,118,108}, new int[]{0,36,-39}, heartRadius * heartFade);
        check(heartCull > heartRadius * heartFade && heartCull < clearance, "Heart cull envelope covers shader volume inside real chamber");
        try (var core = new ZipFile("libs/gtlcore-1.2.3.2-fix4.jar"); var add = new ZipFile("libs/gtladditions-3.3.1Custom.jar")) {
            var eye = read(core, CORE + "client/renderer/machine/EyeOfHarmonyRenderer");
            check(calls(method(eye, "render"), "com/mojang/blaze3d/vertex/PoseStack", "m_85837_") == 1, "Eye has one parent translation");
            for (String child : Set.of("renderStar", "renderOrbitObjects", "renderOuterSpaceShell"))
                check(calls(method(eye, "render"), eye.name, child) == 1, "Eye preserves " + child);
            check(method(eye, "renderOrbitObjects").desc.equals("(FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;DDD)V"),
                    "Eye redundant child anchor uses exact float/Pose/Buffer/DDD descriptor");
            var astro = read(add, ADD + "client/render/machine/ArcanicAstrographRender");
            check(calls(method(astro, "render"), astro.name, "getRenderPosition") == 1
                    && method(astro, "getRenderPosition").desc.equals("(Lnet/minecraft/core/Direction;)Lnet/minecraft/world/phys/Vec3;"), "Astrograph center invocation");
            var trait = read(add, ADD + "common/machine/trait/AstralArrayCompressionTrait");
            check(method(trait, "getCompressionCenter").desc.equals("()Lnet/minecraft/world/phys/Vec3;"), "Independent collection center descriptor");
            check(trait.methods.stream().filter(m -> m.name.equals("getMachine") && m.desc.equals("()L" + ADD
                    + "common/machine/multiblock/controller/ArcanicAstrograph;")).count() == 1, "Collection covariant Shadow getter");
            var heart = method(read(add, ADD + "client/render/machine/HeartOfTheUniverseRenderer"), "render");
            check(calls(heart, ADD + "utils/CommonUtils", "getRotatedRenderPosition") == 1
                    && constants(heart).containsAll(Set.of(36D,-39D)), "Heart original center remains unscaled");
            var uniforms = method(read(add, ADD + "client/render/machine/heart/HeartBlackHoleRenderer$HeartVolumeUniforms"), "applyVolumePass");
            check(uniforms.desc.equals("(F)V") && constants(uniforms).containsAll(Set.of(29F,1.25F))
                    && calls(uniforms, "com/mojang/blaze3d/shaders/Uniform", "m_5985_") == 4, "Heart real scalar uniform path");
            envelopes(core, add, sizing, eye, astro);
            hooks(core, add);
        }
        String anchors = Files.readString(Path.of("src/main/java/com/gtl/enhancedcore/common/structure/CelestialEffectAnchors.java"));
        check(anchors.contains("fromControllerOrigin(machine, -55, -91)") && anchors.contains("fromControllerOrigin(machine, 103, -75)"), "Blueprint anchor vectors retained");
        check(anchors.contains("enhanced$chamberOffset(") && anchors.contains("machine.isFlipped()"), "Actual GTCEu front/up/flip transform retained");
        return assertions;
    }

    private static double cavity(JsonObject manifest, String name, int[] center, int[] delta, double effectRadius) throws Exception {
        var metadata = manifest.getAsJsonObject(name);
        var dimensions = metadata.getAsJsonArray("size"); var controller = metadata.getAsJsonArray("controllerPosition");
        for (int i = 0; i < 3; i++) check(controller.get(i).getAsInt() + delta[i] == center[i], name + " center axis " + i);
        double nearest2 = Double.POSITIVE_INFINITY; int controllers = 0, solids = 0;
        try (var reader = new BufferedReader(new InputStreamReader(new GZIPInputStream(Files.newInputStream(
                STRUCTURES.resolve(name + ".pattern.gz"))), StandardCharsets.US_ASCII))) {
            String[] header = reader.readLine().split(" ");
            int w = Integer.parseInt(header[0]), h = Integer.parseInt(header[1]), d = Integer.parseInt(header[2]);
            check(w == dimensions.get(0).getAsInt() && h == dimensions.get(1).getAsInt() && d == dimensions.get(2).getAsInt(), name + " dimensions");
            for (int z = 0; z < d; z++) for (int y = 0; y < h; y++) {
                String row = reader.readLine(); check(row != null && row.length() == w, name + " complete untrimmed row " + z + "/" + y);
                for (int x = 0; x < w; x++) {
                    char symbol = row.charAt(x);
                    if (symbol == '@') { controllers++; check(x == controller.get(0).getAsInt() && y == controller.get(1).getAsInt()
                            && z == controller.get(2).getAsInt(), name + " controller position"); }
                    if (symbol == ' ') continue;
                    solids++;
                    // Complete unit cube clearance, with the half-block center offset canceled on both sides.
                    double dx = Math.max(Math.abs(x-center[0])-.5,0), dy = Math.max(Math.abs(y-center[1])-.5,0), dz = Math.max(Math.abs(z-center[2])-.5,0);
                    double distance2 = dx*dx + dy*dy + dz*dz; nearest2 = Math.min(nearest2,distance2);
                    check(distance2 > 42*42, name + " fixed cube outside R42 at " + x + "," + y + "," + z);
                }
            }
            check(reader.readLine() == null && controllers == 1 && solids > 0, name + " full pattern and unique controller");
        }
        check(effectRadius < 42 && effectRadius*effectRadius < nearest2, name + " final visual envelope fits");
        return Math.sqrt(nearest2);
    }

    private static void envelopes(ZipFile core, ZipFile add, ClassNode sizing, ClassNode eye, ClassNode astro) throws Exception {
        double space = objRadius(core,"assets/gtlcore/models/obj/space.obj"), star = objRadius(core,"assets/gtlcore/models/obj/star.obj");
        check(Math.abs(space-74) < 1E-5 && Math.abs(star-74) < 1E-5,"Actual OBJ radii measured near 74");
        for (var renderer : List.of(eye,astro)) {
            check(constants(method(renderer,"renderOuterSpaceShell")).contains(.175F),renderer.name + " native shell scale");
            check(constants(method(renderer,"renderStar")).contains(.02F),renderer.name + " native star scale");
        }
        for (String name : List.of("EYE_SCALE","ASTROGRAPH_SCALE")) {
            check(space*.175*number(sizing,name) < 36,"Complete outer shell fits R36: " + name);
            check(star*.02*number(sizing,name)*number(sizing,"STAR_SCALE") < 36,"Central star fits R36: " + name);
        }
        for (int i=1;i<=3;i++) {
            var model = json(core,"assets/gtlcore/models/obj/" + List.of("the_nether","overworld","the_end").get(i-1) + ".json");
            check(model.get("model").getAsString().equals("gtlcore:models/obj/star.obj"),"Eye planet uses measured OBJ " + i);
            check((100*i+160+star)*(.007+.003*i)*number(sizing,"EYE_SCALE")*number(sizing,"EYE_ORBIT_SCALE") < 36,"Eye orbit including planet surface " + i);
        }
        var generator = method(read(add,ADD + "client/render/machine/ArcanicAstrographRender$Companion"),"getOrbitRadius");
        check(constants(generator).containsAll(Set.of(10.304D,.12D)) && calls(generator,"kotlin/ranges/RangesKt","coerceAtMost") == 1,"Random orbit radius capped at 10.304");
        check(constants(method(astro,"renderOrbitObjects")).containsAll(Set.of(.4F,.06F)),"Astrograph planet scale formula");
        List<String> models = new ArrayList<>();
        for (var insn : method(read(add,ADD + "utils/Constants"),"<clinit>").instructions)
            if (insn instanceof LdcInsnNode ldc && ldc.cst instanceof String text && text.startsWith("obj/planets/")) models.add(text);
        check(models.size() == 13,"All thirteen actual planet models");
        for (int i=0;i<models.size();i++) {
            var model = json(add,"assets/gtladditions/models/" + models.get(i) + ".json");
            check(model.get("parent").getAsString().equals("minecraft:block/cube_all") && !model.has("elements"),"Unit cube planet " + models.get(i));
            // The native model rotates 0..1 cube vertices around its origin; use sqrt(3), not sqrt(3)/2.
            double bound = (10.304 + Math.sqrt(3)*(.4+.06*i)*number(sizing,"ASTROGRAPH_PLANET_SCALE"))*number(sizing,"ASTROGRAPH_SCALE");
            check(bound < 36,"Orbit plus full rotating cube fits R36: " + models.get(i));
        }
        double coreRadius = star*.02*number(sizing,"STAR_SCALE");
        double innerSurface = number(sizing,"ASTROGRAPH_INNER_ORBIT") - Math.sqrt(3)*.4*number(sizing,"ASTROGRAPH_PLANET_SCALE");
        check(innerSurface > coreRadius,"Expanded central star remains separate from innermost planet at every rotation");
    }

    private static void hooks(ZipFile core, ZipFile add) throws Exception {
        var config = JsonParser.parseString(Files.readString(Path.of("src/main/resources/gtl_enhancedcore.mixins.json"))).getAsJsonObject();
        Set<String> common = new HashSet<>(),client = new HashSet<>();
        config.getAsJsonArray("mixins").forEach(v -> common.add(v.getAsString())); config.getAsJsonArray("client").forEach(v -> client.add(v.getAsString()));
        var names = List.of("gtlcore.client.EyeOfHarmonyEffectAnchorMixin","gtladditions.client.ArcanicAstrographEffectAnchorMixin",
                "gtladditions.client.HeartOfTheUniverseEffectSizeMixin","gtladditions.client.HeartOfTheUniverseEffectBoundsMixin",
                "gtlcore.client.CelestialEffectViewDistanceMixin","gtladditions.ArcanicAstrographCollectionAnchorMixin");
        for (String name : names) {
            boolean collection = name.endsWith("CollectionAnchorMixin");
            check(collection ? common.contains(name) && !client.contains(name) : client.contains(name) && !common.contains(name),name + " exact client/common side");
            for (boolean structures : new boolean[]{false,true}) for (boolean enhancements : new boolean[]{false,true})
                check(ForeignMachineMixinPolicy.shouldApply(new Settings(structures,enhancements),MAIN.replace('/','.') + "mixin." + name) == structures,name + " structure switch ownership");
            var mixin = mainClass("mixin/" + name.replace('.','/'));
            var declaration = annotations(mixin.visibleAnnotations,mixin.invisibleAnnotations).stream().filter(a -> a.desc.equals("Lorg/spongepowered/asm/mixin/Mixin;")).findFirst().orElseThrow();
            var targets = (List<?>) value(declaration,"targets"); check(targets != null && !targets.isEmpty(),name + " compiled target");
            for (Object targetName : targets) {
                String target = targetName.toString().replace('.','/'); var upstream = read(target.startsWith(CORE) ? core : add,target); int injected = 0;
                for (var handler : mixin.methods) for (var annotation : annotations(handler.visibleAnnotations,handler.invisibleAnnotations)) {
                    if (!(value(annotation,"method") instanceof List<?> selectors)) continue;
                    injected++; check(!annotation.desc.endsWith("ModifyArgs;"),name + " avoids synthetic Args module classes");
                    var selected = new ArrayList<MethodNode>();
                    for (Object selector : selectors) {
                        String text = selector.toString(); var matching = upstream.methods.stream().filter(m -> text.equals(m.name) || text.equals(m.name+m.desc)).toList();
                        check(matching.size() == 1,name + " exact native method " + target + ":" + text); selected.addAll(matching);
                    }
                    for (var at : nested(value(annotation,"at"))) {
                        if (!(value(at,"target") instanceof String signature) || !signature.startsWith("L")) continue;
                        int split = signature.indexOf(';'); String owner = signature.substring(1,split),member = signature.substring(split+1); int count = 0;
                        for (var method : selected) for (var insn : method.instructions)
                            if (insn instanceof MethodInsnNode call && call.owner.equals(owner) && (call.name+call.desc).equals(member)) count++;
                        check(count > 0,name + " exact invocation " + signature);
                    }
                    for (var constant : nested(value(annotation,"constant"))) for (String kind : List.of("floatValue","doubleValue","intValue")) {
                        Object expected = value(constant,kind);
                        if (expected != null) check(selected.stream().anyMatch(m -> constants(m).contains(expected)),name + " exact native constant " + expected);
                    }
                }
                check(injected > 0,name + " compiled injectors cover " + target);
            }
        }
    }
    private static double objRadius(ZipFile jar,String path) throws Exception {
        double maximum = 0; int vertices = 0;
        try (var reader = new BufferedReader(new InputStreamReader(jar.getInputStream(jar.getEntry(path)),StandardCharsets.UTF_8))) {
            for (String line;(line=reader.readLine())!=null;) if (line.startsWith("v ")) {
                String[] p=line.trim().split("\\s+"); double x=Double.parseDouble(p[1]),y=Double.parseDouble(p[2]),z=Double.parseDouble(p[3]);
                maximum=Math.max(maximum,Math.sqrt(x*x+y*y+z*z)); vertices++;
            }
        }
        check(vertices>400,path + " complete OBJ vertices"); return maximum;
    }
    private static JsonObject json(ZipFile jar,String path) throws Exception {
        try (var reader=new InputStreamReader(jar.getInputStream(jar.getEntry(path)),StandardCharsets.UTF_8)) { return JsonParser.parseReader(reader).getAsJsonObject(); }
    }
    private static ClassNode mainClass(String name) throws Exception {
        try (var input=CelestialEffectAnchorRegression.class.getClassLoader().getResourceAsStream(MAIN+name+".class")) {
            if (input==null) throw new AssertionError("Missing compiled production class "+name);
            var node=new ClassNode(); new ClassReader(input).accept(node,ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES); return node;
        }
    }
    private static ClassNode read(ZipFile jar,String name) throws Exception {
        try (var input=jar.getInputStream(jar.getEntry(name+".class"))) { var node=new ClassNode(); new ClassReader(input).accept(node,ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES); return node; }
    }
    private static double number(ClassNode node,String name) { return ((Number)node.fields.stream().filter(f -> f.name.equals(name)).findFirst().orElseThrow().value).doubleValue(); }
    private static MethodNode method(ClassNode node,String name) { return node.methods.stream().filter(m -> m.name.equals(name)).findFirst().orElseThrow(); }
    private static int calls(MethodNode method,String owner,String name) { int count=0; for(var i:method.instructions) if(i instanceof MethodInsnNode call && call.owner.equals(owner) && call.name.equals(name)) count++; return count; }
    private static Set<Object> constants(MethodNode method) { var values=new HashSet<>(); for(var i:method.instructions) if(i instanceof LdcInsnNode ldc) values.add(ldc.cst); return values; }
    @SafeVarargs private static List<AnnotationNode> annotations(List<AnnotationNode>... sources) { var result=new ArrayList<AnnotationNode>(); for(var s:sources) if(s!=null) result.addAll(s); return result; }
    private static Object value(AnnotationNode annotation,String key) { if(annotation.values!=null) for(int i=0;i<annotation.values.size();i+=2) if(annotation.values.get(i).equals(key)) return annotation.values.get(i+1); return null; }
    private static List<AnnotationNode> nested(Object value) { if(value instanceof AnnotationNode n) return List.of(n); if(value instanceof List<?> list) return list.stream().filter(AnnotationNode.class::isInstance).map(AnnotationNode.class::cast).toList(); return List.of(); }
    private static void check(boolean valid,String message) { if(!valid) throw new AssertionError(message); assertions++; }
}
