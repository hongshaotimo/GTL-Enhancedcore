package com.gtl.enhancedcore.mixin.gtceu;

import com.gregtechceu.gtceu.integration.ae2.gui.widget.ConfigWidget;
import com.lowdragmc.lowdraglib.gui.widget.TextFieldWidget;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Preserves long amount validation when GTLCore's GUI mixin is client-only. */
@Mixin(targets = "com.gregtechceu.gtceu.integration.ae2.gui.widget.AmountSetWidget", remap = false)
public abstract class ServerAmountSetWidgetMixin {
    @Shadow(remap = false)
    @Final
    private TextFieldWidget amountText;

    @Inject(method = "<init>(IILcom/gregtechceu/gtceu/integration/ae2/gui/widget/ConfigWidget;)V",
            at = @At("TAIL"), require = 1, allow = 1, remap = false)
    private void gtlEnhancedcore$preserveLongAmountValidation(int x, int y, ConfigWidget parent, CallbackInfo ci) {
        amountText.setNumbersOnly(0L, Long.MAX_VALUE).setMaxStringLength(19);
    }
}
