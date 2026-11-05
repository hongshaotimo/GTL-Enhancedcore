package com.gtl.enhancedcore.common.recipe;

/** Fixed parallel declarations supplied by modifiers rather than a machine getter. */
public final class ModifierParallelCapacity {
    private ModifierParallelCapacity() {}
    /** -1 leaves native getter/hatch capacity unchanged, including ADD's 50000 forge getter. */
    public static int limit(String namespace, String path) {
        if (!"gtceu".equals(namespace)) return -1;
        if ("assembly_line".equals(path)) return MachineRecipeModifiers.ASSEMBLY_LINE_PARALLEL;
        int fusion = FusionParallelPolicy.limit(namespace, path);
        return fusion > 0 ? fusion : -1;
    }
}
