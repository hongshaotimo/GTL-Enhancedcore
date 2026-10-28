package com.gtl.enhancedcore.mixin.gtceu;

import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gtl.enhancedcore.common.cloud.store.CloudMachineIdentity;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.UUID;

@Mixin(value = MetaMachine.class, remap = false)
public abstract class CloudMachineIdentityMixin implements CloudMachineIdentity {
    @Unique private UUID cloud$machineIdentity;
    @Override public UUID cloud$machineInstance(boolean enroll) {
        if (cloud$machineIdentity == null && enroll) {
            MetaMachine machine = (MetaMachine)(Object)this;
            if (machine.isRemote()) throw new IllegalStateException("Controller enrollment requires server");
            cloud$machineIdentity = UUID.randomUUID(); machine.markDirty();
        }
        return cloud$machineIdentity;
    }
    @Inject(method = "saveCustomPersistedData", at = @At("TAIL"), require = 1)
    private void cloud$saveIdentity(CompoundTag tag, boolean forDrop, CallbackInfo ci) {
        if (!forDrop && cloud$machineIdentity != null) tag.putUUID("gtl_cloud_machine_instance", cloud$machineIdentity);
        else tag.remove("gtl_cloud_machine_instance");
    }
    @Inject(method = "loadCustomPersistedData", at = @At("TAIL"), require = 1)
    private void cloud$loadIdentity(CompoundTag tag, CallbackInfo ci) {
        cloud$machineIdentity = tag.hasUUID("gtl_cloud_machine_instance") ? tag.getUUID("gtl_cloud_machine_instance") : null;
    }
}
