package com.gtl.enhancedcore.common.data.machines.multiblock.StellarConfinementFusionReactorStructure;

import com.gregtechceu.gtceu.api.pattern.FactoryBlockPattern;
import com.gtl.enhancedcore.common.structure.StructurePatterns;

public final class StellarConfinementFusionReactorStructure {
    private StellarConfinementFusionReactorStructure() {}
    public static FactoryBlockPattern create() {
        return StructurePatterns.start("/data/gtl_enhancedcore/structures/stellarconfinementfusionreactorstructure.pattern.gz");
    }
}
