package com.gtl.enhancedcore.mixin.gtceu;

import com.gregtechceu.gtceu.api.machine.multiblock.PartAbility;
import com.gtl.enhancedcore.common.registration.RetiredMachines;
import net.minecraft.world.level.block.Block;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Retired interface shells must never be selectable as structural parts. */
@Mixin(value = PartAbility.class, remap = false)
public abstract class RetiredMachinePartAbilityMixin {
    @Inject(method = "register", at = @At("HEAD"), cancellable = true, require = 1)
    private void retired$noAbility(int tier, Block block, CallbackInfo ci) {
        // Definition ID is already available before the block registry callback.
        if (block instanceof com.gregtechceu.gtceu.api.block.IMachineBlock machine
                && RetiredMachines.contains(machine.getDefinition().getId().toString())) ci.cancel();
    }
}
