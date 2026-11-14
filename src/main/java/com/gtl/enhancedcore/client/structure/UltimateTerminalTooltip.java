package com.gtl.enhancedcore.client.structure;

import com.gtl.enhancedcore.GTLEnhancedcore;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

@Mod.EventBusSubscriber(modid = GTLEnhancedcore.MOD_ID, value = Dist.CLIENT)
public final class UltimateTerminalTooltip {
    private UltimateTerminalTooltip() {}

    @SubscribeEvent
    public static void tooltip(ItemTooltipEvent event) {
        var id = ForgeRegistries.ITEMS.getKey(event.getItemStack().getItem());
        if (id == null || !id.toString().equals("gtlcore:ultimate_terminal")) return;
        var lines = event.getToolTip();
        for (int i = 0; i < lines.size(); i++) {
            var line = lines.get(i);
            if (!(line.getContents() instanceof TranslatableContents contents)) continue;
            String key = switch (contents.getKey()) {
                case "gtlcore.item.ultimate_terminal.tooltip.0" -> "gtl_enhancedcore.ultimate_terminal.tooltip.build";
                case "gtlcore.item.ultimate_terminal.tooltip.1" -> "gtl_enhancedcore.ultimate_terminal.tooltip.detect";
                default -> null;
            };
            if (key == null) continue;
            var replacement = Component.translatable(key, contents.getArgs()).setStyle(line.getStyle());
            line.getSiblings().forEach(sibling -> replacement.append(sibling.copy()));
            lines.set(i, replacement);
        }
    }
}
