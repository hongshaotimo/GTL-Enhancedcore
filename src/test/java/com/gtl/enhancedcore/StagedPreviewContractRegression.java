package com.gtl.enhancedcore;

import com.gtl.enhancedcore.common.integration.MultiblockPreviewLaziness;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipFile;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.BasicVerifier;

/** Exercises the exact installed GTLCore bytecode; runtime audit verifies the actual Mixin merge. */
final class StagedPreviewContractRegression {
    private static final String MARKER = "com.gtl.enhancedcore.mixin.jei.StagedMultiblockPreviewMixin";
    private static final String TARGET = "org.gtlcore.gtlcore.api.gui.PatternPreviewWidget";
    private static final String ENTRY = TARGET.replace('.', '/') + ".class";
    private static int assertions;

    private StagedPreviewContractRegression() {}

    public static void main(String[] args) throws Exception {
        System.out.println("Staged preview contract passed: " + run() + " assertions");
    }

    static int run() throws Exception {
        assertions = 0;
        ClassNode original = read();
        ClassNode transformed = read();
        MultiblockPreviewLaziness.postApply(TARGET, transformed, MARKER);
        check(transformed.interfaces.contains("com/gtl/enhancedcore/client/preview/PreviewPageAccess"), "Opaque preview bridge installed");
        MethodNode constructor = transformed.methods.stream().filter(method -> method.name.equals("<init>")).findFirst().orElseThrow();
        check(calls(constructor, "com/gtl/enhancedcore/client/preview/PreviewPreparation", "patterns") == 1,
                "Only the constructor cache boundary is staged");
        for (String untouched : List.of("initializePattern", "initializeModules", "setupScene", "onPosSelected",
                "loadControllerFormed", "toggleModules", "gatherBlockDrops")) {
            var before = original.methods.stream().filter(method -> method.name.equals(untouched)).findFirst().orElseThrow();
            var after = transformed.methods.stream().filter(method -> method.name.equals(untouched)).findFirst().orElseThrow();
            check(opcodes(before).equals(opcodes(after)), "Original feature retained: " + untouched);
        }
        for (var method : transformed.methods) {
            if (!method.name.startsWith("enhanced$")) continue;
            new Analyzer<>(new BasicVerifier()).analyze(transformed.name, method);
            check(true, "Generated bridge stack verified: " + method.name);
        }
        var finish = transformed.methods.stream().filter(method -> method.name.equals("enhanced$finishPattern")).findFirst().orElseThrow();
        check(calls(finish, TARGET.replace('.', '/') + "$MBPattern", "<init>") == 1,
                "Native MBPattern finalization retained");
        var drop = transformed.methods.stream().filter(method -> method.name.equals("enhanced$gatherDrop")).findFirst().orElseThrow();
        check(calls(drop, "java/util/Map", "computeIfAbsent") == 1,
                "Native candidate material accumulation retained");
        boolean duplicateRejected = false;
        try { MultiblockPreviewLaziness.postApply(TARGET, transformed, MARKER); }
        catch (IllegalStateException expected) { duplicateRejected = true; }
        check(duplicateRejected, "Duplicate transform rejected");
        var changed = read();
        changed.methods.removeIf(method -> method.name.equals("gatherBlockDrops"));
        boolean changedRejected = false;
        try { MultiblockPreviewLaziness.postApply(TARGET, changed, MARKER); }
        catch (IllegalStateException expected) { changedRejected = true; }
        check(changedRejected, "Upstream signature drift rejected");
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        transformed.accept(writer);
        check(writer.toByteArray().length > 0, "Generated methods serialize");
        return assertions;
    }

    private static ClassNode read() throws Exception {
        try (var jar = new ZipFile(Path.of("libs/gtlcore-1.2.3.2-fix4.jar").toFile())) {
            ClassNode result = new ClassNode();
            new ClassReader(jar.getInputStream(jar.getEntry(ENTRY)).readAllBytes()).accept(result, 0);
            return result;
        }
    }

    private static long calls(MethodNode method, String owner, String name) {
        long result = 0;
        for (var insn : method.instructions) if (insn instanceof MethodInsnNode call
                && call.owner.equals(owner) && call.name.equals(name)) result++;
        return result;
    }

    private static List<String> opcodes(MethodNode method) {
        var result = new java.util.ArrayList<String>();
        for (var insn : method.instructions) {
            if (insn.getOpcode() < 0) continue;
            String signature = "" + insn.getOpcode();
            if (insn instanceof MethodInsnNode call) signature += call.owner + call.name + call.desc;
            if (insn instanceof FieldInsnNode field) signature += field.owner + field.name + field.desc;
            result.add(signature);
        }
        return result;
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        assertions++;
    }
}
