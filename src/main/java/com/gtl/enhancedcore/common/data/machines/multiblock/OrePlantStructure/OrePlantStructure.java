package com.gtl.enhancedcore.common.data.machines.multiblock.OrePlantStructure;

import com.gtl.enhancedcore.common.structure.StructurePatterns;
import com.gregtechceu.gtceu.api.pattern.FactoryBlockPattern;

public final class OrePlantStructure {
    private OrePlantStructure() {}

    public static FactoryBlockPattern create() {
        return StructurePatterns.start("/data/gtl_enhancedcore/structures/ore_plant.pattern.gz")
                ;
    }
}
