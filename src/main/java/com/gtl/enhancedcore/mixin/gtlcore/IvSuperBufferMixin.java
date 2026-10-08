package com.gtl.enhancedcore.mixin.gtlcore;

import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gtl.enhancedcore.common.recipe.iv.*;
import com.gtladd.gtladditions.common.machine.multiblock.part.MESuperPatternBufferPartMachine;
import org.gtlcore.gtlcore.common.machine.multiblock.part.ae.MEPatternBufferPartMachine;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = MESuperPatternBufferPartMachine.class, remap = false)
public abstract class IvSuperBufferMixin extends MEPatternBufferPartMachine implements IvBufferAccess {
    @Override public boolean iv$isFoaEnabled() {
        return ((MESuperPatternBufferPartMachine)(Object)this).isFOAModeEnabled();
    }
    protected IvSuperBufferMixin(IMachineBlockEntity holder, int size, IO io) { super(holder, size, io); }
    @Inject(method = "setFOAModeEnabled", at = @At("HEAD"), cancellable = true)
    private void iv$foa(boolean enabled, CallbackInfo ci) {
        if (enabled && IvBuffers.isolated(this)) {
            iv$getState().message = "gtl_enhancedcore.diagnostic.iv_foa";
            IvTaskLog.event(this, null, "ENABLE_FOA", "REJECTED", "reason", iv$getState().message);
            ci.cancel();
        }
    }
    /** Both sides use the same widget tree and preserve name ownership in packets. */
    @Inject(method = "createUIWidget", at = @At("RETURN"), remap = false)
    private void iv$translateCustomName(org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<com.lowdragmc.lowdraglib.gui.widget.Widget> cir) {
        var widget = cir.getReturnValue();
        IvBufferUi.installNameControls(this, widget);
    }
}
