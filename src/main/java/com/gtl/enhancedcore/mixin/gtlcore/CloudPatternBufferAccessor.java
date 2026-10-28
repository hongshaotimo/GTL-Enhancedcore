package com.gtl.enhancedcore.mixin.gtlcore;

import com.lowdragmc.lowdraglib.misc.ItemStackTransfer;
import com.lowdragmc.lowdraglib.misc.FluidTransferList;
import org.gtlcore.gtlcore.common.machine.multiblock.part.ae.MEPatternBufferPartMachine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Read-only access to physical per-pattern catalysts and the upstream cache setting. */
@Mixin(value = MEPatternBufferPartMachine.class, remap = false)
public interface CloudPatternBufferAccessor {
    @Accessor(value = "catalystItems", remap = false) ItemStackTransfer[] cloud$catalystItems();
    @Accessor(value = "catalystFluids", remap = false) FluidTransferList[] cloud$catalystFluids();
    @Accessor(value = "cacheRecipeCount", remap = false) byte[] cloud$cacheRecipeCount();
}
