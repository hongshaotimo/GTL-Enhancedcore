package com.gtl.enhancedcore.common.integration;

import java.util.ArrayList;
import java.util.List;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.IincInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LookupSwitchInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TableSwitchInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

/** GTLCore removes EV wireless laser hatches; AH's matching conversions must start at IV. */
public final class AdvancedHatchLaserRecipeCompatibility {
    public static final String TARGET = "com/xingmot/gtmadvancedhatch/common/data/recipe/AHRecipes";
    public static final String EX_TARGET = "com/xingmot/gtmadvancedhatch/integration/gtlcore/ExGTLRecipes";
    public static final String MARKER = "com.gtl.enhancedcore.mixin.gtmadvancedhatch.AdvancedHatchLaserRecipeCompatibilityMixin";
    private static final String MACHINES = "com/xingmot/gtmadvancedhatch/common/data/AHMachines";
    private static final String WIRELESS = "com/hepdd/gtmthings/data/WirelessMachines";
    private static final String BUILDER = "com/gregtechceu/gtceu/data/recipe/builder/GTRecipeBuilder";
    private static final String ARRAY = "[Lcom/gregtechceu/gtceu/api/machine/MachineDefinition;";
    private static final List<String> OUTPUTS = List.of(
            "NET_ENERGY_INPUT_HATCH_256A", "NET_ENERGY_OUTPUT_HATCH_256A",
            "NET_ENERGY_INPUT_HATCH_1024A", "NET_ENERGY_OUTPUT_HATCH_1024A",
            "NET_ENERGY_INPUT_HATCH_4096A", "NET_ENERGY_OUTPUT_HATCH_4096A",
            "NET_LASER_INPUT_HATCH_16384A", "NET_LASER_OUTPUT_HATCH_16384A");
    private static final List<String> FIX9_OUTPUTS = List.of(
            "NET_ENERGY_INPUT_HATCH_256A", "NET_ENERGY_OUTPUT_HATCH_256A",
            "NET_LASER_INPUT_HATCH_65536A", "NET_LASER_OUTPUT_HATCH_65536A",
            "NET_ENERGY_INPUT_HATCH_1024A", "NET_ENERGY_OUTPUT_HATCH_1024A",
            "NET_ENERGY_INPUT_HATCH_4096A", "NET_ENERGY_OUTPUT_HATCH_4096A",
            "NET_LASER_INPUT_HATCH_16384A", "NET_LASER_OUTPUT_HATCH_16384A");

    private AdvancedHatchLaserRecipeCompatibility() {}

