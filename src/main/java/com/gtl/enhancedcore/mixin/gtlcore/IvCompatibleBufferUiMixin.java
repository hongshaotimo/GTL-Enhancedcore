package com.gtl.enhancedcore.mixin.gtlcore;

import com.gtl.enhancedcore.common.recipe.iv.IvBufferUi;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import org.gtlcore.gtlcore.common.machine.multiblock.part.ae.MEPatternBufferPartMachine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = MEPatternBufferPartMachine.class, remap = false)
public abstract class IvCompatibleBufferUiMixin {
    @Inject(method = "createUIWidget", at = @At("RETURN"))
    private void iv$nameControls(CallbackInfoReturnable<Widget> cir) {
        IvBufferUi.installNameControls((MEPatternBufferPartMachine)(Object)this, cir.getReturnValue());
    }
}
