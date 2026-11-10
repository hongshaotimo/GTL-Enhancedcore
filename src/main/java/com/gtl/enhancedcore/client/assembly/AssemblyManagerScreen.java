package com.gtl.enhancedcore.client.assembly;

import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gtl.enhancedcore.client.plot.CampusPlotSearch;
import com.gtl.enhancedcore.common.assembly.AssemblyManagerNetwork;
import com.gtl.enhancedcore.common.assembly.AssemblyMirrorNetwork;
import com.gtl.enhancedcore.common.gui.GuiStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/** Server-snapshot mirror management, without world reads or chunk loading. */
public final class AssemblyManagerScreen extends Screen {
    private static final int INK = GuiStyle.LIGHT_TEXT, MUTED = GuiStyle.LIGHT_SECONDARY;
    private static final int ACCENT = 0xff17677d, ROW_HEIGHT = 29;
    private AssemblyMirrorNetwork.View received;
    private final List<Entry> entries = new ArrayList<>();
    private List<Entry> filtered = List.of();
    private final List<RowButton> rowButtons = new ArrayList<>();
    private EditBox search;
    private PanelButton sourcesTab, mirrorsTab, refreshButton, clearButton, previous, next, rebind, unbind;
    private boolean mirrors = AssemblyMirrorClient.mirrorsTab();
    private BlockPos inspectedSource, inspectedMirror;
    private int left, right, cardTop, bodyTop, bodyBottom, divider, listTop, visibleRows, scroll, refreshTicks;

    public AssemblyManagerScreen() { super(tr("title")); }
    static Component tr(String key, Object... args) {
        return Component.translatable("gtl_enhancedcore.assembly.mirror." + key, args);
    }

    @Override protected void init() {
        left = Math.max(8, (width - 700) / 2); right = width - left;
        cardTop = 29; bodyTop = 109; bodyBottom = height - 25;
        divider = left + (right - left) / 2; listTop = bodyTop + 36;
        visibleRows = Math.max(1, (bodyBottom - listTop - 4) / ROW_HEIGHT);
        String value = search == null ? AssemblyMirrorClient.query(mirrors) : search.getValue();
        rowButtons.clear();
        refreshButton = button(tr("refresh"), right - 51, cardTop + 4, 45, 17, b -> requestRefresh());
        refreshButton.setTooltip(Tooltip.create(tr("refresh_hint")));
        clearButton = button(tr("clear_network"), right - 101, cardTop + 4, 46, 17,
                b -> action(AssemblyMirrorNetwork.CLEAR_NETWORK, BlockPos.ZERO));
        clearButton.setTooltip(Tooltip.create(tr("clear_network_hint")));
        int tabWidth = (right - left - 4) / 2;
        sourcesTab = button(tr("sources_tab", 0), left, 87, tabWidth, 19, b -> switchTab(false));
        mirrorsTab = button(tr("mirrors_tab", 0), left + tabWidth + 4, 87, right - left - tabWidth - 4, 19,
                b -> switchTab(true));
        search = addRenderableWidget(new EditBox(font, left + 8, bodyTop + 7, divider - left - 18, 12, tr("search")));
        search.setBordered(false); search.setTextColor(GuiStyle.DARK_TEXT);
        search.setTextColorUneditable(GuiStyle.DARK_SECONDARY);
        search.setMaxLength(128); search.setHint(tr("search")); search.setValue(value);
        search.setResponder(q -> { AssemblyMirrorClient.query(mirrors, q); filter(true); });
        previous = button(tr("scroll_up"), divider - 40, bodyTop + 23, 15, 12, b -> scroll(-visibleRows));
        next = button(tr("scroll_down"), divider - 22, bodyTop + 23, 15, 12, b -> scroll(visibleRows));
        previous.setTooltip(Tooltip.create(tr("previous_hint"))); next.setTooltip(Tooltip.create(tr("next_hint")));
        for (int i = 0; i < visibleRows; i++) rowButtons.add(addRenderableWidget(
                new RowButton(left + 5, listTop + i * ROW_HEIGHT, divider - left - 12, ROW_HEIGHT - 2, i)));
        rebind = button(tr("rebind"), divider + 9, bodyBottom - 42, right - divider - 18, 18,
                b -> action(AssemblyMirrorNetwork.REBIND, inspectedMirror));
        unbind = button(tr("unbind"), divider + 9, bodyBottom - 21, right - divider - 18, 18,
                b -> action(AssemblyMirrorNetwork.UNBIND, inspectedMirror));
        received = null; refresh();
        setFocused(null); search.setFocused(false);
    }

