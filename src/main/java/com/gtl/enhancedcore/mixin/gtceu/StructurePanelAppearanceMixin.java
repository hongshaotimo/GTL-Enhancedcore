package com.gtl.enhancedcore.mixin.gtceu;

import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.registry.registrate.MultiblockMachineBuilder;
import com.gtl.enhancedcore.common.structure.StructurePanelAppearance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = MultiblockMachineBuilder.class, remap = false)
public abstract class StructurePanelAppearanceMixin {
    @Inject(method = "register()Lcom/gregtechceu/gtceu/api/machine/MultiblockMachineDefinition;",
            at = @At("RETURN"), require = 1, allow = 1)
    private void enhancedcore$panelAppearance(CallbackInfoReturnable<MultiblockMachineDefinition> cir) {
        StructurePanelAppearance.configure(cir.getReturnValue());
    }
}
