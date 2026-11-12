package com.gtl.enhancedcore;

import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.zip.ZipFile;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

/** Contracts for the installed native preview and its small client extensions. */
public final class PreviewMaterialRowsRegression {
    private static final String WIDGET = "org/gtlcore/gtlcore/api/gui/PatternPreviewWidget";
    private static final String CONTROLS = "org/gtlcore/gtlcore/client/preview/PreviewControls";
    private static final String PANEL = "org/gtlcore/gtlcore/client/preview/PreviewCandidatePanel";
    private static int assertions;

    public static void main(String[] args) throws Exception {
        System.out.println("Native preview contracts passed: " + run() + " assertions");
    }

    static int run() throws Exception {
        assertions = 0;
        try (var jar = new ZipFile("libs/gtlcore-1.2.3.3-fix3.jar")) {
            var widget = read(jar, WIDGET);
            var controls = read(jar, CONTROLS);
            var selected = method(widget, "onPosSelected",
                    "(Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Direction;)V");
            check(calls(selected, PANEL + "$Group", "of") > 0
                            && calls(selected, CONTROLS, "setCandidates") == 1,
                    "Native block selection forwards predicate candidate groups to fullscreen controls");
            var candidates = method(controls, "setCandidates", "(Ljava/util/List;)V");
            check(calls(candidates, PANEL, "setGroups") == 1,
                    "Native controls update the candidate panel directly");
            check(fieldRead(candidates, CONTROLS, "candidatePanel", "L" + PANEL + ";"),
                    "Candidate groups reach the installed native panel");
            method(controls, "showCandidates", "()V");
            method(controls, "fullscreen", "()Z");
            method(controls, "heightText", "()Lnet/minecraft/network/chat/Component;");
            method(controls, "drawInfo", "(Lnet/minecraft/client/gui/GuiGraphics;)V");
            method(controls, "dimensions", "()Lorg/gtlcore/gtlcore/client/preview/PreviewHeight$Dimensions;");
            hooks("PreviewCandidateSelectionMixin", widget);
            hooks("PreviewFootprintInfoMixin", controls);
            candidatesClosedOnEntry(hooks("PreviewControlsBehaviorMixin", controls));
            hooks("PreviewAppearanceMixin", controls);
            hooks("PreviewCandidateAppearanceMixin", read(jar, PANEL));
            hooks("PreviewTextControlAppearanceMixin", widget);
            hooks("FullscreenPreviewAppearanceMixin", read(jar,
                    "org/gtlcore/gtlcore/client/preview/FullscreenPreviewScreen"));
        }
        var config = JsonParser.parseString(Files.readString(Path.of(
                "src/main/resources/gtl_enhancedcore.mixins.json"))).getAsJsonObject();
        var enabled = new HashSet<String>();
        for (String section : List.of("mixins", "client", "server")) {
            if (config.has(section)) config.getAsJsonArray(section).forEach(entry -> enabled.add(entry.getAsString()));
        }
        var client = config.getAsJsonArray("client").asList().stream().map(entry -> entry.getAsString()).toList();
        for (String extension : List.of("PreviewCandidateSelectionMixin", "PreviewFootprintInfoMixin",
                "PreviewControlsBehaviorMixin", "PreviewAppearanceMixin", "PreviewCandidateAppearanceMixin",
                "PreviewTextControlAppearanceMixin", "FullscreenPreviewAppearanceMixin")) {
            check(client.contains("gtlcore.client." + extension), extension + " is enabled only on the client");
            check(!config.getAsJsonArray("mixins").asList().stream()
                    .anyMatch(entry -> entry.getAsString().equals("gtlcore.client." + extension)),
                    extension + " is absent from common mixins");
        }
        for (String retired : List.of("PreviewMaterialRowsMixin", "PreviewMaterialLayoutMixin", "PreviewVisibilityMixin")) {
            check(!enabled.contains("gtlcore.client." + retired), retired + " no longer overrides native UI");
        }
        return assertions + StagedPreviewContractRegression.run();
    }

