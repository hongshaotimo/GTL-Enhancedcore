package com.gtl.enhancedcore.common.integration;

import java.util.List;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

/** Uses LDLib's holder-aware logical side instead of resolving Minecraft on the server. */
public final class SliderTextFieldSideCompatibility {
    public static final String TARGET = "com/gtladd/gtladditions/api/gui/widget/SliderTextFieldWidget";
    public static final String MARKER = "com.gtl.enhancedcore.mixin.gtladditions.SliderTextFieldSideCompatibilityMixin";
    public static final String WIDGET = "com/lowdragmc/lowdraglib/gui/widget/Widget";
    private static final String CLIENT = "net/minecraft/client/Minecraft";
    private static final String SET_DESCRIPTOR = "(I)L" + TARGET + ";";

    private SliderTextFieldSideCompatibility() {}

    /** Replaces one consecutive call pair without changing callbacks, branches, frames or stack peaks. */
    public static int transform(ClassNode node) {
        if (!TARGET.equals(node.name)) throw new IllegalStateException("Unexpected ADD slider target: " + node.name);
        var methods = node.methods.stream().filter(method -> method.name.equals("setCurrentValue")).toList();
        if (methods.size() != 1 || !methods.getFirst().desc.equals(SET_DESCRIPTOR))
            throw new IllegalStateException("ADD slider setter ABI changed");
        var method = methods.getFirst();
        if ((method.access & Opcodes.ACC_STATIC) != 0 || !node.superName.equals("com/lowdragmc/lowdraglib/gui/widget/WidgetGroup"))
            throw new IllegalStateException("ADD slider setter receiver changed");
        List<MethodInsnNode> calls = java.util.Arrays.stream(method.instructions.toArray())
                .filter(instruction -> instruction instanceof MethodInsnNode call && call.owner.equals(CLIENT))
                .map(instruction -> (MethodInsnNode)instruction).toList();
        if (calls.size() != 2) throw new IllegalStateException("ADD slider client-call inventory changed: " + calls.size());
        var instance = calls.getFirst();
        var thread = calls.getLast();
        if (instance.getOpcode() != Opcodes.INVOKESTATIC || !instance.name.equals("m_91087_")
                || !instance.desc.equals("()L" + CLIENT + ";") || instance.itf
                || instance.getNext() != thread || thread.getOpcode() != Opcodes.INVOKEVIRTUAL
                || !thread.name.equals("m_18695_") || !thread.desc.equals("()Z") || thread.itf
                || !(thread.getNext() instanceof JumpInsnNode branch) || branch.getOpcode() != Opcodes.IFEQ)
            throw new IllegalStateException("ADD slider side-condition bytecode changed");
        method.instructions.set(instance, new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.set(thread, new MethodInsnNode(Opcodes.INVOKEVIRTUAL, WIDGET, "isRemote", "()Z", false));
        return 1;
    }
}
