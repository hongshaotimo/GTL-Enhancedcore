package com.gtl.enhancedcore.common.data.machines.multiblock.LargeFurnaceStructure;

import com.gtl.enhancedcore.common.structure.StructurePatterns;
import com.gregtechceu.gtceu.api.pattern.FactoryBlockPattern;

public class LargeFurnaceStructure {
    private LargeFurnaceStructure() {
    }

    public static FactoryBlockPattern create() {
        return StructurePatterns.start("/data/gtl_enhancedcore/structures/large_furnace.pattern.gz");
    }
}
