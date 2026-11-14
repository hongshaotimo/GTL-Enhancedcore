package com.gtl.enhancedcore.mixin.gtceu;

import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.data.RotationState;
import com.gregtechceu.gtceu.api.registry.registrate.MultiblockMachineBuilder;
import com.gtl.enhancedcore.common.config.MachineOverrideConfig;
import com.gtl.enhancedcore.common.structure.StructurePanelAppearance;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = MultiblockMachineBuilder.class, remap = false)
public abstract class StructurePanelAppearanceMixin {
    @Inject(method = "register()Lcom/gregtechceu/gtceu/api/machine/MultiblockMachineDefinition;",
            at = @At("HEAD"), require = 1, allow = 1)
    private void enhancedcore$titanControllerFront(CallbackInfoReturnable<MultiblockMachineDefinition> cir) {
        var builder = (MultiblockMachineBuilder) (Object) this;
        if (!MachineOverrideConfig.current().replaceOriginalStructures()
                || !builder.id.getNamespace().equals("gtladditions")
                || !builder.id.getPath().equals("titan_crip_earthbore")) return;
        // The imported controller sits in a wall; the original cleanroom overlay only has top/bottom faces.
        builder.rotationState(RotationState.NON_Y_AXIS);
        builder.workableCasingRenderer(new ResourceLocation("gtlcore", "block/casings/echo_casing"),
                new ResourceLocation("gtl_enhancedcore", "block/multiblock/titan_crip_earthbore"));
    }

    @Inject(method = "register()Lcom/gregtechceu/gtceu/api/machine/MultiblockMachineDefinition;",
            at = @At("RETURN"), require = 1, allow = 1)
    private void enhancedcore$panelAppearance(CallbackInfoReturnable<MultiblockMachineDefinition> cir) {
        StructurePanelAppearance.configure(cir.getReturnValue());
    }
}
