package com.gtl.enhancedcore.common.cloud.transfer;

import net.minecraft.nbt.CompoundTag;

public interface CloudTerminalSource {
    CompoundTag cloud$sourceState();
    void cloud$markReconciled();
}
