package com.gtl.enhancedcore.mixin.ftbchunks.client;

import com.gtl.enhancedcore.client.ftbchunks.ChunkMapLayout;
import com.gtl.enhancedcore.client.ftbchunks.ClaimMapBackground;
import com.gtl.enhancedcore.client.ftbchunks.ClaimModeState;
import com.gtl.enhancedcore.client.ftbchunks.RectangleSelectionState;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.ftb.mods.ftbchunks.client.gui.ChunkScreen;
import dev.ftb.mods.ftblibrary.icon.Color4I;
import dev.ftb.mods.ftblibrary.icon.Icons;
import dev.ftb.mods.ftblibrary.ui.GuiHelper;
import dev.ftb.mods.ftblibrary.ui.Panel;
import dev.ftb.mods.ftblibrary.ui.SimpleButton;
import dev.ftb.mods.ftblibrary.ui.Widget;
import dev.ftb.mods.ftblibrary.ui.input.MouseButton;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * FTB Chunks 2001.3.8 claim screen: fixed 31x31 chunks instead of 15x15.
 * Constants are matched by ordinal against the upstream bytecode (see FtbClaimMapRegression).
 */
@Mixin(value = ChunkScreen.class, remap = false)
public abstract class ChunkScreenMixin {
    @Inject(method = "addWidgets", at = @At("HEAD"))
    private void gtlEnhancedcore$configureLayout(CallbackInfo ci) {
        ChunkScreen self = (ChunkScreen) (Object) this;
        ChunkMapLayout.configure(self.getWidth(), self.getHeight());
    }

    @Inject(method = "addWidgets", at = @At("RETURN"))
    private void gtlEnhancedcore$addClaimModeButton(CallbackInfo ci) {
        ChunkScreen self = (ChunkScreen) (Object) this;
        SimpleButton button = new SimpleButton((Panel) self,
                Component.translatable(ClaimModeState.current().translationKey()), Icons.INFO,
                (clicked, mouseButton) -> {
                    ClaimModeState.cycle();
                    clicked.setTitle(Component.translatable(ClaimModeState.current().translationKey()));
                });
        button.setPosAndSize(self.getWidth() - 18, 2, 16, 16);
        self.add((Widget) button);
    }

    @Inject(method = "mouseReleased", at = @At("RETURN"))
    private void gtlEnhancedcore$resetFirstChunk(MouseButton button, CallbackInfo ci) {
        RectangleSelectionState.reset();
    }

    // ---- addWidgets: centring, chunk origin, button grid, map data request ----

    @ModifyConstant(method = "addWidgets", constant = @Constant(intValue = 240), require = 2)
    private int gtlEnhancedcore$widgetMapSize(int original) {
        return ChunkMapLayout.mapSize();
    }

    @ModifyConstant(method = "addWidgets", constant = @Constant(intValue = 7), require = 6)
    private int gtlEnhancedcore$radius(int original) {
        return ChunkMapLayout.RADIUS;
    }

    @ModifyConstant(method = "addWidgets", constant = @Constant(intValue = 15), require = 2)
    private int gtlEnhancedcore$chunkCount(int original) {
        return ChunkMapLayout.CHUNKS;
    }

    @ModifyConstant(method = "addWidgets", constant = {
            @Constant(intValue = 16, ordinal = 0), @Constant(intValue = 16, ordinal = 1)}, require = 2)
    private int gtlEnhancedcore$buttonSpacing(int original) {
        return ChunkMapLayout.tileSize();
    }

    // ---- drawBackground: background texture, grid, player marker ----

    @ModifyConstant(method = "drawBackground", constant = @Constant(intValue = 240), require = 6)
    private int gtlEnhancedcore$drawMapSize(int original) {
        return ChunkMapLayout.mapSize();
    }

    @ModifyConstant(method = "drawBackground", constant = @Constant(intValue = 15), require = 2)
    private int gtlEnhancedcore$gridLines(int original) {
        return ChunkMapLayout.CHUNKS;
    }

    @ModifyConstant(method = "drawBackground", constant = @Constant(intValue = 16), require = 2)
    private int gtlEnhancedcore$gridSpacing(int original) {
        return ChunkMapLayout.tileSize();
    }

    @ModifyConstant(method = "drawBackground", constant = @Constant(intValue = 112), require = 2)
    private int gtlEnhancedcore$playerCenter(int original) {
        return ChunkMapLayout.center();
    }

    @ModifyExpressionValue(method = "drawBackground", at = @At(value = "INVOKE",
            target = "Ldev/ftb/mods/ftblibrary/math/MathUtils;mod(DD)D"), require = 2)
    private double gtlEnhancedcore$playerOffset(double original) {
        return original * ChunkMapLayout.pixelScale();
    }

    /** Upstream samples the 15x15 minimap texture; draw the 31x31 area from region textures instead. */
    @Redirect(method = "drawBackground", at = @At(value = "INVOKE",
            target = "Ldev/ftb/mods/ftblibrary/ui/GuiHelper;drawTexturedRect(Lnet/minecraft/client/gui/GuiGraphics;IIIILdev/ftb/mods/ftblibrary/icon/Color4I;FFFF)V"),
            require = 1)
    private void gtlEnhancedcore$drawRegionBackground(GuiGraphics graphics, int x, int y, int w, int h,
                                                      Color4I color, float u0, float v0, float u1, float v1) {
        ClaimMapBackground.draw(graphics, ((ChunkScreenAccessor) (Object) this).getDimension(), x, y);
    }
}
