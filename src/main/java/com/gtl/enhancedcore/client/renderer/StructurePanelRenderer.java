package com.gtl.enhancedcore.client.renderer;

import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gtl.enhancedcore.common.structure.StructurePanelAppearance;
import com.lowdragmc.lowdraglib.client.renderer.IBlockRendererProvider;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;

/** Draw only the casing layer; the original controller and hatch renderers retain their overlays and effects. */
public final class StructurePanelRenderer {
    private StructurePanelRenderer() {}

    public static boolean render(List<BakedQuad> quads, ResourceLocation controller, MetaMachine rendered,
                                 Direction side, RandomSource random) {
        var panel = StructurePanelAppearance.panel(controller);
        if (panel == null) return false;
        var state = panel.get();
        // LDLib-backed blocks return no quads from vanilla's three-argument BakedModel getter.
        // Their renderer also needs the actual hatch position, rather than its controller's position.
        if (state.getBlock() instanceof IBlockRendererProvider provider) {
            // MachineRenderer also calls this path for item stacks, where no machine/world exists.
            quads.addAll(provider.getRenderer(state).renderModel(rendered == null ? null : rendered.getLevel(),
                    rendered == null ? null : rendered.getPos(), state, side, random));
        } else {
            quads.addAll(Minecraft.getInstance().getBlockRenderer().getBlockModel(state).getQuads(state, side, random));
        }
        return true;
    }
}
