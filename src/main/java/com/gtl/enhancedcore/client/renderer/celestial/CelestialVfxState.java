package com.gtl.enhancedcore.client.renderer.celestial;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.renderer.ShaderInstance;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

/** Keeps the immediate draws local to ADD's already isolated render pass. */
final class CelestialVfxState implements AutoCloseable {
    private final ShaderInstance shader = RenderSystem.getShader();
    private final boolean shaderActive = shader != null && shader.getId() == GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
    private final boolean depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
    private final boolean depthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
    private final int depthFunction = GL11.glGetInteger(GL11.GL_DEPTH_FUNC);
    private final boolean blend = GL11.glIsEnabled(GL11.GL_BLEND);
    private final int sourceRgb = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB);
    private final int destinationRgb = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
    private final int sourceAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA);
    private final int destinationAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
    private final boolean cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
    private final int drawTarget = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
    private final int readTarget = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);

    CelestialVfxState() {
        // ExtendedShader has its own applied-program cache. Use its lifecycle
        // only when it really owns the current program; a lazy RS reference
        // must not be applied just because this renderer finished drawing.
        if (shaderActive) {
            shader.clear();
            restoreTargets();
        }
    }

    @Override
    public void close() {
        RenderSystem.setShader(() -> shader);
        if (shaderActive) shader.apply();
        restoreTargets();
        if (depth) RenderSystem.enableDepthTest(); else RenderSystem.disableDepthTest();
        RenderSystem.depthFunc(depthFunction);
        RenderSystem.depthMask(depthMask);
        RenderSystem.blendFuncSeparate(sourceRgb, destinationRgb, sourceAlpha, destinationAlpha);
        if (blend) RenderSystem.enableBlend(); else RenderSystem.disableBlend();
        if (cull) RenderSystem.enableCull(); else RenderSystem.disableCull();
    }

    private void restoreTargets() {
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, drawTarget);
        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readTarget);
    }
}