    private PanelButton button(Component label, int x, int y, int w, int h, Button.OnPress action) {
        return addRenderableWidget(new PanelButton(x, y, w, h, label, action));
    }

    void refresh() {
        var view = AssemblyMirrorClient.view();
        if (view == null || search == null || view == received) return;
        received = view; rebuildEntries(); updateButtons();
    }

    private void rebuildEntries() {
        entries.clear();
        if (received != null) {
            if (mirrors) for (var mirror : received.mirrors()) {
                Component label = tr("mirror_source", displayName(mirror.sourceName()));
                entries.add(new Entry(mirror.pos(), label, tr(mirror.superBuffer() ? "super_mirror" : "normal_mirror"),
                        CampusPlotSearch.prepare(List.of(label.getString(), displayName(mirror.sourceName()).getString(), coordinates(mirror.pos()))),
                        "", mirror.loaded()));
            }
            else for (var source : received.sources()) {
                Component label = displayName(source.name());
                entries.add(new Entry(source.pos(), label, tr(source.superBuffer() ? "super_source" : "normal_source"),
                        CampusPlotSearch.prepare(List.of(label.getString(), coordinates(source.pos()))), source.blockedReason(), true));
            }
        }
        entries.sort(Comparator.comparing((Entry e) -> e.name().getString()).thenComparingLong(e -> e.pos().asLong()));
        if (inspectedSource == null && received != null) inspectedSource = received.selectedSource();
        filter(false);
    }

    private void filter(boolean resetScroll) {
        var query = CampusPlotSearch.compile(search.getValue());
        filtered = entries.stream().filter(e -> query.matches(e.aliases())).toList();
        if (resetScroll) scroll = 0;
        scroll = Math.max(0, Math.min(scroll, Math.max(0, filtered.size() - visibleRows)));
        updateButtons();
    }

    private void switchTab(boolean target) {
        if (mirrors == target) return;
        AssemblyMirrorClient.query(mirrors, search.getValue());
        mirrors = target; AssemblyMirrorClient.mirrorsTab(target); scroll = 0;
        search.setValue(AssemblyMirrorClient.query(mirrors));
        rebuildEntries(); updateButtons();
    }

    private void scroll(int delta) {
        scroll = Math.max(0, Math.min(scroll + delta, Math.max(0, filtered.size() - visibleRows)));
        updateButtons();
    }

    private Entry entryAt(int slot) {
        int index = scroll + slot;
        return index >= 0 && index < filtered.size() ? filtered.get(index) : null;
    }

    private void choose(Entry entry) {
        if (entry == null) return;
        if (mirrors) inspectedMirror = entry.pos();
        else { inspectedSource = entry.pos(); action(AssemblyMirrorNetwork.SELECT_SOURCE, entry.pos()); }
        updateButtons();
    }

    private void action(int action, BlockPos pos) {
        if (received != null && pos != null) AssemblyMirrorNetwork.requestAction(received.hand(), action, pos);
    }
    private void requestRefresh() {
        if (received == null) return;
        refreshTicks = 0; AssemblyMirrorNetwork.requestRefresh(received.hand());
    }

    private AssemblyMirrorNetwork.Source selectedSource() {
        return received == null ? null : received.sources().stream()
                .filter(s -> s.pos().equals(received.selectedSource())).findFirst().orElse(null);
    }
    private AssemblyMirrorNetwork.Source sourceDetails() {
        return received == null ? null : received.sources().stream()
                .filter(s -> s.pos().equals(inspectedSource)).findFirst().orElse(null);
    }
    private AssemblyMirrorNetwork.Mirror mirrorDetails() {
        return received == null ? null : received.mirrors().stream()
                .filter(m -> m.pos().equals(inspectedMirror)).findFirst().orElse(null);
    }

