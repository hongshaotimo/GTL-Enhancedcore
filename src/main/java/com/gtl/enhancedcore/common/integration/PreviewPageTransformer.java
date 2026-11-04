package com.gtl.enhancedcore.common.integration;

import java.util.HashMap;
import java.util.Map;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

/** Checked bridges retain the installed GTLCore material sorting and drop semantics. */
final class PreviewPageTransformer {
    private static final String WIDGET = "org/gtlcore/gtlcore/api/gui/PatternPreviewWidget";
    private static final String PATTERN = WIDGET + "$MBPattern";
    private static final String ACCESS = "com/gtl/enhancedcore/client/preview/PreviewPageAccess";
    private static final String PREPARATION = "com/gtl/enhancedcore/client/preview/PreviewPreparation";
    private static final String MAP = "Ljava/util/Map;";
    private static final String SET = "Ljava/util/Set;";
    private static final String POS = "Lnet/minecraft/core/BlockPos;";
    private static final String INFO = "Lcom/lowdragmc/lowdraglib/utils/BlockInfo;";
    private static final String CONTROLLER = "Lcom/gregtechceu/gtceu/api/machine/feature/multiblock/IMultiController;";
    private static final String HOST = "Lorg/gtlcore/gtlcore/api/machine/multiblock/IModularMachineHost;";

    private PreviewPageTransformer() {}

    static void apply(ClassNode node) {
        require(WIDGET.equals(node.name), "unexpected target " + node.name);
        require(!node.interfaces.contains(ACCESS), "duplicate page transformation");
        MethodNode constructor = method(node, "<init>",
                "(Lcom/gregtechceu/gtceu/api/machine/MultiblockMachineDefinition;)V");
        int cacheCalls = 0;
        for (var insn : constructor.instructions.toArray()) {
            if (insn instanceof MethodInsnNode call && call.owner.equals("java/util/Map")
                    && call.name.equals("computeIfAbsent")) {
                constructor.instructions.insertBefore(call, new VarInsnNode(Opcodes.ALOAD, 0));
                call.setOpcode(Opcodes.INVOKESTATIC);
                call.owner = PREPARATION;
                call.name = "patterns";
                call.desc = "(" + MAP + "Ljava/lang/Object;Ljava/util/function/Function;Ljava/lang/Object;)Ljava/lang/Object;";
                call.itf = false;
                cacheCalls++;
            }
        }
        require(cacheCalls == 1, "constructor cache calls=" + cacheCalls);
        MethodNode page = method(node, "setPage", "(ILcom/lowdragmc/lowdraglib/gui/util/ClickData;)V");
        InsnList guard = new InsnList();
        LabelNode proceed = new LabelNode();
        guard.add(new VarInsnNode(Opcodes.ALOAD, 0));
        guard.add(new VarInsnNode(Opcodes.ILOAD, 1));
        guard.add(new VarInsnNode(Opcodes.ALOAD, 2));
        guard.add(new MethodInsnNode(Opcodes.INVOKESTATIC, PREPARATION, "requestPage",
                "(Ljava/lang/Object;ILcom/lowdragmc/lowdraglib/gui/util/ClickData;)Z", false));
        guard.add(new JumpInsnNode(Opcodes.IFEQ, proceed));
        guard.add(new InsnNode(Opcodes.RETURN));
        guard.add(proceed);
        page.instructions.insert(guard);

        node.interfaces.add(ACCESS);
        MethodNode array = bridge(node, "enhanced$newPatterns", "(I)[Ljava/lang/Object;");
        array.instructions.add(new VarInsnNode(Opcodes.ILOAD, 1));
        array.instructions.add(new TypeInsnNode(Opcodes.ANEWARRAY, PATTERN));
        array.instructions.add(new InsnNode(Opcodes.ARETURN));
        var patternsField = node.fields.stream().filter(field -> field.name.equals("patterns")
                && field.desc.equals("[L" + PATTERN + ";")).toList();
        require(patternsField.size() == 1, "missing patterns field");
        patternsField.getFirst().access &= ~Opcodes.ACC_FINAL;
        MethodNode replace = bridge(node, "enhanced$replacePatterns", "([Ljava/lang/Object;)V");
        replace.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        replace.instructions.add(new VarInsnNode(Opcodes.ALOAD, 1));
        replace.instructions.add(new TypeInsnNode(Opcodes.CHECKCAST, "[L" + PATTERN + ";"));
        replace.instructions.add(new FieldInsnNode(Opcodes.PUTFIELD, WIDGET, "patterns", "[L" + PATTERN + ";"));
        replace.instructions.add(new InsnNode(Opcodes.RETURN));
        MethodNode world = bridge(node, "enhanced$previewWorld", "()Lcom/lowdragmc/lowdraglib/utils/TrackedDummyWorld;");
        world.instructions.add(new FieldInsnNode(Opcodes.GETSTATIC, WIDGET, "LEVEL",
                "Lcom/lowdragmc/lowdraglib/utils/TrackedDummyWorld;"));
        world.instructions.add(new InsnNode(Opcodes.ARETURN));
        delegate(node, "enhanced$nextRegion", "locateNextRegion", "()" + POS, true);
        delegate(node, "enhanced$previewController", "previewController", "(" + INFO + POS
                + ")Lnet/minecraft/world/level/block/entity/BlockEntity;", true);
        delegate(node, "enhanced$plainBlock", "reusableBlockInfo",
                "(Lnet/minecraft/world/level/block/state/BlockState;)" + INFO, false);
        delegate(node, "enhanced$formPreview", "loadControllerFormed",
                "(Ljava/util/Collection;" + CONTROLLER + ")V", false);
        delegate(node, "enhanced$initializeModules", "initializeModules",
                "(" + HOST + POS + MAP + MAP + SET + ")Ljava/util/List;", true);
        delegate(node, "enhanced$mergeModulePredicates", "mergeModulePredicates",
                "(" + CONTROLLER + MAP + ")V", false);
        extractDrop(node);
        extractFinish(node);
    }

