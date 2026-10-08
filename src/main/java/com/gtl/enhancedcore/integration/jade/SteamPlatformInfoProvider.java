package com.gtl.enhancedcore.integration.jade;

import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;
import com.gtl.enhancedcore.GTLEnhancedcore;
import com.gtl.enhancedcore.common.machine.IndustrialSteamPlatformMachine;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

/**
 * 工业泛用蒸汽机的 Jade 信息：实时蒸汽余量 + 蒸汽不足警告。
 * 与右键 GUI 共用同一组翻译键（gui.gtl_enhancedcore.steam_platform.*）。
 */
public class SteamPlatformInfoProvider implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {

    public static final ResourceLocation UID = GTLEnhancedcore.id("steam_platform_info");

    private static final String KEY_STORED = "gtlEnhancedcoreSteamStored";
    private static final String KEY_CAPACITY = "gtlEnhancedcoreSteamCapacity";
    private static final String KEY_INSUFFICIENT = "gtlEnhancedcoreSteamInsufficient";

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        if (accessor.getBlockEntity() instanceof MetaMachineBlockEntity machineBE
                && machineBE.getMetaMachine() instanceof IndustrialSteamPlatformMachine platform) {
            data.putLong(KEY_STORED, platform.getSteamStoredMb());
            data.putLong(KEY_CAPACITY, platform.getSteamCapacityMb());
            // 判定用实时存量而不是标志位：EU 检测先于 beforeWorking 失败时标志位不会置位，
            // 会导致蒸汽耗尽时 Jade 只显示 GTLCore 的"电力输入不足"而没有蒸汽警告。
            data.putBoolean(KEY_INSUFFICIENT,
                    platform.isFormed() && platform.getSteamStoredMb() < IndustrialSteamPlatformMachine.STEAM_PER_RECIPE_MB);
        }
    }

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        CompoundTag data = accessor.getServerData();
        if (!data.contains(KEY_STORED)) {
            return;
        }
        tooltip.add(Component.translatable("gui.gtl_enhancedcore.steam_platform.steam_stored",
                Component.literal(String.valueOf(data.getLong(KEY_STORED))).withStyle(ChatFormatting.AQUA),
                Component.literal(String.valueOf(data.getLong(KEY_CAPACITY))).withStyle(ChatFormatting.DARK_AQUA))
                .withStyle(ChatFormatting.GRAY));
        if (data.getBoolean(KEY_INSUFFICIENT)) {
            tooltip.add(Component.translatable("gui.gtl_enhancedcore.steam_platform.low_steam")
                    .withStyle(ChatFormatting.RED));
        }
    }

    @Override
    public ResourceLocation getUid() {
        return UID;
    }
}
