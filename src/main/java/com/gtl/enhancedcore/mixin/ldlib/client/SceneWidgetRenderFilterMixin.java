package com.gtl.enhancedcore.mixin.ldlib.client;

import com.lowdragmc.lowdraglib.client.scene.WorldSceneRenderer;
import com.lowdragmc.lowdraglib.gui.widget.SceneWidget;
import java.util.Collection;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Avoids the Stream path seen in the LDLib 1.0.33.b preview ray-trace crash. */
@Mixin(value = SceneWidget.class, remap = false)
public abstract class SceneWidgetRenderFilterMixin {
    @Shadow protected WorldSceneRenderer renderer;

    @Dynamic("LDLib 1.0.33.b synthetic render-filter lambda; descriptor verified in the dependency JAR")
    @Inject(method = "lambda$createScene$2(Lnet/minecraft/core/BlockPos;)Z",
            at = @At("HEAD"), cancellable = true, require = 1, allow = 1)
    private void enhanced$filterWithoutStream(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        // Read the live renderer and every render group: layer/scene changes must apply immediately.
        for (Collection<BlockPos> blocks : renderer.renderedBlocksMap.keySet()) {
            if (blocks.contains(pos)) {
                cir.setReturnValue(true);
                return;
            }
        }
        cir.setReturnValue(false);
    }
}