    private void updateButtons() {
        if (sourcesTab == null || received == null) return;
        sourcesTab.selected = !mirrors; mirrorsTab.selected = mirrors;
        sourcesTab.setMessage(tr("sources_tab", received.sources().size()));
        mirrorsTab.setMessage(tr("mirrors_tab", received.mirrors().size()));
        clearButton.active = received.anchor() != null;
        previous.active = scroll > 0; next.active = scroll + visibleRows < filtered.size();
        for (int i = 0; i < rowButtons.size(); i++) {
            RowButton row = rowButtons.get(i); Entry entry = entryAt(i);
            row.visible = entry != null;
            row.active = entry != null && (mirrors || (received.online() && entry.blockedReason().isEmpty()));
            if (entry != null) {
                row.setMessage(entry.name());
                Component tip = entry.name().copy().append("\n" + coordinates(entry.pos())).append("\n").append(entry.description());
                if (!entry.blockedReason().isEmpty()) tip = tip.copy().append("\n").append(tr(entry.blockedReason()));
                if (!entry.loaded()) tip = tip.copy().append("\n").append(tr("unloaded_hint"));
                row.setTooltip(Tooltip.create(tip));
            }
        }
        var mirror = mirrorDetails(); var source = selectedSource();
        rebind.visible = unbind.visible = mirrors && mirror != null;
        boolean editable = mirror != null && mirror.loaded() && received.online();
        unbind.active = editable;
        rebind.active = editable && source != null && source.blockedReason().isEmpty()
                && !source.pos().equals(mirror.source()) && (!mirror.superBuffer() || source.superBuffer());
        rebind.setTooltip(Tooltip.create(tr(!editable ? "unloaded_hint" : source == null ? "choose_source_first"
                : mirror.superBuffer() && !source.superBuffer() ? "type_mismatch" : "rebind_hint")));
        unbind.setTooltip(Tooltip.create(tr("unbind_hint")));
    }

    @Override public void tick() {
        super.tick();
        var view = AssemblyMirrorClient.view();
        if (view == null || minecraft.player == null || minecraft.level == null
                || !minecraft.level.dimension().location().equals(view.dimension())
                || !AssemblyManagerNetwork.isHolding(minecraft.player)) { onClose(); return; }
        refresh(); search.tick();
        if (++refreshTicks >= 30) requestRefresh();
    }

    @Override public void render(GuiGraphics g, int mx, int my, float partial) {
        renderBackground(g);
        GuiTextures.BACKGROUND.draw(g, mx, my, left - 4, 5, right - left + 8, height - 10);
        g.drawString(font, title, left + 3, 13, INK, false);
        Component subtitle = tr("subtitle");
        if (width >= 420) g.drawString(font, subtitle, right - font.width(subtitle) - 3, 13, MUTED, false);
        renderNetwork(g);
        inset(g, left, bodyTop, divider - 3, bodyBottom, 0xffd5d8d6);
        inset(g, divider + 2, bodyTop, right, bodyBottom, 0xffe1e2dc);
        inset(g, left + 5, bodyTop + 4, divider - 8, bodyTop + 22, 0xff30393d);
        g.renderOutline(left + 5, bodyTop + 4, divider - left - 13, 18, search.isFocused() ? ACCENT : 0xff778285);
        text(g, tr("result_count", filtered.size()), left + 7, bodyTop + 26, divider - left - 51, MUTED);
        if (filtered.isEmpty()) {
            Component empty = received == null || received.anchor() == null ? tr("network_required")
                    : !received.online() ? tr("network_offline_hint") : tr(search.getValue().isBlank()
                    ? (mirrors ? "no_mirrors" : "no_sources") : "no_matches");
            wrapped(g, empty, left + 9, listTop + 7, divider - left - 23, bodyBottom - 6, MUTED);
        }
        renderDetails(g);
        Component footer = received != null && received.message() != null && !received.message().getString().isBlank()
                ? received.message() : tr(received != null && received.selectedSource() != null ? "ready_hint" : "select_hint");
        text(g, footer, left + 3, height - 17, right - left - 6, MUTED);
        super.render(g, mx, my, partial);
        if (search.getValue().isEmpty() && !search.isFocused())
            text(g, tr("search"), search.getX() + 2, search.getY() + 2, search.getWidth() - 5, GuiStyle.DARK_SECONDARY);
        if (my >= height - 23 && my < height - 7 && mx >= left && mx < right)
            g.renderTooltip(font, font.split(footer, Math.min(280, width - 16)), mx, my);
        if (my >= cardTop + 22 && my < cardTop + 52 && mx >= left && mx < right && received != null) {
            Component tip = received.selectedSource() == null ? tr("choose_source_first")
                    : tr("current_source", displayName(received.selectedName())).copy().append("\n" + coordinates(received.selectedSource()));
            g.renderTooltip(font, font.split(tip, Math.min(280, width - 16)), mx, my);
        }
    }

