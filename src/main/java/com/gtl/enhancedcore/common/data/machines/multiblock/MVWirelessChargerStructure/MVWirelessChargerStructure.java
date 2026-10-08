package com.gtl.enhancedcore.common.data.machines.multiblock.MVWirelessChargerStructure;

import com.gtl.enhancedcore.common.structure.StructurePatterns;
import com.gregtechceu.gtceu.api.pattern.FactoryBlockPattern;

public final class MVWirelessChargerStructure {
    private MVWirelessChargerStructure() {}

    public static FactoryBlockPattern create() {
        return StructurePatterns.start("/data/gtl_enhancedcore/structures/m_v_wireless_charger.pattern.gz")
                ;
    }
}
