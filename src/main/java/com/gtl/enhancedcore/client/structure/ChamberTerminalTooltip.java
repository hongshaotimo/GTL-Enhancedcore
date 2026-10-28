package com.gtl.enhancedcore.client.structure;

import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

@Mod.EventBusSubscriber(modid="gtl_enhancedcore",value=Dist.CLIENT)
public final class ChamberTerminalTooltip {
    private ChamberTerminalTooltip(){}
    @SubscribeEvent public static void tooltip(ItemTooltipEvent event){
        var id=ForgeRegistries.ITEMS.getKey(event.getItemStack().getItem());
        if(id!=null&&id.toString().equals("gtlcore:ultimate_terminal")) {
            event.getToolTip().add(Component.translatable("gtl_enhancedcore.chambers.tooltip").withStyle(net.minecraft.ChatFormatting.GRAY));
            event.getToolTip().add(Component.translatable("gtl_enhancedcore.chambers.tooltip_duration").withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
        }
    }
}