    private static void extractDrop(ClassNode node) {
        MethodNode original = method(node, "gatherBlockDrops", "(" + MAP + ")" + MAP);
        AbstractInsnNode start = null, end = null;
        for (var insn : original.instructions) {
            if (insn instanceof MethodInsnNode call && call.owner.equals("java/util/Map$Entry")
                    && call.name.equals("getKey")) {
                require(start == null, "multiple drop entry reads");
                start = previousCode(insn);
            }
            if (insn instanceof FieldInsnNode field && field.getOpcode() == Opcodes.PUTFIELD
                    && field.owner.equals(WIDGET + "$PartInfo") && field.name.equals("amount")) {
                require(end == null, "multiple drop counter writes");
                end = insn.getNext();
            }
        }
        require(start instanceof VarInsnNode load && load.getOpcode() == Opcodes.ALOAD && load.var == 4
                && end != null, "drop loop signature changed");
        MethodNode drop = bridge(node, "enhanced$gatherDrop", "(" + MAP + "Ljava/util/Map$Entry;)V");
        // Keep the original locals (2=accumulator, 4=entry); argument copies must not overlap.
        drop.instructions.add(new VarInsnNode(Opcodes.ALOAD, 2));
        drop.instructions.add(new VarInsnNode(Opcodes.ASTORE, 4));
        drop.instructions.add(new VarInsnNode(Opcodes.ALOAD, 1));
        drop.instructions.add(new VarInsnNode(Opcodes.ASTORE, 2));
        copyRange(original, drop, start, end, Map.of());
        drop.instructions.add(new InsnNode(Opcodes.RETURN));
    }

