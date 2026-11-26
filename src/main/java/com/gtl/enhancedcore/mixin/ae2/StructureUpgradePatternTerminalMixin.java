package com.gtl.enhancedcore.mixin.ae2;

import appeng.api.crafting.IPatternDetails;
import appeng.crafting.pattern.ProcessingPatternItem;
import appeng.parts.encoding.PatternEncodingLogic;
import com.gtl.enhancedcore.integration.ae2.StructureUpgradePatternMarker;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = PatternEncodingLogic.class, remap = false)
public abstract class StructureUpgradePatternTerminalMixin {
    @WrapOperation(method = "loadEncodedPattern", at = @At(value = "INVOKE", target =
            "Lappeng/api/crafting/PatternDetailsHelper;decodePattern(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/level/Level;)Lappeng/api/crafting/IPatternDetails;"), require = 1)
    private IPatternDetails gtlEnhancedcore$readMaterialList(ItemStack stack, Level level,
                                                           Operation<IPatternDetails> original) {
        IPatternDetails details = original.call(stack, level);
        if (details == null && level != null && stack.getItem() instanceof ProcessingPatternItem processing) {
            var materialList = processing.decode(stack, level, false);
            if (StructureUpgradePatternMarker.isMaterialList(materialList)) return materialList;
        }
        return details;
    }
}
