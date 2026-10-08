package com.gtl.enhancedcore.common.recipe.iv;

public interface IvBufferMethods {
    void iv$refreshPatterns();
    void iv$patternChanged(int slot);
    appeng.api.crafting.IPatternDetails iv$realPattern(int slot, net.minecraft.world.item.ItemStack stack);
}
