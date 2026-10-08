package com.gtl.enhancedcore.mixin.gtceu;

import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.pattern.BlockPattern;
import java.util.function.Supplier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * 暴露 MultiblockMachineDefinition 的 patternFactory 字段（private Supplier）。
 */
@Mixin(value = MultiblockMachineDefinition.class, remap = false)
public interface MultiblockMachineDefinitionAccessor {

    @Accessor("patternFactory")
    void gtlEnhancedcore$setPatternFactory(Supplier<BlockPattern> patternFactory);
}
