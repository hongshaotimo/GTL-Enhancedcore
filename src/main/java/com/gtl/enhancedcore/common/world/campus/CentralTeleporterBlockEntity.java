package com.gtl.enhancedcore.common.world.campus;

import com.gtl.enhancedcore.common.world.plot.PlotChunkGenerator;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/** A terminal receives its first binding only inside a real campus dimension. */
public final class CentralTeleporterBlockEntity extends BlockEntity {
    @Nullable
    private ResourceLocation boundDimension;

    public CentralTeleporterBlockEntity(BlockEntityType<?> type, BlockPos position, BlockState state) {
        super(type, position, state);
    }

    @Nullable
    public ResourceLocation boundDimension() { return boundDimension; }

    @Override
    public void onLoad() {
        super.onLoad();
        bindToCurrentCampusIfUnbound();
    }

    void bindToCurrentCampusIfUnbound() {
        if (boundDimension != null || !(level instanceof ServerLevel serverLevel)
                || !(serverLevel.getChunkSource().getGenerator() instanceof PlotChunkGenerator generator)
                || !generator.settings().isCampus()) return;
        boundDimension = serverLevel.dimension().location();
        setChanged();
        serverLevel.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        String savedDimension = tag.getString("BoundDimension");
        boundDimension = savedDimension.isEmpty() ? null : ResourceLocation.tryParse(savedDimension);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (boundDimension != null) tag.putString("BoundDimension", boundDimension.toString());
    }

    @Override
    public CompoundTag getUpdateTag() { return saveWithoutMetadata(); }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
