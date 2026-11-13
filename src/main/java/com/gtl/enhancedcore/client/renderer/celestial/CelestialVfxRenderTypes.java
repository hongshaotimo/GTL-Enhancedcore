package com.gtl.enhancedcore.client.renderer.celestial;

import com.gtl.enhancedcore.GTLEnhancedcore;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.io.IOException;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Depth-tested additive light, no depth writes or quad sorting; shared by both orreries. */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = GTLEnhancedcore.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public abstract class CelestialVfxRenderTypes extends RenderType {
    private static ShaderInstance shader;
    private static ShaderInstance coreShader;
    private CelestialVfxRenderTypes() {
        super("celestial_unused", DefaultVertexFormat.POSITION_COLOR_TEX, VertexFormat.Mode.QUADS,
                256, false, false, () -> {}, () -> {});
    }
    static final RenderType LIGHT = create("gtl_enhancedcore_celestial_light", DefaultVertexFormat.POSITION_COLOR_TEX,
            VertexFormat.Mode.QUADS, 1 << 20, false, false,
            CompositeState.builder().setShaderState(new ShaderStateShard(() -> shader))
                    .setTransparencyState(LIGHTNING_TRANSPARENCY).setWriteMaskState(COLOR_WRITE)
                    .setDepthTestState(LEQUAL_DEPTH_TEST)
                    .setCullState(NO_CULL).createCompositeState(false));

    static final RenderType VOLUME = create("gtl_enhancedcore_celestial_volume", DefaultVertexFormat.POSITION_COLOR_TEX,
            VertexFormat.Mode.QUADS, 256, false, false,
            CompositeState.builder().setShaderState(new ShaderStateShard(() -> shader))
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY).setWriteMaskState(COLOR_WRITE)
                    .setDepthTestState(LEQUAL_DEPTH_TEST)
                    .setCullState(NO_CULL).createCompositeState(false));

    static final RenderType CORE = create("gtl_enhancedcore_celestial_core", DefaultVertexFormat.POSITION_COLOR_TEX,
            VertexFormat.Mode.QUADS, 256, false, false,
            CompositeState.builder().setShaderState(new ShaderStateShard(() -> coreShader))
                    .setTransparencyState(NO_TRANSPARENCY).setWriteMaskState(COLOR_DEPTH_WRITE)
                    .setDepthTestState(LEQUAL_DEPTH_TEST)
                    .setCullState(NO_CULL).createCompositeState(false));

    static ShaderInstance shader() { return shader; }
    static ShaderInstance coreShader() { return coreShader; }

    @SubscribeEvent
    public static void register(RegisterShadersEvent event) throws IOException {
        event.registerShader(new ShaderInstance(event.getResourceProvider(), GTLEnhancedcore.id("celestial_light"),
                DefaultVertexFormat.POSITION_COLOR_TEX), loaded -> shader = loaded);
        event.registerShader(new ShaderInstance(event.getResourceProvider(), GTLEnhancedcore.id("celestial_core"),
                DefaultVertexFormat.POSITION_COLOR_TEX), loaded -> coreShader = loaded);
    }
}
