package com.gtl.enhancedcore.mixin.gtceu;

import com.gregtechceu.gtceu.api.capability.IOpticalComputationHatch;
import com.gregtechceu.gtceu.api.capability.IOpticalComputationProvider;
import com.gregtechceu.gtceu.api.machine.trait.NotifiableComputationContainer;
import com.gtl.enhancedcore.common.machine.hatch.CreativeComputationReceiverHatchMachine;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Collection;
import java.util.Set;

/** Saturate the network switch's int CWU sum when creative receivers overflow it. */
@Mixin(targets = "com.gregtechceu.gtceu.common.machine.multiblock.electric.research.NetworkSwitchMachine$MultipleComputationHandler",
        remap = false)
public abstract class CreativeComputationNetworkSwitchMixin {

    @Shadow @Final private Set<IOpticalComputationHatch> providers;

    @Inject(method = "getMaxCWUt(Ljava/util/Collection;)I", at = @At("RETURN"), cancellable = true, remap = false)
    private void enhanced$saturateMaxCWUt(Collection<IOpticalComputationProvider> seen,
                                         CallbackInfoReturnable<Integer> cir) {
        enhanced$saturateCreativeOverflow(cir);
    }

    @Inject(method = "getMaxCWUtForDisplay()I", at = @At("RETURN"), cancellable = true, remap = false)
    private void enhanced$saturateDisplayCWUt(CallbackInfoReturnable<Integer> cir) {
        enhanced$saturateCreativeOverflow(cir);
    }

    @Unique
    private void enhanced$saturateCreativeOverflow(CallbackInfoReturnable<Integer> cir) {
        if (cir.getReturnValueI() >= 0) return;
        for (IOpticalComputationHatch provider : providers) {
            if (provider instanceof NotifiableComputationContainer container
                    && container.getMachine() instanceof CreativeComputationReceiverHatchMachine) {
                cir.setReturnValue(Integer.MAX_VALUE);
                return;
            }
        }
    }
}
