package com.gtl.enhancedcore.common.world.campus;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Portable campus-bound terminal; its loot table preserves only the bound dimension. */
public final class CentralTeleporterBlock extends BaseEntityBlock {
    private static final VoxelShape SHAPE = Block.box(1, 0, 1, 15, 6, 15);

    public CentralTeleporterBlock(Properties properties) { super(properties); }

    @Override
    public RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }

    @Override
    public void appendHoverText(ItemStack stack, BlockGetter level, List<Component> lines, TooltipFlag flag) {
        super.appendHoverText(stack, level, lines, flag);
        lines.add(Component.translatable("gtl_enhancedcore.central_teleporter.tips").withStyle(ChatFormatting.DARK_AQUA));
        lines.add(Component.translatable("gtl_enhancedcore.central_teleporter.range").withStyle(ChatFormatting.WHITE));
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos position, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos position, BlockState state) {
        return CampusDeviceBlocks.CENTRAL_TELEPORTER_BLOCK_ENTITY.create(position, state);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos position, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, position, state, placer, stack);
        // BlockItem has restored BlockEntityTag before this callback. Existing bindings must win.
        if (level.getBlockEntity(position) instanceof CentralTeleporterBlockEntity terminal)
            terminal.bindToCurrentCampusIfUnbound();
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos position, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        if (player instanceof ServerPlayer serverPlayer) CampusTeleportNetwork.open(serverPlayer, position);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
