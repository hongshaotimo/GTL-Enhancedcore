package com.gtl.enhancedcore.common.integration;

import java.util.List;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodNode;

/** Retains Enhancedcore's selectable rectangle mode instead of GTLCore's Ctrl release expansion. */
public final class GtlFtbRectangleClaim {
    public static final String CLIENT_MIXIN = "com.gtl.enhancedcore.mixin.ftbchunks.client.ChunkScreenMixin";
    private static final String TARGET = "dev.ftb.mods.ftbchunks.client.gui.ChunkScreen";
    private static final String UPSTREAM = "org.gtlcore.gtlcore.mixin.ftbchunks.ChunkScreenMixin";
    private static final String HOOK = "gtlcore$rectClaimOnRelease";
    private static final String DESCRIPTOR = "(Ldev/ftb/mods/ftblibrary/ui/input/MouseButton;"
            + "Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V";

    private GtlFtbRectangleClaim() {}

    public static void postApply(String target, ClassNode node, String mixin) {
        if (!CLIENT_MIXIN.equals(mixin)) return;
        if (!TARGET.equals(target)) throw new IllegalStateException("Unexpected FTB claim target: " + target);
        MethodNode found = null;
        for (MethodNode method : node.methods) {
            if (!ownedByUpstream(method)) continue;
            if (!(method.name.equals(HOOK) || method.name.endsWith("$" + HOOK))
                    || !method.desc.equals(DESCRIPTOR)) {
                throw new IllegalStateException("GTLCore FTB claim hook changed: " + method.name + method.desc);
            }
            if (found != null) throw new IllegalStateException("Ambiguous GTLCore FTB claim hook");
            found = method;
        }
        // Older GTLCore versions have no overlapping hook.
        if (found == null) return;
        found.instructions.clear();
        found.tryCatchBlocks.clear();
        if (found.localVariables != null) found.localVariables.clear();
        found.visibleLocalVariableAnnotations = null;
        found.invisibleLocalVariableAnnotations = null;
        found.instructions.add(new InsnNode(Opcodes.RETURN));
        found.maxStack = 0;
        found.maxLocals = 3;
    }

    private static boolean ownedByUpstream(MethodNode method) {
        List<AnnotationNode> annotations = method.visibleAnnotations;
        if (annotations == null) return false;
        for (AnnotationNode annotation : annotations) {
            if (!"Lorg/spongepowered/asm/mixin/transformer/meta/MixinMerged;".equals(annotation.desc)
                    || annotation.values == null) continue;
            for (int i = 0; i + 1 < annotation.values.size(); i += 2) {
                if ("mixin".equals(annotation.values.get(i)) && UPSTREAM.equals(annotation.values.get(i + 1))) return true;
            }
        }
        return false;
    }
}
