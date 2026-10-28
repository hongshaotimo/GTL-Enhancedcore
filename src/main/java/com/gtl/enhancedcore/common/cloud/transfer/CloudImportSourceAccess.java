package com.gtl.enhancedcore.common.cloud.transfer;

import java.util.UUID;
import net.minecraft.nbt.CompoundTag;

/** Persisted in the same source BE NBT as the debited pattern inventory. */
public interface CloudImportSourceAccess {
    UUID cloud$sourceIdentity();
    boolean cloud$processed(UUID transaction);
    void cloud$markProcessed(UUID transaction);
    boolean cloud$importQuarantined();
    void cloud$quarantineImport(String reason);
    CompoundTag cloud$saveImportMarkers();
    void cloud$loadImportMarkers(CompoundTag tag);
}
