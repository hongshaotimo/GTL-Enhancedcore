package com.gtl.enhancedcore.mixin.gtlcore.client;

import com.lowdragmc.lowdraglib.gui.widget.SlotWidget;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.gtlcore.gtlcore.api.gui.PatternPreviewWidget;
import org.gtlcore.gtlcore.client.preview.PreviewControls;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keep GTLCore's candidate groups and layout, opening its panel on a block selection. */
@Mixin(value = PatternPreviewWidget.class, remap = false)
public abstract class PreviewCandidateSelectionMixin {
    @Shadow private PreviewControls previewControls;
    @Shadow @Final private List<SlotWidget> candidates;

    @Inject(method = "onPosSelected", at = @At("TAIL"), require = 1)
    private void enhanced$showSelectedCandidates(BlockPos pos, Direction facing, CallbackInfo ci) {
        if (previewControls != null && previewControls.fullscreen() && !candidates.isEmpty()) {
            previewControls.showCandidates();
        }
    }
}
