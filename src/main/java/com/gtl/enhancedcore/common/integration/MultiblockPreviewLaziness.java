package com.gtl.enhancedcore.common.integration;

import java.util.List;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;

/** Precise postApply edits for GTLCore's eager multiblock JEI preview path. */
public final class MultiblockPreviewLaziness {
    public static final String MARKER_MIXIN = "com.gtl.enhancedcore.mixin.jei.LazyMultiblockPreviewMixin";
    private static final String WRAPPER_MIXIN = "org.gtlcore.gtlcore.mixin.gtm.MultiblockInfoWrapperMixin";
    private static final String CATEGORY_MIXIN = "org.gtlcore.gtlcore.mixin.gtm.MultiblockInfoCategoryMixin";
    private static final String MERGED = "Lorg/spongepowered/asm/mixin/transformer/meta/MixinMerged;";
    private static final String LAZY = "com/gtl/enhancedcore/client/preview/LazyMultiblockPreviewWidget";

    private MultiblockPreviewLaziness() {}

    public static void postApply(String target, ClassNode node, String mixin) {
        if ("com.gtl.enhancedcore.mixin.jei.StagedMultiblockPreviewMixin".equals(mixin)) {
            PreviewPageTransformer.apply(node);
            return;
        }
        if (!MARKER_MIXIN.equals(mixin)) return;
        if ("com.gregtechceu.gtceu.integration.jei.multipage.MultiblockInfoWrapper".equals(target)
                ) {
            replacePreviewFactory(node);
        } else if ("com.gregtechceu.gtceu.integration.jei.multipage.MultiblockInfoCategory".equals(target)
                ) {
            injectCategoryPreparation(node);
        } else {
            throw new IllegalStateException("Unexpected lazy multiblock preview target: " + target);
        }
    }

    private static void replacePreviewFactory(ClassNode node) {
        int replaced = 0;
        for (MethodNode method : node.methods) {
            if (!ownedBy(method, WRAPPER_MIXIN) || !method.name.endsWith("MultiblockInfoWrapper")) continue;
            for (var instruction : method.instructions) {
                if (!(instruction instanceof MethodInsnNode call) || call.getOpcode() != Opcodes.INVOKESTATIC) continue;
                if (!"org/gtlcore/gtlcore/api/gui/PatternPreviewWidget".equals(call.owner)
                        || !"getPatternWidget".equals(call.name)) continue;
                call.owner = LAZY;
                call.name = "create";
                call.desc = "(Lcom/gregtechceu/gtceu/api/machine/MultiblockMachineDefinition;)Lcom/lowdragmc/lowdraglib/gui/widget/Widget;";
                replaced++;
            }
        }
        if (replaced != 1) {
            throw new IllegalStateException("Expected one GTLCore eager multiblock preview call, found " + replaced);
        }
    }

    private static void injectCategoryPreparation(ClassNode node) {
        int injected = 0;
        for (MethodNode method : node.methods) {
            if (!ownedBy(method, CATEGORY_MIXIN) || !method.name.endsWith("setRecipe")
                    || !method.desc.equals("(Lmezz/jei/api/gui/builder/IRecipeLayoutBuilder;"
                        + "Lcom/gregtechceu/gtceu/integration/jei/multipage/MultiblockInfoWrapper;"
                        + "Lmezz/jei/api/recipe/IFocusGroup;)V")) continue;
            InsnList prefix = new InsnList();
            LabelNode continueLabel = new LabelNode();
            prefix.add(new VarInsnNode(Opcodes.ALOAD, 1));
            prefix.add(new VarInsnNode(Opcodes.ALOAD, 2));
            prefix.add(new MethodInsnNode(Opcodes.INVOKESTATIC,
                    "com/gtl/enhancedcore/client/preview/LazyMultiblockPreviewWidget", "prepare",
                    "(Ljava/lang/Object;Ljava/lang/Object;)Z", false));
            prefix.add(new JumpInsnNode(Opcodes.IFEQ, continueLabel));
            prefix.add(new InsnNode(Opcodes.RETURN));
            prefix.add(continueLabel);
            method.instructions.insert(prefix);
            injected++;
        }
        if (injected != 1) {
            throw new IllegalStateException("Expected one GTLCore multiblock category setRecipe method, found " + injected);
        }
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
