package com.gtl.enhancedcore.common.data.machines.multiblock.IvsijiantaoStructure;

import com.gtl.enhancedcore.common.structure.StructurePatterns;
import com.gregtechceu.gtceu.api.pattern.FactoryBlockPattern;

public final class IvsijiantaoStructure {
    private IvsijiantaoStructure() {}

    public static FactoryBlockPattern create() {
        return StructurePatterns.start("/data/gtl_enhancedcore/structures/ivsijiantao.pattern.gz")
                ;
    }
}
