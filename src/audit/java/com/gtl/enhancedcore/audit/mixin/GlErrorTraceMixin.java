package com.gtl.enhancedcore.audit.mixin;

import com.gtl.enhancedcore.GTLEnhancedcore;
import com.mojang.blaze3d.platform.GlDebug;
import org.lwjgl.opengl.GL43;
import org.lwjgl.system.MemoryUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Capture actual GL callers in isolated audits only; never shipped in the production mod. */
@Mixin(GlDebug.class)
public abstract class GlErrorTraceMixin {
    @Unique private static int audit$errors;

    @Inject(method = "printDebugLog", at = @At("HEAD"))
    private static void audit$trace(int source, int type, int id, int severity, int length, long message,
            long userParam, CallbackInfo ci) {
        if (type != GL43.GL_DEBUG_TYPE_ERROR || audit$errors++ >= 12) return;
        GTLEnhancedcore.LOGGER.error("[GL_AUDIT] {}",
                MemoryUtil.memUTF8(message, length), new IllegalStateException("OpenGL call trace"));
    }
}
