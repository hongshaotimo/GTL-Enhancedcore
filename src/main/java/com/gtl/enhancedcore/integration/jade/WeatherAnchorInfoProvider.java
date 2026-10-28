package com.gtl.enhancedcore.integration.jade;

import com.gregtechceu.gtceu.api.block.MetaMachineBlock;
import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;
import com.gtl.enhancedcore.GTLEnhancedcore;
import com.gtl.enhancedcore.common.machine.WeatherAnchorMachine;
import com.gtl.enhancedcore.common.machine.WeatherAnchorModes;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

/** Reports the anchor's world effect; its empty recipe map is only a mode selector. */
public final class WeatherAnchorInfoProvider implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {
    private static final ResourceLocation UID = GTLEnhancedcore.id("weather_anchor_info");
    private static final String MODES = "gtlEnhancedcoreWeatherAnchorModes";
    private static final String FORMED = "gtlEnhancedcoreWeatherAnchorFormed";
    private static final String ENABLED = "gtlEnhancedcoreWeatherAnchorEnabled";
    private static final String ACTIVE = "gtlEnhancedcoreWeatherAnchorActive";

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        if (accessor.getBlockEntity() instanceof MetaMachineBlockEntity entity
                && entity.getMetaMachine() instanceof WeatherAnchorMachine anchor) {
            data.putInt(MODES, anchor.getSelectedModes());
            data.putBoolean(FORMED, anchor.isFormed());
            data.putBoolean(ENABLED, anchor.getRecipeLogic().isWorkingEnabled());
            data.putBoolean(ACTIVE, anchor.isAnchoring());
        }
    }

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        CompoundTag data = accessor.getServerData();
        if (!data.contains(MODES)) return;
        int selected = data.getInt(MODES);
        if (!data.getBoolean(FORMED)) {
            tooltip.add(Component.translatable("gtceu.multiblock.invalid_structure").withStyle(ChatFormatting.RED));
        } else if (!data.getBoolean(ENABLED)) {
            tooltip.add(Component.translatable("gtl_enhancedcore.diagnostic.paused").withStyle(ChatFormatting.RED));
        } else if (data.getBoolean(ACTIVE)) {
            tooltip.add(Component.translatable("gtl_enhancedcore.weather_anchor.jade_active").withStyle(ChatFormatting.GREEN));
        } else if (selected != 0) {
            tooltip.add(Component.translatable("gtl_enhancedcore.weather_anchor.jade_inactive").withStyle(ChatFormatting.YELLOW));
        } else {
            tooltip.add(Component.translatable("gtl_enhancedcore.weather_anchor.jade_no_target").withStyle(ChatFormatting.YELLOW));
        }
        if (selected != 0) {
            tooltip.add(Component.translatable("gtl_enhancedcore.weather_anchor.jade_selected",
                    WeatherAnchorModes.describe(selected)).withStyle(ChatFormatting.GRAY));
        }
    }

    @Override public ResourceLocation getUid() { return UID; }
}
