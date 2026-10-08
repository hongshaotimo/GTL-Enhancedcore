package com.gtl.enhancedcore.mixin.gtceu;

import com.gregtechceu.gtceu.api.pattern.BlockPattern;
import com.gtl.enhancedcore.common.structure.GtlMegastructurePatterns;
import com.gtl.enhancedcore.common.structure.LucidEtchdreamerPreview;
import com.lowdragmc.lowdraglib.utils.BlockInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Keep unconstrained cells absent from GTLCore's preview position map. */
@Mixin(value = BlockPattern.class, remap = false)
public abstract class LucidEtchdreamerPreviewMixin {
    @Inject(method = "getPreview", at = @At("HEAD"), cancellable = true)
    private void enhanced$sparseLucidPreview(int[] repetitions, CallbackInfoReturnable<BlockInfo[][][]> cir) {
        BlockPattern pattern = (BlockPattern) (Object) this;
        if (GtlMegastructurePatterns.needsLucidPreview(pattern)) {
            cir.setReturnValue(LucidEtchdreamerPreview.create(pattern));
        }
    }
}
