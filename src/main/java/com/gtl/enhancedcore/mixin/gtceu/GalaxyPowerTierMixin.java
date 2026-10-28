package com.gtl.enhancedcore.mixin.gtceu;

import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gtl.enhancedcore.common.machine.galaxy.GalaxyLinks;
import com.gtl.enhancedcore.common.machine.galaxy.GalaxyPower;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = WorkableElectricMultiblockMachine.class, remap = false)
public abstract class GalaxyPowerTierMixin {
    @Inject(method = "getTier", at = @At("RETURN"), cancellable = true)
    private void galaxy$tier(CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(GalaxyPower.tier((WorkableElectricMultiblockMachine)(Object)this, cir.getReturnValueI()));
    }
    @Inject(method = {"getOverclockVoltage", "getMaxVoltage"}, at = @At("HEAD"), cancellable = true)
    private void galaxy$voltage(CallbackInfoReturnable<Long> cir) {
        if (GalaxyLinks.active((WorkableElectricMultiblockMachine)(Object)this))
            cir.setReturnValue(Long.MAX_VALUE);
    }
}
