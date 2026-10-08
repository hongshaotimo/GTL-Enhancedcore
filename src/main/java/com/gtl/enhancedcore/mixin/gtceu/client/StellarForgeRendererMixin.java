package com.gtl.enhancedcore.mixin.gtceu.client;

import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gregtechceu.gtceu.api.registry.registrate.MachineBuilder;
import com.gtl.enhancedcore.client.renderer.machine.StellarForgeMachineRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = MachineBuilder.class, remap = false)
public abstract class StellarForgeRendererMixin {
    @Inject(method = "register()Lcom/gregtechceu/gtceu/api/machine/MachineDefinition;", at = @At("HEAD"))
    private void stellar$renderer(CallbackInfoReturnable<MachineDefinition> cir) {
        var builder = (MachineBuilder<?>)(Object)this;
        var id = builder.id;
        if (!id.getNamespace().equals("gtceu") || !id.getPath().equals("star_ultimate_material_forge_factory")) return;
        // The renderer getter alone cannot register the block entity's render dispatcher.
        builder.renderer(StellarForgeMachineRenderer::new).hasTESR(true);
    }
}
