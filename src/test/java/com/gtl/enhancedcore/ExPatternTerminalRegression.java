package com.gtl.enhancedcore;

import com.gtl.enhancedcore.client.pattern.PatternTerminalSlotProjection;
import com.gtl.enhancedcore.common.integration.ExPatternTerminalExtras;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodNode;

/** Sparse inventory index safety and precise ownership of the Plus control replacement. */
final class ExPatternTerminalRegression {
    private static final String TARGET = "com.glodblock.github.extendedae.client.gui.GuiExPatternTerminal";
    private static final String PLUS = "com.extendedae_plus.mixin.extendedae.client.gui.GuiExPatternTerminalMixin";
    private static final String CI = "(Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V";

    static int run() {
        int checks = 0;
        // Exhaust all occupancy patterns across a row boundary, including holes and an empty provider.
        for (int mask = 0; mask < 1024; mask++) {
            final int occupancy = mask;
            int[] actual = PatternTerminalSlotProjection.occupiedSlots(10, slot -> (occupancy & (1 << slot)) != 0);
            check(actual.length == Integer.bitCount(mask), "Visible count matches occupied server slots"); checks++;
            int previous = -1;
            for (int index : actual) {
                check(index > previous && (mask & (1 << index)) != 0,
                        "Compact display maps monotonically to original occupied indices"); checks++;
                previous = index;
            }
        }
        int[] before = PatternTerminalSlotProjection.occupiedSlots(36, i -> i == 2 || i == 35);
        int[] after = PatternTerminalSlotProjection.occupiedSlots(36, i -> i == 17 || i == 35);
        check(Arrays.equals(before, new int[]{2, 35}) && Arrays.equals(after, new int[]{17, 35}),
                "Incremental occupancy rebuild must not reuse the previous compact indices"); checks++;
        ClassNode target = fixture();
        MethodNode unrelated = hook("another$injectConstructor", "another.mod.Mixin");
        unrelated.instructions.add(new InsnNode(Opcodes.RETURN));
        target.methods.add(unrelated);
        ExPatternTerminalExtras.postApply(TARGET, target, ExPatternTerminalExtras.CLIENT_MIXIN);
        MethodNode patched = target.methods.get(0);
        check(patched.instructions.size() == 4 && patched.instructions.get(1).getOpcode() == Opcodes.ICONST_1,
                "Plus constructor enables rows without constructing its old toolbar toggle"); checks++;
        check(unrelated.instructions.size() == 1, "Another mod's similarly named hook remains intact"); checks++;
        ClassNode noPlus = new ClassNode();
        ExPatternTerminalExtras.postApply(TARGET, noPlus, ExPatternTerminalExtras.CLIENT_MIXIN);
        check(noPlus.methods.isEmpty(), "ExtendedAE without Plus needs no post-merge patch"); checks++;
        ClassNode missing = fixture();
        missing.methods.remove(1);
        expectFailure(missing, "Missing Plus refresh hook fails closed"); checks++;
        ClassNode duplicate = fixture();
        duplicate.methods.add(hook("other$injectConstructor", PLUS));
        expectFailure(duplicate, "Duplicate Plus constructor hook fails closed"); checks++;
        ClassNode changedFlag = fixture();
        changedFlag.methods.get(0).instructions.clear();
        expectFailure(changedFlag, "Changed upstream state flag fails closed"); checks++;
        return checks;
    }

    public static void main(String[] args) {
        System.out.println("Extended pattern terminal regression: " + run() + " checks passed");
    }

    private static ClassNode fixture() {
        ClassNode node = new ClassNode();
        node.name = TARGET.replace('.', '/');
        MethodNode constructor = hook("handler$abc$injectConstructor", PLUS);
        constructor.instructions.add(new FieldInsnNode(Opcodes.PUTFIELD, node.name, "eap$showSlots", "Z"));
        node.methods.add(constructor);
        MethodNode refresh = hook("handler$def$onRefreshListEnd", PLUS);
        refresh.instructions.add(new FieldInsnNode(Opcodes.GETFIELD, node.name, "eap$showSlots", "Z"));
        node.methods.add(refresh);
        return node;
    }

    private static MethodNode hook(String name, String owner) {
        MethodNode method = new MethodNode(Opcodes.ACC_PRIVATE, name, CI, null, null);
        AnnotationNode annotation = new AnnotationNode("Lorg/spongepowered/asm/mixin/transformer/meta/MixinMerged;");
        annotation.values = new ArrayList<>(List.of("mixin", owner));
        method.visibleAnnotations = new ArrayList<>(List.of(annotation));
        return method;
    }

    private static void expectFailure(ClassNode node, String message) {
        try {
            ExPatternTerminalExtras.postApply(TARGET, node, ExPatternTerminalExtras.CLIENT_MIXIN);
        } catch (IllegalStateException expected) {
            return;
        }
        throw new AssertionError(message);
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
