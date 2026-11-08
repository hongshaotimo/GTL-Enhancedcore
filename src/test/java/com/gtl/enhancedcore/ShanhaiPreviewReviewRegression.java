package com.gtl.enhancedcore;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.joml.Vector3f;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.SimpleRemapper;
import org.objectweb.asm.tree.ClassNode;

/** 山海：execute production Mixin bytecode against small Forge-free scene/pattern fixtures. */
public final class ShanhaiPreviewReviewRegression {
    private static int assertions;

    public static void main(String[] args) throws Exception {
        System.out.println("Shanhai preview review passed: " + run() + " assertions");
    }

    static int run() throws Exception {
        assertions = 0;
        camera();
        restoredRows();
        candidateCache();
        releaseCaches();
        return assertions;
    }

    private static void camera() throws Exception {
        var type = fixture("PreviewVisibilityMixin", Set.of("<init>", "enhanced$applyVisibility",
                "enhanced$materialSelection"), Set.of("sceneWidget", "enhanced$visibilityMode",
                "enhanced$unfilteredCore", "enhanced$replaceable", "enhanced$selectedPositions",
                "enhanced$controller", "enhanced$selection"));
        var instance = type.getDeclaredConstructor().newInstance();
        var scene = new Scene();
        scene.core.addAll(List.of("controller", "coil", "casing"));
        field(instance, "sceneWidget", scene);
        field(instance, "enhanced$controller", "controller");
        field(instance, "enhanced$unfilteredCore", List.copyOf(scene.core));
        field(instance, "enhanced$replaceable", Set.of("coil"));
        invoke(instance, "enhanced$applyVisibility");
        check(scene.updates == 0, "Unchanged visible positions must preserve the mesh and camera without an update");
        field(instance, "enhanced$visibilityMode", 1);
        invoke(instance, "enhanced$applyVisibility");
        check(scene.core.equals(Set.of("controller")), "Main mode keeps the controller");
        check(scene.center.equals(new Vector3f(7, 8, 9)) && scene.zoom == 42,
                "Filtering preserves a user's pan and zoom despite native setRenderedCore side effects");
        field(instance, "enhanced$visibilityMode", 2);
        invoke(instance, "enhanced$applyVisibility");
        check(scene.core.equals(Set.of("controller", "coil")), "Parts mode keeps replaceable positions");
        field(instance, "enhanced$selectedPositions", Set.of("casing"));
        ((Selection) invoke(instance, "enhanced$materialSelection")).item = "chosen";
        invoke(instance, "enhanced$applyVisibility");
        check(scene.core.equals(Set.of("controller", "casing")), "Selected material keeps its positions and the controller");
        check(scene.center.equals(new Vector3f(7, 8, 9)) && scene.zoom == 42,
                "Repeated mode and material changes preserve camera geometry");
    }

    private static void restoredRows() throws Exception {
        var rowsType = fixture("PreviewMaterialRowsMixin", Set.of("<init>", "enhanced$restoreRows"),
                Set.of("scrollableWidgetGroup", "enhanced$candidateRow"));
        var rowsInstance = rowsType.getDeclaredConstructor().newInstance();
        var primary = new Row();
        var candidate = new Row();
        primary.y = 132;
        candidate.y = 134;
        field(rowsInstance, "scrollableWidgetGroup", primary);
        field(rowsInstance, "enhanced$candidateRow", candidate);
        invoke(rowsInstance, "enhanced$restoreRows");
        check(primary.y == 110 && candidate.y == 134 && primary.x == 3 && candidate.x == 3,
                "Actual row geometry repairs the old fullscreen snapshot");
        check(primary.width == 154 && candidate.width == 154 && primary.height == 22 && candidate.height == 22,
                "Actual rows recover compact dimensions");
        var type = fixture("PreviewMaterialLayoutMixin", Set.of("<init>", "enhanced$restoreMaterialRows"),
                Set.of("widget", "scene", "fullscreen", "width", "screenHeight"));
        var instance = type.getDeclaredConstructor().newInstance();
        var rows = new Rows();
        rows.primaryY = 132; // Actual native snapshot when a page is rebuilt during fullscreen.
        rows.candidateY = 134;
        var scene = new Scene();
        field(instance, "widget", rows);
        field(instance, "scene", scene);
        invoke(instance, "enhanced$restoreMaterialRows", (Object) null);
        check(rows.primaryY == 110 && rows.candidateY == 134,
                "Exit repairs stale native row snapshots after fullscreen page changes");
        check(scene.width == 150 && scene.height == 104, "Exit restores the compact scene height");
        check(rows.expanded, "Exit does not collapse the expanded candidate row");
    }

