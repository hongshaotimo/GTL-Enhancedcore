package com.gtl.enhancedcore.common.cloud.ui;

import com.lowdragmc.lowdraglib.gui.texture.*;
import com.lowdragmc.lowdraglib.gui.widget.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;

/** A compact AE terminal: product grid, search, pagination and per-pattern catalysts. */
public final class CloudPatternLibraryWidget extends CloudPatternPanel {
    private final WidgetGroup patterns = new WidgetGroup(28, 22, 162, 112);
    private final WidgetGroup catalysts = new WidgetGroup(28, 22, 162, 112);
    private final WidgetGroup recovery = new WidgetGroup(28, 22, 162, 112);
    private final WidgetGroup recoveryDetails = new WidgetGroup(28, 22, 162, 112);
    private final java.util.List<ImageWidget> recoveryDetailLines = new java.util.ArrayList<>();
    private final ImageWidget[] recoveryRows = new ImageWidget[4];
    private final ButtonWidget[] recoveryReviews = new ButtonWidget[4];
    private final ButtonWidget recoveryEntry, recoveryPrevious, recoveryNext;
    private boolean recoveryShown, recoveryAutoOpened, recoveryDetailsShown;
    private String recoveryDetailId = "";
    private final ButtonWidget[] cards = new ButtonWidget[36];
    private final ImageWidget[] icons = new ImageWidget[36];
    private final ImageWidget state;
    private final ButtonWidget filter;
    private final ButtonWidget networkImport;
    private final ButtonWidget networkUndo;
    private DialogWidget networkConfirmation;