    private void renderNetwork(GuiGraphics g) {
        inset(g, left, cardTop, right, 83, 0xffd1d9d9);
        boolean bound = received != null && received.anchor() != null;
        boolean online = bound && received.online();
        g.fill(left + 7, cardTop + 8, left + 12, cardTop + 13, online ? ACCENT : 0xff916637);
        Component state = tr(!bound ? "network_unbound" : online ? "network_online" : "network_offline_title",
                bound ? received.networkName() : "");
        text(g, state, left + 17, cardTop + 7, right - left - 122, INK);
        if (!bound) {
            wrapped(g, tr("bind_hint"), left + 8, cardTop + 24, right - left - 16, 80, MUTED);
            return;
        }
        Component source = received.selectedSource() == null ? tr("no_current_source")
                : tr("current_source", displayName(received.selectedName()));
        text(g, source, left + 8, cardTop + 24, right - left - 16, ACCENT);
        Component location = received.selectedSource() == null ? tr("network_anchor", coordinates(received.anchor()))
                : tr("source_position", coordinates(received.selectedSource()));
        text(g, location, left + 8, cardTop + 39, right - left - 16, MUTED);
    }

    private void renderDetails(GuiGraphics g) {
        int x = divider + 10, y = bodyTop + 8, w = right - x - 8;
        var mirror = mirrorDetails();
        Component mirrorType = mirror == null ? tr("mirror_details") : tr(mirror.superBuffer() ? "super_mirror" : "normal_mirror");
        text(g, mirrors && mirror != null ? mirror.loaded() ? mirrorType : tr("unloaded_mirror", mirrorType)
                : tr(mirrors ? "mirror_details" : "source_details"), x, y, w, ACCENT);
        g.fill(x, y + 13, right - 9, y + 14, 0xffb4bfbe);
        if (mirrors) {
            if (mirror == null) { wrapped(g, tr("choose_mirror"), x, y + 24, w, bodyBottom - 6, MUTED); return; }
            text(g, Component.literal(coordinates(mirror.pos())), x, y + 20, w, MUTED);
            text(g, tr("mirror_source", displayName(mirror.sourceName())), x, y + 34, w, INK);
            text(g, Component.literal(coordinates(mirror.source())), x, y + 46, w, MUTED);
            if (bodyBottom - bodyTop > 132) wrapped(g, tr(!mirror.loaded() ? "unloaded_hint" : "rebind_hint"),
                    x, y + 65, w, bodyBottom - 47, MUTED);
        } else {
            var source = sourceDetails();
            if (source == null) { wrapped(g, tr("choose_source"), x, y + 24, w, bodyBottom - 6, MUTED); return; }
            text(g, displayName(source.name()), x, y + 20, w, INK);
            text(g, Component.literal(coordinates(source.pos())), x, y + 32, w, MUTED);
            text(g, tr(source.superBuffer() ? "super_source" : "normal_source"), x, y + 46, w, MUTED);
            text(g, tr("bound_mirrors", source.mirrors()), x, y + 58, w, MUTED);
            wrapped(g, source.blockedReason().isEmpty() ? tr(source.pos().equals(received.selectedSource())
                    ? "ready_hint" : "saving_hint") : tr(source.blockedReason()), x, y + 70, w, bodyBottom - 5,
                    source.blockedReason().isEmpty() ? ACCENT : GuiStyle.LIGHT_WARNING);
        }
    }

    private void text(GuiGraphics g, Component text, int x, int y, int w, int color) {
        String raw = text.getString();
        String fitted = font.width(raw) <= w ? raw : font.plainSubstrByWidth(raw, Math.max(0, w - font.width("…"))) + "…";
        g.drawString(font, fitted, x, y, color, false);
    }
    private void wrapped(GuiGraphics g, Component text, int x, int y, int w, int bottom, int color) {
        for (var line : font.split(text, w)) {
            if (y + font.lineHeight > bottom) break;
            g.drawString(font, line, x, y, color, false); y += font.lineHeight + 2;
        }
    }
    private static void inset(GuiGraphics g, int x, int y, int right, int bottom, int color) {
        g.fill(x, y, right, bottom, color);
        g.fill(x, y, right, y + 1, 0xff8a9393); g.fill(x, y, x + 1, bottom, 0xff8a9393);
        g.fill(x, bottom - 1, right, bottom, 0xfff2f2eb); g.fill(right - 1, y, right, bottom, 0xfff2f2eb);
    }

