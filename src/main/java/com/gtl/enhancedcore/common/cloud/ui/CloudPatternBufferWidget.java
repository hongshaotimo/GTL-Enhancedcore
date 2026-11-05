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
    private final WidgetGroup recovery = new WidgetGroup(7, 22, 162, 112);
    private final ButtonWidget[] recoveryRows = new ButtonWidget[4];
    private final ButtonWidget recoveryOpen, recoveryClaim, recoveryPrevious, recoveryNext;
    private final ImageWidget recoveryReason;
    private final ButtonWidget[] entries = new ButtonWidget[36];
    private final ImageWidget[] icons = new ImageWidget[36];
    private final ImageWidget state;
    private final ButtonWidget mode;
    private final ButtonWidget orderButton, cancelOrders, confirmCancel, resumeOrders, reconnect;
    private boolean managing, confirmingCancel, recovering;
    public CloudPatternBufferWidget(CloudPatternUiHost host) {
        super(host, "buffer", "buffer");
        addWidget(patterns); addWidget(catalysts); addWidget(orders); addWidget(cancellation); addWidget(recovery);
        state = new ImageWidget(8, 132, 99, 8, new TextTexture(this::connectionState).setColor(MUTED)
                .setDropShadow(false).setWidth(99).setType(TextTexture.TextType.LEFT_HIDE));
        state.setId("cloud.state"); state.setClientSideWidget(); addWidget(state);
        orderButton = button(this, 111, 130, 58, 10, () -> tr("orders_open"), this::showOrders);
        orderButton.setId("cloud.orders.open").setHoverTooltips(PREFIX + "orders_open", PREFIX + "orders_cpu_hint", PREFIX + "orders_origin_hint");
        recoveryOpen = button(this, 111, 130, 58, 10, () -> tr("recovery_pending", snapshot.getInt("recovery")), this::showRecovery);
        recoveryOpen.setId("cloud.recovery.open").setHoverTooltips(PREFIX + "recovery_node_hint");
        machineCatalystsButton(131, this::showPatterns);
        searchField(patterns, 98);
        mode = cycleButton(patterns, 102, 0, 60, 14, () -> type.isEmpty() ? tr("none") : recipeType(type),
                direction -> { cycleMode(direction); action("set_mode"); });
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
        resumeOrders.setId("cloud.orders.resume").setHoverTooltips(PREFIX + "orders_resume");
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
        confirmCancel.setId("cloud.orders.confirm").setHoverTooltips(PREFIX + "orders_running_loss", PREFIX + "orders_energy_loss");
        button(cancellation, 0, 96, 162, 14, () -> tr("orders_keep"), this::showOrders).setId("cloud.orders.keep");
        text(recovery, 0, 3, 140, 10, () -> tr("recovery_pending", snapshot.getInt("recovery")), INK);
        glyphButton(recovery, 146, 0, 16, 16, CloudPatternGlyphTexture.BACK, this::showOrders).setId("cloud.recovery.back");
        text(recovery, 0, 21, 162, 10, () -> snapshot.getList("recoveryEntries", Tag.TAG_COMPOUND).isEmpty() ? tr("no_recovery") : "", MUTED);
        for (int i = 0; i < recoveryRows.length; i++) {
            final int index = i;
            recoveryRows[i] = button(recovery, 0, 17 + i * 16, 162, 14, () -> recoveryLabel(index), () -> selectRecovery(index));
            recoveryRows[i].setId("cloud.recovery." + i);
        }
        recoveryReason = CloudOrderRecoveryText.line(recovery, 0, 82, 162,
                () -> CloudOrderRecoveryText.reason(selectedRecovery()), MUTED, "cloud.recovery.reason");
        recoveryPrevious = glyphButton(recovery, 0, 96, 16, 14, CloudPatternGlyphTexture.PREVIOUS, () -> changeRecoveryPage(-1));
        recoveryPrevious.setId("cloud.recovery.previous").setHoverTooltips(PREFIX + "previous_page");
        text(recovery, 20, 98, 36, 10, () -> tr("page_count", recoveryPage + 1, Math.max(1, snapshot.getInt("recoveryPages"))), MUTED);
        recoveryNext = glyphButton(recovery, 60, 96, 16, 14, CloudPatternGlyphTexture.NEXT, () -> changeRecoveryPage(1));
        recoveryNext.setId("cloud.recovery.next").setHoverTooltips(PREFIX + "next_page");
        recoveryClaim = button(recovery, 82, 96, 80, 14, () -> tr("recovery_claim_short"), this::claimRecovery);
        recoveryClaim.setId("cloud.recovery.claim");
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
        managing = false; confirmingCancel = false; recovering = false; closeCatalysts(); updatePages();
    }
    private void showOrders() {
        closeCatalysts(); managing = true; confirmingCancel = false; recovering = false; updatePages();
    }
    private void showCancelConfirmation() { confirmingCancel = true; managing = true; recovering = false; updatePages(); }
    private void showRecovery() {
        closeCatalysts(); managing = true; confirmingCancel = false; recovering = true; updatePages(); updateRecovery();
    }
    private void updatePages() {
        orders.setVisible(managing && !confirmingCancel && !recovering); cancellation.setVisible(confirmingCancel);
        recovery.setVisible(managing && recovering && !confirmingCancel);
        if (managing) { patterns.setVisible(false); catalysts.setVisible(false); }
        else { patterns.setVisible(true); catalysts.setVisible(false); }
        orderButton.setVisible(!managing);
        recoveryOpen.setVisible(managing && !confirmingCancel && !recovering);
    }
    private CompoundTag recoveryEntry(int index) {
        var rows = snapshot.getList("recoveryEntries", Tag.TAG_COMPOUND);
        return index < rows.size() ? rows.getCompound(index) : null;
    }
    private CompoundTag selectedRecovery() {
        if (recoveryId.isEmpty()) return null;
        for (Tag value : snapshot.getList("recoveryEntries", Tag.TAG_COMPOUND)) {
            var row = (CompoundTag)value;
            if (row.getString("id").equals(recoveryId)) return row;
        }
        return null;
    }
    private String recoveryLabel(int index) {
        var row = recoveryEntry(index); if (row == null) return "";
        return (row.getString("id").equals(recoveryId) ? "> " : "")
                + tr("recovery_order_pick_row", recoveryPage * 4 + index + 1, CloudOrderRecoveryText.mode(row), row.getInt("jobCount"));
    }
    private void selectRecovery(int index) {
        var row = recoveryEntry(index); if (row == null) return;
        recoveryId = row.getString("id"); updateRecovery();
    }
    private void changeRecoveryPage(int direction) {
        int next = recoveryPage + direction;
        if (next < 0 || next >= Math.max(1, snapshot.getInt("recoveryPages"))) return;
        recoveryPage = next; recoveryId = ""; request();
    }
    private void claimRecovery() {
        var row = selectedRecovery();
        if (!isRemote() || row == null || !row.getBoolean("recoverAllowed")) return;
        action("recover");
    }
    private void updateRecovery() {
        recoveryPage = Math.max(0, snapshot.getInt("recoveryPage"));
        if (!recoveryId.isEmpty() && selectedRecovery() == null) recoveryId = "";
        recoveryOpen.setActive(snapshot.getInt("recovery") > 0);
        recoveryOpen.setHoverTooltips(net.minecraft.network.chat.Component.translatable(PREFIX + "recovery_pending", snapshot.getInt("recovery")),
                net.minecraft.network.chat.Component.translatable(PREFIX + "recovery_node_hint"));
        recoveryPrevious.setActive(recoveryPage > 0);
        recoveryNext.setActive(recoveryPage + 1 < snapshot.getInt("recoveryPages"));
        for (int i = 0; i < recoveryRows.length; i++) {
            var row = recoveryEntry(i);
            recoveryRows[i].setVisible(row != null); recoveryRows[i].setActive(row != null);
            recoveryRows[i].setHoverTooltips(CloudOrderRecoveryText.tooltip(row));
        }
        var selected = selectedRecovery(); var tooltip = CloudOrderRecoveryText.tooltip(selected);
        recoveryClaim.setActive(selected != null && selected.getBoolean("recoverAllowed"));
        recoveryClaim.setHoverTooltips(tooltip); recoveryReason.setHoverTooltips(tooltip);
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
    private void cycleMode(int direction) {
        var modes = selectableModes(); if (modes.isEmpty()) return; int current = 0;
        for (int i = 0; i < modes.size(); i++) if (modes.get(i).getString("id").equals(type)) current = i;
        type = modes.get(Math.floorMod(current + direction, modes.size())).getString("id");
    }
    @Override void updated() {
        page = Math.max(0, snapshot.getInt("page"));
        var modes = selectableModes();
        if (modes.stream().noneMatch(mode -> mode.getString("id").equals(type))) type = modes.stream().filter(mode -> mode.getBoolean("current")).findFirst().orElse(modes.isEmpty() ? new CompoundTag() : modes.get(0)).getString("id");
        state.setHoverTooltips(net.minecraft.network.chat.Component.literal(status()), net.minecraft.network.chat.Component.literal(diagnostic()),
                net.minecraft.network.chat.Component.translatable(PREFIX + "active", snapshot.getInt("active")), net.minecraft.network.chat.Component.translatable(PREFIX + "queued", snapshot.getInt("queued")),
                net.minecraft.network.chat.Component.translatable(PREFIX + "native_inputs"),
                net.minecraft.network.chat.Component.translatable(PREFIX + "auxiliary_inputs"));
        mode.setHoverTooltips(net.minecraft.network.chat.Component.literal(type.isEmpty() ? tr("none") : recipeType(type)),
                net.minecraft.network.chat.Component.translatable(PREFIX + "mode_hint"));
        updateCatalystPage(patterns, catalysts);
        if (managing) { patterns.setVisible(false); catalysts.setVisible(false); }
        orders.setVisible(managing && !confirmingCancel && !recovering); cancellation.setVisible(confirmingCancel);
        recovery.setVisible(managing && recovering && !confirmingCancel);
        orderButton.setVisible(!managing); recoveryOpen.setVisible(managing && !confirmingCancel && !recovering);
        updateRecovery();
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
