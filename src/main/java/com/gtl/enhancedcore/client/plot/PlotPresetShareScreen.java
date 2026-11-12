package com.gtl.enhancedcore.client.plot;

import com.gtl.enhancedcore.common.world.plot.PlotPresetShare;
import com.gtl.enhancedcore.common.world.plot.PlotSettings;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/** Sharing is local text only. Import confirms a draft; this screen never changes world creation state. */
public final class PlotPresetShareScreen extends Screen {
    private static final String KEY = "gtl_enhancedcore.plot_preset_share.";
    private final Screen parent;
    private final PlotSettings current;
    private final Consumer<PlotSettings> imported;
    private final PlotPreviewRenderer.Cached preview = new PlotPreviewRenderer.Cached();
    private String text = "";
    private Component status = tr("empty_hint");
    private PlotSettings parsed;
    private boolean rejectedPaste, copied;
    private MultiLineEditBox editor;
    private ShareButton apply;
    private int left, top, panelWidth, panelHeight, contentY, inputWidth, previewX, previewWidth;

    public PlotPresetShareScreen(Screen parent, PlotSettings current, Consumer<PlotSettings> imported) {
        super(tr("title"));
        this.parent = parent; this.current = current; this.imported = imported;
    }
    private static Component tr(String key, Object... args) { return Component.translatable(KEY + key, args); }
    @Override protected void init() {
        panelWidth = Math.min(780, width - 16); panelHeight = Math.min(390, height - 12);
        left = (width - panelWidth) / 2; top = (height - panelHeight) / 2;
        contentY = top + 78;
        inputWidth = (panelWidth - 38) * 3 / 5;
        previewX = left + 14 + inputWidth + 12; previewWidth = left + panelWidth - 14 - previewX;
        int toolbarWidth = Math.min(150, (panelWidth - 32) / 2);
        button(tr("export"), left + 14, top + 43, toolbarWidth, () -> {
            minecraft.keyboardHandler.setClipboard(PlotPresetShare.encode(current)); copied = true; status = tr("copied");
        });
        button(tr("paste"), left + 20 + toolbarWidth, top + 43, toolbarWidth, this::paste);
        editor = addRenderableWidget(new MultiLineEditBox(font, left + 14, contentY, inputWidth,
                panelHeight - 142, tr("input_hint"), tr("input_label")) {
            @Override public boolean keyPressed(int key, int scan, int modifiers) {
                if (Screen.isPaste(key) && minecraft.keyboardHandler.getClipboard().length() > PlotPresetShare.MAX_TEXT_LENGTH) {
                    rejectPaste(); return true;
                }
                return super.keyPressed(key, scan, modifiers);
            }
        });
        editor.setCharacterLimit(PlotPresetShare.MAX_TEXT_LENGTH + 1);
        editor.setValue(text);
        editor.setValueListener(value -> { text = value; rejectedPaste = false; copied = false; validate(); });
        button(CommonComponents.GUI_CANCEL, left + 14, top + panelHeight - 28, 72, this::onClose);
        apply = button(tr("import"), left + panelWidth - 142, top + panelHeight - 28, 128, () -> {
            validate();
            if (parsed != null) { imported.accept(parsed); minecraft.setScreen(parent); }
        });
        validate(); setInitialFocus(editor);
    }
    private void paste() {
        String pasted = minecraft.keyboardHandler.getClipboard();
        // Reject before the edit widget can truncate the incoming document.
        if (pasted.length() > PlotPresetShare.MAX_TEXT_LENGTH) { rejectPaste(); return; }
        editor.setValue(pasted);
    }
    private void rejectPaste() {
        rejectedPaste = true; copied = false; parsed = null; status = tr("error.too_long");
        if (apply != null) apply.active = false;
    }
    private void validate() {
        parsed = null;
        if (rejectedPaste) status = tr("error.too_long");
        else if (text.isBlank()) status = tr("empty_hint");
        else {
            try {
                var settings = PlotPresetShare.decode(text);
                if (settings.isAvailableForCreation()) { parsed = settings; status = tr("ready"); }
                else status = Component.translatable("gtl_enhancedcore.personal_plot.retired_preset");
            }
            catch (PlotPresetShare.ShareException invalid) { status = tr("error." + invalid.errorKey()); }
        }
        if (copied) status = tr("copied");
        if (apply != null) apply.active = parsed != null;
    }
    @Override public void tick() { editor.tick(); }
    @Override public void onClose() { minecraft.setScreen(parent); }
    @Override public void removed() { preview.close(); }
    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        g.fillGradient(0, 0, width, height, 0xff171b20, 0xff101216);
        g.fill(left, top, left + panelWidth, top + panelHeight, 0xff252a30);
        g.renderOutline(left, top, panelWidth, panelHeight, 0xff535c64);
        g.drawString(font, title, left + 14, top + 12, 0xfff1eee5, false);
        g.drawString(font, clipped(tr("hint"), panelWidth - 28), left + 14, top + 27, 0xffa4acb3, false);
        g.drawString(font, tr("input_label"), left + 14, contentY - 11, 0xffa4acb3, false);
        g.drawString(font, clipped(tr(parsed == null ? "preview_current" : "preview_import"), previewWidth),
                previewX, contentY - 11, 0xffa4acb3, false);
        g.fill(previewX - 4, contentY, previewX + previewWidth + 4, top + panelHeight - 54, 0xff1c2126);
        PlotSettings shown = parsed == null ? current : parsed;
        int imageSize = Math.max(1, Math.min(previewWidth - 14, panelHeight - 187));
        int imageX = previewX + (previewWidth - imageSize) / 2;
        preview.draw(g, shown, imageX, contentY + 7, imageSize, 2, false);
        int summaryY = contentY + imageSize + 15;
        g.drawString(font, clipped(tr("summary", shown.plotChunks(), shown.plotChunks()), previewWidth - 8),
                previewX + 4, summaryY, 0xffe0ddd4, false);
        if (summaryY + 24 < top + panelHeight - 54) {
            g.drawString(font, clipped(tr("height", shown.bedrockY(), shown.surfaceY()), previewWidth - 8),
                    previewX + 4, summaryY + 12, 0xffa4acb3, false);
        }
        int statusY = top + panelHeight - 45;
        int color = parsed != null ? 0xffa5c49a : text.isBlank() ? 0xffa4acb3 : 0xffe69b8d;
        g.drawString(font, clipped(status, panelWidth - 28), left + 14, statusY, color, false);
        super.render(g, mouseX, mouseY, partial);
        if (mouseY >= statusY && mouseY <= statusY + 10)
            g.renderTooltip(font, font.split(status, Math.min(280, panelWidth - 28)), mouseX, mouseY);
        else if (mouseY >= top + 26 && mouseY <= top + 37)
            g.renderTooltip(font, font.split(tr("hint"), Math.min(280, panelWidth - 28)), mouseX, mouseY);
    }
    private String clipped(Component value, int pixels) { return font.plainSubstrByWidth(value.getString(), pixels); }
    private ShareButton button(Component label, int x, int y, int w, Runnable action) {
        return addRenderableWidget(new ShareButton(label, x, y, w, action));
    }
    private final class ShareButton extends AbstractButton {
        private final Runnable action;
        ShareButton(Component label, int x, int y, int w, Runnable action) { super(x, y, w, 20, label); this.action = action; }
        @Override public void onPress() { action.run(); }
        @Override protected void updateWidgetNarration(NarrationElementOutput output) { defaultButtonNarrationText(output); }
        @Override protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partial) {
            g.fill(getX(), getY(), getX() + width, getY() + height,
                    !active ? 0xff2b3035 : isHoveredOrFocused() ? 0xff50574b : 0xff363d43);
            g.renderOutline(getX(), getY(), width, height, isHoveredOrFocused() ? 0xffc1bc9d : 0xff5b646b);
            g.drawString(font, clipped(getMessage(), width - 8), getX() + 4, getY() + 6,
                    active ? 0xffe9e6da : 0xff747b81, false);
        }
    }
}
