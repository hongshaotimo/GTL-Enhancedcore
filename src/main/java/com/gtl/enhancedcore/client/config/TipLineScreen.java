package com.gtl.enhancedcore.client.config;

import com.gtl.enhancedcore.common.config.TooltipEditorLayout;
import com.gtl.enhancedcore.common.config.TooltipLineDraft;
import com.gtl.enhancedcore.common.config.TooltipMarkup;
import com.gtl.enhancedcore.common.config.TooltipOverrides.*;
import com.gtl.enhancedcore.common.config.TooltipTextEditor;
import com.gtl.enhancedcore.common.config.TooltipTextEditor.Selection;
import com.gtl.enhancedcore.mixin.mc.client.MultiLineEditBoxAccessor;
import com.gtl.enhancedcore.mixin.mc.client.MultilineTextFieldAccessor;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.components.MultilineTextField;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.Whence;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;

/** FTB-style compact tools, with explicit local/whole-line scope and a transactional palette. */
final class TipLineScreen extends ConfigPage {
    private final Consumer<Line> accept;
    private final TooltipLineDraft draft;
    private final Map<String, Button> styleButtons = new LinkedHashMap<>();
    private TipTextBox textBox;
    private Button scopeButton, fontButton, preview;
    private TooltipColorButton colorButton;
    private TooltipEditorLayout layout;
    private boolean wholeLine, refocusText;
    private Selection savedSelection;
    private double savedScroll;
    private String toolbarText;
    private Selection toolbarSelection;
    private Entry previewEntry;

