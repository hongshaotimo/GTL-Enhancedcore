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

    public static final RenderType ENERGY = energy("gtl_enhancedcore_neutron_star_energy", TRANSLUCENT_TARGET);
    public static final RenderType WORLD_ENERGY = energy("gtl_enhancedcore_neutron_star_scene_energy", MAIN_TARGET);
    // Vanilla translucent settings with a no-op output shard so ADD retains the composition target.
    public static final RenderType WORLD_TRANSLUCENT = create("gtl_enhancedcore_scene_translucent",
            DefaultVertexFormat.BLOCK, VertexFormat.Mode.QUADS, 2097152, true, true,
            CompositeState.builder().setShaderState(RENDERTYPE_TRANSLUCENT_SHADER)
                    .setTextureState(BLOCK_SHEET_MIPPED).setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setLightmapState(LIGHTMAP).setOutputState(MAIN_TARGET).createCompositeState(true));

    private static RenderType energy(String name, OutputStateShard target) {
        return create(name,
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
                    .setOutputState(target)
                    .createCompositeState(false));
    }
}
