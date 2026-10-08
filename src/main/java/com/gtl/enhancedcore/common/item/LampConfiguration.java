package com.gtl.enhancedcore.common.item;

import com.gregtechceu.gtceu.api.item.LampBlockItem;
import com.gregtechceu.gtceu.common.block.LampBlock;
import net.minecraft.world.item.ItemStack;

/** JEI identity follows LampBlockItem's placement semantics, not unrelated item metadata. */
public final class LampConfiguration {
    private LampConfiguration() {}

    public static String subtype(ItemStack stack) {
        if (!(stack.getItem() instanceof LampBlockItem lamp)) return "";
        boolean inverted, light, bloom;
        if (stack.hasTag()) {
            var tag = stack.getTag();
            inverted = LampBlock.isInverted(tag);
            light = LampBlock.isLightEnabled(tag);
            bloom = LampBlock.isBloomEnabled(tag);
        } else {
            var state = lamp.getBlock().defaultBlockState();
            inverted = LampBlock.isInverted(state);
            light = LampBlock.isLightEnabled(state);
            bloom = LampBlock.isBloomEnabled(state);
        }
        return Integer.toString((inverted ? 4 : 0) | (light ? 2 : 0) | (bloom ? 1 : 0));
    }
}