    private static void candidateCache() throws Exception {
        var type = fixture("PreviewPatternMemoryMixin", Set.of("<init>", "enhanced$candidates"),
                Set.of("enhanced$candidateCache"));
        var instance = type.getDeclaredConstructor().newInstance();
        var first = new Predicate();
        var second = new Predicate();
        for (int i = 0; i < 100_000; i++) invoke(instance, "enhanced$candidates", first);
        check(first.expansions == 1, "A shared predicate expands candidates once across 100000 positions");
        invoke(instance, "enhanced$candidates", second);
        check(second.expansions == 1, "Distinct predicates retain independent candidate sets");
        check(invoke(instance, "enhanced$candidates", (Object) null).equals(Set.of()), "Missing predicates have no candidates");
    }

    private static void releaseCaches() throws Exception {
        var type = fixture("PreviewPatternMemoryMixin", Set.of("<init>", "enhanced$releasePreviewMemory"),
                Set.of("parts", "materials", "predicateMap", "blockMap", "moduleOnlyBlocks", "controllerBase",
                        "enhanced$chambers", "enhanced$materials", "enhanced$candidateCache", "enhanced$replaceable"));
        var instance = type.getDeclaredConstructor().newInstance();
        var machine = new Machine();
        Controller controller = new Controller() {
            public Machine self() { return machine; }
            public Object getMultiblockState() { return null; }
        };
        field(instance, "controllerBase", controller);
        var materials = new HashMap<>(Map.of("position", "item"));
        var choices = new HashMap<>(Map.of("predicate", Set.of("item")));
        var replaceable = new HashSet<>(Set.of("position"));
        var nativeMaterials = new HashMap<>(Map.of("native", "item"));
        field(instance, "materials", nativeMaterials);
        field(instance, "enhanced$materials", materials);
        field(instance, "enhanced$candidateCache", choices);
        field(instance, "enhanced$replaceable", replaceable);
        invoke(instance, "enhanced$releasePreviewMemory");
        check(!materials.isEmpty() && !choices.isEmpty() && !replaceable.isEmpty(),
                "Preview cleanup never touches a controller in a real world");
        machine.world = new DummyWorld();
        field(instance, "parts", new ArrayList<>(List.of("item")));
        field(instance, "predicateMap", new HashMap<>());
        field(instance, "blockMap", new HashMap<>());
        field(instance, "moduleOnlyBlocks", new HashSet<>());
        invoke(instance, "enhanced$releasePreviewMemory");
        check(nativeMaterials.isEmpty(), "Logout clears the new upstream material map retained by MBPattern");
        check(materials.isEmpty() && choices.isEmpty() && replaceable.isEmpty(),
                "Logout clears material and predicate caches even when stale callbacks retain them");
        for (var name : List.of("enhanced$materials", "enhanced$candidateCache", "enhanced$replaceable")) {
            var cache = type.getDeclaredField(name);
            cache.setAccessible(true);
            check(cache.get(instance) == null, "Logout releases cache backing storage: " + name);
        }
    }

