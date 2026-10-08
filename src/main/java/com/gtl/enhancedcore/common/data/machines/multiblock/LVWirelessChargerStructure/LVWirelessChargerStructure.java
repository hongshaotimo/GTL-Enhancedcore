package com.gtl.enhancedcore.common.data.machines.multiblock.LVWirelessChargerStructure;

import com.gtl.enhancedcore.common.structure.StructurePatterns;
import com.gregtechceu.gtceu.api.pattern.FactoryBlockPattern;

public final class LVWirelessChargerStructure {
    private LVWirelessChargerStructure() {}

    public static FactoryBlockPattern create() {
        return StructurePatterns.start("/data/gtl_enhancedcore/structures/l_v_wireless_charger.pattern.gz")
                ;
    }
}
