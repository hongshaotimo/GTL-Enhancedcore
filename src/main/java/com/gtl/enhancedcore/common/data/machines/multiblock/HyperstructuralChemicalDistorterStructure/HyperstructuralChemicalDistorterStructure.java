package com.gtl.enhancedcore.common.data.machines.multiblock.HyperstructuralChemicalDistorterStructure;

import com.gregtechceu.gtceu.api.pattern.FactoryBlockPattern;
import com.gtl.enhancedcore.common.structure.StructurePatterns;

public final class HyperstructuralChemicalDistorterStructure {
    private HyperstructuralChemicalDistorterStructure() {}
    public static FactoryBlockPattern create() {
        return StructurePatterns.start("/data/gtl_enhancedcore/structures/hyperstructuralchemicaldistorterstructure.pattern.gz");
    }
}
