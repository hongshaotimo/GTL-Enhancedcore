package com.gtl.enhancedcore.mixin.gtceu;

import com.gregtechceu.gtceu.api.pattern.BlockPattern;
import com.gregtechceu.gtceu.api.pattern.MultiblockState;
import com.gtl.enhancedcore.common.performance.structure.StructureSearchScheduler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = BlockPattern.class, remap = false, priority = 900)
public abstract class StructureSearchPatternMixin {
    @Inject(method = "checkPatternAt(Lcom/gregtechceu/gtceu/api/pattern/MultiblockState;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Direction;Lnet/minecraft/core/Direction;ZZ)Z",
            at = @At("HEAD"), require = 1)
    private void enhancedcore$watchSearch(MultiblockState state, BlockPos origin, Direction facing,
            Direction upwards, boolean flipped, boolean savePredicate, CallbackInfoReturnable<Boolean> cir) {
        StructureSearchScheduler.observe((BlockPattern) (Object) this, state, origin, facing, upwards, flipped);
    }
}
