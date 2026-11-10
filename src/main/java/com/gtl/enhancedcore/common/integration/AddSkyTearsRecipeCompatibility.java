package com.gtl.enhancedcore.common.integration;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;

/** ADD 3.3.1Custom reads stacks[0] after its empty-input branch; continue its existing input loop instead. */
public final class AddSkyTearsRecipeCompatibility {
    public static final String TARGET = "com/gtladd/gtladditions/common/modify/SkyTearsAndGregHeart";
    private static final String STACK = "net/minecraft/world/item/ItemStack";

    private AddSkyTearsRecipeCompatibility() {}

    public static void transform(ClassNode node) {
        if (!TARGET.equals(node.name)) throw new IllegalArgumentException("Unexpected ADD sky tears target: " + node.name);
        var method = node.methods.stream().filter(value -> value.name.equals("init$lambda$4")
                && value.desc.equals("(Lcom/gregtechceu/gtceu/data/recipe/builder/GTRecipeBuilder;Ljava/util/function/Consumer;)V"))
                .findFirst().orElseThrow();
        JumpInsnNode empty = null, continuation = null;
        for (var instruction : method.instructions) {
            if (empty == null && instruction.getOpcode() == Opcodes.ARRAYLENGTH
                    && next(instruction) instanceof JumpInsnNode jump && jump.getOpcode() == Opcodes.IFLE)
                empty = jump;
            if (instruction instanceof MethodInsnNode call && call.owner.equals(STACK)
                    && call.desc.equals("(Lnet/minecraft/world/item/Item;)Z")
                    && (call.name.equals("is") || call.name.equals("m_150930_"))) {
                if (next(call) instanceof JumpInsnNode jump && jump.getOpcode() == Opcodes.IFEQ) continuation = jump;
                break;
            }
        }
        if (empty == null || continuation == null || empty.label == continuation.label
                || next(empty.label).getOpcode() != Opcodes.ALOAD
                || next(next(empty.label)).getOpcode() != Opcodes.ICONST_0
                || next(next(next(empty.label))).getOpcode() != Opcodes.AALOAD
                || next(continuation.label).getOpcode() != Opcodes.ALOAD
                || !(next(next(continuation.label)) instanceof MethodInsnNode loop)
                || !loop.owner.equals("java/util/Iterator") || !loop.name.equals("hasNext"))
            throw new IllegalStateException("ADD sky tears input loop changed");
        // Both labels already have empty operand stacks and valid frames. No recipe content is rewritten.
        empty.label = continuation.label;
    }

    private static AbstractInsnNode next(AbstractInsnNode node) {
        do { node = node.getNext(); } while (node != null && node.getOpcode() < 0);
        return node;
    }
}
