package com.gtl.enhancedcore.common.cloud.transfer;

import com.gtl.enhancedcore.common.cloud.CloudLibraryService;
import com.gtl.enhancedcore.common.cloud.CloudPatternBufferMachine;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.gtlcore.gtlcore.common.machine.multiblock.part.ae.MEPatternBufferPartMachine;

/** The stick is an address only; every use rechecks the current loaded source and actor. */
public final class CloudImportSourceAddress {
    public static final String KEY = "gtl_cloud_import_source";
    private CloudImportSourceAddress() {}
    public static boolean authorized(ServerPlayer player, MEPatternBufferPartMachine source) {
        if (source instanceof CloudPatternBufferMachine || player.serverLevel() != source.getLevel()
                || player.distanceToSqr(source.getPos().getX()+.5,source.getPos().getY()+.5,source.getPos().getZ()+.5) > 64
                || !player.serverLevel().mayInteract(player, source.getPos())
                || !CloudLibraryService.claimAllows(player, player.serverLevel(), source.getPos())) return false;
        var node = source.getMainNode().getNode();
        var owner = node == null ? null : node.getOwningPlayerProfileId();
        return owner != null && CloudLibraryService.sameTeam(owner, player.getUUID());
    }
    public static boolean capture(ServerPlayer player, MEPatternBufferPartMachine source, ItemStack stick) {
        if (!(source instanceof CloudImportSourceAccess access) || !authorized(player, source)
                || player.distanceToSqr(source.getPos().getX()+.5,source.getPos().getY()+.5,source.getPos().getZ()+.5) > 64) return false;
        CompoundTag address = new CompoundTag(); address.putString("dimension", player.serverLevel().dimension().location().toString());
        address.putLong("position", source.getPos().asLong()); address.putUUID("identity", access.cloud$sourceIdentity());
        stick.getOrCreateTag().put(KEY, address); return true;
    }
}
