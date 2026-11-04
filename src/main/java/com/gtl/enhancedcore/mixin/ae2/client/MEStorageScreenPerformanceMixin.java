package com.gtl.enhancedcore.mixin.ae2.client;

import appeng.client.gui.me.common.MEStorageScreen;
import appeng.client.gui.me.common.Repo;
import com.gtl.enhancedcore.client.performance.RepoRebuildControl;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(value = MEStorageScreen.class, remap = false, priority = 900)
public abstract class MEStorageScreenPerformanceMixin {
    @Shadow @Final protected Repo repo;

    @WrapMethod(method = "updateBeforeRender")
    private void enhancedcore$completeView(Operation<Void> original) {
        original.call();
        ((RepoRebuildControl) repo).enhancedcore$flushView();
    }
}
