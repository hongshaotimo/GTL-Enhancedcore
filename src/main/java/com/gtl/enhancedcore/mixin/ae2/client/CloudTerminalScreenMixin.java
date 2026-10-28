package com.gtl.enhancedcore.mixin.ae2.client;

import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.me.items.PatternEncodingTermScreen;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.widgets.IconButton;
import appeng.menu.me.items.PatternEncodingTermMenu;
import appeng.menu.SlotSemantics;
import com.gtl.enhancedcore.client.cloud.CloudLibraryUploadButton;
import com.gtl.enhancedcore.common.cloud.CloudTerminalMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = PatternEncodingTermScreen.class, remap = false)
public abstract class CloudTerminalScreenMixin extends AEBaseScreen<PatternEncodingTermMenu> {
    @Unique private CloudLibraryUploadButton cloud$uploadButton;
    protected CloudTerminalScreenMixin(PatternEncodingTermMenu menu, Inventory inventory, Component title, ScreenStyle style) { super(menu, inventory, title, style); }
    @Inject(method = "updateBeforeRender", at = @At("TAIL"))
    private void cloud$update(CallbackInfo ci) {
        if (cloud$uploadButton == null) cloud$uploadButton = new CloudLibraryUploadButton(() -> ((CloudTerminalMenu)menu).cloud$upload());
        if (!children().contains(cloud$uploadButton)) addRenderableWidget(cloud$uploadButton);
        var originals = menu.getSlots(SlotSemantics.ENCODED_PATTERN);
        // Follow the actual output slot after AE's responsive layout. The compact
        // button shares Plus's 12px raster and is centered on the original it consumes.
        if (!originals.isEmpty()) {
            var original = originals.get(0);
            cloud$uploadButton.setX(getGuiLeft() + original.x - CloudLibraryUploadButton.SIZE - 4);
            cloud$uploadButton.setY(getGuiTop() + original.y + (16 - CloudLibraryUploadButton.SIZE) / 2);
        }
        cloud$uploadButton.setVisibility(!originals.isEmpty() && ((CloudTerminalMenu)menu).cloud$libraryBound());
        cloud$uploadButton.updateDiagnostic(((CloudTerminalMenu)menu).cloud$diagnostic());
        cloud$uploadButton.active = cloud$uploadButton.visible && ((CloudTerminalMenu)menu).cloud$hasOriginal();
        // Plus 1.5.4.1's 12px selector paints and hit-tests correctly, but sets a
        // vanilla tooltip while also inheriting AE's ITooltip. Keep its original
        // button/action and route its existing text through the AE tooltip owner.
        for (var child : children()) {
            if (child instanceof IconButton button && button.getTooltip() != null
                    && child.getClass().getName().equals(
                    "com.extendedae_plus.mixin.ae2.client.gui.PatternEncodingTermScreenMixin$1")) {
                button.setMessage(Component.translatable("extendedae_plus.button.choose_provider"));
                button.setTooltip(null);
            }
        }
    }
}
