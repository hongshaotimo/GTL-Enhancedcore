package com.gtl.enhancedcore;

import com.gtl.enhancedcore.common.integration.AdvancedHatchLaserRecipeCompatibility;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.ZipFile;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.BasicVerifier;
import org.objectweb.asm.tree.analysis.BasicValue;

/** Audits actual AH bytecode and executes the inserted boundary without launching Minecraft. */
public final class AdvancedHatchLaserRecipeCompatibilityRegression {
    private static int assertions;

    public static int run() throws Exception {
        Path compileJar = Path.of("libs/gtmadvancedhatch-0.1.10-fix2.jar");
        int total = run(compileJar);
        String auditMods = System.getenv("GTLE_AUDIT_MODS");
        if (auditMods != null && !auditMods.isBlank()) {
            Path runtimeJar = Path.of(auditMods, "GTMAdvancedHatch-1.20.1-0.1.10 fix9.jar");
            if (Files.isRegularFile(runtimeJar)) total += run(runtimeJar);
        }
        return total;
    }

    public static int run(Path jar) throws Exception {
        assertions = 0;
        ClassNode node = read(jar);
        MethodNode method = method(node);
        var original = Arrays.asList(method.instructions.toArray());
        int conversions = AdvancedHatchLaserRecipeCompatibility.transform(node);
        check(conversions == 8 || conversions == 10, "The audited fix2/fix9 laser conversion inventory receives the guard");
        List<AbstractInsnNode> added = new ArrayList<>();
        for (var instruction : method.instructions) if (!original.contains(instruction)) added.add(instruction);
        check(added.size() == 3, "Only one tier load, threshold and conditional branch are added");
        check(added.get(0) instanceof VarInsnNode tier && tier.getOpcode() == Opcodes.ILOAD, "Guard uses the existing per-tier local");
        check(added.get(1).getOpcode() == Opcodes.ICONST_5, "Guard starts wireless laser conversions at IV");
        check(added.get(2) instanceof JumpInsnNode jump && jump.getOpcode() == Opcodes.IF_ICMPLT
                && original.contains(jump.label), "Guard reuses the original loop continuation and its frame");
        check(Arrays.stream(method.instructions.toArray()).filter(original::contains).toList().equals(original),
                "Every original ingredient, output, recipe ID, duration and ordinary recipe instruction is preserved in order");
        var frames = new Analyzer<>(new BasicVerifier()).analyze(node.name, method);
        check(true, "Actual transformed recipe method passes ASM stack/local verification");
        var sourceFrame = frames[method.instructions.indexOf(added.getFirst())];
        var targetFrame = frames[method.instructions.indexOf(((JumpInsnNode)added.get(2)).label)];
        check(sourceFrame.getStackSize() == 0 && targetFrame.getStackSize() == 0,
                "The added branch joins an existing empty-stack continuation");
        boolean matchingLocals = true;
        for (int index = 0; index < targetFrame.getLocals(); index++) {
            var required = targetFrame.getLocal(index);
            if (!required.equals(BasicValue.UNINITIALIZED_VALUE) && !required.equals(sourceFrame.getLocal(index))) matchingLocals = false;
        }
        check(matchingLocals, "Every local required at the original continuation is unchanged before the laser block");
        ClassWriter writer = new ClassWriter(0); node.accept(writer);
        ClassNode roundTrip = new ClassNode(); new ClassReader(writer.toByteArray()).accept(roundTrip, 0);
        new Analyzer<>(new BasicVerifier()).analyze(roundTrip.name, method(roundTrip));
        check(true, "Serialized actual bytecode retains valid original continuation frames");
        executeGuard(added, conversions);
        boolean duplicateRejected = false;
        try { AdvancedHatchLaserRecipeCompatibility.transform(node); }
        catch (IllegalStateException expected) { duplicateRejected = true; }
        check(duplicateRejected, "A second transformation fails visibly instead of stacking guards");
        ClassNode changed = read(jar);
        for (var instruction : method(changed).instructions)
            if (instruction instanceof FieldInsnNode field && field.name.equals("WIRELESS_ENERGY_INPUT_HATCH_256A")) field.name += "_changed";
        int before = method(changed).instructions.size();
        boolean driftRejected = false;
        try { AdvancedHatchLaserRecipeCompatibility.transform(changed); }
        catch (IllegalStateException expected) { driftRejected = true; }
        check(driftRejected && method(changed).instructions.size() == before, "Changed source inventory is rejected before modifying unknown bytecode");
        return assertions;
    }

    private static void executeGuard(List<AbstractInsnNode> guard, int conversions) throws Exception {
        ClassNode probe = new ClassNode(); probe.version = Opcodes.V17; probe.access = Opcodes.ACC_PUBLIC;
        probe.name = "com/gtl/enhancedcore/audit/AdvancedHatchLaserGuardProbe"; probe.superName = "java/lang/Object";
        MethodNode method = new MethodNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "recipes", "(I)I", null, null);
        LabelNode end = new LabelNode();
        method.instructions.add(new InsnNode(Opcodes.ICONST_0)); method.instructions.add(new VarInsnNode(Opcodes.ISTORE, 1));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 0)); method.instructions.add(new InsnNode(Opcodes.ICONST_4));
        method.instructions.add(new JumpInsnNode(Opcodes.IF_ICMPLT, end));
        method.instructions.add(new IincInsnNode(1, 1)); // Existing EV ordinary 64A recipe boundary.
        method.instructions.add(new VarInsnNode(guard.get(0).getOpcode(), 0));
        method.instructions.add(new InsnNode(guard.get(1).getOpcode()));
        method.instructions.add(new JumpInsnNode(guard.get(2).getOpcode(), end));
        method.instructions.add(new IincInsnNode(1, conversions));
        method.instructions.add(end); method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 1));
        method.instructions.add(new InsnNode(Opcodes.IRETURN)); probe.methods.add(method);
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS); probe.accept(writer);
        byte[] bytes = writer.toByteArray();
        Class<?> type = new ClassLoader(null) {
            Class<?> define() { return defineClass(probe.name.replace('/', '.'), bytes, 0, bytes.length); }
        }.define();
        var run = type.getMethod("recipes", int.class);
        for (int tier = 0; tier <= 14; tier++) {
            int expected = tier < 4 ? 0 : tier == 4 ? 1 : conversions + 1;
            check(run.invoke(null, tier).equals(expected), "Tier " + tier + " preserves ordinary conversions and applies the exact laser boundary");
        }
    }

    private static ClassNode read(Path jar) throws Exception {
        try (ZipFile zip = new ZipFile(jar.toFile()); var input = zip.getInputStream(zip.getEntry(AdvancedHatchLaserRecipeCompatibility.TARGET + ".class"))) {
            ClassNode node = new ClassNode(); new ClassReader(input).accept(node, 0); return node;
        }
    }
    private static MethodNode method(ClassNode node) {
        return node.methods.stream().filter(value -> value.name.equals("initRecipes") && value.desc.equals("(Ljava/util/function/Consumer;)V")).findFirst().orElseThrow();
    }
    private static void check(boolean condition, String message) { assertions++; if (!condition) throw new AssertionError(message); }
}
