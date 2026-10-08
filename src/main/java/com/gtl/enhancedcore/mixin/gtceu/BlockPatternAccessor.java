package com.gtl.enhancedcore.mixin.gtceu;

import com.gregtechceu.gtceu.api.pattern.BlockPattern;
import com.gregtechceu.gtceu.api.pattern.TraceabilityPredicate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * 暴露 BlockPattern 内部 per-position 谓词数组（protected blockMatches）。
 */
@Mixin(value = BlockPattern.class, remap = false)
public interface BlockPatternAccessor {

    @Accessor("blockMatches")
    TraceabilityPredicate[][][] gtlEnhancedcore$getBlockMatches();

    @Accessor("centerOffset")
    int[] enhanced$getCenterOffset();
}
