package com.gtl.enhancedcore;

import com.google.gson.JsonParser;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipFile;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/** Pins the lifecycle injection sites to AE2's actual ABI; gameplay is checked by the dedicated audit. */
final class AeGridNodeLifecycleRegression {
    private static final String OWNER = "appeng/blockentity/grid/AENetworkInvBlockEntity";
    private static final String MANAGED = "appeng/api/networking/IManagedGridNode";
    private static int assertions;

    static int run() throws Exception {
        assertions = 0;
        try (ZipFile ae = new ZipFile(Path.of("libs/appliedenergistics2-forge-15.4.10.jar").toFile())) {
            var inventory = read(ae.getInputStream(ae.getEntry(OWNER + ".class")));
            var unload = method(inventory, "onChunkUnloaded", "()V");
            var removed = method(inventory, "m_7651_", "()V");
            check(calls(unload, MANAGED, "destroy") == 1, "unload destroys exactly one managed node");
            check(calls(removed, MANAGED, "destroy") == 1, "mapped removal destroys exactly one managed node");
            var sides = method(inventory, "onGridConnectableSidesChanged", "()V");
            check(calls(sides, MANAGED, "setExposedOnSides") == 1, "side updates use the managed node");
            var constructor = inventory.methods.stream().filter(m -> m.name.equals("<init>")).findFirst().orElseThrow();
            check(calls(constructor, OWNER, "onGridConnectableSidesChanged") == 1,
                    "constructor must retain pre-create connection-side initialization");
            var quantum = read(ae.getInputStream(ae.getEntry("appeng/blockentity/qnb/QuantumBridgeBlockEntity.class")));
            check(quantum.methods.stream().filter(m -> m.name.equals("updateStatus"))
                    .anyMatch(m -> calls(m, quantum.name, "onGridConnectableSidesChanged") == 1),
                    "quantum status changes reach the guarded inherited method");
        }
        var config = JsonParser.parseString(Files.readString(Path.of(
                "src/main/resources/gtl_enhancedcore.mixins.json"))).getAsJsonObject();
        check(config.getAsJsonArray("mixins").asList().stream()
                .filter(v -> v.getAsString().equals("ae2.NetworkInvNodeLifecycleMixin")).count() == 1,
                "lifecycle patch is installed once on common side");
        var loader = AeGridNodeLifecycleRegression.class.getClassLoader();
        var mixin = read(loader.getResourceAsStream(
                "com/gtl/enhancedcore/mixin/ae2/NetworkInvNodeLifecycleMixin.class"));
        destructionHook(mixin, "gtlEnhancedcore$beforeChunkNodeDestroy", "onChunkUnloaded()V", false);
        destructionHook(mixin, "gtlEnhancedcore$beforeRemovedNodeDestroy", "setRemoved()V", true);
        var guard = annotation(method(mixin, "gtlEnhancedcore$skipTerminatedNodeSides",
                "(Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V"));
        check(Boolean.TRUE.equals(value(guard, "cancellable")), "only terminal side updates can be cancelled");
        check(List.of("onGridConnectableSidesChanged()V").equals(value(guard, "method")),
                "guard is restricted to connection-side updates");
        check("HEAD".equals(value(at(guard), "value")), "guard runs before reading initialization data");
        return assertions;
    }

    private static void destructionHook(ClassNode mixin, String name, String target, boolean remap) {
        var hook = annotation(method(mixin, name, "(Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V"));
        check(List.of(target).equals(value(hook, "method")), "precise lifecycle method " + target);
        check(Boolean.valueOf(remap).equals(value(hook, "remap")), "correct vanilla/mod remapping " + target);
        for (String limit : List.of("require", "expect", "allow"))
            check(Integer.valueOf(1).equals(value(hook, limit)), "one required destruction site " + target + " " + limit);
        var at = at(hook);
        check("INVOKE".equals(value(at, "value")), "terminal marker precedes node destruction");
        check(("L" + MANAGED + ";destroy()V").equals(value(at, "target")), "actual managed-node destructor target");
        check(Boolean.FALSE.equals(value(at, "remap")), "AE destructor call is not remapped");
    }

    private static ClassNode read(InputStream input) throws Exception {
        try (input) { var result = new ClassNode(); new ClassReader(input).accept(result, 0); return result; }
    }
    private static MethodNode method(ClassNode owner, String name, String desc) {
        var found = owner.methods.stream().filter(m -> m.name.equals(name) && m.desc.equals(desc)).toList();
        check(found.size() == 1, "method resolves uniquely: " + owner.name + "." + name);
        return found.getFirst();
    }
    private static int calls(MethodNode method, String owner, String name) {
        int count = 0;
        for (var instruction : method.instructions) if (instruction instanceof MethodInsnNode call
                && call.owner.equals(owner) && call.name.equals(name)) count++;
        return count;
    }
    private static AnnotationNode annotation(MethodNode method) {
        return method.visibleAnnotations.stream().filter(a -> a.desc.equals(
                "Lorg/spongepowered/asm/mixin/injection/Inject;")).findFirst().orElseThrow();
    }
    private static AnnotationNode at(AnnotationNode annotation) {
        return (AnnotationNode) ((List<?>) value(annotation, "at")).getFirst();
    }
    private static Object value(AnnotationNode annotation, String name) {
        for (int i = 0; i + 1 < annotation.values.size(); i += 2)
            if (name.equals(annotation.values.get(i))) return annotation.values.get(i + 1);
        return null;
    }
    private static void check(boolean condition, String message) {
        assertions++; if (!condition) throw new AssertionError(message);
    }
}
