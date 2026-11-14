package com.gtl.enhancedcore.mixin.gtlcore;

import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController;
import com.gregtechceu.gtceu.api.pattern.BlockPattern;
import com.gregtechceu.gtceu.api.pattern.MultiblockState;
import com.gtl.enhancedcore.common.util.StructureDetectScanContext;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.gtlcore.gtlcore.api.pattern.StructureDetection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = StructureDetection.class, remap = false)
public abstract class StructureDetectNormalModeMixin {

    @ModifyVariable(method = "detect", at = @At("HEAD"), argsOnly = true, ordinal = 0,
            require = 1, allow = 1, remap = false)
    private static boolean gtlEnhancedcore$normalDetectionOnly(boolean ignoredLegacyMode) {
        return false;
    }

    @WrapMethod(method = "check", remap = false)
    private static java.util.List<?> gtlEnhancedcore$scopeNormalScan(IMultiController controller, BlockPattern pattern,
            boolean ignoredLegacyMode, int repeatCount, Operation<java.util.List<?>> original) {
        var scope = StructureDetectScanContext.begin();
        try {
            return scope.finish(original.call(controller, pattern, false, repeatCount));
        } finally {
            scope.close();
        }
    }

    @Redirect(method = "check",
            at = @At(value = "INVOKE", target = "Lcom/gregtechceu/gtceu/api/pattern/BlockPattern;checkPatternAt(Lcom/gregtechceu/gtceu/api/pattern/MultiblockState;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Direction;Lnet/minecraft/core/Direction;ZZ)Z"),
            require = 1, remap = false)
    private static boolean gtlEnhancedcore$checkNormalOrientationOnly(BlockPattern pattern, MultiblockState state,
            BlockPos origin, Direction direction, Direction upwards, boolean ignoredLegacyMode, boolean invert) {
        boolean matched = pattern.checkPatternAt(state, origin, direction, upwards, false, invert);
        StructureDetectScanContext.record(matched && !state.hasError());
        return matched;
    }
}
