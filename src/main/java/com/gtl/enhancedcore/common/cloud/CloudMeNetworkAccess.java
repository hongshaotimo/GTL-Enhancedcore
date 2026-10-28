package com.gtl.enhancedcore.common.cloud;

import com.gregtechceu.gtceu.api.machine.MetaMachine;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;

/** Adds cloud authorization while leaving all native distance, frequency and target checks intact. */
public final class CloudMeNetworkAccess {
    private CloudMeNetworkAccess() {}
    public static boolean denied(ServerPlayer player, BlockPos pos) {
        var level = player.serverLevel();
        if (level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4) == null) return false;
        var machine = MetaMachine.getMachine(level, pos);
        return machine instanceof CloudPatternLibraryMachine library && !library.mayConfigureMeNetwork(player)
                || machine instanceof CloudPatternBufferMachine buffer && !buffer.mayConfigureMeNetwork(player);
    }
}
