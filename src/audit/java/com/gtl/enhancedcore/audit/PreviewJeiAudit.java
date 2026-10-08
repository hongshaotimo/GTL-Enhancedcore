package com.gtl.enhancedcore.audit;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.resources.ResourceLocation;

@JeiPlugin
public final class PreviewJeiAudit implements IModPlugin {
    static IJeiRuntime runtime;

    @Override
    public ResourceLocation getPluginUid() {
        return new ResourceLocation("enhancedcore_audit", "preview");
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime runtime) {
        PreviewJeiAudit.runtime = runtime;
    }
}
