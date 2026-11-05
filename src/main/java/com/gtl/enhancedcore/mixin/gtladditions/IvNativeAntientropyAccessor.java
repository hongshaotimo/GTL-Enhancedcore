package com.gtl.enhancedcore.mixin.gtladditions;

import com.gtladd.gtladditions.common.machine.multiblock.controller.AntientropyCondensationCenter;
import java.math.BigInteger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(value = AntientropyCondensationCenter.class, remap = false)
public interface IvNativeAntientropyAccessor {
    @Invoker("calculateCryotheumDustCost") int iv$cryotheumCost(BigInteger parallels);
    @Accessor("lastCryotheumDustCost") void iv$lastCryotheumCost(int cost);
}
