package com.gtl.enhancedcore.integration.jade;

import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;
import com.gtl.enhancedcore.common.cloud.CloudPatternBufferMachine;
import com.gtl.enhancedcore.common.cloud.CloudPatternLibraryMachine;
import com.gtl.enhancedcore.common.cloud.store.CloudPatternStore;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

/** Compact authority snapshot; Jade never reads a client-side copy of the pattern catalogue. */
public final class CloudPatternInfoProvider implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {
    private static final String KEY = "gtlEnhancedcoreCloudPattern";
    @Override public ResourceLocation getUid() { return new ResourceLocation("gtl_enhancedcore", "cloud_pattern_buffer"); }
    @Override public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        data.remove(KEY);
        if (accessor.getBlockEntity() instanceof MetaMachineBlockEntity entity
                && entity.getMetaMachine() instanceof CloudPatternBufferMachine machine) {
            CompoundTag cloud = new CompoundTag();
            cloud.putString("mode", machine.cloudModeLabel());
            cloud.putInt("matched", machine.cloudMatchedCount());
            data.put(KEY, cloud);
        } else if (accessor.getBlockEntity() instanceof MetaMachineBlockEntity entity
                && entity.getMetaMachine() instanceof CloudPatternLibraryMachine main && main.libraryId() != null) {
            CompoundTag cloud = new CompoundTag();
            cloud.putInt("stored", CloudPatternStore.get(main.serverLevel()).library(main.libraryId()).storedCount());
            data.put(KEY, cloud);
        }
    }
    @Override public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        if (!accessor.getServerData().contains(KEY, Tag.TAG_COMPOUND)) return;
        CompoundTag cloud = accessor.getServerData().getCompound(KEY);
        if (cloud.contains("stored")) {
            tooltip.add(Component.translatable("gtl_enhancedcore.cloud.jade.stored", cloud.getInt("stored")).withStyle(ChatFormatting.AQUA)); return;
        }
        if (cloud.getInt("matched") < 0) return;
        String mode = cloud.getString("mode");
        ResourceLocation type = ResourceLocation.tryParse(mode);
        if (type != null) mode = type.getNamespace() + "." + type.getPath();
        Component modeLabel = mode.isEmpty() ? Component.translatable("gtl_enhancedcore.cloud.ui.none")
                : Component.translatable(mode);
        tooltip.add(Component.translatable("gtl_enhancedcore.cloud.jade.mode", modeLabel).withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable("gtl_enhancedcore.cloud.ui.matched", cloud.getInt("matched")));

    }
}
