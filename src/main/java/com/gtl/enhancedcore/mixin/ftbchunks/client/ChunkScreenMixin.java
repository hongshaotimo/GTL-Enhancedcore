package com.gtl.enhancedcore.mixin.ftbchunks.client;

import com.gtl.enhancedcore.client.ftbchunks.ClaimModeState;
import com.gtl.enhancedcore.client.ftbchunks.RectangleSelectionState;
import dev.ftb.mods.ftbchunks.client.gui.ChunkScreen;
import dev.ftb.mods.ftblibrary.icon.Icons;
import dev.ftb.mods.ftblibrary.ui.Panel;
import dev.ftb.mods.ftblibrary.ui.SimpleButton;
import dev.ftb.mods.ftblibrary.ui.Widget;
import dev.ftb.mods.ftblibrary.ui.input.MouseButton;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 在 FTB Chunks 区块界面右上角添加模式切换按钮（自由绘制 / 矩形选区）。
 */
@Mixin(value = ChunkScreen.class, remap = false)
public abstract class ChunkScreenMixin {
    @Inject(method = "addWidgets", at = @At("RETURN"))
    private void gtlEnhancedcore$addClaimModeButton(CallbackInfo ci) {
        ChunkScreen self = (ChunkScreen) (Object) this;
        SimpleButton btn = new SimpleButton((Panel) self, Component.translatable(ClaimModeState.current().translationKey()), Icons.INFO, (b, mb) -> {
            ClaimModeState.cycle();
            b.setTitle(Component.translatable(ClaimModeState.current().translationKey()));
        });
        btn.setPosAndSize(self.getWidth() - 18, 2, 16, 16);
        self.add((Widget) btn);
    }

    @Inject(method = "mouseReleased", at = @At("RETURN"))
    private void gtlEnhancedcore$resetFirstChunk(MouseButton button, CallbackInfo ci) {
        RectangleSelectionState.reset();
    }
}
