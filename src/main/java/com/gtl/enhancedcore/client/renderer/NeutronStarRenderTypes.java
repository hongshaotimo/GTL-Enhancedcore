package com.gtl.enhancedcore.client.renderer;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/** Alpha-weighted glow for the remaining machine fields. */
@OnlyIn(Dist.CLIENT)
public abstract class NeutronStarRenderTypes extends RenderType {

    private NeutronStarRenderTypes() {
        super("gtl_enhancedcore_neutron_star_unused", DefaultVertexFormat.BLOCK, VertexFormat.Mode.QUADS, 0,
                false, false, () -> {
                }, () -> {
                });
    }

    public static final RenderType ENERGY = create(
            "gtl_enhancedcore_neutron_star_energy",
            DefaultVertexFormat.BLOCK,
            VertexFormat.Mode.QUADS,
            2097152,
            true,
            true,
            CompositeState.builder()
                    .setShaderState(RENDERTYPE_TRANSLUCENT_SHADER)
                    .setTextureState(BLOCK_SHEET_MIPPED)
                    .setTransparencyState(LIGHTNING_TRANSPARENCY)
                    .setWriteMaskState(COLOR_WRITE)
                    .setCullState(NO_CULL)
                    .setLightmapState(LIGHTMAP)
                    .setOutputState(TRANSLUCENT_TARGET)
                    .createCompositeState(false));
}
