package com.gtl.enhancedcore.integration.terminal;

import com.gregtechceu.gtceu.api.pattern.TraceabilityPredicate;
import com.gregtechceu.gtceu.common.block.LampBlock;
import com.lowdragmc.lowdraglib.utils.BlockInfo;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.gtlcore.gtlcore.integration.terminal.StableBlockCandidates;

/** Preserve designed lamp options in both the preview and the terminal's material candidate. */
public final class LampPlacement {
    private LampPlacement() {}

    public static TraceabilityPredicate invertedStructureLamp(Block block) {
        var state = block.defaultBlockState().setValue(LampBlock.INVERTED, true)
                .setValue(LampBlock.LIGHT, true).setValue(LampBlock.BLOOM, true);
        // Do not invalidate old machines or change redstone-sensitive formation rules.
        return new TraceabilityPredicate(world -> world.getBlockState().is(block),
                StableBlockCandidates.mark(() -> new BlockInfo[]{info(state)}));
    }

    public static BlockInfo info(BlockState state) {
        return new BlockInfo(state, false, returnedStack(state), null);
    }

    public static ItemStack returnedStack(BlockState state) {
        ItemStack stack = new ItemStack(state.getBlock().asItem());
        if (state.getBlock() instanceof LampBlock lamp) stack.setTag(lamp.getTagFromState(state));
        return stack;
    }
}