    private static ClassNode hooks(String name, ClassNode target) throws Exception {
        var entry = "com/gtl/enhancedcore/mixin/gtlcore/client/" + name + ".class";
        var mixin = new ClassNode();
        try (var input = PreviewMaterialRowsRegression.class.getClassLoader().getResourceAsStream(entry)) {
            if (input == null) throw new AssertionError("Missing compiled Mixin: " + name);
            new ClassReader(input).accept(mixin, 0);
        }
        for (var field : mixin.fields) {
            if (annotations(field.visibleAnnotations, field.invisibleAnnotations).stream()
                    .anyMatch(annotation -> annotation.desc.endsWith("/Shadow;"))) {
                check(target.fields.stream().anyMatch(actual -> actual.name.equals(field.name) && actual.desc.equals(field.desc)),
                        name + " shadow field exists: " + field.name);
            }
        }
        int injections = 0;
        for (var hook : mixin.methods) {
            for (var annotation : annotations(hook.visibleAnnotations, hook.invisibleAnnotations)) {
                if (annotation.desc.endsWith("/Shadow;")) method(target, hook.name, hook.desc);
                if (!annotation.desc.endsWith("/Inject;") && !annotation.desc.endsWith("/ModifyExpressionValue;")
                        && !annotation.desc.endsWith("/WrapOperation;") && !annotation.desc.endsWith("/Redirect;")) continue;
                injections++;
                for (Object selector : (List<?>) value(annotation, "method")) {
                    var signature = (String) selector;
                    int split = signature.indexOf('(');
                    if (split >= 0) method(target, signature.substring(0, split), signature.substring(split));
                    else check(target.methods.stream().filter(actual -> actual.name.equals(signature)).count() == 1,
                            name + " injection target exists uniquely: " + signature);
                }
                Object points = value(annotation, "at");
                for (Object point : points instanceof List<?> list ? list : List.of(points)) {
                    var at = (AnnotationNode) point;
                    if ((annotation.desc.endsWith("/WrapOperation;") || annotation.desc.endsWith("/Redirect;"))
                            && "INVOKE".equals(value(at, "value"))) {
                        String invocation = (String) value(at, "target");
                        int ownerEnd = invocation.indexOf(';');
                        int descriptorStart = invocation.indexOf('(', ownerEnd);
                        long matches = 0;
                        for (Object selector : (List<?>) value(annotation, "method")) {
                            String signature = (String) selector;
                            for (var actual : target.methods) {
                                if (!(signature.equals(actual.name) || signature.equals(actual.name + actual.desc))) continue;
                                for (var instruction : actual.instructions) if (instruction instanceof MethodInsnNode call
                                        && call.owner.equals(invocation.substring(1, ownerEnd))
                                        && call.name.equals(invocation.substring(ownerEnd + 1, descriptorStart))
                                        && call.desc.equals(invocation.substring(descriptorStart))) matches++;
                            }
                        }
                        int required = (Integer) value(annotation, "require");
                        check(Integer.valueOf(required).equals(value(annotation, "allow")) && matches == required,
                                name + " matches the real installed call count: " + invocation);
                    }
                    if (!"FIELD".equals(value(at, "value"))) continue;
                    String field = (String) value(at, "target");
                    int ownerEnd = field.indexOf(';');
                    int separator = field.indexOf(':', ownerEnd);
                    check(field.substring(1, ownerEnd).equals(target.name)
                                    && target.fields.stream().anyMatch(actual -> actual.name.equals(field.substring(ownerEnd + 1, separator))
                                    && actual.desc.equals(field.substring(separator + 1))),
                            name + " injected field exists: " + field);
                }
            }
        }
        check(injections > 0, name + " contains checked injection targets");
        return mixin;
    }

