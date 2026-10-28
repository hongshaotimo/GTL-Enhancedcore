package com.gtl.enhancedcore.mixin.ftbchunks.client;

import com.gtl.enhancedcore.client.ftbchunks.ChunkMapLayout;
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
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "dev.ftb.mods.ftbchunks.client.gui.ChunkScreen$ChunkButton", remap = false)
public abstract class ChunkButtonMixin extends Widget {
    @Shadow private XZ chunkPos;

    public ChunkButtonMixin(Panel panel) {
        super(panel);
    }

    /** Upstream setSize(16, 16): follow the 31x31 layout tile size. */
    @ModifyConstant(method = "<init>", constant = @Constant(intValue = 16), require = 2)
    private int gtlEnhancedcore$tileSize(int original) {
        return ChunkMapLayout.tileSize();
    }

    /** Force-load icon is drawn 16x16 inside the team colour lambda. */
    @ModifyConstant(method = "lambda$drawBackground$0", constant = @Constant(intValue = 16), require = 2)
    private static int gtlEnhancedcore$forceLoadIconSize(int original) {
        return ChunkMapLayout.tileSize();
    }

    @Inject(method = "drawBackground", at = @At("HEAD"))
    private void gtlEnhancedcore$rectSelect(GuiGraphics graphics, Theme theme, int x, int y, int width,
                                             int height, CallbackInfo ci) {
        if (ClaimModeState.current() != ClaimMode.RECTANGLE) return;
        if (!Widget.isMouseButtonDown(MouseButton.LEFT) && !Widget.isMouseButtonDown(MouseButton.RIGHT)) {
            RectangleSelectionState.reset();
            return;
        }
        if (!isMouseOver()) return;

        XZ first = RectangleSelectionState.getFirstChunk();
        if (first == null) {
            first = chunkPos;
            RectangleSelectionState.setFirstChunk(first);
        }
        Set<XZ> selected = ((ChunkScreenAccessor) getParent()).getSelectedChunks();
        selected.clear();
        int minX = Math.min(first.x(), chunkPos.x());
        int maxX = Math.max(first.x(), chunkPos.x());
        int minZ = Math.min(first.z(), chunkPos.z());
        int maxZ = Math.max(first.z(), chunkPos.z());
        for (int chunkX = minX; chunkX <= maxX; chunkX++) {
            for (int chunkZ = minZ; chunkZ <= maxZ; chunkZ++) {
                selected.add(XZ.of(chunkX, chunkZ));
            }
        }
    }
}
