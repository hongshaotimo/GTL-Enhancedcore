package com.gtl.enhancedcore.client.renderer.machine;

import com.gtl.enhancedcore.GTLEnhancedcore;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.io.IOException;

/** One soft-light atlas and an order-independent additive batch shared by both charger tiers. */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = GTLEnhancedcore.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public abstract class WirelessChargerRenderTypes extends RenderType {
    private static ShaderInstance shader;
    private WirelessChargerRenderTypes() {
        super("wireless_unused", DefaultVertexFormat.POSITION_COLOR_TEX, VertexFormat.Mode.QUADS,
                256, false, false, () -> {}, () -> {});
    }

    // Addition is commutative: skip quad sorting and keep ribbons/core in the same buffer.
    static final RenderType FIELD = field("gtl_enhancedcore_wireless_field", TRANSLUCENT_TARGET);
    static final RenderType WORLD_FIELD = field("gtl_enhancedcore_wireless_scene_field", MAIN_TARGET);

    private static RenderType field(String name, OutputStateShard target) {
        return create(name,
            DefaultVertexFormat.POSITION_COLOR_TEX, VertexFormat.Mode.QUADS, 65536, false, false,
            CompositeState.builder().setShaderState(new ShaderStateShard(() -> shader))
                    .setTextureState(new TextureStateShard(GTLEnhancedcore.id("textures/effect/wireless_field.png"), true, false))
                    .setTransparencyState(LIGHTNING_TRANSPARENCY).setWriteMaskState(COLOR_WRITE)
                    .setCullState(NO_CULL).setOutputState(target).createCompositeState(false));
    }

    @SubscribeEvent
    public static void register(RegisterShadersEvent event) throws IOException {
        event.registerShader(new ShaderInstance(event.getResourceProvider(), GTLEnhancedcore.id("wireless_field"),
                DefaultVertexFormat.POSITION_COLOR_TEX), loaded -> shader = loaded);
    }
}
