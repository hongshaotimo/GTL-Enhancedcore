package com.gtl.enhancedcore;

import com.google.gson.JsonParser;
import com.gtl.enhancedcore.common.integration.GtlTerminalExtras;
import com.gtl.enhancedcore.common.performance.PerformanceMixinPlugin;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipFile;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

/** Uses the actual dependency bytecode; does not start Minecraft or a Mixin environment. */
public final class TerminalExtrasRegression {
    private static final String SOURCE = "org.gtlcore.gtlcore.mixin.ae2.gui.";
    private static final String MERGED = "Lorg/spongepowered/asm/mixin/transformer/meta/MixinMerged;";
    private static final String INJECT = "Lorg/spongepowered/asm/mixin/injection/Inject;";
    private static final Set<String> ACTIONS = Set.of("gTLCore$modifyPatter", "gTLCore$quickUploadPattern",
            "gTLCore$undoQuickUploadPattern", "gTLCore$selectQuickUploadTarget");
    private static int assertions;

    public static void main(String[] args) throws Exception {
        int core = run();
        int plus = args.length == 0 ? 0 : extendedAe(Path.of(args[0]));
        System.out.println("Terminal extras regression passed: " + core + " core + " + plus
                + " optional ExtendedAE Plus assertions (offline bytecode; Minecraft/Mixin runtime not exercised)");
    }

    public static int run() throws Exception {
        assertions = 0;
        var config = JsonParser.parseString(Files.readString(Path.of("src/main/resources/gtl_enhancedcore.mixins.json"))).getAsJsonObject();
        check(config.getAsJsonArray("mixins").asList().stream().anyMatch(v -> v.getAsString().equals("ae2.PatternEncodingTerminalActionsMixin")),
                "server and client share the disabled menu actions");
        check(config.getAsJsonArray("client").asList().stream().anyMatch(v -> v.getAsString().equals("ae2.client.PatternEncodingTerminalExtrasMixin")),
                "screen policy runs only on the client");
        for (String name : List.of("ae2.ExtendedAeTerminalProviderUploadMixin", "ae2.ExtendedAeTerminalMatrixUploadMixin")) {
            check(config.getAsJsonArray("mixins").asList().stream().noneMatch(v -> v.getAsString().equals(name)),
                    "Plus terminal uploads are not intercepted: " + name);
            check(TerminalExtrasRegression.class.getClassLoader().getResource(
                    "com/gtl/enhancedcore/mixin/" + name.replace('.', '/') + ".class") == null,
                    "obsolete Plus upload blocker is absent from the artifact: " + name);
        }
        check(markerTargets(false).equals(Set.of("appeng.client.gui.AEBaseScreen",
                        "appeng.client.gui.me.items.ProcessingEncodingPanel", "net.minecraft.client.gui.screens.Screen")),
                "client policy excludes the Plus arrow/encode-button target");
        String coreJar = JsonParser.parseString(Files.readString(Path.of("gradle/local-dependencies.json"))).getAsJsonObject()
                .entrySet().stream().flatMap(e -> e.getValue().getAsJsonArray().asList().stream())
                .map(v -> v.getAsString()).filter(s -> s.startsWith("gtlcore-")).findFirst().orElseThrow();
        try (ZipFile jar = new ZipFile(Path.of("libs", coreJar).toFile())) {
            exercise(jar, "PatternEncodingTermScreenMixin", "appeng.client.gui.AEBaseScreen", 8, false);
            exercise(jar, "ProcessingEncodingPanelMixin", "appeng.client.gui.me.items.ProcessingEncodingPanel", 2, false);
            exercise(jar, "PatternQuickUploadScreenLifecycleMixin", "net.minecraft.client.gui.screens.Screen", 2, false);
            ClassNode menu = exercise(jar, "PatternEncodingTermMenuMixin", "appeng.menu.me.items.PatternEncodingTermMenu", 4, true);
            executeMenuActions(menu);
        }
        return assertions;
    }

