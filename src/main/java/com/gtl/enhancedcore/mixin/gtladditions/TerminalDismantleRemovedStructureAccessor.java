package com.gtl.enhancedcore.mixin.gtladditions;

import com.gtladd.gtladditions.common.data.RemovedStructure;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = RemovedStructure.class, remap = false)
public interface TerminalDismantleRemovedStructureAccessor {
    @Accessor("blocks")
    Long2ObjectMap<BlockState> enhancedcore$capturedBlocks();
}
