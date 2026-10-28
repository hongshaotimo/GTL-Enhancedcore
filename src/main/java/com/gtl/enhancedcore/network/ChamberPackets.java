package com.gtl.enhancedcore.network;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.*;
import net.minecraftforge.network.simple.SimpleChannel;

/** Single bounded S2C snapshot; no client-provided world target or inventory data. */
public final class ChamberPackets {
    public static final int MAX_POSITIONS=8192;
    private static final SimpleChannel CHANNEL=NetworkRegistry.newSimpleChannel(
            new ResourceLocation("gtl_enhancedcore","chambers"),()->"1","1"::equals,"1"::equals);
    private static boolean registered;
    private ChamberPackets() {}
    public static synchronized void register() {
        if(registered)return; registered=true;
        CHANNEL.registerMessage(0,Snapshot.class,Snapshot::encode,Snapshot::decode,Snapshot::handle,Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }
    public static void send(ServerPlayer player,BlockPos origin,List<BlockPos> positions,boolean minimum) {
        CHANNEL.send(PacketDistributor.PLAYER.with(()->player),new Snapshot(player.level().dimension().location(),origin,List.copyOf(positions),minimum));
    }
    public record Snapshot(ResourceLocation dimension,BlockPos origin,List<BlockPos> positions,boolean minimum) {
        static void encode(Snapshot p,FriendlyByteBuf b) {
            b.writeResourceLocation(p.dimension);b.writeBlockPos(p.origin);b.writeBoolean(p.minimum);b.writeVarInt(p.positions.size());
            p.positions.forEach(b::writeBlockPos);
        }
        static Snapshot decode(FriendlyByteBuf b) {
            var dim=b.readResourceLocation();var origin=b.readBlockPos();boolean minimum=b.readBoolean();int n=b.readVarInt();
            if(n<0||n>MAX_POSITIONS)throw new IllegalArgumentException("Invalid chamber count");
            var positions=new java.util.ArrayList<BlockPos>(n);for(int i=0;i<n;i++)positions.add(b.readBlockPos());
            return new Snapshot(dim,origin,List.copyOf(positions),minimum);
        }
        static void handle(Snapshot p,Supplier<NetworkEvent.Context> context) {
            var ctx=context.get();ctx.enqueueWork(()->net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(
                    net.minecraftforge.api.distmarker.Dist.CLIENT,()->()->com.gtl.enhancedcore.client.structure.ChamberHighlightRenderer.accept(p)));
            ctx.setPacketHandled(true);
        }
    }
}
