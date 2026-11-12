package com.gtl.enhancedcore.client.patterncut;

import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gtl.enhancedcore.client.assembly.AssemblyManagerClient;
import com.gtl.enhancedcore.client.plot.CampusPlotSearch;
import com.gtl.enhancedcore.common.gui.GuiStyle;
import com.gtl.enhancedcore.common.patterncut.PatternCutNetwork;
import com.gtl.enhancedcore.common.patterncut.PatternCutStore.Entry;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/** A small record selector; actual paste actions stay on the world right-click gesture. */
public final class PatternCutScreen extends Screen {
    private static final int INK = GuiStyle.LIGHT_TEXT, MUTED = GuiStyle.LIGHT_SECONDARY;
    private static final int ACCENT = GuiStyle.LIGHT_ACCENT, ROW_HEIGHT = 26;
    private static final int LIST = 0xffc6cbc5, LINE = 0xffb7beb7;
    private PatternCutNetwork.View received;
    private List<Entry> filtered = List.of();
    private final List<CampusPlotSearch.Aliases> aliases = new ArrayList<>();
    private final List<RowButton> recordRows = new ArrayList<>();
    private EditBox search;
    private PanelButton directMode, smartMode, previous, next;
    private UUID selected;
    private boolean smart;
    private long totalPatterns;
    private int left, top, panelWidth, panelHeight, listTop, listBottom, visibleRows, recordScroll, refreshTicks;

