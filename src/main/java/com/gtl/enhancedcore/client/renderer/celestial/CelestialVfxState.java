package com.gtl.enhancedcore.client.renderer.celestial;

import com.gtl.enhancedcore.mixin.mc.client.BlendModeAccessor;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.shaders.BlendMode;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.renderer.ShaderInstance;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryStack;

/** Keeps the immediate draws local to ADD's already isolated render pass. */
public final class CelestialVfxState implements AutoCloseable {
    private final ShaderInstance shader = RenderSystem.getShader();
    private final int program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
    private final boolean shaderActive = shader != null && shader.getId() == program;
    private final BlendMode blendMode = BlendModeAccessor.enhancedcore$lastApplied();
    private final boolean red, green, blue, alpha;
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

    public CelestialVfxState() {
        try (var stack = MemoryStack.stackPush()) {
            var mask = stack.malloc(4);
            GL11.glGetBooleanv(GL11.GL_COLOR_WRITEMASK, mask);
            red = mask.get(0) != 0; green = mask.get(1) != 0; blue = mask.get(2) != 0; alpha = mask.get(3) != 0;
        }
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
        else if (program != 0) GlStateManager._glUseProgram(program);
        restoreTargets();
        if (depth) RenderSystem.enableDepthTest(); else RenderSystem.disableDepthTest();
        RenderSystem.depthFunc(depthFunction);
        RenderSystem.depthMask(depthMask);
        RenderSystem.blendFuncSeparate(sourceRgb, destinationRgb, sourceAlpha, destinationAlpha);
        if (blend) RenderSystem.enableBlend(); else RenderSystem.disableBlend();
        if (cull) RenderSystem.enableCull(); else RenderSystem.disableCull();
        RenderSystem.colorMask(red, green, blue, alpha);
        // Do not call apply(): the HUD sets its inverse-colour factors outside the shader.
        BlendModeAccessor.enhancedcore$lastApplied(blendMode);
    }

    private void restoreTargets() {
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, drawTarget);
        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readTarget);
    }
}
