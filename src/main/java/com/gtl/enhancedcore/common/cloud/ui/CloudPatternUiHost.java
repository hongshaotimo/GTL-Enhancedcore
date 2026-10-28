package com.gtl.enhancedcore.common.cloud.ui;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import com.lowdragmc.lowdraglib.side.item.IItemTransfer;
import com.lowdragmc.lowdraglib.side.fluid.IFluidTransfer;

/** Server-authoritative menu bridge. Returned inventories must recheck this viewer on every mutation. */
public interface CloudPatternUiHost {
    /** Read-only opening packet, before LDLib installs the new server menu. Must still verify access and distance. */
    default CompoundTag cloudUiInitialSnapshot(ServerPlayer player) {
        return cloudUiSnapshot(player, "", "", 0, "", 0, CloudPatternSearch.EMPTY);
    }
    CompoundTag cloudUiSnapshot(ServerPlayer player, String search, String type, int page, String selected);
    default CompoundTag cloudUiSnapshot(ServerPlayer player, String search, String type, int page, String selected, int recoveryPage) {
        return cloudUiSnapshot(player, search, type, page, selected);
    }
    default CompoundTag cloudUiSnapshot(ServerPlayer player, String search, String type, int page, String selected, int recoveryPage, CloudPatternSearch aliases) {
        return cloudUiSnapshot(player, search, type, page, selected, recoveryPage);
    }
    void cloudUiAction(ServerPlayer player, String action, CompoundTag payload);
    /** Small description-synced visual state; must never resolve server inventory from a render call. */
    default boolean cloudUiConnected() { return false; }
    /** The actual AE node state, distinct from library/lease readiness; safe on both sides. */
    default boolean meNetworkConnected() { return false; }
    /** Small event signal, avoiding a full catalog query every tick. */
    default long cloudUiChangeToken() { return 0; }
    default IItemTransfer cloudItemCatalysts(Player player) { return null; }
    default IFluidTransfer cloudFluidCatalysts(Player player) { return null; }
    default IItemTransfer cloudPatternItemCatalysts(Player player, String entryId) { return null; }
    default IFluidTransfer cloudPatternFluidCatalysts(Player player, String entryId) { return null; }
    default IItemTransfer cloudImportInventory(Player player) { return null; }
}
