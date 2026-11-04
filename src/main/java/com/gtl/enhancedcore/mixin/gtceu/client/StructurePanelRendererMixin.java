package com.gtl.enhancedcore.mixin.gtceu.client;

import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiPart;
import com.gregtechceu.gtceu.client.renderer.machine.MachineRenderer;
import com.gtl.enhancedcore.client.renderer.StructurePanelRenderer;
import java.util.List;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = MachineRenderer.class, remap = false)
public abstract class StructurePanelRendererMixin {
    @Inject(method = "renderBaseModel", at = @At("HEAD"), cancellable = true, require = 1, allow = 1)
    private void enhancedcore$controllerPanel(List<BakedQuad> quads, MachineDefinition definition, MetaMachine machine,
                                             Direction front, Direction side, RandomSource random, CallbackInfo ci) {
        if (StructurePanelRenderer.render(quads, definition.getId(), machine, side, random)) ci.cancel();
    }

    @Inject(method = "renderMachine", at = @At("HEAD"), cancellable = true, require = 1, allow = 1)
    private void enhancedcore$formedPartPanel(List<BakedQuad> quads, MachineDefinition definition, MetaMachine machine,
                                             Direction front, Direction side, RandomSource random, Direction modelSide,
                                             ModelState rotation, CallbackInfo ci) {
        if (!(machine instanceof IMultiPart part) || !part.replacePartModelWhenFormed()) return;
        // Some native controller renderers supply a second, hard-coded casing for their hatches.
        // Select the reviewed panel before that branch while keeping the hatch's own overlay calls.
        for (var controller : part.getControllers()) {
            if (!controller.isFormed()) continue;
            if (StructurePanelRenderer.render(quads, controller.self().getDefinition().getId(), machine, side, random)) {
                ci.cancel();
            }
            // A shared hatch must keep the first controller's skin even if a later one is in our palette.
            return;
        }
    }
}
