package com.gtl.enhancedcore.mixin.mc;

import com.gtl.enhancedcore.common.world.personalplot.PersonalPlotPackAccess;
import net.minecraft.server.packs.FilePackResources;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/** close alone permits a lazy reopen of a replacement ZIP at the same path. */
@Mixin(FilePackResources.class)
public abstract class PersonalPlotFilePackMixin implements PersonalPlotPackAccess {
    @Shadow(aliases = "f_243662_", remap = false) private boolean failedToLoad;
    @Shadow(remap = false) public abstract void close();
    @Override public void enhancedcore$retirePersonalPack() { failedToLoad = true; close(); }
}
