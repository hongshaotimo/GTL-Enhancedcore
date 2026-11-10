package com.gtl.enhancedcore.mixin.gtceu;

import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gtl.enhancedcore.common.assembly.AssemblyManagerIndex;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = MetaMachine.class, remap = false)
public abstract class AssemblyManagerMachineLoadMixin {
    @Inject(method = "onLoad", at = @At("TAIL"))
    private void enhanced$indexAssembly(CallbackInfo ci) {
        AssemblyManagerIndex.update((MetaMachine) (Object) this);
    }
}
