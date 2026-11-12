package com.gtl.enhancedcore.mixin.gtlcore;

import com.lowdragmc.lowdraglib.misc.FluidTransferList;
import com.lowdragmc.lowdraglib.misc.ItemStackTransfer;
import org.gtlcore.gtlcore.common.machine.multiblock.part.ae.MEPatternBufferPartMachine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Checks the empty destination slots that GTLCore's paste would overwrite. */
@Mixin(value = MEPatternBufferPartMachine.class, remap = false)
public interface PatternCutBufferAccess {
    @Accessor("catalystItems") ItemStackTransfer[] enhancedcore$cutItems();
    @Accessor("catalystFluids") FluidTransferList[] enhancedcore$cutFluids();
    @Accessor("cacheRecipeCount") byte[] enhancedcore$cutCacheCounts();
    @Accessor("keepByProduct") boolean enhancedcore$keepByProduct();
    @Accessor("keepByProduct") void enhancedcore$keepByProduct(boolean value);
    @Invoker("refreshAllByProduct") void enhancedcore$refreshCutByProduct();
}