    private static void extractFinish(ClassNode node) {
        MethodNode original = method(node, "initializePattern",
                "(Lcom/gregtechceu/gtceu/api/pattern/MultiblockShapeInfo;)L" + PATTERN + ";");
        AbstractInsnNode start = null;
        for (var insn : original.instructions) {
            if (insn instanceof MethodInsnNode call && call.owner.equals("java/util/Map") && call.name.equals("values")) {
                require(start == null, "multiple pattern material finalizations");
                start = previousCode(insn);
            }
        }
        require(start instanceof VarInsnNode load && load.var == 11, "pattern finish signature changed");
        MethodNode finish = bridge(node, "enhanced$finishPattern",
                "(" + MAP + SET + CONTROLLER + MAP + MAP + ")Ljava/lang/Object;");
        copyRange(original, finish, start, null, Map.of(2, 1, 3, 2, 4, 3, 10, 4, 11, 5, 12, 6));
    }

    private static void copyRange(MethodNode original, MethodNode target, AbstractInsnNode start,
            AbstractInsnNode end, Map<Integer, Integer> locals) {
        Map<LabelNode, LabelNode> labels = new HashMap<>();
        for (var insn = start; insn != end; insn = insn.getNext()) {
            require(insn != null, "invalid extraction range");
            if (insn instanceof LabelNode label) labels.put(label, new LabelNode());
        }
        for (var insn = start; insn != end; insn = insn.getNext()) {
            if (insn instanceof FrameNode || insn instanceof LineNumberNode) continue;
            if (insn instanceof JumpInsnNode jump) require(labels.containsKey(jump.label), "extraction jumps outside phase");
            var clone = insn.clone(labels);
            if (clone instanceof VarInsnNode variable && !locals.isEmpty()) {
                require(locals.containsKey(variable.var), "unexpected phase local " + variable.var);
                variable.var = locals.get(variable.var);
            }
            target.instructions.add(clone);
        }
        for (var handler : original.tryCatchBlocks) {
            boolean overlaps = labels.containsKey(handler.start) || labels.containsKey(handler.end) || labels.containsKey(handler.handler);
            require(!overlaps, "extracted phase acquired an exception handler");
        }
    }

    private static AbstractInsnNode previousCode(AbstractInsnNode insn) {
        do { insn = insn.getPrevious(); } while (insn != null && insn.getOpcode() < 0);
        return insn;
    }

    private static void delegate(ClassNode node, String bridgeName, String original, String descriptor, boolean isStatic) {
        method(node, original, descriptor);
        MethodNode bridge = bridge(node, bridgeName, descriptor);
        if (!isStatic) bridge.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        int slot = 1;
        for (Type argument : Type.getArgumentTypes(descriptor)) {
            bridge.instructions.add(new VarInsnNode(argument.getOpcode(Opcodes.ILOAD), slot));
            slot += argument.getSize();
        }
        bridge.instructions.add(new MethodInsnNode(isStatic ? Opcodes.INVOKESTATIC : Opcodes.INVOKESPECIAL,
                WIDGET, original, descriptor, false));
        bridge.instructions.add(new InsnNode(Type.getReturnType(descriptor).getOpcode(Opcodes.IRETURN)));
    }

    private static MethodNode bridge(ClassNode node, String name, String descriptor) {
        require(node.methods.stream().noneMatch(method -> method.name.equals(name)), "duplicate bridge " + name);
        MethodNode method = new MethodNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_SYNTHETIC, name, descriptor, null, null);
        // Mixin's final writer recomputes frames and maxima for these generated methods.
        method.maxStack = 16;
        method.maxLocals = 32;
        node.methods.add(method);
        return method;
    }

    private static MethodNode method(ClassNode node, String name, String descriptor) {
        var matches = node.methods.stream().filter(method -> method.name.equals(name) && method.desc.equals(descriptor)).toList();
        require(matches.size() == 1, "expected one " + name + descriptor + ", found " + matches.size());
        return matches.getFirst();
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException("GTLCore staged preview: " + message);
    }
}
