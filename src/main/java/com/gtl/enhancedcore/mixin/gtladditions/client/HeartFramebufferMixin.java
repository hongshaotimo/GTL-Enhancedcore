package com.gtl.enhancedcore.mixin.gtladditions.client;

import com.gtladd.gtladditions.client.render.machine.heart.HeartBlackHoleFramebuffer;
import org.lwjgl.opengl.GL30;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/** Save and restore the two attachment points separately, including depth-only targets. */
@Mixin(value = HeartBlackHoleFramebuffer.class, remap = false)
public abstract class HeartFramebufferMixin {
    @ModifyConstant(method = {"queryBoundDepthAttachments", "replaceDepthAttachments", "restoreDepthAttachments"},
            constant = @Constant(intValue = GL30.GL_DEPTH_STENCIL_ATTACHMENT), require = 5, allow = 5, remap = false)
    private int enhancedcore$stencilAttachment(int combinedAttachment) {
        return GL30.GL_STENCIL_ATTACHMENT;
    }
}
