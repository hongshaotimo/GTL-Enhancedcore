package com.gtl.enhancedcore.mixin.gtceu;

import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gtl.enhancedcore.common.machine.galaxy.GalaxyWirelessEnergyHandler;
import com.gtladd.gtladditions.api.machine.trait.IWirelessNetworkEnergyHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** ADD soft interface injects a class method returning its own nullable handler. */
@Mixin(value = WorkableElectricMultiblockMachine.class, remap = false, priority = 900)
public abstract class GalaxyControllerWirelessHandlerMixin {
    @Dynamic("GTLAdditions WorkableElectricMultiblockMachineMixin soft interface")
    @Inject(method = "getWirelessNetworkEnergyHandler", at = @At("RETURN"), cancellable = true, require = 1)
    private void galaxy$linkedGrid(CallbackInfoReturnable<IWirelessNetworkEnergyHandler> cir) {
        if (cir.getReturnValue() == null) cir.setReturnValue(GalaxyWirelessEnergyHandler.forMachine(
                (WorkableElectricMultiblockMachine)(Object)this));
    }
}
