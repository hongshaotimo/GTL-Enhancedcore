package com.gtl.enhancedcore.mixin.gtladditions;

import com.gtladd.gtladditions.common.data.RecursiveReverseBuffState;
import com.gtladd.gtladditions.common.machine.multiblock.controller.ForgeOfTheAntichrist;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(value = ForgeOfTheAntichrist.class, remap = false)
public interface IvNativeForgeStateAccessor {
    @Invoker("getRecursiveReverseBuffState") RecursiveReverseBuffState iv$recursiveReverseBuffState();
}
