package com.gtl.enhancedcore.mixin.gtlcore;

import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gtl.enhancedcore.common.recipe.iv.IvBuffers;
import net.minecraft.core.BlockPos;
import org.gtlcore.gtlcore.common.machine.multiblock.part.ae.MEPatternBufferProxyPartMachine;
import org.gtlcore.gtlcore.common.machine.multiblock.part.ae.MEPatternBufferPartMachineBase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = MEPatternBufferProxyPartMachine.class, remap = false)
public abstract class IvBufferProxyGuardMixin {
    @Inject(method = "setBuffer", at = @At("HEAD"), cancellable = true)
    private void iv$exclusive(BlockPos pos, CallbackInfo ci) {
        var proxy = (MEPatternBufferProxyPartMachine)(Object)this;
        if (pos != null && proxy.getLevel() != null
                && MetaMachine.getMachine(proxy.getLevel(), pos) instanceof MEPatternBufferPartMachineBase buffer
                && IvBuffers.isolated(buffer)) ci.cancel();
    }
}
