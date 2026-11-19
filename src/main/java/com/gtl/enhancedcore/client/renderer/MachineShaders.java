package com.gtl.enhancedcore.client.renderer;

import net.irisshaders.iris.api.v0.IrisApi;
import net.minecraftforge.fml.ModList;

/** Never use the shadow camera's coordinates for a visible field. Oculus is optional. */
public final class MachineShaders {
    private static final boolean OCULUS = ModList.get().isLoaded("oculus");

    private MachineShaders() {}

    public static boolean shadow() { return OCULUS && IrisApi.getInstance().isRenderingShadowPass(); }
}
