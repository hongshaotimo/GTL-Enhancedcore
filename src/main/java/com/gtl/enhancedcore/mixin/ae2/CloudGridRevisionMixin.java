package com.gtl.enhancedcore.mixin.ae2;

import appeng.me.Grid;
import com.gtl.enhancedcore.common.cloud.CloudGridRevision;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = Grid.class, remap = false)
public abstract class CloudGridRevisionMixin implements CloudGridRevision {
    @Unique private long cloud$revision;
    @Override public long cloud$topologyRevision() { return cloud$revision; }
    @Inject(method = {"add(Lappeng/me/GridNode;Lnet/minecraft/nbt/CompoundTag;)V", "remove(Lappeng/me/GridNode;)V"}, at = @At("HEAD"), require = 2)
    private void cloud$membershipChanged(CallbackInfo ci) { cloud$revision = Math.incrementExact(cloud$revision); }
}
