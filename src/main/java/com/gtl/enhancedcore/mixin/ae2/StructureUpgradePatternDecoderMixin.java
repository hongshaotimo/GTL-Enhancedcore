package com.gtl.enhancedcore.mixin.ae2;

import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import com.gtl.enhancedcore.integration.ae2.StructureUpgradePatternMarker;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = PatternDetailsHelper.class, remap = false)
public abstract class StructureUpgradePatternDecoderMixin {
    @ModifyReturnValue(method = {
            "decodePattern(Lappeng/api/stacks/AEItemKey;Lnet/minecraft/world/level/Level;)Lappeng/api/crafting/IPatternDetails;",
            "decodePattern(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/level/Level;Z)Lappeng/api/crafting/IPatternDetails;"
    }, at = @At("RETURN"), require = 2)
    private static IPatternDetails gtlEnhancedcore$excludeMaterialLists(IPatternDetails details) {
        return StructureUpgradePatternMarker.isMaterialList(details) ? null : details;
    }
}