    private static int extendedAe(Path artifact) throws Exception {
        assertions = 0;
        try (ZipFile jar = new ZipFile(artifact.toFile())) {
            String screenSource = "com.extendedae_plus.mixin.ae2.client.gui.PatternEncodingTermScreenMixin";
            ClassNode screen = read(jar, screenSource);
            screen.name = "appeng/client/gui/me/items/PatternEncodingTermScreen";
            Map<MethodNode, byte[]> unchanged = new HashMap<>();
            for (MethodNode method : screen.methods) {
                method.visitAnnotation(MERGED, true).visit("mixin", screenSource);
                if (hasAnnotation(method, INJECT)) method.name = "handler$plus$" + method.name;
                unchanged.put(method, bytes(method));
            }
            check(!markerTargets(false).contains(screen.name.replace('/', '.')),
                    "no terminal-policy callback targets the Plus arrow or wrapped encode button");
            // This target has no terminal marker now; callbacks for other mixins leave it alone.
            new PerformanceMixinPlugin().postApply(screen.name.replace('/', '.'), screen, screenSource, null);
            for (String hook : List.of("eap$createUploadButton", "eap$ensureUploadButton", "eap$wrapEncodeButton")) {
                MethodNode method = screen.methods.stream().filter(m -> m.name.equals(hook) || m.name.endsWith("$" + hook)).findFirst().orElseThrow();
                check(method.instructions.size() > 2, "upstream Plus control keeps its implementation: " + hook);
            }
            for (var entry : unchanged.entrySet()) {
                check(Arrays.equals(bytes(entry.getKey()), entry.getValue()), "Plus arrow, selection and encode behavior stay unchanged");
            }

            // Exercise a single menu carrying both mods, as it will be merged in the instance.
            ClassNode menu = read(jar, "com.extendedae_plus.mixin.ae2.menu.ContainerPatternEncodingTermMenuMixin");
            check(markerPriority(true) < mixinPriority(menu), "menu postApply follows Plus callbacks");
            String menuSource = menu.name.replace('/', '.');
            unchanged.clear();
            for (MethodNode method : menu.methods) {
                method.visitAnnotation(MERGED, true).visit("mixin", menuSource);
                if (hasAnnotation(method, INJECT)) method.name = "handler$plus$" + method.name;
                unchanged.put(method, bytes(method));
            }
            try (ZipFile core = new ZipFile(Path.of("libs", "gtlcore-1.2.3.3-fix3.jar").toFile())) {
                ClassNode coreMenu = read(core, SOURCE + "PatternEncodingTermMenuMixin");
                for (MethodNode method : coreMenu.methods) if (ACTIONS.contains(method.name)) {
                    method.visitAnnotation(MERGED, true).visit("mixin", SOURCE + "PatternEncodingTermMenuMixin");
                    menu.methods.add(method);
                }
            }
            menu.name = "appeng/menu/me/items/PatternEncodingTermMenu";
            new PerformanceMixinPlugin().postApply(menu.name.replace('/', '.'), menu, GtlTerminalExtras.MENU_MIXIN, null);
            MethodNode autoUpload = menu.methods.stream().filter(m -> m.name.endsWith("$eap$serverUploadAfterEncode")).findFirst().orElseThrow();
            check(autoUpload.instructions.size() > 1, "Plus server upload after encoding remains active");
            for (var entry : unchanged.entrySet()) {
                check(Arrays.equals(bytes(entry.getKey()), entry.getValue()), "all Plus menu methods stay byte-for-byte unchanged");
            }
            for (MethodNode method : menu.methods) if (ACTIONS.contains(method.name)) {
                check(method.instructions.size() == 1 && method.instructions.getFirst().getOpcode() == Opcodes.RETURN,
                        "GTLCore actions remain disabled alongside active Plus upload");
            }
        }
        return assertions;
    }

    private static ClassNode read(ZipFile jar, String name) throws Exception {
        var node = new ClassNode();
        try (InputStream input = jar.getInputStream(jar.getEntry(name.replace('.', '/') + ".class"))) {
            new ClassReader(input).accept(node, 0);
        }
        return node;
    }

    private static Object value(AnnotationNode annotation, String name) {
        for (int i = 0; i + 1 < annotation.values.size(); i += 2) {
            if (name.equals(annotation.values.get(i))) return annotation.values.get(i + 1);
        }
        return null;
    }

