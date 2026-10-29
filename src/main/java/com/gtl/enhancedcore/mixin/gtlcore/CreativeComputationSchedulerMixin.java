package com.gtl.enhancedcore.mixin.gtlcore;

import com.gtl.enhancedcore.common.machine.hatch.CreativeComputationReservations;
import org.gtlcore.gtlcore.api.machine.computation.ComputationScheduler;
import org.gtlcore.gtlcore.api.machine.computation.ComputationSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/** Only unlimited creative sources bypass the finite scheduler's shared long reservation pool. */
@Mixin(value = ComputationScheduler.class, remap = false)
public abstract class CreativeComputationSchedulerMixin {
    @Unique
    private final CreativeComputationReservations enhanced$creative = new CreativeComputationReservations();

    @Shadow(remap = false)
    public abstract void forget(Object owner);

    @Inject(method = "advance", at = @At("HEAD"))
    private void enhanced$advance(long tick, CallbackInfo ci) {
        enhanced$creative.advance(tick);
    }

    @Inject(method = "available", at = @At("HEAD"), cancellable = true)
    private void enhanced$available(Object owner, List<ComputationSource> sources, CallbackInfoReturnable<Long> cir) {
        if (CreativeComputationReservations.find(sources) != null) cir.setReturnValue(Long.MAX_VALUE);
    }

    @Inject(method = "offer", at = @At("HEAD"), cancellable = true)
    private void enhanced$offer(Object owner, List<ComputationSource> sources, long minimum, long maximum,
                               CallbackInfoReturnable<Long> cir) {
        var source = CreativeComputationReservations.find(sources);
        if (source == null || minimum <= 0 || maximum < minimum) {
            enhanced$creative.forget(owner);
            return;
        }
        // Release an earlier finite-source grant before changing to creative supply.
        forget(owner);
        enhanced$creative.offer(owner, source, maximum);
        cir.setReturnValue(maximum);
    }

    @Inject(method = "withdraw", at = @At("HEAD"), cancellable = true)
    private void enhanced$withdraw(Object owner, long amount,
                                  CallbackInfoReturnable<ComputationScheduler.Transaction> cir) {
        if (!enhanced$creative.contains(owner)) return;
        var receipt = enhanced$creative.withdraw(owner, amount);
        if (receipt == null) {
            cir.setReturnValue(null);
            return;
        }
        var transaction = CreativeComputationTransactionAccessor.enhanced$create(amount);
        ((CreativeComputationTransactionAccessor) (Object) transaction).enhanced$receipts().add(receipt);
        cir.setReturnValue(transaction);
    }

    @Inject(method = "forget", at = @At("HEAD"))
    private void enhanced$forget(Object owner, CallbackInfo ci) {
        enhanced$creative.forget(owner);
    }

    @Inject(method = "invalidate", at = @At("HEAD"))
    private void enhanced$invalidate(CallbackInfo ci) {
        enhanced$creative.invalidate();
    }
}
