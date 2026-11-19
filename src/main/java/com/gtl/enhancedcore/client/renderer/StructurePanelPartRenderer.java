package com.gtl.enhancedcore.client.renderer;

import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiPart;
import com.gregtechceu.gtceu.client.renderer.machine.IPartRenderer;
import com.gtl.enhancedcore.common.structure.StructurePanelAppearance;
import java.util.List;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;

/** More specific default for ordinary hatches; concrete renderer overrides still take precedence. */
public interface StructurePanelPartRenderer extends IPartRenderer {
    @Override
    default boolean renderReplacedPartMachine(List<BakedQuad> quads, IMultiPart part, Direction front, Direction side,
                                              RandomSource random, Direction modelSide, ModelState rotation) {
        for (var controller : part.getControllers()) {
            if (!controller.isFormed()) continue;
            var casing = StructurePanelAppearance.hatchPanel(controller.self().getDefinition().getId());
            if (casing != null) return StructurePanelRenderer.render(quads, casing, part.self(), side, random);
            break;
        }
        // Keep this call in a normal interface: merging it into MachineRenderer breaks its method reference.
        return IPartRenderer.super.renderReplacedPartMachine(quads, part, front, side, random, modelSide, rotation);
    }
}
