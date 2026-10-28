package com.gtl.enhancedcore.mixin.gtlcore;

import com.gtl.enhancedcore.common.cloud.CloudPatternBufferMachine;
import com.gtl.enhancedcore.common.cloud.CloudPatternLibraryMachine;
import com.gtl.enhancedcore.common.cloud.CloudMeTargetPolicy;
import com.gtl.enhancedcore.common.cloud.CloudMeNetworkAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.resources.ResourceLocation;
import org.gtlcore.gtlcore.integration.ae2.wireless.WirelessAeNetworkRuntime;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Opt into GTLCore's existing network selection, membership and reconnect scheduler. */
@Mixin(value = WirelessAeNetworkRuntime.class, remap = false)
public abstract class CloudWirelessTargetMixin {
    @Inject(method = "isWirelessMeTargetId", at = @At("HEAD"), cancellable = true)
    private static void cloud$registeredTarget(ResourceLocation id, CallbackInfoReturnable<Boolean> cir) {
        if (id != null && CloudMeTargetPolicy.registered(id.getNamespace(), id.getPath())) cir.setReturnValue(true);
    }
    @Inject(method = "isWirelessMeTargetObject", at = @At("HEAD"), cancellable = true)
    private static void cloud$actualTarget(Object target, ResourceLocation id, CallbackInfoReturnable<Boolean> cir) {
        if (target instanceof CloudPatternBufferMachine || target instanceof CloudPatternLibraryMachine) cir.setReturnValue(true);
    }
    @Inject(method = "unbindWithTool", at = @At("HEAD"), cancellable = true, require = 1)
    private static void cloud$unbindPermission(ServerPlayer player, BlockPos pos, CallbackInfo ci) {
        if (CloudMeNetworkAccess.denied(player, pos)) ci.cancel();
    }
}