    /** Validates the complete linear block before adding one branch to its existing loop continuation. */
    public static int transform(ClassNode node) {
        if (EX_TARGET.equals(node.name)) return AdvancedHatchExGtlRecipeCompatibility.transform(node);
        if (!TARGET.equals(node.name)) throw new IllegalStateException("Unexpected AH recipe target: " + node.name);
        MethodNode method = node.methods.stream().filter(value -> value.name.equals("initRecipes")
                && value.desc.equals("(Ljava/util/function/Consumer;)V")).findFirst().orElseThrow();
        var sources = new ArrayList<FieldInsnNode>();
        for (var instruction : method.instructions) {
            if (instruction instanceof FieldInsnNode field && field.owner.equals(WIRELESS)
                    && field.name.matches("WIRELESS_ENERGY_(INPUT|OUTPUT)_HATCH_(256|1024|4096|16384|65536)A")) {
                if (field.getOpcode() != Opcodes.GETSTATIC || !field.desc.equals(ARRAY))
                    throw new IllegalStateException("Unexpected wireless laser source descriptor");
                sources.add(field);
            }
        }
        List<String> expected = sources.size() == FIX9_OUTPUTS.size() ? FIX9_OUTPUTS : OUTPUTS;
        if (sources.size() != expected.size()) throw new IllegalStateException("AH laser conversion source inventory changed: " + sources.size());
        var first = sources.getFirst();
        if (!(next(first) instanceof VarInsnNode tier) || tier.getOpcode() != Opcodes.ILOAD)
            throw new IllegalStateException("AH laser conversion no longer indexes the loop tier");
        AbstractInsnNode start = first;
        while (start != null && !assembler(start)) start = start.getPrevious();
        if (start == null) throw new IllegalStateException("Missing AH laser recipe builder start");
        if (previous(start) instanceof JumpInsnNode inserted && inserted.getOpcode() == Opcodes.IF_ICMPLT
                && previous(inserted).getOpcode() == Opcodes.ICONST_5)
            throw new IllegalStateException("AH laser recipe guard is already present");
        AbstractInsnNode end = sources.getLast();
        while (end != null && !save(end)) end = end.getNext();
        if (end == null || !(next(end) instanceof IincInsnNode))
            throw new IllegalStateException("AH laser conversions no longer end at the loop continuation");
        LabelNode continuation = null;
        for (var instruction = end.getNext(); instruction != null && instruction.getOpcode() < 0; instruction = instruction.getNext())
            if (instruction instanceof LabelNode label) { continuation = label; break; }
        if (continuation == null) throw new IllegalStateException("Missing existing AH loop continuation frame");
        int saves = 0, recipes = 0, sourceIndex = 0;
        var outputs = new ArrayList<String>();
        for (var instruction = start; instruction != end.getNext(); instruction = instruction.getNext()) {
            if (instruction instanceof JumpInsnNode || instruction instanceof TableSwitchInsnNode || instruction instanceof LookupSwitchInsnNode
                    || instruction instanceof VarInsnNode variable && variable.getOpcode() != Opcodes.ILOAD && variable.getOpcode() != Opcodes.ALOAD
                    || instruction instanceof IincInsnNode)
                throw new IllegalStateException("AH laser recipe block is no longer linear");
            if (assembler(instruction)) recipes++;
            if (save(instruction)) saves++;
            if (instruction instanceof FieldInsnNode field && field.owner.equals(MACHINES) && field.getOpcode() == Opcodes.GETSTATIC)
                outputs.add(field.name);
            if (instruction instanceof FieldInsnNode field && field.owner.equals(WIRELESS)) {
                if (sourceIndex >= sources.size() || sources.get(sourceIndex++) != field
                        || !(next(field) instanceof VarInsnNode index) || index.getOpcode() != Opcodes.ILOAD || index.var != tier.var)
                    throw new IllegalStateException("AH laser conversions use different source tiers");
            }
        }
        if (!outputs.equals(expected) || recipes != expected.size() || saves != expected.size() || sourceIndex != expected.size())
            throw new IllegalStateException("AH laser conversion block changed: " + outputs + "/" + recipes + "/" + saves);
        boolean originalEvGuard = false;
        for (var instruction = method.instructions.getFirst(); instruction != start; instruction = instruction.getNext())
            if (instruction instanceof JumpInsnNode jump && jump.getOpcode() == Opcodes.IF_ICMPLT && jump.label == continuation
                    && previous(jump).getOpcode() == Opcodes.ICONST_4
                    && previous(previous(jump)) instanceof VarInsnNode index && index.getOpcode() == Opcodes.ILOAD && index.var == tier.var)
                originalEvGuard = true;
        if (!originalEvGuard) throw new IllegalStateException("Missing original AH EV guard for ordinary 64A recipes");
        InsnList guard = new InsnList();
        guard.add(new VarInsnNode(Opcodes.ILOAD, tier.var));
        guard.add(new InsnNode(Opcodes.ICONST_5));
        guard.add(new JumpInsnNode(Opcodes.IF_ICMPLT, continuation));
        method.instructions.insertBefore(start, guard);
        return expected.size();
    }

    private static AbstractInsnNode next(AbstractInsnNode node) {
        do { node = node.getNext(); } while (node != null && node.getOpcode() < 0);
        return node;
    }
    private static AbstractInsnNode previous(AbstractInsnNode node) {
        do { node = node.getPrevious(); } while (node != null && node.getOpcode() < 0);
        return node;
    }
    private static boolean assembler(AbstractInsnNode node) {
        return node instanceof FieldInsnNode field && field.getOpcode() == Opcodes.GETSTATIC
                && field.owner.equals("com/gregtechceu/gtceu/common/data/GTRecipeTypes") && field.name.equals("ASSEMBLER_RECIPES");
    }
    private static boolean save(AbstractInsnNode node) {
        return node instanceof MethodInsnNode call && call.getOpcode() == Opcodes.INVOKEVIRTUAL
                && call.owner.equals(BUILDER) && call.name.equals("save") && call.desc.equals("(Ljava/util/function/Consumer;)V");
    }
}
