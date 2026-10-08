package com.gtl.enhancedcore.network;

import com.gtl.enhancedcore.common.machine.ClaimReplacementTerminalMachine;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 领地置换终端 GUI 按钮包：0=检测，1=替换。附带机器所在 BlockPos 定位机器。
 */
public class C2SClaimReplacementPacket {

    public static final int MODE_DETECT = 0;
    public static final int MODE_REPLACE = 1;
    public static final int MODE_TOGGLE_AE = 2;
    public static final int MODE_STOP = 3;

    private final int mode;
    private final BlockPos machinePos;

    public C2SClaimReplacementPacket(int mode, BlockPos machinePos) {
        this.mode = mode;
        this.machinePos = machinePos;
    }

    public static void encode(C2SClaimReplacementPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.mode);
        buf.writeBlockPos(msg.machinePos);
    }

    public static C2SClaimReplacementPacket decode(FriendlyByteBuf buf) {
        return new C2SClaimReplacementPacket(buf.readVarInt(), buf.readBlockPos());
    }

    public static void handle(C2SClaimReplacementPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (!MachinePacketAccess.canUse(player, msg.machinePos)
                    || msg.mode < MODE_DETECT || msg.mode > MODE_STOP) {
                return;
            }
            if (!(player.level() instanceof ServerLevel serverLevel)) {
                return;
            }
            BlockEntity be = serverLevel.getBlockEntity(msg.machinePos);
            if (!(be instanceof com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity mmbe)) {
                return;
            }
            if (!(mmbe.getMetaMachine() instanceof ClaimReplacementTerminalMachine machine)) {
                return;
            }
            if (!machine.canOperate(player)) return;
            if (msg.mode == MODE_DETECT) {
                machine.detectClicked(player);
            } else if (msg.mode == MODE_REPLACE) {
                machine.replaceClicked(player);
            } else if (msg.mode == MODE_TOGGLE_AE) {
                machine.toggleAeMode(player);
            } else if (msg.mode == MODE_STOP) {
                machine.stopClicked(player);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
