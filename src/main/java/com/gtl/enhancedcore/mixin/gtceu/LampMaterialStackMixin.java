package com.gtl.enhancedcore.mixin.gtceu;

import com.gregtechceu.gtceu.common.block.LampBlock;
import com.gtl.enhancedcore.integration.terminal.LampPlacement;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;

/** GT/GTL material lists and hover picking call this legacy overload, not Forge's pick hook. */
@Mixin(value = LampBlock.class, remap = false)
public abstract class LampMaterialStackMixin extends Block {
    protected LampMaterialStackMixin(Properties properties) {
        super(properties);
    }

    @Override
    public ItemStack getCloneItemStack(BlockGetter level, BlockPos pos, BlockState state) {
        return LampPlacement.returnedStack(state);
    }
}