    public CloudPatternLibraryWidget(CloudPatternUiHost host) {
        super(host, "library", "library");
        setSizeWidth(218);
        addWidget(patterns); addWidget(catalysts); addWidget(recovery); addWidget(recoveryDetails);
        recoveryEntry = glyphButton(this, 6, 108, 18, 18, CloudPatternGlyphTexture.ORDERS, () -> {
            if (recoveryShown) showPatterns(); else showRecovery();
        });
        recoveryEntry.setId("cloud.recovery");
        state = new ImageWidget(28, 132, 160, 8, new TextTexture(this::connectionState).setColor(MUTED)
                .setDropShadow(false).setWidth(160).setType(TextTexture.TextType.LEFT_HIDE));
        state.setClientSideWidget(); addWidget(state);
        addWidget(new ImageWidget(132, 5, 14, 14, CloudPatternGlyphTexture.UPLOAD)
                .setId("cloud.import").setHoverTooltips(PREFIX + "import_direct_hint").setClientSideWidget());
        networkImport = glyphButton(this, 170, 3, 18, 18, CloudPatternGlyphTexture.UPLOAD, this::confirmNetworkImport);
        networkImport.setId("cloud.network.import").setHoverTooltips(PREFIX + "network_import");
        networkUndo = glyphButton(this, 190, 3, 18, 18, CloudPatternGlyphTexture.UNDO, this::confirmNetworkUndo);
        networkUndo.setId("cloud.network.undo").setHoverTooltips(PREFIX + "network_undo");
        networkUndo.setActive(false);
        machineCatalystsButton(110, this::showPatterns);
        searchField(patterns, 98);
        filter = cycleButton(patterns, 102, 0, 60, 14, this::typeLabel, this::cycleType);
        filter.setId("cloud.type");
        for (int i = 0; i < cards.length; i++) {
            final int index = i; int x = i % 9 * 18, y = 18 + i / 9 * 18;
            cards[i] = new CloudPatternCellWidget(x, y, mouseButton -> {
                if (!isRemote()) return;
                if (mouseButton == 0 && importCarriedPattern()) return;
                var entry = entry(index);
                if (entry == null) return;
                if (mouseButton == 2) selectPattern(entry, true);
                else if (mouseButton == 0) transferPattern(entry, net.minecraft.client.gui.screens.Screen.hasShiftDown());
            });
            cards[i].setId("cloud.pattern." + i); patterns.addWidget(cards[i]);
            icons[i] = new ImageWidget(x + 1, y + 1, 16, 16, IGuiTexture.EMPTY);
            icons[i].setClientSideWidget(); patterns.addWidget(icons[i]);
        }
        pagination(patterns, 94);
        catalystPage(catalysts, this::showPatterns);
        text(recovery, 1, 3, 140, 10, () -> tr("recovery_pending", snapshot.getInt("recovery")), INK);
        glyphButton(recovery, 146, 0, 16, 16, CloudPatternGlyphTexture.BACK, this::showPatterns)
                .setId("cloud.recovery.back").setHoverTooltips(PREFIX + "back_patterns");
        for (int i = 0; i < recoveryRows.length; i++) {
            final int index = i; int y = 17 + i * 18;
            recoveryRows[i] = new ImageWidget(0, y, 108, 16, new GuiTextureGroup(CloudPatternTextures.RECESS,
                    new TextTexture(() -> recoveryLabel(index).replace("%", "%%")).setColor(INK).setDropShadow(false)
                            .setWidth(102).setType(TextTexture.TextType.HIDE)));
            recoveryRows[i].setClientSideWidget(); recoveryRows[i].setId("cloud.recovery.row." + i); recovery.addWidget(recoveryRows[i]);
            recoveryReviews[i] = button(recovery, 110, y, 52, 16,
                    () -> tr(isTransfer(recoveryEntry(index)) ? "recovery_review_short" : "recovery_order_details_short"),
                    () -> reviewRecovery(index));
            recoveryReviews[i].setId("cloud.recovery.review." + i);
        }
        recoveryPrevious = glyphButton(recovery, 0, 88, 16, 16, CloudPatternGlyphTexture.PREVIOUS, () -> changeRecoveryPage(-1));
        recoveryPrevious.setId("cloud.recovery.previous").setHoverTooltips(PREFIX + "previous_page");
        text(recovery, 20, 91, 122, 10, () -> tr("page_count", recoveryPage + 1, Math.max(1, snapshot.getInt("recoveryPages"))), MUTED);
        recoveryNext = glyphButton(recovery, 146, 88, 16, 16, CloudPatternGlyphTexture.NEXT, () -> changeRecoveryPage(1));
        recoveryNext.setId("cloud.recovery.next").setHoverTooltips(PREFIX + "next_page");
        text(recoveryDetails, 0, 3, 140, 10, () -> tr("recovery_order_details_title"), INK);
        glyphButton(recoveryDetails, 146, 0, 16, 16, CloudPatternGlyphTexture.BACK, this::showRecovery)
                .setId("cloud.recovery.details.back");
        recoveryDetailLines.add(CloudOrderRecoveryText.line(recoveryDetails, 0, 19, 162,
                () -> tr("recovery_order_status", CloudOrderRecoveryText.status(detailEntry())), INK, "cloud.recovery.details.status"));
        recoveryDetailLines.add(CloudOrderRecoveryText.line(recoveryDetails, 0, 32, 162,
                () -> tr("recovery_order_machine", CloudOrderRecoveryText.machine(detailEntry())), INK, "cloud.recovery.details.machine"));
        recoveryDetailLines.add(CloudOrderRecoveryText.line(recoveryDetails, 0, 45, 162,
                () -> tr("recovery_order_mode", CloudOrderRecoveryText.mode(detailEntry())), INK, "cloud.recovery.details.mode"));
        recoveryDetailLines.add(CloudOrderRecoveryText.line(recoveryDetails, 0, 58, 162,
                () -> tr("recovery_order_location", CloudOrderRecoveryText.value(detailEntry(), "sourceDimension"),
                        CloudOrderRecoveryText.value(detailEntry(), "sourcePos")), INK, "cloud.recovery.details.location"));
        recoveryDetailLines.add(CloudOrderRecoveryText.line(recoveryDetails, 0, 71, 162,
                () -> tr("recovery_order_jobs", detailEntry() == null ? 0 : detailEntry().getInt("jobCount")), INK, "cloud.recovery.details.jobs"));
        text(recoveryDetails, 0, 87, 162, 10, () -> tr("recovery_order_hint_1"), MUTED);
        text(recoveryDetails, 0, 100, 162, 10, () -> tr("recovery_order_hint_2"), MUTED);
        showPatterns();
    }
    private CompoundTag recoveryEntry(int index) {
        var entries = snapshot.getList("recoveryEntries", Tag.TAG_COMPOUND);
        return index < entries.size() ? entries.getCompound(index) : null;
    }
    private static boolean isTransfer(CompoundTag entry) { return entry != null && entry.getString("kind").equals("transfer"); }
    private String recoveryLabel(int index) {
        var entry = recoveryEntry(index);
        if (entry == null) return index == 0 ? tr("no_recovery") : "";
        return tr(isTransfer(entry) ? "recovery_transfer_row" : "recovery_order_row",
                recoveryPage * 4 + index + 1, isTransfer(entry) ? entry.getString("status") : CloudOrderRecoveryText.status(entry));
    }
    private void showRecovery() {
        closeCatalysts(); recoveryShown = true; recoveryDetailsShown = false;
        patterns.setVisible(false); catalysts.setVisible(false); recovery.setVisible(true); recoveryDetails.setVisible(false);
    }
    private void changeRecoveryPage(int direction) {
        int next = recoveryPage + direction;
        if (next >= 0 && next < Math.max(1, snapshot.getInt("recoveryPages"))) { recoveryPage = next; request(); }
    }
    private void reviewRecovery(int index) {
        var entry = recoveryEntry(index);
        if (entry == null) return;
        if (!isTransfer(entry)) {
            recoveryDetailId = entry.getString("id"); recoveryDetailsShown = true;
            recovery.setVisible(false); recoveryDetails.setVisible(true);
            var tooltip = CloudOrderRecoveryText.tooltip(entry);
            for (var line : recoveryDetailLines) line.setHoverTooltips(tooltip);
            return;
        }
        if (!snapshot.getBoolean("recoveryOwner") || snapshot.getBoolean("recoveryBusy")) return;
        recoveryId = entry.getString("operation");
        if (recoveryId.isEmpty()) recoveryId = entry.getString("id");
        action("review_transfer");
    }
    private CompoundTag detailEntry() {
        for (Tag value : snapshot.getList("recoveryEntries", Tag.TAG_COMPOUND)) {
            var entry = (CompoundTag)value;
            if (entry.getString("id").equals(recoveryDetailId) && !isTransfer(entry)) return entry;
        }
        return null;
    }
    private Component[] recoveryTooltip(CompoundTag entry) {
        var lines = new java.util.ArrayList<Component>();
        if (entry == null) {
            lines.add(Component.translatable(PREFIX + (snapshot.getBoolean("recoveryIndexing") ? "recovery_indexing" : "no_recovery")));
        } else if (!isTransfer(entry)) {
            lines.add(Component.translatable(PREFIX + "recovery_order_details_short"));
            lines.addAll(java.util.Arrays.asList(CloudOrderRecoveryText.tooltip(entry)));
            lines.add(Component.translatable(PREFIX + "recovery_original_node"));
        } else {
            lines.add(Component.translatable(PREFIX + "review_transfer"));
            lines.add(Component.translatable(PREFIX + "recovery_operation", entry.getString("operation")));
            lines.add(Component.translatable(PREFIX + "recovery_record_status", entry.getString("status"), entry.getString("journalStatus")));
            if (!entry.getString("sourcePos").isEmpty()) lines.add(Component.translatable(PREFIX + "source",
                    entry.getString("sourceDimension"), entry.getString("sourcePos")));
            if (!entry.getString("expectedName").isEmpty()) lines.add(Component.translatable(PREFIX + "recovery_expected", entry.getString("expectedName")));
            if (entry.contains("actualName")) lines.add(Component.translatable(PREFIX + "recovery_actual",
                    entry.getString("actualName").isEmpty() ? tr("recovery_empty_slot") : entry.getString("actualName")));
            String evidence = entry.getString("evidence");
            if (!evidence.isEmpty()) lines.add(Component.translatable(evidence));
            if (!entry.getString("failure").isEmpty()) lines.add(Component.translatable(PREFIX + "recovery_diagnostic_detail", entry.getString("failure")));
            if (!snapshot.getBoolean("recoveryOwner")) lines.add(Component.translatable(PREFIX + "recovery_owner_only"));
            else if (snapshot.getBoolean("recoveryBusy")) lines.add(Component.translatable(PREFIX + "recovery_busy"));
        }
        if (entry == null || isTransfer(entry)) lines.add(Component.translatable(PREFIX + "recovery_safe_hint"));
        if (entry == null) addDiagnostics(lines);
        else {
            lines.add(Component.translatable(PREFIX + (snapshot.getBoolean("networkConnected") ? "me_connected" : "me_disconnected")));
            if (snapshot.getBoolean("importQuarantined")) lines.add(Component.translatable(PREFIX + "recovery_quarantined"));
        }
        return lines.toArray(Component[]::new);
    }
    private void addDiagnostics(java.util.List<Component> lines) {
        lines.add(Component.translatable(PREFIX + (snapshot.getBoolean("networkConnected") ? "me_connected" : "me_disconnected")));
        if (snapshot.getBoolean("importQuarantined")) lines.add(Component.translatable(PREFIX + "recovery_quarantined"));
        String reason = snapshot.getString("quarantineReason");
        if (!reason.isEmpty()) lines.add(Component.translatable(PREFIX + "recovery_quarantine_reason", reason));
        String detail = snapshot.getString("diagnosticDetail");
        if (!diagnostic().isEmpty()) lines.add(Component.literal(diagnostic()));
        if (!detail.isEmpty()) lines.add(Component.translatable(PREFIX + "recovery_diagnostic_detail", detail));
    }
    private void confirmNetworkImport() {
        if (snapshot.getBoolean("networkImportRunning") || snapshot.getBoolean("networkUndoRunning")) return;
        confirmNetworkAction(false);
    }
    private void confirmNetworkUndo() {
        if (snapshot.getBoolean("networkUndoRunning")
                || (!snapshot.getBoolean("networkUndoAvailable") && !snapshot.getBoolean("networkImportRunning"))) return;
        confirmNetworkAction(true);
    }
    private void confirmNetworkAction(boolean undo) {
        if (!isRemote() || getGui() == null || networkConfirmation != null) return;
        String dialogId = undo ? "cloud.network.undo.confirm" : "cloud.network.confirm";
        String title = undo ? "network_undo" : "network_import";
        var root = getGui().mainGroup;
        var dialog = new DialogWidget(0, 0, root.getSizeWidth(), root.getSizeHeight()) {
            @Override public boolean mouseClicked(double x, double y, int button) { if (button == 0) super.mouseClicked(x, y, button); return true; }
            @Override public boolean mouseReleased(double x, double y, int button) { super.mouseReleased(x, y, button); return true; }
            @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) { return true; }
            @Override public boolean mouseWheelMove(double x, double y, double delta) { return true; }
            @Override public boolean keyPressed(int key, int scan, int modifiers) {
                if (key == 256) dismissNetworkConfirmation();
                return true;
            }
            @Override public boolean keyReleased(int key, int scan, int modifiers) { return true; }
            @Override public boolean charTyped(char value, int modifiers) { return true; }
        };
        networkConfirmation = dialog;
        dialog.setClientSideWidget(); dialog.setId(dialogId);
        panel(dialog, 0, 0, root.getSizeWidth(), root.getSizeHeight(), 0x88000000);
        int height = undo ? 148 : 128;
        var body = new WidgetGroup((root.getSizeWidth() - 176) / 2, Math.max(4, (root.getSizeHeight() - height) / 2), 176, height);
        body.setClientSideWidget(); dialog.addWidget(body);
        body.addWidget(new ImageWidget(0, 0, 176, height, CloudPatternTextures.BUTTON).setClientSideWidget());
        text(body, 8, 8, 160, 12, () -> tr(title), INK);
        body.addWidget(new ImageWidget(8, 25, 160, height - 55, new TextTexture(tr(title + "_confirm"))
                .setColor(INK).setDropShadow(false).setWidth(160).setType(TextTexture.TextType.NORMAL)).setClientSideWidget());
        button(body, 12, height - 24, 66, 16, () -> tr("yes"), () -> {
            if (networkConfirmation != dialog) return;
            dismissNetworkConfirmation(); action(undo ? "network_import_undo" : "network_import");
        }).setId(dialogId + ".yes");
        button(body, 98, height - 24, 66, 16, () -> tr("no"), () -> {
            if (networkConfirmation == dialog) dismissNetworkConfirmation();
        }).setId(dialogId + ".no");
        var search = getGui().getFirstWidgetById("cloud.search");
        if (search != null) search.setFocus(false);
        root.addWidget(dialog);
    }
    private void dismissNetworkConfirmation() {
        var dialog = networkConfirmation;
        if (dialog == null) return;
        networkConfirmation = null;
        dialog.setVisible(false); dialog.setActive(false); getGui().mainGroup.removeWidget(dialog);
    }
    @Override public boolean canFillSearch() {
        return networkConfirmation == null && super.canFillSearch();
    }
    @Override public void initWidget() {
        if (!initialized && getGui() != null) {
            initializeCatalysts(catalysts);
            var input = host.cloudImportInventory(getGui().entityPlayer);
            if (input != null && input.getSlots() > 0) addWidget(new SlotWidget(input, 0, 150, 3, true, true)
                    .setBackgroundTexture(CloudPatternTextures.SLOT).setId("cloud.import.slot")
                    .setHoverTooltips(PREFIX + "import_direct_hint"));
        }
        super.initWidget();
    }
    private String connectionState() {
        if (snapshot.getBoolean("importQuarantined")) return tr("recovery_quarantined_short", snapshot.getInt("recovery"));
        if (snapshot.getInt("recovery") > 0) return tr("recovery_pending", snapshot.getInt("recovery"));
        if (snapshot.getString("diagnostic").endsWith(".uploading"))
            return Component.translatable(snapshot.getString("diagnostic")).getString();
        if (snapshot.getBoolean("networkUndoRunning")) return undoState();
        String network = snapshot.getString("networkStatus");
        if (network.endsWith(".conflict") || network.endsWith(".denied") || network.endsWith(".busy")) return Component.translatable(network).getString();
        if (snapshot.getBoolean("networkImportRunning")) return importState(true);
        if (snapshot.getBoolean("networkUndoFinished")) return undoState();
        if (snapshot.getBoolean("networkImportFinished")) return importState(true);
        return snapshot.getBoolean("networkConnected") ? tr("connected_short", snapshot.getInt("libraryTotal")) : tr("library_connect");
    }
    private String importState(boolean detail) {
        String key = snapshot.getBoolean("networkImportRunning") ? "network_import_progress"
                : snapshot.getString("networkImportStopReason").isEmpty() ? "network_import_done" : "network_import_stopped";
        int moved = snapshot.getInt("networkImportMoved"), skipped = snapshot.getInt("networkImportSkipped");
        int duplicates = snapshot.getCompound("networkImportReasons").getInt("duplicate");
        return detail ? tr(key + "_detail", moved, duplicates, Math.max(0, skipped - duplicates)) : tr(key, moved, skipped);
    }
    private String undoState() {
        return tr(snapshot.getBoolean("networkUndoRunning") ? "network_undo_progress" : "network_undo_done",
                snapshot.getInt("networkUndoReturned"), snapshot.getInt("networkUndoPending"));
    }
    private Component[] importTooltip() {
        var lines = new java.util.ArrayList<Component>();
        lines.add(Component.translatable(PREFIX + "network_import"));
        if (snapshot.getBoolean("networkImportRunning") || snapshot.getBoolean("networkImportFinished")) {
            lines.add(Component.literal(importState(false)));
            var reasons = snapshot.getCompound("networkImportReasons");
            int duplicates = reasons.getInt("duplicate");
            lines.add(Component.translatable(PREFIX + "network_import_counts", duplicates,
                    Math.max(0, snapshot.getInt("networkImportSkipped") - duplicates)).withStyle(net.minecraft.ChatFormatting.GRAY));
            for (String reason : new java.util.TreeSet<>(reasons.getAllKeys())) {
                int count = reasons.getInt(reason);
                if (count > 0) lines.add(Component.translatable(PREFIX + "network_import_reason_count",
                        Component.translatable(PREFIX + "network_import_reason_" + reasonKey(reason)), count)
                        .withStyle(net.minecraft.ChatFormatting.GRAY));
            }
            String stopped = snapshot.getString("networkImportStopReason");
            if (!stopped.isEmpty()) lines.add(Component.translatable(PREFIX + "network_import_stop",
                    Component.translatable(PREFIX + "network_import_reason_" + reasonKey(stopped))).withStyle(net.minecraft.ChatFormatting.GOLD));
        }
        lines.add(Component.translatable(PREFIX + "network_import_hint").withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
        return lines.toArray(Component[]::new);
    }
    private static String reasonKey(String reason) {
        return switch (reason) {
            case "duplicate", "busy", "materials", "proxies", "foa", "permission", "disconnected",
                    "unsupported", "unextractable", "changed", "review", "crafting", "cancelled", "shared_dependency" -> reason;
            default -> "other";
        };
    }
    private Component[] undoTooltip() {
        var lines = new java.util.ArrayList<Component>();
        lines.add(Component.translatable(PREFIX + "network_undo"));
        if (snapshot.getBoolean("networkUndoRunning") || snapshot.getBoolean("networkUndoFinished"))
            lines.add(Component.literal(undoState()));
        if (!snapshot.getBoolean("networkUndoAvailable") && !snapshot.getBoolean("networkImportRunning")
                && !snapshot.getBoolean("networkUndoRunning"))
            lines.add(Component.translatable(PREFIX + "network_undo_unavailable").withStyle(net.minecraft.ChatFormatting.GRAY));
        lines.add(Component.translatable(PREFIX + "network_undo_hint").withStyle(net.minecraft.ChatFormatting.GRAY));
        return lines.toArray(Component[]::new);
    }
    private CompoundTag entry(int index) { var entries = snapshot.getList("entries", Tag.TAG_COMPOUND); return index < entries.size() ? entries.getCompound(index) : null; }
    private void cycleType(int direction) {
        var types = snapshot.getList("types", Tag.TAG_STRING); int index = -1;
        for (int i = 0; i < types.size(); i++) if (types.getString(i).equals(type)) index = i;
        int next = Math.floorMod(index + 1 + direction, types.size() + 1);
        type = next == 0 ? "" : types.getString(next - 1); page = 0; request();
    }
    private String typeLabel() { return type.isEmpty() ? tr("all_types_short") : recipeType(type); }
    private void showPatterns() {
        closeCatalysts(); recoveryShown = false; recoveryDetailsShown = false;
        recovery.setVisible(false); recoveryDetails.setVisible(false); patterns.setVisible(true); catalysts.setVisible(false);
    }
    @Override void updated() {
        page = Math.max(0, snapshot.getInt("page"));
        recoveryPage = Math.max(0, snapshot.getInt("recoveryPage"));
        var stateTooltip = new java.util.ArrayList<Component>();
        stateTooltip.add(Component.literal(status()));
        addDiagnostics(stateTooltip);
        String networkStatus = snapshot.getString("networkStatus");
        if (!networkStatus.isEmpty()) stateTooltip.add(Component.translatable(networkStatus));
        if (snapshot.getBoolean("networkImportRunning") || snapshot.getBoolean("networkImportFinished"))
            stateTooltip.addAll(java.util.Arrays.asList(importTooltip()));
        if (snapshot.getBoolean("networkUndoRunning") || snapshot.getBoolean("networkUndoFinished"))
            stateTooltip.addAll(java.util.Arrays.asList(undoTooltip()));
        state.setHoverTooltips(stateTooltip.toArray(Component[]::new));
        networkImport.setActive(!snapshot.getBoolean("importQuarantined") && !snapshot.getBoolean("networkImportRunning") && !snapshot.getBoolean("networkUndoRunning"));
        var importLines = new java.util.ArrayList<Component>(java.util.Arrays.asList(importTooltip()));
        if (snapshot.getBoolean("importQuarantined")) addDiagnostics(importLines);
        networkImport.setHoverTooltips(importLines.toArray(Component[]::new));
        networkUndo.setActive(!snapshot.getBoolean("networkUndoRunning")
                && (snapshot.getBoolean("networkUndoAvailable") || snapshot.getBoolean("networkImportRunning")));
        networkUndo.setHoverTooltips(undoTooltip());
        filter.setHoverTooltips(Component.literal(typeLabel()), Component.translatable(PREFIX + "type_hint"));
        updateCatalystPage(patterns, catalysts);
        var recoveryLines = new java.util.ArrayList<Component>();
        recoveryLines.add(Component.translatable(PREFIX + "recovery_pending", snapshot.getInt("recovery")));
        if (snapshot.getBoolean("recoveryIndexing")) recoveryLines.add(Component.translatable(PREFIX + "recovery_indexing"));
        recoveryLines.add(Component.translatable(PREFIX + "recovery_open_hint"));
        addDiagnostics(recoveryLines);
        recoveryEntry.setHoverTooltips(recoveryLines.toArray(Component[]::new));
        if (!recoveryAutoOpened && (snapshot.getInt("recovery") > 0 || snapshot.getBoolean("importQuarantined"))) {
            recoveryAutoOpened = true; showRecovery();
        }
        if (recoveryDetailsShown && detailEntry() == null) recoveryDetailsShown = false;
        recovery.setVisible(recoveryShown && !recoveryDetailsShown);
        recoveryDetails.setVisible(recoveryShown && recoveryDetailsShown);
        if (recoveryDetailsShown) for (var line : recoveryDetailLines) line.setHoverTooltips(CloudOrderRecoveryText.tooltip(detailEntry()));
        if (recoveryShown) { patterns.setVisible(false); catalysts.setVisible(false); }
        recoveryPrevious.setActive(recoveryPage > 0);
        recoveryNext.setActive(recoveryPage + 1 < snapshot.getInt("recoveryPages"));
        for (int i = 0; i < recoveryRows.length; i++) {
            var entry = recoveryEntry(i);
            recoveryRows[i].setVisible(entry != null || i == 0);
            recoveryRows[i].setHoverTooltips(recoveryTooltip(entry));
            recoveryReviews[i].setVisible(entry != null);
            recoveryReviews[i].setActive(entry != null && (!isTransfer(entry)
                    || snapshot.getBoolean("recoveryOwner") && !snapshot.getBoolean("recoveryBusy")));
            recoveryReviews[i].setHoverTooltips(recoveryTooltip(entry));
        }
        for (int i = 0; i < cards.length; i++) {
            var entry = entry(i); cards[i].setActive(true); icons[i].setVisible(entry != null);
            if (entry == null) { cards[i].setButtonTexture(CloudPatternTextures.SLOT); cards[i].setHoverTooltips(new Component[0]); continue; }
            cards[i].setButtonTexture(entry.getString("id").equals(selected) ? CloudPatternTextures.SLOT_SELECTED : CloudPatternTextures.SLOT);
            var tooltip = new java.util.ArrayList<Component>(java.util.Arrays.asList(entryTooltip(entry)));
            tooltip.add(Component.translatable(PREFIX + "take_pattern_hint").withStyle(net.minecraft.ChatFormatting.GRAY));
            cards[i].setHoverTooltips(tooltip.toArray(Component[]::new)); icons[i].setImage(new CloudPatternProductTexture(entry));
        }
    }
    static Component[] entryTooltip(CompoundTag entry) {
        var lines = new java.util.ArrayList<Component>(); lines.add(Component.literal(display(entry, "name")));
        lines.add(Component.translatable(PREFIX + (entry.getBoolean("leased") ? "leased" : "available"))
                .withStyle(entry.getBoolean("leased") ? net.minecraft.ChatFormatting.GRAY : net.minecraft.ChatFormatting.GREEN));
        String types = entry.getList("types", Tag.TAG_STRING).stream().map(value -> recipeType(value.getAsString())).collect(java.util.stream.Collectors.joining(", "));
        if (types.isEmpty()) types = recipeType(entry.getString("type"));
        if (!types.isEmpty()) lines.add(Component.translatable(PREFIX + "metadata_types", types).withStyle(net.minecraft.ChatFormatting.GRAY));
        if (entry.getBoolean("catalystsOnly")) lines.add(Component.translatable(PREFIX + "catalysts_retained").withStyle(net.minecraft.ChatFormatting.GOLD));
        if (!entry.getString("author").isEmpty()) lines.add(Component.translatable(PREFIX + "metadata_author", entry.getString("author")).withStyle(net.minecraft.ChatFormatting.GRAY));
        if (entry.getBoolean("hasCircuit")) lines.add(Component.translatable(PREFIX + "metadata_circuit", entry.getInt("circuit")).withStyle(net.minecraft.ChatFormatting.GRAY));
        if (!entry.getString("target").isEmpty()) lines.add(Component.literal(entry.getString("target")).withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
        if (!entry.getString("diagnostic").isEmpty()) lines.add(Component.translatable(entry.getString("diagnostic")).withStyle(net.minecraft.ChatFormatting.RED));
        lines.add(Component.translatable(PREFIX + "catalyst_hint").withStyle(net.minecraft.ChatFormatting.GRAY));
        return lines.toArray(Component[]::new);
    }
}