    private static Class<?> fixture(String name, Set<String> methods, Set<String> fields) throws Exception {
        String original = "com/gtl/enhancedcore/mixin/gtlcore/client/" + name;
        String renamed = "com/gtl/enhancedcore/ShanhaiFixture" + name;
        var node = new ClassNode();
        new ClassReader(Files.readAllBytes(Path.of("build/classes/java/main/" + original + ".class"))).accept(node, 0);
        node.access = Opcodes.ACC_PUBLIC;
        node.interfaces.clear();
        node.visibleAnnotations = null;
        node.invisibleAnnotations = null;
        node.innerClasses.clear();
        node.nestHostClass = null;
        node.methods.removeIf(method -> !methods.contains(method.name)
                && !method.name.startsWith("lambda$enhanced$applyVisibility$"));
        node.fields.removeIf(field -> !fields.contains(field.name));
        var mapping = new HashMap<String, String>();
        mapping.put(original, renamed);
        mapping.put("appeng/api/stacks/AEItemKey", "java/lang/String");
        mapping.put("net/minecraft/core/BlockPos", "java/lang/String");
        mapping.put("com/lowdragmc/lowdraglib/gui/widget/SceneWidget", internal(Scene.class));
        mapping.put("com/lowdragmc/lowdraglib/gui/widget/WidgetGroup", internal(Rows.class));
        mapping.put("com/lowdragmc/lowdraglib/gui/widget/DraggableScrollableWidgetGroup", internal(Row.class));
        mapping.put("com/lowdragmc/lowdraglib/client/scene/ISceneBlockRenderHook", "java/lang/Object");
        mapping.put("org/spongepowered/asm/mixin/injection/callback/CallbackInfo", "java/lang/Object");
        mapping.put("com/gtl/enhancedcore/client/preview/PreviewMaterialRows$Selection", internal(Selection.class));
        mapping.put("com/gtl/enhancedcore/client/preview/PreviewMaterialRows", internal(Candidates.class));
        mapping.put("com/gtl/enhancedcore/client/preview/PreviewMaterialAccess$Rows", internal(RowAccess.class));
        mapping.put("com/gregtechceu/gtceu/api/pattern/TraceabilityPredicate", internal(Predicate.class));
        mapping.put("com/gregtechceu/gtceu/api/machine/feature/multiblock/IMultiController", internal(Controller.class));
        mapping.put("com/gregtechceu/gtceu/api/machine/MetaMachine", internal(Machine.class));
        mapping.put("com/gregtechceu/gtceu/api/machine/multiblock/MultiblockControllerMachine", internal(Machine.class));
        mapping.put("net/minecraft/world/level/Level", "java/lang/Object");
        mapping.put("com/lowdragmc/lowdraglib/utils/TrackedDummyWorld", internal(DummyWorld.class));
        mapping.put("com/gregtechceu/gtceu/api/pattern/MultiblockState", "java/lang/Object");
        mapping.put("com/gtl/enhancedcore/client/preview/PreviewPatternMemory", internal(Release.class));
        var writer = new ClassWriter(0);
        node.accept(new ClassRemapper(writer, new SimpleRemapper(mapping)));
        return new ClassLoader(ShanhaiPreviewReviewRegression.class.getClassLoader()) {
            Class<?> define() { return defineClass(renamed.replace('/', '.'), writer.toByteArray(), 0, writer.toByteArray().length); }
        }.define();
    }

    private static String internal(Class<?> type) { return type.getName().replace('.', '/'); }
    private static void field(Object target, String name, Object value) throws Exception {
        var field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
    private static Object invoke(Object target, String name, Object... args) throws Exception {
        var method = java.util.Arrays.stream(target.getClass().getDeclaredMethods())
                .filter(candidate -> candidate.getName().equals(name)).findFirst().orElseThrow();
        method.setAccessible(true);
        return method.invoke(target, args);
    }
    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }

    public static final class Selection { String item; public String selected() { return item; } }
    public static final class Predicate { int expansions; }
    public interface Controller { Machine self(); Object getMultiblockState(); }
    public static final class Machine { Object world = new Object(); public Object getLevel() { return world; } }
    public static final class DummyWorld {}
    public interface Release { static void releaseState(Object state) {} }
    public static final class Candidates {
        public static Set<String> candidates(Predicate predicate) {
            if (predicate == null) return Set.of();
            predicate.expansions++;
            return Set.of("coil");
        }
    }
    public interface RowAccess { void enhanced$restoreRows(); }
    public static final class Rows implements RowAccess {
        int primaryY, candidateY;
        boolean expanded = true;
        public void enhanced$restoreRows() { primaryY = 110; candidateY = 134; }
    }
    public static final class Row {
        int x, y, width, height;
        public void setSelfPosition(int valueX, int valueY) { x = valueX; y = valueY; }
        public void setSize(int valueWidth, int valueHeight) { width = valueWidth; height = valueHeight; }
    }
    public static final class Scene {
        final Set<String> core = new HashSet<>();
        Vector3f center = new Vector3f(7, 8, 9);
        float zoom = 42;
        int updates, width, height;
        public Set<String> getCore() { return core; }
        public Vector3f getCenter() { return center; }
        public float getZoom() { return zoom; }
        public Scene setRenderedCore(java.util.Collection<String> visible, Object hook) {
            core.clear(); core.addAll(visible); updates++;
            center = new Vector3f(); zoom = 3.5f;
            return this;
        }
        public Scene setCenter(Vector3f value) { center = value; return this; }
        public Scene setZoom(float value) { zoom = value; return this; }
        public void needCompileCache() {}
        public void setSize(int valueWidth, int valueHeight) { width = valueWidth; height = valueHeight; }
    }
}
