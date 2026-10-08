package com.gtl.enhancedcore.mixin.ftbchunks.client;

import com.gtl.enhancedcore.client.ftbchunks.ClaimMode;
import com.gtl.enhancedcore.client.ftbchunks.ClaimModeState;
import com.gtl.enhancedcore.client.ftbchunks.RectangleSelectionState;
import dev.ftb.mods.ftblibrary.math.XZ;
import dev.ftb.mods.ftblibrary.ui.Panel;
import dev.ftb.mods.ftblibrary.ui.Theme;
import dev.ftb.mods.ftblibrary.ui.Widget;
import dev.ftb.mods.ftblibrary.ui.input.MouseButton;
import java.util.Set;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 矩形框选：按住鼠标拖过区块按钮时，把起点到当前区块之间的所有区块加入选中集合。
 */
@Mixin(targets = "dev.ftb.mods.ftbchunks.client.gui.ChunkScreen$ChunkButton", remap = false)
public abstract class ChunkButtonMixin extends Widget {
    @Shadow
    private XZ chunkPos;

    public ChunkButtonMixin(Panel panel) {
        super(panel);
    }

    @Inject(method = "drawBackground", at = @At("HEAD"))
    private void gtlEnhancedcore$rectSelect(GuiGraphics matrixStack, Theme theme, int x, int y, int w, int h, CallbackInfo ci) {
        if (ClaimModeState.current() != ClaimMode.RECTANGLE) {
            return;
        }
        if (!Widget.isMouseButtonDown(MouseButton.LEFT) && !Widget.isMouseButtonDown(MouseButton.RIGHT)) {
            RectangleSelectionState.reset();
            return;
        }
        if (!this.isMouseOver()) {
            return;
        }
        XZ first = RectangleSelectionState.getFirstChunk();
        if (first == null) {
            first = this.chunkPos;
            RectangleSelectionState.setFirstChunk(first);
        }
        Set<XZ> selected = ((ChunkScreenAccessor) this.getParent()).getSelectedChunks();
        selected.clear();
        int x1 = Math.min(first.x(), this.chunkPos.x());
        int x2 = Math.max(first.x(), this.chunkPos.x());
        int z1 = Math.min(first.z(), this.chunkPos.z());
        int z2 = Math.max(first.z(), this.chunkPos.z());
        for (int xi = x1; xi <= x2; xi++) {
            for (int zi = z1; zi <= z2; zi++) {
                selected.add(XZ.of(xi, zi));
            }
        }
    }
}
