package com.gtl.enhancedcore.network;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;

/** Validate senders before resolving block entities; packets must not load remote chunks. */
final class MachinePacketAccess {
    private MachinePacketAccess() {}

    static boolean canUse(ServerPlayer player, BlockPos pos) {
        return player != null && !player.isSpectator() && player.isAlive()
                && player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64.0
                && player.serverLevel().hasChunkAt(pos)
                && player.serverLevel().mayInteract(player, pos)
                && player.containerMenu instanceof com.lowdragmc.lowdraglib.gui.modular.ModularUIContainer menu
                && menu.getModularUI().holder instanceof com.gregtechceu.gtceu.api.machine.MetaMachine machine
                && machine.getLevel() == player.serverLevel() && machine.getPos().equals(pos);
    }
}