    TipLineScreen(Screen parent, Line line, Consumer<Line> accept) {
        super(parent, "edit_line"); this.accept = accept;
        var rendered = Component.Serializer.fromJson(line.component());
        draft = new TooltipLineDraft(line, rendered == null ? "" : rendered.getString());
    }
    @Override protected void init() {
        captureCaret(); super.init();
        layout = TooltipEditorLayout.create(bodyWidth, height);
        var slots = layout.toolbar();
        var scope = slots.getFirst();
        scopeButton = button(scopeLabel(), left + scope.x(), scope.y(), scope.width(), widget -> {
            wholeLine = !wholeLine; refreshToolbar(); refocusText = true;
        });
        scopeButton.setTooltip(Tooltip.create(tr("format_scope_help")));
        styleButtons.clear();
        String[] glyphs = {"B", "I", "U", "S", "K"};
        for (int i = 0; i < TooltipMarkup.STYLE_KEYS.size(); i++) {
            String key = TooltipMarkup.STYLE_KEYS.get(i);
            var slot = slots.get(i + 1);
            var glyphStyle = switch (key) {
                case "bold" -> Style.EMPTY.withBold(true); case "italic" -> Style.EMPTY.withItalic(true);
                case "underlined" -> Style.EMPTY.withUnderlined(true); case "strikethrough" -> Style.EMPTY.withStrikethrough(true);
                default -> Style.EMPTY;
            };
            var button = button(Component.literal(glyphs[i]).setStyle(glyphStyle), left + slot.x(), slot.y(), slot.width(), () -> toggleStyle(key));
            button.setTooltip(Tooltip.create(tr(key).copy().append("\n").append(tr("format_scope_help"))));
            styleButtons.put(key, button);
        }
        var colorSlot = slots.get(6);
        colorButton = addRenderableWidget(new TooltipColorButton(left + colorSlot.x(), colorSlot.y(), colorSlot.width(), 20,
                TooltipColorButton.Choice.inherit(), true, this::chooseColor));
        var fontSlot = slots.get(7);
        fontButton = button("font_picker", left + fontSlot.x(), fontSlot.y(), fontSlot.width(), this::chooseFont);
        var resetSlot = slots.get(8);
        button(Component.literal("↺"), left + resetSlot.x(), resetSlot.y(), resetSlot.width(), () -> {
            if (wholeLine) { draft.resetStyle(); refreshToolbar(); refocusText = true; }
            else applyMarker("[重置]");
        }).setTooltip(Tooltip.create(tr("format_reset")));

        textBox = addRenderableWidget(new TipTextBox(layout.textY(), layout.textHeight()));
        textBox.setCharacterLimit(TooltipTextEditor.LIMIT);
        // Arm the responder after initialisation: opening a translate/with component is not an edit.
        textBox.setValue(draft.text());
        textBox.setValueListener(value -> { if (!value.equals(draft.text())) { draft.text(value); previewEntry = null; } });
        restoreCaret(savedSelection == null ? new Selection(draft.text().length(), draft.text().length()) : savedSelection);
        textBox.restoreScroll(savedScroll);
        setFocused(textBox);
        int half = (bodyWidth - 6) / 2;
        button("visibility." + draft.visibility().name().toLowerCase(Locale.ROOT), left, layout.controlsY(), half, widget -> {
            draft.visibility(Visibility.values()[(draft.visibility().ordinal() + 1) % Visibility.values().length]);
            previewEntry = null;
            widget.setMessage(tr("visibility." + draft.visibility().name().toLowerCase(Locale.ROOT)));
        });
        preview = button("preview", left + half + 6, layout.controlsY(), half, () -> {});
        preview.setTooltip(Tooltip.create(tr("preview_help")));
        button("save", left, layout.saveY(), half, () -> attempt(() -> { accept.accept(draft.line()); onClose(); }));
        button("cancel", left + half + 6, layout.saveY(), half, this::onClose);
        refreshToolbar();
    }
    private Component scopeLabel() { return tr(wholeLine ? "format_scope.line" : "format_scope.local"); }
    private MultilineTextField textField() { return ((MultiLineEditBoxAccessor) (MultiLineEditBox) textBox).gtl$textField(); }
    private Selection selection() {
        var field = textField();
        return new Selection(field.cursor(), ((MultilineTextFieldAccessor) field).gtl$selectionAnchor());
    }
    private void restoreCaret(Selection selection) {
        var field = textField();
        field.setSelecting(false); field.seekCursor(Whence.ABSOLUTE, selection.anchor());
        field.setSelecting(true); field.seekCursor(Whence.ABSOLUTE, selection.cursor()); field.setSelecting(false);
    }
    private void captureCaret() {
        if (textBox != null) { savedSelection = selection(); savedScroll = textBox.savedScroll(); }
    }
    @Override public void removed() { captureCaret(); super.removed(); }
    @Override public void resize(Minecraft minecraft, int width, int height) { captureCaret(); super.resize(minecraft, width, height); }
    private TooltipMarkup.Formatting localFormat() { return TooltipMarkup.formatAt(draft.text(), selection().from()); }
    private boolean enabled(String key) { return wholeLine ? draft.style(key) : localFormat().style(key, draft.style(key)); }
    private TooltipColorButton.Choice colorChoice() {
        if (wholeLine) return new TooltipColorButton.Choice(draft.color(), draft.rainbow());
        var local = localFormat();
        boolean animated = local.rainbow() == null ? draft.rainbow() : local.rainbow();
        return new TooltipColorButton.Choice(local.color() == null ? draft.color() : TooltipMarkup.hex(local.color()), animated);
    }
    private String currentFont() {
        if (wholeLine) return draft.font();
        String local = localFormat().font(); return local == null ? draft.font() : local;
    }
    private void refreshToolbar() {
        if (textBox == null || colorButton == null) return;
        previewEntry = null;
        scopeButton.setMessage(scopeLabel());
        var local = wholeLine ? null : localFormat();
        for (var entry : styleButtons.entrySet()) entry.getValue().setFGColor(
                (wholeLine ? draft.style(entry.getKey()) : local.style(entry.getKey(), draft.style(entry.getKey()))) ? 0xffff66 : 0xe8e8e8);
        colorButton.choice(colorChoice());
        fontButton.setTooltip(Tooltip.create(tr("font_picker").copy().append(": ").append(TooltipFontScreen.name(currentFont()))));
        toolbarText = draft.text(); toolbarSelection = selection();
    }
    private void toggleStyle(String key) {
        boolean enabled = !enabled(key);
        if (wholeLine) { draft.style(key, enabled); refreshToolbar(); refocusText = true; }
        else applyMarker(TooltipMarkup.styleTag(key, enabled));
    }
    private void chooseColor() {
        Selection target = selection(); boolean lineScope = wholeLine;
        minecraft.setScreen(new TooltipColorScreen(this, colorChoice(), choice -> {
            if (lineScope) { draft.color(choice.color()); draft.rainbow(choice.rainbow()); }
            else applyMarker(target, choice.marker());
        }));
    }
    private void chooseFont() {
        Selection target = selection(); boolean lineScope = wholeLine;
        minecraft.setScreen(new TooltipFontScreen(this, currentFont(), !lineScope, chosen -> {
            if (lineScope) draft.font(chosen);
            else applyMarker(target, chosen.isEmpty() ? "[默认字体]" : "[字体=" + chosen + "]");
        }));
    }
    private void applyMarker(String marker) { applyMarker(selection(), marker); }
    private void applyMarker(Selection selection, String marker) {
        try {
            var edited = TooltipTextEditor.apply(draft.text(), selection, marker);
            textBox.setValue(edited.text()); restoreCaret(edited.selection());
            refreshToolbar(); refocusText = true;
        } catch (TooltipTextEditor.LimitExceeded ex) { notice(tr("format_limit", TooltipTextEditor.LIMIT)); }
    }
    boolean dirty() { return draft.dirty(); }
    @Override public void tick() {
        if (refocusText) { setFocused(textBox); refocusText = false; }
        textBox.tick();
        if (!draft.text().equals(toolbarText) || !selection().equals(toolbarSelection)) refreshToolbar();
    }
    @Override public void renderBackground(GuiGraphics g) {
        super.renderBackground(g);
        if (layout != null) g.fill(left - 3, 29, left + bodyWidth + 3, layout.hintY() - 2, 0xb01b2432);
    }
    @Override public void render(GuiGraphics g, int x, int y, float partial) {
        super.render(g, x, y, partial);
        g.drawString(font, clipped(tr(wholeLine ? "format_line_help" : "format_local_help"), bodyWidth), left, layout.hintY(), 0xaaaaaa);
        g.fill(left, layout.previewY(), left + bodyWidth, layout.previewY() + 19, 0xcc101720);
        try {
            if (previewEntry == null) previewEntry = new Entry(Mode.REPLACE, List.of(draft.line()));
            var rendered = ClientTooltipConfig.render(previewEntry, hasShiftDown());
            if (rendered.isEmpty()) g.drawString(font, clipped(tr("preview_hidden"), bodyWidth - 12), left + 6, layout.previewY() + 5, 0xaaaaaa);
            else g.drawString(font, clipped(rendered.getFirst(), bodyWidth - 12), left + 6, layout.previewY() + 5, 0xffffff);
            if (preview.isHovered()) g.renderTooltip(font, rendered, Optional.empty(), x, y);
        } catch (RuntimeException ignored) { /* Invalid custom font input is diagnosed by its own picker. */ }
        if (dirty() && !statusVisible()) g.drawString(font, clipped(tr("unsaved"), bodyWidth), left, height - 43, 0xffaa00);
    }
    private final class TipTextBox extends MultiLineEditBox {
        TipTextBox(int y, int height) { super(TipLineScreen.this.font, left, y, bodyWidth - 8, height, tr("text_hint"), tr("text")); }
        double savedScroll() { return scrollAmount(); }
        void restoreScroll(double value) { setScrollAmount(value); }
    }
}
