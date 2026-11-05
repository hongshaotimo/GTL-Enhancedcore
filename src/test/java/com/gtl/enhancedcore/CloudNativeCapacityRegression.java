package com.gtl.enhancedcore;

import com.gtl.enhancedcore.common.recipe.iv.IvNativeCapacity;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

/** Capacity arithmetic cannot overflow or narrow native long capabilities to int. */
public final class CloudNativeCapacityRegression {
    private static int assertions;
    public static int run() {
        assertions = 0;
        check(IvNativeCapacity.budget(1, 1) == 1, "ordinary single native recipe keeps one operation");
        check(IvNativeCapacity.budget(64, 128) == 8192, "finite native parallel and thread capacity multiplies exactly");
        check(IvNativeCapacity.budget(1L << 34, 128) == 1L << 41, "native parallel capacity stays wider than int");
        check(IvNativeCapacity.budget(Long.MAX_VALUE, 1) == Long.MAX_VALUE, "infinite parallel with one recipe remains long max");
        check(IvNativeCapacity.budget(Long.MAX_VALUE, Integer.MAX_VALUE) == Long.MAX_VALUE, "independent infinite parallel and threads saturate without overflow");
        check(IvNativeCapacity.budget((long)Integer.MAX_VALUE * Integer.MAX_VALUE, 2) == 9223372028264841218L,
                "large finite capacity preserves its exact long value before saturation");
        check(IvNativeCapacity.budget(Long.MAX_VALUE / 2 + 1, 2) == Long.MAX_VALUE, "overflowing native budget saturates positively");
        check(com.gtl.enhancedcore.common.recipe.ModifierParallelCapacity.limit("gtceu", "assembly_line") == 64,
                "assembly cloud admission sees the same fixed64 declaration as the real native modifier");
        for (String path : new String[]{"luv_fusion_reactor", "zpm_fusion_reactor", "uv_fusion_reactor", "uhv_fusion_reactor", "uev_fusion_reactor"})
            check(com.gtl.enhancedcore.common.recipe.ModifierParallelCapacity.limit("gtceu", path)
                            == com.gtl.enhancedcore.common.recipe.FusionParallelPolicy.limit("gtceu", path),
                    "each enhanced MK tier shares its existing fusion modifier capacity");
        for (String path : new String[]{"compressed_fusion_reactor", "circuit_assembly_line", "advanced_assembly_line", "star_ultimate_material_forge_factory"})
            check(com.gtl.enhancedcore.common.recipe.ModifierParallelCapacity.limit("gtceu", path) == -1,
                    "other native getter/hatch and ADD forge50000 paths stay unchanged");
        check(com.gtl.enhancedcore.common.recipe.ModifierParallelCapacity.limit("other", "assembly_line") == -1,
                "the exact gtceu registry namespace is required");
        modifierSwitchGuard();
        return assertions;
    }
    private static void modifierSwitchGuard() {
        try (var input = IvNativeCapacity.class.getResourceAsStream("IvNativeCapacity.class")) {
            var node = new ClassNode(); new ClassReader(input).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            var facade = node.methods.stream().filter(method -> method.name.equals("modifierParallel")).findFirst().orElseThrow();
            var instructions = new java.util.ArrayList<AbstractInsnNode>();
            for (var instruction : facade.instructions) if (instruction.getOpcode() >= 0) instructions.add(instruction);
            check(instructions.get(0) instanceof MethodInsnNode current
                            && current.owner.equals("com/gtl/enhancedcore/common/config/MachineOverrideConfig")
                            && current.name.equals("current")
                            && instructions.get(1) instanceof MethodInsnNode enabled
                            && enabled.owner.equals("com/gtl/enhancedcore/common/config/MachineOverrideConfig$Settings")
                            && enabled.name.equals("enhanceOriginalMachines")
                            && instructions.get(2).getOpcode() == Opcodes.IFNE
                            && instructions.get(3).getOpcode() == Opcodes.ICONST_M1
                            && instructions.get(4).getOpcode() == Opcodes.IRETURN,
                    "disabled startup enhancements return getter fallback before querying any machine definition");
        } catch (java.io.IOException exception) { throw new AssertionError("modifier capacity switch ABI", exception); }
    }
    public static void main(String[] args) { System.out.println("Cloud native capacity checks: " + run()); }
    private static void check(boolean valid, String label) {
        assertions++;
        if (!valid) throw new AssertionError(label);
    }
}
