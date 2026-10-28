package com.gtl.enhancedcore.mixin.gtlcore;

import com.gtl.enhancedcore.common.cloud.CloudMeNetworkAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import org.gtlcore.gtlcore.integration.ae2.wireless.WirelessAePackets;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Native selection packets keep cloud ownership/claim checks before any bind or disconnect. */
@Mixin(value = WirelessAePackets.class, remap = false)
public abstract class CloudWirelessPermissionMixin {
    @Inject(method = "isCloseEnough", at = @At("HEAD"), cancellable = true, require = 1)
    private static void cloud$permission(ServerPlayer player, BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        // Despite its name, fix4 returns true for a rejected (too far away) target.
        if (CloudMeNetworkAccess.denied(player, pos)) cir.setReturnValue(true);
    }
}
