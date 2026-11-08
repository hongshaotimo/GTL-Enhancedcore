package com.gtl.enhancedcore.common.integration;

import java.util.ArrayList;
import java.util.List;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;

/** Narrows only AH fix9's Core laser-conversion tier list; highest-tier ADD routing is untouched. */
public final class AdvancedHatchExGtlRecipeCompatibility {
    public static final String OWNER = "com/gtl/enhancedcore/common/integration/AdvancedHatchExGtlRecipeCompatibility";
    public static final String MACHINE = "com/gregtechceu/gtceu/api/machine/MachineDefinition";
    public static final String ARRAY = "[L" + MACHINE + ";";
    public static final String PAIR_DESCRIPTOR = "(Ljava/util/function/Consumer;II" + ARRAY.repeat(4) + "II)V";
    public static final String RECIPE_DESCRIPTOR = "(Ljava/util/function/Consumer;IILjava/lang/String;L" + MACHINE + ";L" + MACHINE + ";I)V";
    private static final String AH = "com/xingmot/gtmadvancedhatch/common/data/AHMachines";
    private static final String CORE = "org/gtlcore/gtlcore/common/data/GTLMachines";
    private static final String EX = "com/xingmot/gtmadvancedhatch/integration/gtlcore/ExGTLMachines";
    private static final List<String> EXPECTED_FIELDS = List.of(
            AH + "/NET_HIGH_TIERS",
            AH + "/NET_LASER_INPUT_HATCH_262144A", AH + "/NET_LASER_OUTPUT_HATCH_262144A",
            CORE + "/WIRELESS_ENERGY_INPUT_HATCH_262144A", CORE + "/WIRELESS_ENERGY_OUTPUT_HATCH_262144A",
            AH + "/NET_LASER_INPUT_HATCH_1048576A", AH + "/NET_LASER_OUTPUT_HATCH_1048576A",
            CORE + "/WIRELESS_ENERGY_INPUT_HATCH_1048576A", CORE + "/WIRELESS_ENERGY_OUTPUT_HATCH_1048576A",
            AH + "/NET_LASER_INPUT_HATCH_4194304A", AH + "/NET_LASER_OUTPUT_HATCH_4194304A",
            CORE + "/WIRELESS_ENERGY_INPUT_HATCH_4194304A", CORE + "/WIRELESS_ENERGY_OUTPUT_HATCH_4194304A",
            AH + "/NET_HIGHEST_TIERS",
            AH + "/NET_LASER_INPUT_HATCH_16777216A", AH + "/NET_LASER_OUTPUT_HATCH_16777216A",
            EX + "/WIRELESS_ENERGY_INPUT_HATCH_16777216A", EX + "/WIRELESS_ENERGY_OUTPUT_HATCH_16777216A",
            AH + "/NET_LASER_INPUT_HATCH_67108864A", AH + "/NET_LASER_OUTPUT_HATCH_67108864A",
            EX + "/WIRELESS_ENERGY_INPUT_HATCH_67108864A", EX + "/WIRELESS_ENERGY_OUTPUT_HATCH_67108863A",
            AH + "/NET_LASER_INPUT_HATCH_2147483647A", AH + "/NET_LASER_OUTPUT_HATCH_2147483647A",
            AH + "/NET_LASER_INPUT_HATCH_67108864A", AH + "/NET_LASER_OUTPUT_HATCH_67108864A");

    private AdvancedHatchExGtlRecipeCompatibility() {}

    /** Core's current registration filter removes EV only; keep caller-owned arrays immutable. */
    public static int[] withoutEv(int[] tiers) {
        int removed = 0;
        for (int tier : tiers) if (tier == 4) removed++;
        if (removed == 0) return tiers;
        int[] result = new int[tiers.length - removed];
        int index = 0;
        for (int tier : tiers) if (tier != 4) result[index++] = tier;
        return result;
    }

    public static int transform(ClassNode node) {
        if (!node.name.equals(AdvancedHatchLaserRecipeCompatibility.EX_TARGET)) throw new IllegalStateException("Unexpected AH Core recipe target: " + node.name);
        var init = node.methods.stream().filter(method -> method.name.equals("initRecipes") && method.desc.equals("(Ljava/util/function/Consumer;)V")).findFirst().orElseThrow();
        var fields = new ArrayList<String>();
        FieldInsnNode tierSource = null;
        int pairCalls = 0;
        for (var instruction : init.instructions) {
            if (instruction instanceof FieldInsnNode field) {
                if (field.getOpcode() != Opcodes.GETSTATIC || !(field.desc.equals(ARRAY) || field.desc.equals("[I")))
                    throw new IllegalStateException("Unexpected AH Core recipe field access");
                fields.add(field.owner + "/" + field.name);
                if (field.owner.equals(AH) && field.name.equals("NET_HIGH_TIERS")) tierSource = field;
            }
            if (instruction instanceof MethodInsnNode call) {
                if (call.owner.equals(OWNER)) throw new IllegalStateException("AH Core laser recipe filter is already present");
                if (call.owner.equals(node.name)) {
                    if (!call.name.equals("registerLaserRecipes") || !call.desc.equals(PAIR_DESCRIPTOR) || call.getOpcode() != Opcodes.INVOKESTATIC)
                        throw new IllegalStateException("Unexpected AH Core laser conversion helper");
                    pairCalls++;
                }
            }
        }
        if (!fields.equals(EXPECTED_FIELDS) || tierSource == null || pairCalls != 6)
            throw new IllegalStateException("AH Core laser conversion inventory changed: " + fields + "/" + pairCalls);
        var pair = node.methods.stream().filter(method -> method.name.equals("registerLaserRecipes") && method.desc.equals(PAIR_DESCRIPTOR)).findFirst().orElseThrow();
        int singleCalls = 0;
        for (var instruction : pair.instructions) if (instruction instanceof MethodInsnNode call) {
            if (!call.owner.equals(node.name) || !call.name.equals("registerLaserRecipe") || !call.desc.equals(RECIPE_DESCRIPTOR) || call.getOpcode() != Opcodes.INVOKESTATIC)
                throw new IllegalStateException("AH Core laser direction helper changed");
            singleCalls++;
        }
        if (singleCalls != 2 || node.methods.stream().noneMatch(method -> method.name.equals("registerLaserRecipe") && method.desc.equals(RECIPE_DESCRIPTOR)))
            throw new IllegalStateException("AH Core laser recipe direction inventory changed");
        init.instructions.insert(tierSource, new MethodInsnNode(Opcodes.INVOKESTATIC, OWNER, "withoutEv", "([I)[I", false));
        return 6;
    }
}
