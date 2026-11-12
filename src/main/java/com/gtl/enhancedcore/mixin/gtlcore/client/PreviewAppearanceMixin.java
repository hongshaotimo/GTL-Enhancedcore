package com.gtl.enhancedcore.mixin.gtlcore.client;

import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gtl.enhancedcore.client.preview.PreviewStyle;
import com.lowdragmc.lowdraglib.gui.editor.Icons;
import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib.gui.texture.ItemStackTexture;
import com.lowdragmc.lowdraglib.gui.widget.ButtonWidget;
import com.lowdragmc.lowdraglib.gui.widget.ImageWidget;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import net.minecraft.world.item.ItemStack;
import org.gtlcore.gtlcore.client.preview.PreviewControls;
import org.gtlcore.gtlcore.client.preview.PreviewPatternPanel;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

/** Adjusts only fullscreen presentation after fix3 has laid out its native controls and panels. */
@Mixin(value = PreviewControls.class, remap = false)
public abstract class PreviewAppearanceMixin {
    @Shadow @Final private WidgetGroup widget;
    @Shadow @Final private ButtonWidget expand;
    @Shadow @Final private ButtonWidget candidateToggle;
    @Shadow @Final private ButtonWidget locateController;
    @Shadow @Final private PreviewPatternPanel patternPanel;
    @Shadow @Final private ButtonWidget patternToggle;
    @Shadow @Final private ButtonWidget originToggle;
    @Shadow @Final private ButtonWidget replaceableToggle;
    @Shadow @Final private ImageWidget info;
    @Shadow @Final private ItemStack titleItem;
    @Shadow private boolean fullscreen;
    @Shadow private boolean showCandidates;
    @Shadow private boolean showPattern;
    @Shadow private boolean highlightReplaceable;
    @Shadow private int width;
    @Shadow private int toolbarLeft;

    @Inject(method = "layout()V", at = @At("TAIL"), require = 1, allow = 1)
    private void enhanced$alignFullscreenToolbar(CallbackInfo ci) {
        if (!fullscreen) return;
        originToggle.setVisible(false);
        originToggle.setActive(false);
        List<Widget> icons = List.of(replaceableToggle, patternToggle,
                locateController, info, candidateToggle, expand);
        List<ButtonWidget> textControls = new ArrayList<>();
        for (var child : widget.widgets) {
            if (child instanceof ButtonWidget button && button != originToggle
                    && button.isVisible() && !icons.contains(button)) {
                textControls.add(button);
            }
        }
        int count = textControls.size() + icons.size();
        int toolbarWidth = textControls.size() * PreviewStyle.TEXT_BUTTON_WIDTH
                + icons.size() * PreviewStyle.BUTTON_SIZE + (count - 1) * PreviewStyle.BUTTON_GAP;
        toolbarLeft = width - 12 - toolbarWidth;
        int x = toolbarLeft;
        for (var button : textControls) {
            enhanced$position(button, x, PreviewStyle.TEXT_BUTTON_WIDTH);
            x += PreviewStyle.TEXT_BUTTON_WIDTH + PreviewStyle.BUTTON_GAP;
        }
        for (var icon : icons) {
            enhanced$position(icon, x, PreviewStyle.BUTTON_SIZE);
            x += PreviewStyle.BUTTON_SIZE + PreviewStyle.BUTTON_GAP;
        }
        enhanced$styleToolbar();
        patternPanel.setBackground(PreviewStyle.PANEL);
        // The AE editor, scrollbar and material operations are left to PreviewPatternPanel.
        for (var child : patternPanel.widgets) {
            if (child instanceof ButtonWidget button) {
                button.setButtonTexture(PreviewStyle.iconButton(button.getSelfPositionY() < 22
                        ? GuiTextures.CLOSE_ICON : PreviewStyle.PATTERN));
                button.setHoverTexture(IGuiTexture.EMPTY);
            }
        }
    }

    @Inject(method = "updateReplaceableButton()V", at = @At("TAIL"), require = 1, allow = 1)
    private void enhanced$keepToggleAppearance(CallbackInfo ci) {
        if (fullscreen) enhanced$styleToolbar();
    }

    @Unique
    private static void enhanced$position(Widget child, int x, int width) {
        child.setSelfPosition(x, PreviewStyle.TOOLBAR_Y);
        child.setSize(width, PreviewStyle.BUTTON_SIZE);
    }

    @Unique
    private void enhanced$styleToolbar() {
        expand.setButtonTexture(PreviewStyle.iconButton(GuiTextures.CLOSE_ICON));
        candidateToggle.setButtonTexture(PreviewStyle.iconButton(
                () -> showCandidates ? Icons.EYE : Icons.EYE_OFF, () -> showCandidates));
        locateController.setButtonTexture(PreviewStyle.iconButton(new ItemStackTexture(titleItem)));
        patternToggle.setButtonTexture(PreviewStyle.iconButton(() -> PreviewStyle.PATTERN, () -> showPattern));
        replaceableToggle.setButtonTexture(PreviewStyle.iconButton(
                () -> highlightReplaceable ? Icons.EDIT_ON : Icons.EDIT_OFF, () -> highlightReplaceable));
        info.setImage(PreviewStyle.iconButton(PreviewStyle.INFO));
    }
}
