package com.gtl.enhancedcore.mixin.gtlcore;

import com.gtl.enhancedcore.common.assembly.AssemblyManagerIndex;
import org.gtlcore.gtlcore.common.machine.multiblock.part.ae.MEPatternBufferPartMachineBase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = MEPatternBufferPartMachineBase.class, remap = false)
public abstract class AssemblyManagerBufferIndexMixin {
    @Inject(method = "setCustomName", at = @At("TAIL"))
    private void enhanced$renameAssembly(String name, CallbackInfo ci) {
        AssemblyManagerIndex.update((MEPatternBufferPartMachineBase) (Object) this);
    }

    @Inject(method = "onMachineRemoved", at = @At("HEAD"))
    private void enhanced$removeAssembly(CallbackInfo ci) {
        AssemblyManagerIndex.remove((MEPatternBufferPartMachineBase) (Object) this);
    }
}
