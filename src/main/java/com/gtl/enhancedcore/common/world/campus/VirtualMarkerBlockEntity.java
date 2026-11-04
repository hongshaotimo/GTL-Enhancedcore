package com.gtl.enhancedcore.common.world.campus;

import com.gtl.enhancedcore.GTLEnhancedcore;
import com.gtl.enhancedcore.common.world.plot.PlannedCampus;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/** Persisted and chunk-synchronised plan metadata; an empty id denotes a hand-placed marker. */
public final class VirtualMarkerBlockEntity extends BlockEntity {
    private String machineId = "";
    private String displayNameKey = "";
    private int slotIndex = -1;
    private int width = 1;
    private int height = 1;
    private int depth = 1;

    public VirtualMarkerBlockEntity(BlockEntityType<?> type, BlockPos position, BlockState state) {
        super(type, position, state);
    }

    /** Full pending BE NBT for a ProtoChunk; does not create or change a live world entity. */
    public static CompoundTag initialTag(BlockPos position, PlannedCampus.Plot plot) {
        CompoundTag tag = new CompoundTag();
        tag.putString("id", GTLEnhancedcore.id("virtual_marker").toString());
        tag.putInt("x", position.getX());
        tag.putInt("y", position.getY());
        tag.putInt("z", position.getZ());
        tag.putString("MachineId", plot.id());
        tag.putString("DisplayNameKey", plot.displayNameKey());
        tag.putInt("SlotIndex", plot.slotIndex());
        tag.putIntArray("Size", new int[] {plot.width(), plot.height(), plot.depth()});
        return tag;
    }

    public String machineId() { return machineId; }
    public String displayNameKey() { return displayNameKey; }
    public int slotIndex() { return slotIndex; }
    public int width() { return width; }
    public int height() { return height; }
    public int depth() { return depth; }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        machineId = tag.getString("MachineId");
        displayNameKey = tag.getString("DisplayNameKey");
        slotIndex = tag.contains("SlotIndex") ? tag.getInt("SlotIndex") : -1;
        int[] size = tag.getIntArray("Size");
        width = size.length == 3 ? size[0] : 1;
        height = size.length == 3 ? size[1] : 1;
        depth = size.length == 3 ? size[2] : 1;
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putString("MachineId", machineId);
        tag.putString("DisplayNameKey", displayNameKey);
        tag.putInt("SlotIndex", slotIndex);
        tag.putIntArray("Size", new int[] {width, height, depth});
    }

    @Override
    public CompoundTag getUpdateTag() { return saveWithoutMetadata(); }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