    private static void candidatesClosedOnEntry(ClassNode mixin) throws Exception {
        var source = method(mixin, "enhanced$startWithCandidatesClosed",
                "(IILorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V");
        var inject = annotations(source.visibleAnnotations, source.invisibleAnnotations).stream()
                .filter(annotation -> annotation.desc.endsWith("/Inject;")).findFirst().orElseThrow();
        var at = (AnnotationNode) ((List<?>) value(inject, "at")).getFirst();
        check(((List<?>) value(inject, "method")).equals(List.of("enter"))
                        && "HEAD".equals(value(at, "value")) && Integer.valueOf(1).equals(value(inject, "require")),
                "Candidate default is applied at the native enter HEAD");
        var axes = method(mixin, "enhanced$hideRotationOrigin",
                "(Lnet/minecraft/client/gui/GuiGraphics;Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V");
        var hide = annotations(axes.visibleAnnotations, axes.invisibleAnnotations).stream()
                .filter(annotation -> annotation.desc.endsWith("/Inject;")).findFirst().orElseThrow();
        var axesAt = (AnnotationNode) ((List<?>) value(hide, "at")).getFirst();
        check(((List<?>) value(hide, "method")).equals(List.of("drawRotationOrigin"))
                        && "HEAD".equals(value(axesAt, "value"))
                        && Boolean.TRUE.equals(value(hide, "cancellable"))
                        && calls(axes, "org/spongepowered/asm/mixin/injection/callback/CallbackInfo", "cancel") == 1,
                "The complete native rotation-origin drawing is cancelled");

        var probe = new ClassNode();
        probe.version = Opcodes.V17;
        probe.access = Opcodes.ACC_PUBLIC;
        probe.name = "com/gtl/enhancedcore/audit/PreviewControlsEntryProbe";
        probe.superName = "java/lang/Object";
        for (String field : List.of("fullscreen", "showCandidates")) {
            probe.fields.add(new FieldNode(Opcodes.ACC_PUBLIC, field, "Z", null, null));
        }
        var constructor = new MethodNode(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        constructor.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        constructor.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false));
        constructor.instructions.add(new InsnNode(Opcodes.RETURN));
        probe.methods.add(constructor);
        var entry = new MethodNode(Opcodes.ACC_PUBLIC, source.name, "(IILjava/lang/Object;)V", null, null);
        source.accept(entry);
        // Callback is unused; substitute Object to avoid loading the game or Mixin runtime.
        entry.visibleAnnotations = entry.invisibleAnnotations = null;
        entry.localVariables = null;
        for (var instruction : entry.instructions) if (instruction instanceof FieldInsnNode field
                && field.owner.equals(mixin.name)) field.owner = probe.name;
        probe.methods.add(entry);
        var writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        probe.accept(writer);
        byte[] bytes = writer.toByteArray();
        Class<?> type = new ClassLoader(null) {
            Class<?> define() { return defineClass(probe.name.replace('/', '.'), bytes, 0, bytes.length); }
        }.define();
        Object controls = type.getConstructor().newInstance();
        var fullscreen = type.getField("fullscreen");
        var candidates = type.getField("showCandidates");
        var enter = type.getMethod(entry.name, int.class, int.class, Object.class);
        candidates.setBoolean(controls, true);
        enter.invoke(controls, 960, 600, null);
        check(!candidates.getBoolean(controls), "Compiled production hook closes candidates on first fullscreen entry");
        fullscreen.setBoolean(controls, true);
        candidates.setBoolean(controls, true);
        enter.invoke(controls, 1280, 720, null);
        check(candidates.getBoolean(controls), "Compiled production hook keeps an open candidate panel when fullscreen resizes");
    }

    private static List<AnnotationNode> annotations(List<AnnotationNode> visible, List<AnnotationNode> invisible) {
        var result = new ArrayList<AnnotationNode>();
        if (visible != null) result.addAll(visible);
        if (invisible != null) result.addAll(invisible);
        return result;
    }

    private static Object value(AnnotationNode annotation, String key) {
        if (annotation.values != null) for (int i = 0; i < annotation.values.size(); i += 2) {
            if (key.equals(annotation.values.get(i))) return annotation.values.get(i + 1);
        }
        return null;
    }

    private static ClassNode read(ZipFile jar, String name) throws Exception {
        var node = new ClassNode();
        try (var stream = jar.getInputStream(jar.getEntry(name + ".class"))) {
            new ClassReader(stream).accept(node, 0);
        }
        return node;
    }

    private static MethodNode method(ClassNode owner, String name, String descriptor) {
        var matches = owner.methods.stream().filter(candidate -> candidate.name.equals(name)
                && candidate.desc.equals(descriptor)).toList();
        check(matches.size() == 1, "Installed method exists uniquely: " + owner.name + "." + name + descriptor);
        return matches.getFirst();
    }

    private static long calls(MethodNode method, String owner, String name) {
        long count = 0;
        for (var instruction : method.instructions) if (instruction instanceof MethodInsnNode call
                && call.owner.equals(owner) && call.name.equals(name)) count++;
        return count;
    }

    private static boolean fieldRead(MethodNode method, String owner, String name, String descriptor) {
        for (var instruction : method.instructions) if (instruction instanceof FieldInsnNode field
                && field.owner.equals(owner) && field.name.equals(name) && field.desc.equals(descriptor)) return true;
        return false;
    }

    private static void check(boolean value, String message) {
        assertions++;
        if (!value) throw new AssertionError(message);
    }
}
