package com.gtl.enhancedcore.mixin.gtladditions.client;

import com.lowdragmc.lowdraglib.gui.widget.Widget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Mixin(targets = "com.gtladd.gtladditions.api.machine.gui.StructureTierPage$TierScreenWidget", remap = false)
public abstract class CreativeStructureTierLabelMixin {
    @ModifyConstant(method = "drawBottomActions", constant =
            @Constant(stringValue = "gtladditions.structure_tier.button.unlock"), require = 1)
    private String enhancedcore$creativeLabel(String inventoryLabel) {
        return ((Widget) (Object) this).getGui().entityPlayer.isCreative()
                ? "gtl_enhancedcore.structure_tier.button.unlock_creative"
                : inventoryLabel;
    }
}
