package com.gtl.enhancedcore.common.cloud.ui;

import com.lowdragmc.lowdraglib.gui.texture.*;
import com.lowdragmc.lowdraglib.gui.widget.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

/** Product monitor and mode selector; middle-click opens that pattern's catalyst inventory. */
public final class CloudPatternBufferWidget extends CloudPatternPanel {
    private final WidgetGroup patterns = new WidgetGroup(7, 22, 162, 112);
    private final WidgetGroup catalysts = new WidgetGroup(7, 22, 162, 112);
    private final WidgetGroup orders = new WidgetGroup(7, 22, 162, 112);
    private final WidgetGroup cancellation = new WidgetGroup(7, 22, 162, 112);
    private final ButtonWidget[] entries = new ButtonWidget[36];
    private final ImageWidget[] icons = new ImageWidget[36];
    private final ImageWidget state;
    private final ButtonWidget mode;
    private final ButtonWidget orderButton, cancelOrders, confirmCancel, resumeOrders, reconnect;
    private boolean managing, confirmingCancel;
    public CloudPatternBufferWidget(CloudPatternUiHost host) {
        super(host, "buffer", "buffer");
        addWidget(patterns); addWidget(catalysts); addWidget(orders); addWidget(cancellation);
        state = new ImageWidget(8, 132, 99, 8, new TextTexture(this::connectionState).setColor(MUTED)
                .setDropShadow(false).setWidth(99).setType(TextTexture.TextType.LEFT_HIDE));
        state.setClientSideWidget(); addWidget(state);
        orderButton = button(this, 111, 130, 58, 10, () -> tr("orders_open"), this::showOrders);
        orderButton.setId("cloud.orders.open").setHoverTooltips(PREFIX + "orders_cpu_hint");
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
        text(orders, 0, 0, 162, 10, () -> tr("orders_count", snapshot.getInt("queued"), snapshot.getInt("active")), INK);
        text(orders, 0, 13, 162, 10, () -> tr(snapshot.getBoolean("accepting") ? "orders_accepting" : "orders_paused"), MUTED);
        text(orders, 0, 26, 162, 10, () -> tr("orders_refund_stock", snapshot.getInt("refundTypes")), INK);
        text(orders, 0, 39, 162, 10, () -> tr("orders_output_stock", snapshot.getInt("completedOutputTypes")), INK);
        cancelOrders = button(orders, 0, 53, 162, 14, () -> tr("orders_stop_refund"), this::showCancelConfirmation);
        cancelOrders.setId("cloud.orders.cancel").setHoverTooltips(PREFIX + "orders_cpu_hint", PREFIX + "orders_running_loss");
        resumeOrders = button(orders, 0, 71, 79, 14, () -> tr("orders_resume"), () -> action("resume"));
        resumeOrders.setId("cloud.orders.resume");
        reconnect = button(orders, 83, 71, 79, 14, () -> tr("orders_reconnect"), () -> action("reconnect"));
        reconnect.setId("cloud.orders.reconnect").setHoverTooltips(PREFIX + "orders_origin_hint");
        glyphButton(orders, 0, 94, 18, 14, CloudPatternGlyphTexture.BACK, this::showPatterns).setId("cloud.orders.back");
        text(orders, 24, 96, 138, 10, this::returnState, MUTED);
        text(cancellation, 0, 0, 162, 10, () -> tr("orders_confirm_title"), INK);
        text(cancellation, 0, 16, 162, 10, () -> tr("orders_unused_return"), MUTED);
        text(cancellation, 0, 27, 162, 10, () -> tr("orders_finished_return"), MUTED);
        text(cancellation, 0, 38, 162, 10, () -> tr("orders_running_loss"), INK);
        text(cancellation, 0, 49, 162, 10, () -> tr("orders_energy_loss"), MUTED);
        text(cancellation, 0, 60, 162, 10, () -> tr("orders_cpu_cancel"), MUTED);
        confirmCancel = button(cancellation, 0, 78, 162, 14, () -> tr("orders_confirm_refund"), this::cancelConfirmed);
        confirmCancel.setId("cloud.orders.confirm");
        button(cancellation, 0, 96, 162, 14, () -> tr("orders_keep"), this::showOrders).setId("cloud.orders.keep");
        catalystPage(catalysts, this::showPatterns); showPatterns();
    }
    @Override public void initWidget() {
        if (!initialized && getGui() != null) initializeCatalysts(catalysts);
        super.initWidget();
    }
    private CompoundTag entry(int index) { var list = snapshot.getList("entries", Tag.TAG_COMPOUND); return index < list.size() ? list.getCompound(index) : new CompoundTag(); }
    private String connectionState() {
        String status = snapshot.getString("status"), network = snapshot.getString("networkStatus");
        if (status.endsWith(".unsupported")) return tr("unsupported_short");
        if (status.endsWith(".quarantined") || status.endsWith(".no_mode") || status.endsWith(".origin") || status.endsWith(".paused"))
            return net.minecraft.network.chat.Component.translatable(status).getString();
        if (network.endsWith(".conflict") || network.endsWith(".denied") || network.endsWith(".busy")) return net.minecraft.network.chat.Component.translatable(network).getString();
        return snapshot.getBoolean("networkConnected") ? tr("matched_short", snapshot.getInt("matched")) : tr("buffer_connect");
    }
    private void showPatterns() {
        managing = false; confirmingCancel = false; closeCatalysts(); updatePages();
    }
    private void showOrders() {
        closeCatalysts(); managing = true; confirmingCancel = false; updatePages();
    }
    private void showCancelConfirmation() { confirmingCancel = true; managing = true; updatePages(); }
    private void updatePages() {
        orders.setVisible(managing && !confirmingCancel); cancellation.setVisible(confirmingCancel);
        if (managing) { patterns.setVisible(false); catalysts.setVisible(false); }
        else { patterns.setVisible(true); catalysts.setVisible(false); }
        orderButton.setVisible(!managing);
    }
    private void cancelConfirmed() {
        if (!isRemote()) return;
        var payload = new CompoundTag(); payload.putLong("revision", snapshot.getLong("revision")); payload.putBoolean("confirm", true);
        writeClientAction(211, buffer -> { buffer.writeUtf("cancel", 32); buffer.writeNbt(payload); });
        confirmingCancel = false; updatePages();
    }
    private String returnState() {
        if (snapshot.getInt("refundTypes") + snapshot.getInt("completedOutputTypes") == 0) return tr("orders_nothing_pending");
        return tr(snapshot.getBoolean("returnNetworkReady") ? "orders_returning" : "orders_return_waiting");
    }
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
        if (managing) { patterns.setVisible(false); catalysts.setVisible(false); }
        orders.setVisible(managing && !confirmingCancel); cancellation.setVisible(confirmingCancel);
        orderButton.setVisible(!managing);
        cancelOrders.setActive(snapshot.getInt("queued") > 0 && snapshot.getBoolean("tasksHealthy"));
        confirmCancel.setActive(snapshot.getInt("queued") > 0 && snapshot.getBoolean("tasksHealthy"));
        resumeOrders.setActive(!snapshot.getBoolean("accepting") && snapshot.getBoolean("resumeAllowed"));
        reconnect.setActive(snapshot.getBoolean("hasOrigin") && !snapshot.getBoolean("returnNetworkReady"));
        var returnHint = net.minecraft.network.chat.Component.translatable(PREFIX + (snapshot.getBoolean("returnNetworkReady")
                ? "orders_returning" : "orders_origin_hint"));
        cancelOrders.setHoverTooltips(net.minecraft.network.chat.Component.translatable(PREFIX + "orders_cpu_hint"),
                net.minecraft.network.chat.Component.translatable(PREFIX + "orders_running_loss"), returnHint);
        for (int i = 0; i < entries.length; i++) {
            var entry = entry(i); entries[i].setActive(!entry.isEmpty()); icons[i].setVisible(!entry.isEmpty());
            entries[i].setButtonTexture(entry.getString("id").equals(selected) ? CloudPatternTextures.SLOT_SELECTED : CloudPatternTextures.SLOT);
            if (entry.isEmpty()) { entries[i].setHoverTooltips(new net.minecraft.network.chat.Component[0]); continue; }
            entries[i].setHoverTooltips(CloudPatternLibraryWidget.entryTooltip(entry)); icons[i].setImage(new CloudPatternProductTexture(entry));
        }
    }
}
