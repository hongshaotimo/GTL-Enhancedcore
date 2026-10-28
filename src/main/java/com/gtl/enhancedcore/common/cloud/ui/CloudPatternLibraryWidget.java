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
        addWidget(patterns); addWidget(catalysts);
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
        searchField(patterns, 98);
        filter = button(patterns, 102, 0, 60, 14, this::typeLabel, this::cycleType);
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
        showPatterns();
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
    private void cycleType() {
        var types = snapshot.getList("types", Tag.TAG_STRING); int index = -1;
        for (int i = 0; i < types.size(); i++) if (types.getString(i).equals(type)) index = i;
        type = index + 1 < types.size() ? types.getString(index + 1) : ""; page = 0; request();
    }
    private String typeLabel() { return type.isEmpty() ? tr("all_types_short") : recipeType(type); }
    private void showPatterns() { closeCatalysts(); patterns.setVisible(true); catalysts.setVisible(false); }
    @Override void updated() {
        page = Math.max(0, snapshot.getInt("page"));
        var stateTooltip = new java.util.ArrayList<Component>();
        stateTooltip.add(Component.literal(status()));
        String networkStatus = snapshot.getString("networkStatus");
        if (!networkStatus.isEmpty()) stateTooltip.add(Component.translatable(networkStatus));
        if (snapshot.getBoolean("networkImportRunning") || snapshot.getBoolean("networkImportFinished"))
            stateTooltip.addAll(java.util.Arrays.asList(importTooltip()));
        if (snapshot.getBoolean("networkUndoRunning") || snapshot.getBoolean("networkUndoFinished"))
            stateTooltip.addAll(java.util.Arrays.asList(undoTooltip()));
        state.setHoverTooltips(stateTooltip.toArray(Component[]::new));
        networkImport.setActive(!snapshot.getBoolean("networkImportRunning") && !snapshot.getBoolean("networkUndoRunning"));
        networkImport.setHoverTooltips(importTooltip());
        networkUndo.setActive(!snapshot.getBoolean("networkUndoRunning")
                && (snapshot.getBoolean("networkUndoAvailable") || snapshot.getBoolean("networkImportRunning")));
        networkUndo.setHoverTooltips(undoTooltip());
        filter.setHoverTooltips(Component.literal(typeLabel()));
        updateCatalystPage(patterns, catalysts);
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
