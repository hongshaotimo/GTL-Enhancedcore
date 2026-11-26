package com.gtl.enhancedcore.integration.ae2;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.crafting.pattern.AEProcessingPattern;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

public final class StructureUpgradePatternMarker {
    private static final String MARKER = "gtl_enhancedcore:structure_upgrade_material_list";

    private StructureUpgradePatternMarker() {}

    public static ItemStack mark(ItemStack output) {
        output.getOrCreateTagElement(MARKER);
        return output;
    }

    public static boolean isMaterialList(IPatternDetails details) {
        if (!(details instanceof AEProcessingPattern pattern)) return false;
        for (GenericStack output : pattern.getSparseOutputs()) {
            if (output != null && output.what() instanceof AEItemKey item) {
                var tag = item.getTag();
                if (tag != null && tag.contains(MARKER, Tag.TAG_COMPOUND)) return true;
            }
        }
        return false;
    }
}
