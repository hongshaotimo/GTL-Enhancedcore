package com.gtl.enhancedcore.common.integration;

import java.util.List;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

/** Replaces Plus's hide-all-slots control with our independent empty-slot display control. */
public final class ExPatternTerminalExtras {
    public static final String CLIENT_MIXIN =
            "com.gtl.enhancedcore.mixin.extendedae.client.ExPatternTerminalEmptySlotsMixin";
    private static final String TARGET = "com.glodblock.github.extendedae.client.gui.GuiExPatternTerminal";
    private static final String PLUS = "com.extendedae_plus.mixin.extendedae.client.gui.GuiExPatternTerminalMixin";
    private static final String DESCRIPTOR = "(Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V";
    private static final String MERGED = "Lorg/spongepowered/asm/mixin/transformer/meta/MixinMerged;";

    private ExPatternTerminalExtras() {}

    public static void postApply(String target, ClassNode node, String mixin) {
        if (!CLIENT_MIXIN.equals(mixin)) return;
        if (!TARGET.equals(target)) throw new IllegalStateException("Unexpected extended terminal target: " + target);
        boolean plusPresent = node.methods.stream().anyMatch(method -> ownedBy(method, PLUS));
        if (!plusPresent) return;
        MethodNode constructorHook = find(node, "injectConstructor");
        // Validate the second hook too: it is the code that removes every SlotsRow when false.
        MethodNode refreshHook = find(node, "onRefreshListEnd");
        String field = null;
        for (var instruction : constructorHook.instructions) {
            if (instruction instanceof FieldInsnNode access && access.getOpcode() == Opcodes.PUTFIELD
                    && access.owner.equals(node.name) && access.name.equals("eap$showSlots") && access.desc.equals("Z")) {
                if (field != null) throw new IllegalStateException("Duplicate Plus terminal slot flag assignment");
                field = access.name;
            }
        }
        if (field == null) throw new IllegalStateException("Plus terminal slot flag changed");
        boolean refreshReadsFlag = false;
        for (var instruction : refreshHook.instructions) {
            if (instruction instanceof FieldInsnNode access && access.getOpcode() == Opcodes.GETFIELD
                    && access.owner.equals(node.name) && access.name.equals(field) && access.desc.equals("Z")) {
                refreshReadsFlag = true;
            }
        }
        if (!refreshReadsFlag) throw new IllegalStateException("Plus terminal row filter changed");
        // Do not construct/register the old left-toolbar button. Its refresh hook already guards null.
        constructorHook.instructions.clear();
        constructorHook.tryCatchBlocks.clear();
        if (constructorHook.localVariables != null) constructorHook.localVariables.clear();
        constructorHook.visibleLocalVariableAnnotations = null;
        constructorHook.invisibleLocalVariableAnnotations = null;
        constructorHook.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        constructorHook.instructions.add(new InsnNode(Opcodes.ICONST_1));
        constructorHook.instructions.add(new FieldInsnNode(Opcodes.PUTFIELD, node.name, field, "Z"));
        constructorHook.instructions.add(new InsnNode(Opcodes.RETURN));
        constructorHook.maxStack = 2;
        constructorHook.maxLocals = 2;
    }

    private static MethodNode find(ClassNode node, String originalName) {
        MethodNode found = null;
        for (MethodNode method : node.methods) {
            if (DESCRIPTOR.equals(method.desc) && ownedBy(method, PLUS)
                    && (method.name.equals(originalName) || method.name.endsWith("$" + originalName))) {
                if (found != null) throw new IllegalStateException("Ambiguous Plus terminal hook: " + originalName);
                found = method;
            }
        }
        if (found == null) throw new IllegalStateException("Plus terminal hook changed: " + originalName);
        return found;
    }

    private static boolean ownedBy(MethodNode method, String owner) {
        List<AnnotationNode> annotations = method.visibleAnnotations;
        if (annotations == null) return false;
        for (AnnotationNode annotation : annotations) {
            if (!MERGED.equals(annotation.desc) || annotation.values == null) continue;
            for (int i = 0; i + 1 < annotation.values.size(); i += 2) {
                if ("mixin".equals(annotation.values.get(i)) && owner.equals(annotation.values.get(i + 1))) return true;
            }
        }
        return false;
    }
}
