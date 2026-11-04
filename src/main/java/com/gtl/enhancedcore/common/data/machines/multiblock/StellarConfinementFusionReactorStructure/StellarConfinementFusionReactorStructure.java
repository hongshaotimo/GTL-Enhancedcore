package com.gtl.enhancedcore.common.data.machines.multiblock.StellarConfinementFusionReactorStructure;

import com.gregtechceu.gtceu.api.pattern.FactoryBlockPattern;
import com.gtl.enhancedcore.common.structure.StructurePatterns;
import com.gtl.enhancedcore.common.structure.ReviewedStructurePalette;

public final class StellarConfinementFusionReactorStructure {
    private StellarConfinementFusionReactorStructure() {}
    public static FactoryBlockPattern create() {
        return ReviewedStructurePalette.bind(StructurePatterns.start(
                "/data/gtl_enhancedcore/structures/stellarconfinementfusionreactorstructure.pattern.gz"),
                "stellar_confinement_fusion_reactor");
    }
}
