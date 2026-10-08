package com.gtl.enhancedcore.audit.mixin;

import com.mojang.blaze3d.platform.Window;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Window.class)
public abstract class HiddenAuditWindowMixin {
    @Inject(method = "<init>", at = @At("RETURN"))
    private void audit$hide(CallbackInfo ci) {
        if (Boolean.getBoolean("gtl.enhancedcore.previewAudit") || Boolean.getBoolean("gtl.enhancedcore.stellarAudit")
                || Boolean.getBoolean("gtl.enhancedcore.lampAudit")
                || Boolean.getBoolean("gtl.enhancedcore.coilDebugAudit")
                || Boolean.getBoolean("gtl.enhancedcore.functionalTipsAudit")
                || Boolean.getBoolean("gtl.enhancedcore.patternGeneratorAudit")
                || Boolean.getBoolean("gtl.enhancedcore.chunkBenchmark"))
            GLFW.glfwHideWindow(((Window) (Object) this).getWindow());
    }
}
