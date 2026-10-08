package com.gtl.enhancedcore.audit.mixin;

import net.minecraft.SystemReport;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Isolated harness only: hardware inventory must not block tests on an unresponsive Windows WMI. */
@Mixin(value = SystemReport.class, remap = false)
public abstract class HardwareReportMixin {
    @Inject(method = "m_143535_", at = @At("HEAD"), cancellable = true)
    private void audit$hardware(CallbackInfo ci) {
        if (Boolean.getBoolean("gtl.enhancedcore.skipHardwareReport")) ci.cancel();
    }
}
