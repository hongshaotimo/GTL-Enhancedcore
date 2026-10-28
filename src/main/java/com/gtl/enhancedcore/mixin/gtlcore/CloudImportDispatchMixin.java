package com.gtl.enhancedcore.mixin.gtlcore;

import com.gtl.enhancedcore.common.cloud.transfer.CloudImportSourceAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = {"org.gtlcore.gtlcore.common.machine.multiblock.part.ae.MEPatternBufferPartMachineBase",
        "com.gtladd.gtladditions.common.machine.multiblock.part.MESuperPatternBufferPartMachine"}, remap = false)
public abstract class CloudImportDispatchMixin {
    @Inject(method = "pushPattern", at = @At("HEAD"), cancellable = true)
    private void cloud$deny(appeng.api.crafting.IPatternDetails pattern, appeng.api.stacks.KeyCounter[] inputs, CallbackInfoReturnable<Boolean> cir) {
        if ((Object)this instanceof CloudImportSourceAccess source && (source.cloud$importQuarantined() || com.gtl.enhancedcore.common.cloud.transfer.CloudNetworkPatternImport.sourceLocked(source))) cir.setReturnValue(false);
    }
}
