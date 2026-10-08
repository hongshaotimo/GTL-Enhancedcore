package com.gtl.enhancedcore.common.data.machines.multiblock.VoidConstrainedMiningFieldStructure;

import com.gregtechceu.gtceu.api.pattern.FactoryBlockPattern;
import com.gtl.enhancedcore.common.structure.StructurePatterns;

public final class VoidConstrainedMiningFieldStructure {
    private VoidConstrainedMiningFieldStructure() {}
    public static FactoryBlockPattern create() {
        return StructurePatterns.start("/data/gtl_enhancedcore/structures/voidconstrainedminingfieldstructure.pattern.gz");
    }
}
