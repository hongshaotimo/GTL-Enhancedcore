package com.gtl.enhancedcore.mixin.ae2;

import appeng.blockentity.grid.AENetworkInvBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** AE2 15.4.10 can notify an already unloaded quantum bridge while disconnecting its neighbors. */
@Mixin(value = AENetworkInvBlockEntity.class, remap = false)
public abstract class NetworkInvNodeLifecycleMixin {
    @Unique
    private boolean gtlEnhancedcore$nodeTerminated;

    @Inject(method = "onChunkUnloaded()V", at = @At(value = "INVOKE",
            target = "Lappeng/api/networking/IManagedGridNode;destroy()V", remap = false),
            remap = false, require = 1, expect = 1, allow = 1)
    private void gtlEnhancedcore$beforeChunkNodeDestroy(CallbackInfo ci) {
        gtlEnhancedcore$nodeTerminated = true;
    }

    @Inject(method = "setRemoved()V", at = @At(value = "INVOKE",
            target = "Lappeng/api/networking/IManagedGridNode;destroy()V", remap = false),
            remap = true, require = 1, expect = 1, allow = 1)
    private void gtlEnhancedcore$beforeRemovedNodeDestroy(CallbackInfo ci) {
        gtlEnhancedcore$nodeTerminated = true;
    }

    @Inject(method = "onGridConnectableSidesChanged()V", at = @At("HEAD"),
            cancellable = true, remap = false, require = 1, expect = 1, allow = 1)
    private void gtlEnhancedcore$skipTerminatedNodeSides(CallbackInfo ci) {
        // A managed node consumes its initialization data once; a destroyed instance cannot be revived.
        if (gtlEnhancedcore$nodeTerminated) ci.cancel();
    }
}
