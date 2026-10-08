package com.gtl.enhancedcore.client.renderer;

import com.gtl.enhancedcore.GTLEnhancedcore;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.io.IOException;

/** Camera rays bend around the horizon; the two passes share one optical solution. */
@Mod.EventBusSubscriber(modid = GTLEnhancedcore.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class BlackHoleRenderer extends RenderType {
    private static final float HORIZON_RADIUS = 6.5F;
    private static final BufferBuilder VERTICES = new BufferBuilder(256);
    private static ShaderInstance shader;
    private static final RenderType HORIZON = fieldType("singularity_horizon", true);
    private static final RenderType DISK = fieldType("singularity_disk", false);
    private static final float[][][] VOLUME = {
            {{1,-1,-1},{1,-1,1},{1,1,1},{1,1,-1}},
            {{-1,-1,1},{-1,-1,-1},{-1,1,-1},{-1,1,1}},
            {{-1,1,-1},{1,1,-1},{1,1,1},{-1,1,1}},
            {{-1,-1,1},{1,-1,1},{1,-1,-1},{-1,-1,-1}},
            {{1,-1,1},{-1,-1,1},{-1,1,1},{1,1,1}},
            {{-1,-1,-1},{1,-1,-1},{1,1,-1},{-1,1,-1}}
    };

    private BlackHoleRenderer() {
        super("singularity_unused", DefaultVertexFormat.POSITION, VertexFormat.Mode.QUADS,
                256, false, false, () -> {}, () -> {});
    }

    private static RenderType fieldType(String name, boolean opaque) {
        return create("gtl_enhancedcore_" + name, DefaultVertexFormat.POSITION,
                VertexFormat.Mode.QUADS, 256, false, false,
                CompositeState.builder()
                        .setShaderState(new ShaderStateShard(() -> shader))
                        .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                        .setDepthTestState(LEQUAL_DEPTH_TEST)
                        .setWriteMaskState(opaque ? COLOR_DEPTH_WRITE : COLOR_WRITE)
                        .setCullState(CULL)
                        .createCompositeState(false));
    }

    @SubscribeEvent
    public static void registerShader(RegisterShadersEvent event) throws IOException {
        event.registerShader(new ShaderInstance(event.getResourceProvider(),
                GTLEnhancedcore.id("singularity"), DefaultVertexFormat.POSITION), loaded -> shader = loaded);
    }

    public static void render(PoseStack pose, Vector3f normal, Vector3f tangent, float tick, boolean preview) {
        if (shader == null) return;
        Matrix4f view = BlackHoleFrame.view(pose.last().pose(),
                RenderSystem.getModelViewMatrix(), preview, HORIZON_RADIUS);
        float determinant = view.determinant();
        if (!Float.isFinite(determinant) || Math.abs(determinant) < 0.000001F) return;

        ShaderInstance previous = RenderSystem.getShader();
        shader.safeGetUniform("FieldViewMat").set(view);
        shader.safeGetUniform("InverseFieldViewMat").set(new Matrix4f(view).invert());
        shader.safeGetUniform("DiskNormal").set(normal.x, normal.y, normal.z);
        shader.safeGetUniform("DiskTangent").set(tangent.x, tangent.y, tangent.z);
        shader.safeGetUniform("PhaseTime").set(tick / 20.0F);
        try {
            draw(HORIZON, 1, determinant < 0);
            draw(DISK, 0, determinant < 0);
        } finally {
            RenderSystem.setShader(() -> previous);
        }
    }

    private static void draw(RenderType type, int opaque, boolean mirrored) {
        type.setupRenderState();
        try {
            shader.safeGetUniform("OpaquePass").set(opaque);
            VERTICES.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
            // Inward faces draw the far boundary once per pixel, even when the
            // camera enters the volume. A mirrored preview reverses the winding.
            for (float[][] face : VOLUME) {
                for (int i = 0; i < 4; i++) {
                    float[] vertex = face[mirrored ? 3-i : i];
                    VERTICES.vertex(vertex[0]*8.1F, vertex[1]*8.1F, vertex[2]*8.1F).endVertex();
                }
            }
            // Draw immediately: per-machine uniforms must not leak to another
            // buffered machine, the JEI scene, or a differently oriented structure.
            BufferUploader.drawWithShader(VERTICES.end());
        } finally {
            type.clearRenderState();
        }
    }
}
