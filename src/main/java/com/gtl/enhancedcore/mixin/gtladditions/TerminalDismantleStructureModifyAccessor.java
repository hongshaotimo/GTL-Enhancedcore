package com.gtl.enhancedcore.mixin.gtladditions;

import com.gtladd.gtladditions.common.data.RemovedStructure;
import com.gtladd.gtladditions.common.machine.trait.StructureModifyTrait;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = StructureModifyTrait.class, remap = false)
public interface TerminalDismantleStructureModifyAccessor {
    @Accessor("removedStructure")
    RemovedStructure enhancedcore$removedStructure();
}
