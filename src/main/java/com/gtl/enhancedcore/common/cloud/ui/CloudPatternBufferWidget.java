package com.gtl.enhancedcore.common.cloud.ui;

import com.lowdragmc.lowdraglib.gui.texture.*;
import com.lowdragmc.lowdraglib.gui.widget.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

/** Product monitor and mode selector; middle-click opens that pattern's catalyst inventory. */
public final class CloudPatternBufferWidget extends CloudPatternPanel {
    private final WidgetGroup patterns = new WidgetGroup(7, 22, 162, 112);
    private final WidgetGroup catalysts = new WidgetGroup(7, 22, 162, 112);
    private final ButtonWidget[] entries = new ButtonWidget[36];
    private final ImageWidget[] icons = new ImageWidget[36];
    private final ImageWidget state;
    private final ButtonWidget mode;
    public CloudPatternBufferWidget(CloudPatternUiHost host) {
        super(host, "buffer", "buffer");
        addWidget(patterns); addWidget(catalysts);
        state = new ImageWidget(8, 132, 160, 8, new TextTexture(this::connectionState).setColor(MUTED)
                .setDropShadow(false).setWidth(160).setType(TextTexture.TextType.LEFT_HIDE));
        state.setClientSideWidget(); addWidget(state);
        searchField(patterns, 98);
        mode = button(patterns, 102, 0, 60, 14, () -> type.isEmpty() ? tr("none") : recipeType(type), () -> { cycleMode(); action("set_mode"); });
        mode.setId("cloud.mode").setHoverTooltips(PREFIX + "mode_hint");
        for (int i = 0; i < entries.length; i++) {
            final int index = i; int x = i % 9 * 18, y = 18 + i / 9 * 18;
            entries[i] = new CloudPatternCellWidget(x, y, mouseButton -> {
                if (!isRemote()) return;
                var entry = entry(index); if (!entry.isEmpty()) selectPattern(entry, mouseButton == 2);
            }); entries[i].setId("cloud.buffer.pattern." + i); patterns.addWidget(entries[i]);
            icons[i] = new ImageWidget(x + 1, y + 1, 16, 16, IGuiTexture.EMPTY); icons[i].setClientSideWidget(); patterns.addWidget(icons[i]);
        }
        pagination(patterns, 94);
        catalystPage(catalysts, this::showPatterns); showPatterns();
    }
    @Override public void initWidget() {
        if (!initialized && getGui() != null) initializeCatalysts(catalysts);
        super.initWidget();
    }
    private CompoundTag entry(int index) { var list = snapshot.getList("entries", Tag.TAG_COMPOUND); return index < list.size() ? list.getCompound(index) : new CompoundTag(); }
    private String connectionState() {
        String status = snapshot.getString("status"), network = snapshot.getString("networkStatus");
        if (status.endsWith(".mixed_inputs")) return tr("mixed_short");
        if (status.endsWith(".unsupported")) return tr("unsupported_short");
        if (status.endsWith(".quarantined") || status.endsWith(".no_mode") || status.endsWith(".origin") || status.endsWith(".paused"))
            return net.minecraft.network.chat.Component.translatable(status).getString();
        if (network.endsWith(".conflict") || network.endsWith(".denied") || network.endsWith(".busy")) return net.minecraft.network.chat.Component.translatable(network).getString();
        return snapshot.getBoolean("networkConnected") ? tr("matched_short", snapshot.getInt("matched")) : tr("buffer_connect");
    }
    private void showPatterns() { closeCatalysts(); patterns.setVisible(true); catalysts.setVisible(false); }
    private java.util.List<CompoundTag> selectableModes() {
        var modes = new java.util.ArrayList<CompoundTag>();
        for (Tag raw : snapshot.getList("modes", Tag.TAG_COMPOUND)) { var mode = (CompoundTag) raw; if (mode.getBoolean("current") || mode.getBoolean("available")) modes.add(mode); }
        return modes;
    }
    private void cycleMode() {
        var modes = selectableModes(); if (modes.isEmpty()) return; int current = -1;
        for (int i = 0; i < modes.size(); i++) if (modes.get(i).getString("id").equals(type)) current = i;
        type = modes.get((current + 1) % modes.size()).getString("id");
    }
    @Override void updated() {
        page = Math.max(0, snapshot.getInt("page"));
        var modes = selectableModes();
        if (modes.stream().noneMatch(mode -> mode.getString("id").equals(type))) type = modes.stream().filter(mode -> mode.getBoolean("current")).findFirst().orElse(modes.isEmpty() ? new CompoundTag() : modes.get(0)).getString("id");
        state.setHoverTooltips(net.minecraft.network.chat.Component.literal(status()), net.minecraft.network.chat.Component.literal(diagnostic()),
                net.minecraft.network.chat.Component.translatable(PREFIX + "active", snapshot.getInt("active")), net.minecraft.network.chat.Component.translatable(PREFIX + "queued", snapshot.getInt("queued")));
        mode.setHoverTooltips(net.minecraft.network.chat.Component.literal(type.isEmpty() ? tr("none") : recipeType(type)),
                net.minecraft.network.chat.Component.translatable(PREFIX + "mode_hint"));
        updateCatalystPage(patterns, catalysts);
        for (int i = 0; i < entries.length; i++) {
            var entry = entry(i); entries[i].setActive(!entry.isEmpty()); icons[i].setVisible(!entry.isEmpty());
            entries[i].setButtonTexture(entry.getString("id").equals(selected) ? CloudPatternTextures.SLOT_SELECTED : CloudPatternTextures.SLOT);
            if (entry.isEmpty()) { entries[i].setHoverTooltips(new net.minecraft.network.chat.Component[0]); continue; }
            entries[i].setHoverTooltips(CloudPatternLibraryWidget.entryTooltip(entry)); icons[i].setImage(new CloudPatternProductTexture(entry));
        }
    }
}
