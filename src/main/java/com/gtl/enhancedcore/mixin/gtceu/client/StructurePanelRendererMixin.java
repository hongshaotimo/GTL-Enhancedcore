package com.gtl.enhancedcore.mixin.gtceu.client;

import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.client.renderer.machine.MachineRenderer;
import com.gtl.enhancedcore.client.renderer.StructurePanelPartRenderer;
import com.gtl.enhancedcore.client.renderer.StructurePanelRenderer;
import com.gtl.enhancedcore.common.structure.StructurePanelAppearance;
import java.util.List;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = MachineRenderer.class, remap = false)
public abstract class StructurePanelRendererMixin implements StructurePanelPartRenderer {
    @Inject(method = "renderBaseModel", at = @At("HEAD"), cancellable = true, require = 1, allow = 1)
    private void enhancedcore$controllerPanel(List<BakedQuad> quads, MachineDefinition definition, MetaMachine machine,
                                             Direction front, Direction side, RandomSource random, CallbackInfo ci) {
        if (StructurePanelRenderer.render(quads, StructurePanelAppearance.panel(definition.getId()), machine, side, random)) ci.cancel();
    }
}
