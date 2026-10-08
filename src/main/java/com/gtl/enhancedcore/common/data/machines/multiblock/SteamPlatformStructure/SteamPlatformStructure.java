package com.gtl.enhancedcore.common.data.machines.multiblock.SteamPlatformStructure;

import com.gtl.enhancedcore.common.structure.StructurePatterns;
import com.gregtechceu.gtceu.api.pattern.FactoryBlockPattern;

public final class SteamPlatformStructure {
    private SteamPlatformStructure() {}

    public static FactoryBlockPattern create() {
        return StructurePatterns.start("/data/gtl_enhancedcore/structures/steam_platform.pattern.gz")
                ;
    }
}