    public PatternCutScreen() { super(tr("title")); }
    private static Component tr(String key, Object... args) {
        return Component.translatable("gtl_enhancedcore.pattern_cut." + key, args);
    }
    @Override protected void init() {
        panelWidth = Math.min(300, width - 16); panelHeight = Math.min(220, height - 16);
        left = (width - panelWidth) / 2; top = (height - panelHeight) / 2;
        listTop = top + 64; listBottom = top + panelHeight - 49;
        visibleRows = Math.max(1, (listBottom - listTop - 2) / ROW_HEIGHT);
        recordRows.clear();
        search = addRenderableWidget(new EditBox(font, left + 29, top + 46, panelWidth - 42, 12, tr("search")));
        search.setBordered(false); search.setTextColor(INK);
        search.setTextColorUneditable(MUTED); search.setMaxLength(128);
        search.setValue(PatternCutClient.query());
        search.setResponder(value -> { PatternCutClient.query(value); filter(true); });
        previous = button(tr("up"), left + panelWidth - 25, listTop + 1, 14, 14, Control.UP, b -> scroll(-visibleRows));
        next = button(tr("down"), left + panelWidth - 25, listBottom - 16, 14, 14, Control.DOWN, b -> scroll(visibleRows));
        for (int i = 0; i < visibleRows; i++) recordRows.add(addRenderableWidget(
                new RowButton(left + 10, listTop + i * ROW_HEIGHT, panelWidth - 37, i)));
        int modeWidth = 79, modeY = top + panelHeight - 43;
        directMode = button(tr("mode_direct_short"), left + panelWidth - 170, modeY, modeWidth, 18, Control.MODE, b -> setMode(false));
        smartMode = button(tr("mode_smart_short"), left + panelWidth - 90, modeY, modeWidth, 18, Control.MODE, b -> setMode(true));
        directMode.setTooltip(Tooltip.create(tr("mode_direct_hint")));
        smartMode.setTooltip(Tooltip.create(tr("mode_smart_hint")));
        var close = button(tr("close"), left + panelWidth - 25, top + 7, 16, 16, Control.CLOSE, b -> onClose());
        close.setTooltip(Tooltip.create(tr("close_hint")));
        received = null; refresh(); setFocused(null); search.setFocused(false);
    }
    private PanelButton button(Component label, int x, int y, int w, int h, Control control, Button.OnPress action) {
        return addRenderableWidget(new PanelButton(x, y, w, h, label, control, action));
    }
    public void refresh() {
        var view = PatternCutClient.view();
        if (search == null || view == null || view == received) return;
        received = view; selected = view.selected(); smart = view.smart();
        aliases.clear(); totalPatterns = 0;
        for (var entry : view.entries()) {
            aliases.add(CampusPlotSearch.prepare(List.of(name(entry.name()).getString(), coordinates(entry), entry.dimension())));
            totalPatterns += entry.patterns();
        }
        filter(false);
    }
    private void filter(boolean reset) {
        var query = CampusPlotSearch.compile(search.getValue());
        var matches = new ArrayList<Entry>();
        if (received != null) for (int i = 0; i < received.entries().size(); i++)
            if (query.matches(aliases.get(i))) matches.add(received.entries().get(i));
        filtered = List.copyOf(matches);
        if (reset) recordScroll = 0;
        recordScroll = clamp(recordScroll); updateButtons();
    }
    private int clamp(int value) { return Math.max(0, Math.min(value, Math.max(0, filtered.size() - visibleRows))); }
    private void scroll(int delta) { recordScroll = clamp(recordScroll + delta); updateButtons(); }
    private Entry recordAt(int slot) {
        int index = recordScroll + slot;
        return index < filtered.size() ? filtered.get(index) : null;
    }
    private void choose(int slot) {
        var entry = recordAt(slot);
        if (entry == null || received == null) return;
        selected = entry.id(); PatternCutNetwork.requestSelect(received.hand(), selected); updateButtons();
    }
    private void setMode(boolean value) {
        if (received == null || smart == value) return;
        smart = value; PatternCutNetwork.requestMode(received.hand(), value); updateButtons();
    }
    private void updateButtons() {
        if (directMode == null) return;
        directMode.chosen = !smart; smartMode.chosen = smart;
        previous.active = recordScroll > 0; next.active = recordScroll + visibleRows < filtered.size();
        previous.visible = next.visible = filtered.size() > visibleRows;
        for (int i = 0; i < recordRows.size(); i++) {
            var entry = recordAt(i); var row = recordRows.get(i); row.visible = entry != null;
            if (entry == null) continue;
            row.setMessage(name(entry.name()));
            var tip = row.getMessage().copy().append("\n").append(tr("record_count", entry.patterns()))
                    .append("\n" + entry.dimension() + " · " + coordinates(entry));
            row.details = Tooltip.create(tip);
            row.longName = font.width(row.getMessage()) > row.nameWidth(entry);
            row.setTooltip(row.longName ? row.details : null);
        }
    }
    @Override public void tick() {
        super.tick(); var view = PatternCutClient.view();
        if (view == null || minecraft.player == null || minecraft.level == null
                || !PatternCutNetwork.isTool(minecraft.player.getItemInHand(view.hand()))) { onClose(); return; }
        refresh(); search.tick();
        if (++refreshTicks >= 30) { refreshTicks = 0; PatternCutNetwork.requestRefresh(view.hand()); }
    }
    @Override public void render(GuiGraphics g, int mx, int my, float partial) {
        renderBackground(g);
        GuiTextures.BACKGROUND.draw(g, mx, my, left, top, panelWidth, panelHeight);
        paper(g, left + 11, top + 9, ACCENT);
        text(g, title, left + 26, top + 11, panelWidth - 57, INK);
        text(g, tr("summary", received == null ? 0 : received.entries().size(), totalPatterns), left + 11, top + 28, panelWidth - 22, MUTED);
        inset(g, left + 9, top + 41, panelWidth - 18, 19, 0xffe4e7df);
        magnifier(g, left + 15, top + 46, search.isFocused() ? ACCENT : MUTED);
        if (search.isFocused()) g.fill(left + 10, top + 59, left + panelWidth - 10, top + 60, ACCENT);
        inset(g, left + 8, listTop - 1, panelWidth - 16, listBottom - listTop + 1, LIST);
        if (filtered.isEmpty()) {
            int center = left + panelWidth / 2;
            paper(g, center - 4, listTop + 27, 0xff7b887e);
            var empty = tr(search.getValue().isBlank() ? "empty" : "no_matches");
            text(g, empty, center - Math.min(font.width(empty), panelWidth - 36) / 2, listTop + 46, panelWidth - 36, MUTED);
        }
        if (filtered.size() > visibleRows) {
            int railX = left + panelWidth - 22, railY = listTop + 18, railHeight = listBottom - listTop - 37;
            g.fill(railX, railY, railX + 8, railY + railHeight, 0xffa8b0a8);
            int thumb = Math.max(9, railHeight * visibleRows / filtered.size());
            int thumbY = railY + (railHeight - thumb) * recordScroll / (filtered.size() - visibleRows);
            GuiTextures.BUTTON.draw(g, mx, my, railX, thumbY, 8, thumb);
        }
        text(g, tr("mode_label"), left + 11, top + panelHeight - 38, panelWidth - 187, MUTED);
        var footer = received != null && received.message() != null && !received.message().getString().isBlank()
                && !received.message().equals(tr("hint"))
                ? received.message() : tr(smart ? "operation_smart_compact" : "operation_direct_compact");
        mouse(g, left + 12, top + panelHeight - 17, MUTED);
        text(g, footer, left + 25, top + panelHeight - 17, panelWidth - 36, MUTED);
        super.render(g, mx, my, partial);
        if (search.getValue().isEmpty() && !search.isFocused())
            text(g, tr("search"), search.getX(), search.getY(), search.getWidth(), 0xff68736a);
        if (mx >= left + 9 && mx < left + panelWidth - 9 && my >= top + panelHeight - 20 && my < top + panelHeight - 6) {
            var help = footer.copy().append("\n\n").append(tr(smart ? "mode_smart_hint" : "mode_direct_hint"))
                    .append("\n").append(tr("close_hint"));
            g.renderTooltip(font, font.split(help, Math.min(280, width - 16)), mx, my);
        }
    }
    private void inset(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + h, color);
        g.fill(x, y, x + w, y + 1, 0xff7d887f);
        g.fill(x, y, x + 1, y + h, 0xff7d887f);
        g.fill(x + 1, y + h - 1, x + w, y + h, 0xffe7eae2);
        g.fill(x + w - 1, y + 1, x + w, y + h, 0xffe7eae2);
    }
    private void paper(GuiGraphics g, int x, int y, int color) {
        g.fill(x, y, x + 8, y + 11, color);
        g.fill(x + 1, y + 1, x + 7, y + 10, 0xffe4e7df);
        g.fill(x + 2, y + 4, x + 6, y + 5, color);
        g.fill(x + 2, y + 7, x + 6, y + 8, color);
    }
    private void magnifier(GuiGraphics g, int x, int y, int color) {
        g.fill(x + 1, y, x + 5, y + 1, color);
        g.fill(x, y + 1, x + 1, y + 5, color);
        g.fill(x + 5, y + 1, x + 6, y + 5, color);
        g.fill(x + 1, y + 5, x + 5, y + 6, color);
        g.fill(x + 5, y + 5, x + 7, y + 7, color);
        g.fill(x + 7, y + 7, x + 8, y + 8, color);
    }
    private void mouse(GuiGraphics g, int x, int y, int color) {
        g.fill(x + 1, y, x + 6, y + 1, color);
        g.fill(x, y + 1, x + 1, y + 8, color);
        g.fill(x + 6, y + 1, x + 7, y + 8, color);
        g.fill(x + 1, y + 8, x + 6, y + 9, color);
        g.fill(x + 1, y + 3, x + 6, y + 4, color);
        g.fill(x + 3, y + 1, x + 4, y + 3, color);
    }
    private void text(GuiGraphics g, Component component, int x, int y, int w, int color) {
        String raw = component.getString();
        String fitted = font.width(raw) <= w ? raw : font.plainSubstrByWidth(raw, Math.max(0, w - font.width("…"))) + "…";
        g.drawString(font, fitted, x, y, color, false);
    }
    private static Component name(String value) { return AssemblyManagerClient.displayName(value); }
    private static String coordinates(Entry entry) { var pos = entry.source(); return pos.getX() + ", " + pos.getY() + ", " + pos.getZ(); }
    @Override public boolean mouseClicked(double x, double y, int button) {
        if (super.mouseClicked(x, y, button)) return true;
        if (button == 0 && x >= left + 9 && x < left + panelWidth - 9 && y >= top + 41 && y < top + 60) {
            setFocused(search); search.setFocused(true); return true;
        }
        return false;
    }
    @Override public boolean mouseScrolled(double x, double y, double amount) {
        if (x >= left + 8 && x < left + panelWidth - 8 && y >= listTop && y < listBottom) { scroll(amount > 0 ? -1 : 1); return true; }
        return super.mouseScrolled(x, y, amount);
    }
    @Override public boolean keyPressed(int key, int scan, int modifiers) {
        if (key == GLFW.GLFW_KEY_ESCAPE || !search.isFocused() && minecraft.options.keyInventory.matches(key, scan)) { onClose(); return true; }
        if (key == GLFW.GLFW_KEY_PAGE_UP || key == GLFW.GLFW_KEY_PAGE_DOWN) { scroll(key == GLFW.GLFW_KEY_PAGE_UP ? -visibleRows : visibleRows); return true; }
        return super.keyPressed(key, scan, modifiers);
    }
    @Override public boolean isPauseScreen() { return false; }
    private enum Control { MODE, UP, DOWN, CLOSE }
    private class PanelButton extends Button {
        private boolean chosen;
        private final Control control;
        PanelButton(int x, int y, int w, int h, Component label, Control control, OnPress action) {
            super(x, y, w, h, label, action, DEFAULT_NARRATION); this.control = control;
        }
        @Override protected void renderWidget(GuiGraphics g, int mx, int my, float partial) {
            int x = getX(), y = getY(), w = getWidth(), h = getHeight();
            GuiTextures.BUTTON.draw(g, mx, my, x, y, w, h);
            if (chosen) inset(g, x + 1, y + 1, w - 2, h - 2, 0xffb9c1b8);
            else if (active && isHoveredOrFocused()) g.fill(x + 2, y + 2, x + w - 2, y + h - 2, 0x25ffffff);
            int color = active ? INK : 0xff8b938c;
            if (control == Control.MODE) {
                g.fill(x + 7, y + 6, x + 13, y + 12, 0xff7d887f);
                g.fill(x + 8, y + 7, x + 12, y + 11, 0xffdfe4db);
                if (chosen) g.fill(x + 9, y + 8, x + 11, y + 10, ACCENT);
                text(g, getMessage(), x + 19, y + 5, w - 24, color);
            } else if (control == Control.CLOSE) {
                int cx = x + w / 2 - 2, cy = y + h / 2 - 2;
                for (int i = 0; i < 5; i++) {
                    g.fill(cx + i, cy + i, cx + i + 1, cy + i + 1, color);
                    g.fill(cx + 4 - i, cy + i, cx + 5 - i, cy + i + 1, color);
                }
            } else {
                int cx = x + w / 2, cy = y + h / 2;
                for (int i = 0; i < 3; i++) {
                    int py = control == Control.UP ? cy - 2 + i : cy + 1 - i;
                    g.fill(cx - i, py, cx + i + 1, py + 1, color);
                }
            }
        }
    }
    private final class RowButton extends Button {
        private final int slot;
        private Tooltip details;
        private boolean longName;
        RowButton(int x, int y, int w, int slot) { super(x, y, w, ROW_HEIGHT, Component.empty(), b -> choose(slot), DEFAULT_NARRATION); this.slot = slot; }
        private int nameWidth(Entry entry) { return getWidth() - font.width(tr("record_count", entry.patterns())) - 28; }
        @Override protected void renderWidget(GuiGraphics g, int mx, int my, float partial) {
            var entry = recordAt(slot); if (entry == null) return;
            boolean chosen = entry.id().equals(selected);
            int x = getX(), y = getY(), w = getWidth();
            if (chosen || isHoveredOrFocused()) g.fill(x, y + 1, x + w, y + getHeight() - 1, chosen ? 0xffdbe0d6 : 0xffd1d7cd);
            if (chosen) g.fill(x, y + 3, x + 2, y + getHeight() - 3, ACCENT);
            paper(g, x + 5, y + 6, chosen ? ACCENT : 0xff778477);
            text(g, name(entry.name()), x + 18, y + 3, nameWidth(entry), INK);
            var count = tr("record_count", entry.patterns());
            text(g, count, x + w - font.width(count) - 4, y + 3, font.width(count), MUTED);
            text(g, Component.literal(coordinates(entry)), x + 18, y + 15, w - 23, MUTED);
            if (slot < visibleRows - 1 && recordAt(slot + 1) != null) g.fill(x + 18, y + getHeight() - 1, x + w - 4, y + getHeight(), LINE);
            setTooltip(longName || hasShiftDown() ? details : null);
        }
    }
}
