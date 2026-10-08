package com.gtl.enhancedcore.integration.jade;

import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;
import com.gtl.enhancedcore.GTLEnhancedcore;
import com.gtl.enhancedcore.common.recipe.MachineDiagnostics;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.*;
import snownee.jade.api.config.IPluginConfig;

public final class MachineDiagnosticProvider implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {
    private static final String KEY = "enhancedMachineDiagnostic";
    private static final String DETAILS_KEY = "enhancedMachineDiagnosticDetails";

    @Override public ResourceLocation getUid() { return GTLEnhancedcore.id("machine_diagnostic"); }

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        if (accessor.getBlockEntity() instanceof MetaMachineBlockEntity entity) {
            var reasons = new ListTag();
            for (var reason : MachineDiagnostics.currentDetails(entity.getMetaMachine()))
                reasons.add(StringTag.valueOf(Component.Serializer.toJson(reason)));
            data.remove(KEY);
            data.remove(DETAILS_KEY);
            if (!reasons.isEmpty()) {
                data.putString(KEY, reasons.getString(0));
                data.put(DETAILS_KEY, reasons);
            }
        }
    }

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        var data = accessor.getServerData();
        if (!data.contains(DETAILS_KEY, Tag.TAG_LIST) && data.contains(KEY, Tag.TAG_STRING)) {
            Component reason = MachineDiagnostics.decode(data.getString(KEY));
            if (reason != null) tooltip.add(reason.copy().withStyle(ChatFormatting.YELLOW));
            return;
        }
        var reasons = data.getList(DETAILS_KEY, Tag.TAG_STRING);
        for (int index = 0; index < Math.min(MachineDiagnostics.MAX_DETAILS, reasons.size()); index++) {
            Component reason = MachineDiagnostics.decode(reasons.getString(index));
            if (reason != null) tooltip.add(reason.copy().withStyle(ChatFormatting.YELLOW));
        }
    }
}
