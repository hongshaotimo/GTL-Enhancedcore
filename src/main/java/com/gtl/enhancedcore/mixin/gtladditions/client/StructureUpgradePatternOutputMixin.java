package com.gtl.enhancedcore.mixin.gtladditions.client;

import com.gtl.enhancedcore.integration.ae2.StructureUpgradePatternMarker;
import com.gtladd.gtladditions.integration.xei.structureupgrade.StructureUpgradeEntry;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = StructureUpgradeEntry.class, remap = false)
public abstract class StructureUpgradePatternOutputMixin {
    @ModifyReturnValue(method = "patternOutput", at = @At("RETURN"), require = 1)
    private ItemStack gtlEnhancedcore$markMaterialList(ItemStack output) {
        return StructureUpgradePatternMarker.mark(output);
    }
}
