package com.gtl.enhancedcore;

import com.google.gson.JsonParser;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.zip.ZipFile;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/** Pins the fail-before-removal contract of the current GTLCore manipulator adapter. */
public final class IvManipulatorTransferRegression {
    private static int assertions;
    private static final String SNAPSHOT = "org/gtlcore/gtlcore/common/item/manipulator/ManipulatorSnapshot";
    private static final String EXCEPTION = SNAPSHOT + "$UnsupportedBlockEntityException";

    public static int run() throws Exception {
        assertions = 0;
        String jar = JsonParser.parseString(Files.readString(Path.of("gradle/local-dependencies.json"))).getAsJsonObject()
                .getAsJsonArray("implementation").asList().stream().map(value -> value.getAsString())
                .filter(value -> value.startsWith("gtlcore-")).findFirst().orElseThrow();
        try (ZipFile zip = new ZipFile(Path.of("libs", jar).toFile())) {
            ClassNode snapshot = read(zip.getInputStream(zip.getEntry(SNAPSHOT + ".class")));
            ClassNode task = read(zip.getInputStream(zip.getEntry("org/gtlcore/gtlcore/common/item/manipulator/ManipulatorTask.class")));
            MethodNode capture = method(snapshot, "capture");
            var copyBranch = Arrays.stream(capture.instructions.toArray()).filter(instruction ->
                    instruction instanceof org.objectweb.asm.tree.FieldInsnNode field && field.name.equals("COPY")).findFirst().orElseThrow();
            var contentsCall = Arrays.stream(capture.instructions.toArray()).filter(instruction ->
                    instruction instanceof MethodInsnNode call && call.name.equals("captureManipulatorContents")).findFirst().orElseThrow();
            check(capture.instructions.indexOf(copyBranch) < capture.instructions.indexOf(contentsCall),
                    "COPY proxy path precedes content capture and does not transport the IV ledger");
            MethodNode scan = method(task, "scan");
            check(scan.tryCatchBlocks.stream().anyMatch(block -> EXCEPTION.equals(block.type)
                    && callsBetween(scan, block.start, block.end, SNAPSHOT, "capture")),
                    "MOVE scanning catches unsupported snapshots instead of scheduling removal");
            MethodNode commit = method(task, "commit");
            check(commit.tryCatchBlocks.stream().anyMatch(block -> EXCEPTION.equals(block.type)
                    && callsBetween(commit, block.start, block.end, SNAPSHOT, "capture")),
                    "MOVE commit rechecks unsupported snapshots before removing live source");
            int captureIndex = firstCall(commit, SNAPSHOT, "capture");
            int chargeIndex = firstCall(commit,
                    "org/gtlcore/gtlcore/integration/ae2/manipulator/ManipulatorNetwork", "charge");
            check(captureIndex >= 0 && chargeIndex > captureIndex, "Snapshot rejection precedes energy payment");
            int removeIndex = firstCall(commit, "net/minecraft/server/level/ServerPlayerGameMode", "m_9280_");
            check(removeIndex > captureIndex, "Snapshot rejection precedes block removal");
        }
        ClassNode mixin;
        try (InputStream input = IvManipulatorTransferRegression.class.getClassLoader().getResourceAsStream(
                "com/gtl/enhancedcore/mixin/gtlcore/IvPatternBufferMixin.class")) { mixin = read(input); }
        MethodNode guard = method(mixin, "iv$protectManipulator");
        check(firstCall(guard, "com/gtl/enhancedcore/common/recipe/iv/IvBufferTransfer", "allowCut") >= 0,
                "Manipulator reuses the existing healthy idle transfer admission");
        check(Arrays.stream(guard.instructions.toArray()).anyMatch(instruction ->
                instruction instanceof org.objectweb.asm.tree.TypeInsnNode type && type.desc.equals(EXCEPTION)),
                "Guard throws the exact upstream skip exception rather than returning an empty snapshot");
        return assertions;
    }

    private static boolean callsBetween(MethodNode method, org.objectweb.asm.tree.AbstractInsnNode start,
                                        org.objectweb.asm.tree.AbstractInsnNode end, String owner, String name) {
        for (var instruction = start; instruction != null && instruction != end; instruction = instruction.getNext())
            if (instruction instanceof MethodInsnNode call && call.owner.equals(owner) && call.name.equals(name)) return true;
        return false;
    }

    private static int firstCall(MethodNode method, String owner, String name) {
        for (var instruction : method.instructions.toArray())
            if (instruction instanceof MethodInsnNode call && call.owner.equals(owner) && call.name.equals(name))
                return method.instructions.indexOf(instruction);
        return -1;
    }

    private static ClassNode read(InputStream input) throws Exception {
        try (input) {
            ClassNode node = new ClassNode();
            new ClassReader(input).accept(node, 0);
            return node;
        }
    }

    private static MethodNode method(ClassNode node, String name) {
        return node.methods.stream().filter(method -> method.name.equals(name)).findFirst().orElseThrow();
    }

    private static void check(boolean condition, String description) {
        assertions++;
        if (!condition) throw new AssertionError(description);
    }
}
