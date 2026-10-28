package com.gtl.enhancedcore.common.data.machines.multiblock.DragonFieldProliferationCoreStructure;

import com.gregtechceu.gtceu.api.pattern.FactoryBlockPattern;
import com.gtl.enhancedcore.common.structure.StructurePatterns;
import com.gtl.enhancedcore.common.structure.ReviewedStructurePalette;

public final class DragonFieldProliferationCoreStructure {
    private DragonFieldProliferationCoreStructure() {}
    public static FactoryBlockPattern create() {
        return ReviewedStructurePalette.bind(StructurePatterns.start("/data/gtl_enhancedcore/structures/dragonfieldproliferationcorestructure.pattern.gz"),
                "dragon_field_proliferation_core");
    }
}
