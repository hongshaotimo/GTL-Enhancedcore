package com.gtl.enhancedcore.mixin.gtlcore;

import org.gtlcore.gtlcore.api.machine.computation.ComputationScheduler;
import org.gtlcore.gtlcore.api.machine.computation.ComputationSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.List;

@Mixin(value = ComputationScheduler.Transaction.class, remap = false)
public interface CreativeComputationTransactionAccessor {
    @Invoker("<init>")
    static ComputationScheduler.Transaction enhanced$create(long amount) {
        throw new AssertionError("Mixin constructor invoker");
    }

    @Accessor("receipts")
    List<ComputationSource.Receipt> enhanced$receipts();
}
