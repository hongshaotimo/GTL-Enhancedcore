package com.gtl.enhancedcore.integration.terminal;

import com.gregtechceu.gtceu.api.item.LampBlockItem;
import net.minecraft.world.item.ItemStack;
import java.util.ArrayList;
import java.util.List;

/** Accept the eight native lamp configurations without changing global item/NBT equality. */
public final class LampBuildCandidates {
    private LampBuildCandidates() {}

    public static List<ItemStack> expand(List<ItemStack> candidates) {
        if (candidates == null || candidates.stream().noneMatch(LampBuildCandidates::isUnconfiguredLamp)) {
            return candidates;
        }
        List<ItemStack> expanded = new ArrayList<>();
        for (ItemStack candidate : candidates) {
            expanded.add(candidate);
            if (isUnconfiguredLamp(candidate)) {
                LampBlockItem lamp = (LampBlockItem) candidate.getItem();
                for (int variant = 0; variant < 8; variant++) {
                    expanded.add(lamp.getBlock().getStackFromIndex(variant));
                }
            }
        }
        return expanded;
    }

    private static boolean isUnconfiguredLamp(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof LampBlockItem && !stack.hasTag();
    }
}
