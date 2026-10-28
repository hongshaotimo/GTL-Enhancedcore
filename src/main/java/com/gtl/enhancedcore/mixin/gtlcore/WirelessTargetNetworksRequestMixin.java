package com.gtl.enhancedcore.mixin.gtlcore;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;
import org.gtlcore.gtlcore.integration.ae2.wireless.WirelessAePackets;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.At;

/** Complete the wireless target page request when GTLCore rejects it, such as out of range. */
@Mixin(targets = "org.gtlcore.gtlcore.integration.ae2.wireless.WirelessAePackets$RequestTargetNetworksPacket", remap = false)
public abstract class WirelessTargetNetworksRequestMixin {
    @WrapOperation(method = "lambda$handle$1",
            at = @At(value = "INVOKE", target = "Lorg/gtlcore/gtlcore/integration/ae2/wireless/WirelessAePackets;isCloseEnough(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/core/BlockPos;)Z", remap = false),
            require = 1)
    private static boolean enhanced$finishRejectedRequest(ServerPlayer player, BlockPos pos,
            Operation<Boolean> original,
            @Local(argsOnly = true) WirelessAePackets.RequestTargetNetworksPacket request) {
        boolean rejected = original.call(player, pos);
        if (rejected) enhanced$sendEmpty(player, request);
        return rejected;
    }

    @WrapOperation(method = "lambda$handle$1",
            at = @At(value = "INVOKE", target = "Lorg/gtlcore/gtlcore/integration/ae2/wireless/MeInventoryRequestLimiter;tryAcquire(Ljava/lang/Object;J)Z", remap = false),
            require = 1)
    private static boolean enhanced$finishRateLimitedRequest(@Coerce Object limiter, Object key, long tick,
            Operation<Boolean> original, @Local(argsOnly = true) net.minecraftforge.network.NetworkEvent.Context context,
            @Local(argsOnly = true) WirelessAePackets.RequestTargetNetworksPacket request) {
        boolean allowed = original.call(limiter, key, tick);
        if (!allowed) enhanced$sendEmpty(context.getSender(), request);
        return allowed;
    }

    @WrapOperation(method = "lambda$handle$1",
            at = @At(value = "INVOKE", target = "Lorg/gtlcore/gtlcore/integration/ae2/wireless/WirelessAeNetworkRuntime;canBindAsWirelessTarget(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Direction;)Z", remap = false),
            require = 1)
    private static boolean enhanced$finishUnavailableTarget(ServerLevel level, BlockPos pos, Direction side,
            Operation<Boolean> original, @Local(argsOnly = true) net.minecraftforge.network.NetworkEvent.Context context,
            @Local(argsOnly = true) WirelessAePackets.RequestTargetNetworksPacket request) {
        boolean available = original.call(level, pos, side);
        if (!available) enhanced$sendEmpty(context.getSender(), request);
        return available;
    }

    @Unique
    private static void enhanced$sendEmpty(ServerPlayer player, WirelessAePackets.RequestTargetNetworksPacket request) {
        if (player != null) {
            WirelessAePackets.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                    new WirelessAePackets.SyncTargetNetworksPacket(request.targetPos(), List.of()));
        }
    }
}