    @Override public boolean mouseScrolled(double x, double y, double amount) {
        if (x >= left && x < divider && y >= listTop && y < bodyBottom) { scroll(amount > 0 ? -1 : 1); return true; }
        return super.mouseScrolled(x, y, amount);
    }
    @Override public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (key == GLFW.GLFW_KEY_ESCAPE) { onClose(); return true; }
        if (!search.isFocused() && minecraft.options.keyInventory.matches(key, scanCode)) { onClose(); return true; }
        if (!search.isFocused() && key == GLFW.GLFW_KEY_PAGE_DOWN) { scroll(visibleRows); return true; }
        if (!search.isFocused() && key == GLFW.GLFW_KEY_PAGE_UP) { scroll(-visibleRows); return true; }
        return super.keyPressed(key, scanCode, modifiers);
    }
    @Override public void removed() {
        if (search != null) AssemblyMirrorClient.query(mirrors, search.getValue());
        super.removed();
    }
    @Override public boolean isPauseScreen() { return false; }
    private static Component displayName(String name) { return AssemblyManagerClient.displayName(name); }
    private static String coordinates(BlockPos pos) {
        return pos == null ? tr("unknown_position").getString() : pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
    }
    private record Entry(BlockPos pos, Component name, Component description, CampusPlotSearch.Aliases aliases,
                         String blockedReason, boolean loaded) {}

    private class PanelButton extends Button {
        private boolean selected;
        PanelButton(int x, int y, int w, int h, Component label, OnPress action) {
            super(x, y, w, h, label, action, DEFAULT_NARRATION);
        }
        @Override protected void renderWidget(GuiGraphics g, int mx, int my, float partial) {
            int x = getX(), y = getY(); boolean hover = isHoveredOrFocused();
            g.fill(x, y, x + getWidth(), y + getHeight(), selected ? 0xffbdd8dc : hover && active ? 0xffe9ece6 : 0xffc9ceca);
            g.renderOutline(x, y, getWidth(), getHeight(), selected || hover && active ? ACCENT : 0xff858e8d);
            if (selected) g.fill(x + 1, y + getHeight() - 3, x + getWidth() - 1, y + getHeight() - 1, ACCENT);
            String label = font.plainSubstrByWidth(getMessage().getString(), Math.max(0, getWidth() - 6));
            g.drawString(font, label, x + (getWidth() - font.width(label)) / 2, y + (getHeight() - font.lineHeight) / 2,
                    active ? INK : 0xff737c7b, false);
        }
    }
    private final class RowButton extends Button {
        private final int slot;
        RowButton(int x, int y, int w, int h, int slot) {
            super(x, y, w, h, Component.empty(), b -> choose(entryAt(slot)), DEFAULT_NARRATION);
            this.slot = slot;
        }
        @Override protected void renderWidget(GuiGraphics g, int mx, int my, float partial) {
            Entry entry = entryAt(slot);
            if (entry == null) return;
            boolean selected = entry.pos().equals(mirrors ? inspectedMirror : received.selectedSource());
            int x = getX(), y = getY(), w = getWidth(), h = getHeight();
            g.fill(x, y, x + w, y + h, selected ? 0xffbfd9dd : isHoveredOrFocused() ? 0xffe9eeea : 0xffe0e3dc);
            g.fill(x, y + h - 1, x + w, y + h, 0xffb5bfbb);
            if (selected) g.fill(x, y, x + 3, y + h - 1, ACCENT);
            if (isHoveredOrFocused()) g.renderOutline(x, y, w, h, ACCENT);
            text(g, entry.name(), x + 7, y + 4, w - 13, active ? INK : 0xff737b79);
            text(g, mirrors ? Component.literal(coordinates(entry.pos())) : entry.description(), x + 7, y + 16, w - 13, MUTED);
        }
    }
}
