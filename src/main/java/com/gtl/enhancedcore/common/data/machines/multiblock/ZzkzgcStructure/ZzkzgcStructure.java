package com.gtl.enhancedcore.common.data.machines.multiblock.ZzkzgcStructure;

import com.gtl.enhancedcore.common.structure.StructurePatterns;
import com.gregtechceu.gtceu.api.pattern.FactoryBlockPattern;

public final class ZzkzgcStructure {
    private ZzkzgcStructure() {}

    public static FactoryBlockPattern create() {
        return StructurePatterns.start("/data/gtl_enhancedcore/structures/zzkzgc.pattern.gz")
                ;
    }
}
