package com.gtl.enhancedcore.mixin.gtladditions;

import com.gtl.enhancedcore.common.machine.galaxy.GalaxyLinks;
import com.gtladd.gtladditions.api.machine.logic.ApocalypticMatrixRecipeLogic;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = ApocalypticMatrixRecipeLogic.class, remap = false)
public abstract class GalaxyMatrixThreadsMixin {
    // The matrix reads its trait directly when allocating the native batch.
    @ModifyExpressionValue(method = "calculateParallels", at = @At(value = "INVOKE",
            target = "Lcom/gtladd/gtladditions/common/machine/trait/MatrixQuantumWarpTrait;finiteThreads()J"),
            require = 1, allow = 1)
    private long galaxy$matrixBatchThreads(long nativeThreads) {
        var matrix = ((ApocalypticMatrixRecipeLogic) (Object) this).getMachine();
        if (GalaxyLinks.unlimited(matrix)) return Math.max(nativeThreads, Integer.MAX_VALUE);
        return GalaxyLinks.active(matrix) ? nativeThreads + GalaxyLinks.EXTRA_THREADS : nativeThreads;
    }
}