    private static ClassNode exercise(ZipFile jar, String source, String target, int count, boolean menu) throws Exception {
        ClassNode node = new ClassNode();
        try (InputStream input = jar.getInputStream(jar.getEntry((SOURCE + source).replace('.', '/') + ".class"))) {
            new ClassReader(input).accept(node, 0);
        }
        node.name = target.replace('.', '/');
        check(markerPriority(menu) < mixinPriority(node), "terminal postApply follows upstream callbacks: " + source);
        List<MethodNode> affected = new ArrayList<>();
        Map<MethodNode, byte[]> preserved = new HashMap<>();
        for (MethodNode method : node.methods) {
            boolean selected = menu ? ACTIONS.contains(method.name) : hasAnnotation(method, INJECT);
            if (!method.name.startsWith("<")) {
                method.visitAnnotation(MERGED, true).visit("mixin", SOURCE + source);
            }
            if (selected) {
                check(!method.name.startsWith("<"), "constructors must keep their native initialization");
                if (!menu) method.name = "handler$abc123$" + method.name;
                affected.add(method);
            } else {
                preserved.put(method, bytes(method));
            }
        }
        check(affected.size() == count, "actual upstream injectors/actions are fully covered: " + source);
        // Another mod may use the same hook name and descriptor. Its provenance must prevent changes.
        MethodNode first = affected.getFirst();
        var foreign = new MethodNode(Opcodes.ACC_PUBLIC, "handler$foreign$" + original(first.name), first.desc, null, null);
        foreign.instructions.add(new InsnNode(Opcodes.NOP));
        foreign.instructions.add(new InsnNode(Opcodes.RETURN));
        foreign.visitAnnotation(MERGED, true).visit("mixin", "other.mod.TerminalMixin");
        node.methods.add(foreign);
        preserved.put(foreign, bytes(foreign));

        String marker = menu ? GtlTerminalExtras.MENU_MIXIN : GtlTerminalExtras.CLIENT_MIXIN;
        new PerformanceMixinPlugin().postApply(target, node, marker, null);
        for (MethodNode method : affected) {
            check(method.instructions.size() == 1 && method.instructions.getFirst().getOpcode() == Opcodes.RETURN,
                    "extra handler cannot create widgets, intercept events, send packets or mutate inventory: " + method.name);
            check(method.tryCatchBlocks.isEmpty() && (method.localVariables == null || method.localVariables.isEmpty()),
                    "removed instructions leave no dangling debug/exception labels");
            int locals = (method.access & Opcodes.ACC_STATIC) == 0 ? 1 : 0;
            for (Type argument : Type.getArgumentTypes(method.desc)) locals += argument.getSize();
            check(method.maxStack == 0 && method.maxLocals == locals, "wide mouse arguments retain valid local slots");
        }
        for (var entry : preserved.entrySet()) {
            check(Arrays.equals(entry.getValue(), bytes(entry.getKey())), "native/other logic stays byte-for-byte: " + entry.getKey().name);
        }
        byte[] after = bytes(first);
        GtlTerminalExtras.postApply(target, node, marker);
        check(Arrays.equals(after, bytes(first)), "repeat postApply is harmless");
        GtlTerminalExtras.postApply(target, node, "other.mod.UnrelatedMixin");
        check(Arrays.equals(after, bytes(first)), "unrelated plugin callbacks do not patch the class");

        // Fail explicitly when an upgrade makes the hooks unrecognizable, rather than silently re-enable them.
        String descriptor = first.desc;
        first.desc = "(Ljava/lang/Object;)V";
        rejected(() -> GtlTerminalExtras.postApply(target, node, marker), "signature drift");
        first.desc = descriptor;
        var annotations = first.visibleAnnotations;
        first.visibleAnnotations = null;
        rejected(() -> GtlTerminalExtras.postApply(target, node, marker), "missing upstream provenance");
        first.visibleAnnotations = annotations;
        var duplicate = new MethodNode(first.access, first.name, first.desc, null, null);
        duplicate.visibleAnnotations = first.visibleAnnotations;
        node.methods.add(duplicate);
        rejected(() -> GtlTerminalExtras.postApply(target, node, marker), "ambiguous hooks");
        node.methods.remove(duplicate);
        return node;
    }

