package com.gtl.enhancedcore.common.world.plotinfo;

import com.gtl.enhancedcore.GTLEnhancedcore;
import com.gtl.enhancedcore.common.world.plot.PlotChunkGenerator;
import com.gtl.enhancedcore.common.world.plot.PlotSettings;
import java.util.Optional;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

/** A bounded, server-to-client-only snapshot; it offers no runtime terrain mutation action. */
public final class PlotWorldInfoNetwork {
    private static final String PROTOCOL = "3";
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(GTLEnhancedcore.MOD_ID, "plot_info"), () -> PROTOCOL,
            PROTOCOL::equals, PROTOCOL::equals);
    private static boolean initialized;
    private PlotWorldInfoNetwork() {}

    public static synchronized void register() {
        if (initialized) return;
        CHANNEL.registerMessage(0, Snapshot.class, Snapshot::encode, Snapshot::decode, Snapshot::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        initialized = true;
    }

    /** Called on the server thread when the player uses the controller item. */
    public static void send(ServerPlayer player) {
        var generator = player.server.overworld().getChunkSource().getGenerator();
        PlotSettings settings = generator instanceof PlotChunkGenerator plot ? plot.settings() : null;
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new Snapshot(settings));
    }

    public record Snapshot(PlotSettings settings) {
        static void encode(Snapshot packet, FriendlyByteBuf buffer) {
            PlotSettings s = packet.settings;
            buffer.writeBoolean(s != null);
            if (s == null) return;
            buffer.writeVarInt(s.plotChunks());
            buffer.writeUtf(s.groundBlock(), 192); buffer.writeUtf(s.roadBlock(), 192);
            buffer.writeUtf(s.borderBlock(), 192); buffer.writeUtf(s.markerBlock(), 192);
            buffer.writeEnum(s.roadStyle()); buffer.writeVarInt(s.roadWidth());
            buffer.writeEnum(s.markerMode()); buffer.writeVarInt(s.markerSize());
            buffer.writeInt(s.bedrockY()); buffer.writeInt(s.surfaceY());
            buffer.writeUtf(s.lineBlock(), 192); buffer.writeEnum(s.lineStyle());
            buffer.writeVarInt(s.dashLength()); buffer.writeVarInt(s.dashGap()); buffer.writeVarInt(s.borderWidth());
            buffer.writeUtf(s.plotEdgeBlock(), 192); buffer.writeVarInt(s.plotEdgeWidth());
        }
        static Snapshot decode(FriendlyByteBuf b) {
            if (!b.readBoolean()) return new Snapshot(null);
            return new Snapshot(new PlotSettings(b.readVarInt(), b.readUtf(192), b.readUtf(192),
                    b.readUtf(192), b.readUtf(192), b.readEnum(PlotSettings.RoadStyle.class), b.readVarInt(),
                    b.readEnum(PlotSettings.MarkerMode.class), b.readVarInt(), b.readInt(), b.readInt(), b.readUtf(192),
                    b.readEnum(PlotSettings.LineStyle.class), b.readVarInt(), b.readVarInt(), b.readVarInt(),
                    b.readUtf(192), b.readVarInt()));
        }
        static void handle(Snapshot packet, Supplier<NetworkEvent.Context> context) {
            NetworkEvent.Context ctx = context.get();
            ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                    () -> () -> com.gtl.enhancedcore.client.plotinfo.PlotWorldInfoScreen.open(packet.settings)));
            ctx.setPacketHandled(true);
        }
    }
}
