package com.gtl.enhancedcore;

import com.gtl.enhancedcore.client.ftbchunks.ChunkMapLayout;
import java.util.jar.JarFile;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/** Upstream constant counts behind the fixed 31x31 FTB claim map Mixins, plus layout maths. */
final class FtbClaimMapRegression {
    private static final String JAR = "libs/ftb-chunks-forge-2001.3.8.jar";
    private static final String SCREEN = "dev/ftb/mods/ftbchunks/client/gui/ChunkScreen";
    private static int assertions;

    static int run() throws Exception {
        assertions = 0;
        ClassNode screen = read(SCREEN);
        MethodNode add = method(screen, "addWidgets");
        check(ints(add, 240) == 2, "addWidgets centres with two 240 constants");
        check(ints(add, 7) == 6, "addWidgets uses radius 7 six times (origin + map request)");
        check(ints(add, 15) == 2, "addWidgets loops 15x15 buttons");
        check(firstTwoSixteensAreSpacing(add), "first two 16s in addWidgets are button spacing");
        MethodNode draw = method(screen, "drawBackground");
        check(ints(draw, 240) == 6, "drawBackground has six 240 constants");
        check(ints(draw, 15) == 2, "drawBackground draws 15 grid lines per axis");
        check(ints(draw, 16) == 2, "drawBackground grid spacing 16 appears twice");
        check(ints(draw, 112) == 2, "drawBackground player centre 112 appears twice");
        check(calls(draw, "drawTexturedRect") == 1, "single background texture draw to redirect");
        check(calls(draw, "mod") == 2, "two in-chunk player offsets");

        ClassNode button = read(SCREEN + "$ChunkButton");
        check(ints(method(button, "<init>"), 16) == 2, "ChunkButton setSize(16, 16)");
        check(ints(method(button, "lambda$drawBackground$0"), 16) == 2, "force-load icon drawn 16x16");

        check(ChunkMapLayout.CHUNKS == 31 && ChunkMapLayout.RADIUS == 15, "fixed 31x31 with radius 15");
        check(ChunkMapLayout.tileFor(960, 540) == 16, "GUI scale 2 at 1080p keeps 16 px tiles");
        check(ChunkMapLayout.tileFor(640, 360) == 10, "GUI scale 3 at 1080p shrinks to 10 px");
        check(ChunkMapLayout.tileFor(100, 100) == 4, "tiny windows clamp to 4 px");
        check(ChunkMapLayout.tileFor(4000, 4000) == 16, "huge windows clamp to 16 px");
        for (int[] s : new int[][]{{640, 360}, {960, 540}, {854, 480}, {1280, 720}}) {
            int tile = ChunkMapLayout.tileFor(s[0], s[1]);
            check(31 * tile <= s[1] - 40 && 31 * tile <= s[0] - 40, "map fits inside margins");
        }
        return assertions;
    }

    public static void main(String[] args) throws Exception {
        System.out.println("FTB claim map assertions: " + run());
    }

    private static boolean firstTwoSixteensAreSpacing(MethodNode m) {
        int seen = 0;
        for (AbstractInsnNode insn = m.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            if (isInt(insn, 16)) {
                AbstractInsnNode next = insn.getNext();
                if (next == null || next.getOpcode() != org.objectweb.asm.Opcodes.IMUL) return false;
                if (++seen == 2) return true;
            }
        }
        return false;
    }

    private static int ints(MethodNode m, int value) {
        int n = 0;
        for (AbstractInsnNode insn : m.instructions) if (isInt(insn, value)) n++;
        return n;
    }

    private static boolean isInt(AbstractInsnNode insn, int value) {
        return insn instanceof IntInsnNode i && (i.getOpcode() == org.objectweb.asm.Opcodes.BIPUSH
                || i.getOpcode() == org.objectweb.asm.Opcodes.SIPUSH) && i.operand == value;
    }

    private static int calls(MethodNode m, String name) {
        int n = 0;
        for (AbstractInsnNode insn : m.instructions)
            if (insn instanceof MethodInsnNode c && c.name.equals(name)) n++;
        return n;
    }

    private static MethodNode method(ClassNode node, String name) {
        return node.methods.stream().filter(m -> m.name.equals(name)).findFirst().orElseThrow();
    }

    private static ClassNode read(String name) throws Exception {
        ClassNode node = new ClassNode();
        try (JarFile jar = new JarFile(JAR); var in = jar.getInputStream(jar.getJarEntry(name + ".class"))) {
            new ClassReader(in).accept(node, 0);
        }
        return node;
    }

    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }
}
