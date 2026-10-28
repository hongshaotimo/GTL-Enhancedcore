package com.gtl.enhancedcore.client;

import com.gtl.enhancedcore.GTLEnhancedcore;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;

/** The wireless bottle toggles once per press, including a long held use key. */
@Mod.EventBusSubscriber(modid = GTLEnhancedcore.MOD_ID, value = Dist.CLIENT)
public final class WirelessTimeAccelerationInput {
    private static final ResourceLocation ITEM = new ResourceLocation("kubejs", "time_twister_wireless");
    private static boolean pressed;
    private WirelessTimeAccelerationInput() {}

    @SubscribeEvent
    public static void use(InputEvent.InteractionKeyMappingTriggered event) {
        Minecraft client = Minecraft.getInstance();
        if (!event.isUseItem() || client.player == null
                || !ITEM.equals(ForgeRegistries.ITEMS.getKey(client.player.getItemInHand(event.getHand()).getItem()))) return;
        if (pressed) {
            event.setCanceled(true);
            event.setSwingHand(false);
        } else pressed = true;
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || !client.options.keyUse.isDown()) pressed = false;
    }

    @SubscribeEvent
    public static void tooltip(ItemTooltipEvent event) {
        if (!ITEM.equals(ForgeRegistries.ITEMS.getKey(event.getItemStack().getItem()))) return;
        event.getToolTip().add(Component.translatable("gtl_enhancedcore.time_acceleration.toggle")
                .withStyle(ChatFormatting.GRAY));
        event.getToolTip().add(Component.translatable("gtl_enhancedcore.time_acceleration.single_target")
                .withStyle(ChatFormatting.YELLOW));
        event.getToolTip().add(Component.translatable("gtl_enhancedcore.time_acceleration.iv_cost")
                .withStyle(ChatFormatting.YELLOW));
    }
}
