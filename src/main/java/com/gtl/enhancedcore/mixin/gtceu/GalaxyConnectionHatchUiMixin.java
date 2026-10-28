package com.gtl.enhancedcore.mixin.gtceu;

import com.gregtechceu.gtceu.api.gui.fancy.IFancyUIProvider;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gtl.enhancedcore.common.machine.galaxy.GalaxyConnectionHatch;
import java.util.List;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = WorkableElectricMultiblockMachine.class, remap = false)
public abstract class GalaxyConnectionHatchUiMixin {
    @Inject(method = "getSubTabs", at = @At("RETURN"), cancellable = true, require = 1)
    private void galaxy$excludeConnectionPorts(CallbackInfoReturnable<List<IFancyUIProvider>> cir) {
        var tabs = cir.getReturnValue();
        if (tabs == null || tabs.isEmpty()) return;
        cir.setReturnValue(tabs.stream().filter(tab -> !(tab instanceof GalaxyConnectionHatch)).toList());
    }
}