    private static void executeMenuActions(ClassNode patched) throws Exception {
        // JVM-verifies the real patched action bodies, then exercises ordinary and inherited wireless calls.
        String name = "com/gtl/enhancedcore/regression/DisabledTerminalProbe";
        var writer = new ClassWriter(0);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, name, null, "java/lang/Object", null);
        var constructor = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        constructor.visitCode();
        constructor.visitVarInsn(Opcodes.ALOAD, 0);
        constructor.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        constructor.visitInsn(Opcodes.RETURN);
        constructor.visitMaxs(1, 1);
        constructor.visitEnd();
        for (MethodNode method : patched.methods) if (ACTIONS.contains(method.name)) method.accept(writer);
        writer.visitEnd();
        var loader = new ProbeLoader();
        Class<?> base = loader.define(name.replace('/', '.'), writer.toByteArray());
        var child = new ClassWriter(0);
        child.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, name + "Wireless", null, name, null);
        var childConstructor = child.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        childConstructor.visitCode();
        childConstructor.visitVarInsn(Opcodes.ALOAD, 0);
        childConstructor.visitMethodInsn(Opcodes.INVOKESPECIAL, name, "<init>", "()V", false);
        childConstructor.visitInsn(Opcodes.RETURN);
        childConstructor.visitMaxs(1, 1);
        childConstructor.visitEnd();
        child.visitEnd();
        Class<?> wireless = loader.define((name + "Wireless").replace('/', '.'), child.toByteArray());
        for (Class<?> type : List.of(base, wireless)) {
            Object instance = type.getConstructor().newInstance();
            for (Integer data : Arrays.asList(null, 0, 2, -3, Integer.MIN_VALUE)) {
                type.getMethod("gTLCore$modifyPatter", Integer.class).invoke(instance, data);
                assertions++;
            }
            type.getMethod("gTLCore$quickUploadPattern").invoke(instance);
            type.getMethod("gTLCore$undoQuickUploadPattern").invoke(instance);
            type.getMethod("gTLCore$selectQuickUploadTarget", int.class).invoke(instance, 0);
            assertions += 3;
        }
    }

    private static String original(String name) {
        return name.startsWith("handler$abc123$") ? name.substring("handler$abc123$".length()) : name;
    }

    private static boolean hasAnnotation(MethodNode method, String desc) {
        return method.visibleAnnotations != null && method.visibleAnnotations.stream().anyMatch(a -> a.desc.equals(desc))
                || method.invisibleAnnotations != null && method.invisibleAnnotations.stream().anyMatch(a -> a.desc.equals(desc));
    }

    private static int markerPriority(boolean menu) throws Exception {
        return mixinPriority(marker(menu));
    }

    private static Set<String> markerTargets(boolean menu) throws Exception {
        var node = marker(menu);
        var annotation = node.invisibleAnnotations.stream()
                .filter(a -> a.desc.equals("Lorg/spongepowered/asm/mixin/Mixin;")).findFirst().orElseThrow();
        @SuppressWarnings("unchecked") var targets = (List<Type>) value(annotation, "value");
        return targets.stream().map(Type::getClassName).collect(java.util.stream.Collectors.toSet());
    }

    private static ClassNode marker(boolean menu) throws Exception {
        String marker = menu ? GtlTerminalExtras.MENU_MIXIN : GtlTerminalExtras.CLIENT_MIXIN;
        var node = new ClassNode();
        try (InputStream input = TerminalExtrasRegression.class.getClassLoader().getResourceAsStream(marker.replace('.', '/') + ".class")) {
            new ClassReader(input).accept(node, 0);
        }
        return node;
    }

    private static int mixinPriority(ClassNode node) {
        String descriptor = "Lorg/spongepowered/asm/mixin/Mixin;";
        var annotations = new ArrayList<AnnotationNode>();
        if (node.visibleAnnotations != null) annotations.addAll(node.visibleAnnotations);
        if (node.invisibleAnnotations != null) annotations.addAll(node.invisibleAnnotations);
        for (AnnotationNode annotation : annotations) {
            if (annotation.desc.equals(descriptor)) {
                Object priority = value(annotation, "priority");
                return priority instanceof Integer n ? n : 1000;
            }
        }
        throw new AssertionError("Missing Mixin priority contract: " + node.name);
    }

    private static byte[] bytes(MethodNode method) {
        var writer = new ClassWriter(0);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "Probe", null, "java/lang/Object", null);
        method.accept(writer);
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static void rejected(Runnable action, String reason) {
        try { action.run(); } catch (IllegalStateException expected) { assertions++; return; }
        throw new AssertionError("Upstream incompatibility must be explicit: " + reason);
    }

    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }

    private static final class ProbeLoader extends ClassLoader {
        Class<?> define(String name, byte[] code) { return defineClass(name, code, 0, code.length); }
    }
}
