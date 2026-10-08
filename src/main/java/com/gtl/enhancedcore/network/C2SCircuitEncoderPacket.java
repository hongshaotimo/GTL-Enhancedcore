package com.gtl.enhancedcore.network;

import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;
import com.gtl.enhancedcore.common.machine.hatch.CircuitEncoderHatchMachine;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 客户端切换电路编码仓的电路选择（编解码 writeVarInt/readVarInt + writeBlockPos/readBlockPos 配对）。
 */
public class C2SCircuitEncoderPacket {
    public static final int ACTION_TOGGLE = 0;
    public static final int ACTION_SET_ALL = 1;

    private final int action;
    private final int value;
    private final BlockPos pos;

    public C2SCircuitEncoderPacket(int action, int value, BlockPos pos) {
        this.action = action;
        this.value = value;
        this.pos = pos;
    }

    public static void encode(C2SCircuitEncoderPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.action);
        buf.writeVarInt(msg.value);
        buf.writeBlockPos(msg.pos);
    }

    public static C2SCircuitEncoderPacket decode(FriendlyByteBuf buf) {
        return new C2SCircuitEncoderPacket(buf.readVarInt(), buf.readVarInt(), buf.readBlockPos());
    }

    public static void handle(C2SCircuitEncoderPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (!MachinePacketAccess.canUse(player, msg.pos)
                    || (msg.action != ACTION_TOGGLE && msg.action != ACTION_SET_ALL)
                    || (msg.action == ACTION_TOGGLE && (msg.value < 0 || msg.value > CircuitEncoderHatchMachine.CIRCUIT_MAX))
                    || (msg.action == ACTION_SET_ALL && msg.value != 0 && msg.value != 1)) {
                return;
            }
            BlockEntity blockEntity = player.serverLevel().getBlockEntity(msg.pos);
            if (blockEntity instanceof MetaMachineBlockEntity machineBlockEntity
                    && machineBlockEntity.getMetaMachine() instanceof CircuitEncoderHatchMachine hatch) {
                switch (msg.action) {
                    case ACTION_TOGGLE -> hatch.toggleCircuit(msg.value);
                    case ACTION_SET_ALL -> hatch.setAllCircuits(msg.value != 0);
                    default -> {
                    }
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
