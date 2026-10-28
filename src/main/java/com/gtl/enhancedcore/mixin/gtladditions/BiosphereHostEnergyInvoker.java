package com.gtl.enhancedcore.mixin.gtladditions;

import com.gtladd.gtladditions.common.machine.multiblock.controller.bs.BiosphereIIIController;
import java.math.BigInteger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** ADD records signed consumption; a negative amount reverses the captured host's tick statistic. */
@Mixin(value = BiosphereIIIController.class, remap = false)
public interface BiosphereHostEnergyInvoker {
    @Invoker("recordEnergyConsumption") void cloud$recordEnergyConsumption(BigInteger amount);
}
