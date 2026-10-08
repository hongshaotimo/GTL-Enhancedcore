package com.gtl.enhancedcore.mixin.gtladditions;

import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController;
import com.gtl.enhancedcore.common.util.SuprachronalModuleRemoval;
import org.gtlcore.gtlcore.common.machine.multiblock.electric.SuprachronalAssemblyLineMachine;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "org.gtlcore.gtlcore.api.gui.PatternPreviewWidget$MBPattern", remap = false)
public abstract class SuprachronalModulePreviewRemovalMixin {
    @Shadow(remap = false)
    @Final
    IMultiController controllerBase;

    @Shadow(remap = false)
    @Final
    @Mutable
    boolean hasModule;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void enhanced$removeSuprachronalModuleToggle(CallbackInfo callback) {
        hasModule = SuprachronalModuleRemoval.previewHasModules(hasModule,
                controllerBase instanceof SuprachronalAssemblyLineMachine);
    }
}
