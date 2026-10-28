package com.gtl.enhancedcore.common.cloud.ui;

import com.lowdragmc.lowdraglib.gui.texture.*;
import com.lowdragmc.lowdraglib.gui.widget.*;
import java.util.function.Supplier;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** Each open menu has its own query and bounded page snapshot; nothing here is block-entity sync data. */
public abstract class CloudPatternPanel extends WidgetGroup {
    static final int WIDTH = 176, HEIGHT = 140;
    static final int INK = 0xff393641, MUTED = 0xff666372, GREEN = 0xff8c78a9;
    static final String PREFIX = "gtl_enhancedcore.cloud.ui.";
    final CloudPatternUiHost host;
    CompoundTag snapshot = new CompoundTag();
    String search = "", type = "", selected = "";
    String recoveryId = "";
    String delegate = "";
    private String pendingCatalyst = "";
    private String visibleCatalyst = "";
    int recoveryPage;
    int page, tab;
    private long lastPoll = -1;
    private long lastHostChange = Long.MIN_VALUE;
    private long searchDue;
    private int requestSerial, serverRequestSerial;
    private static final long SEARCH_DELAY_NANOS = 120_000_000L;
    private CloudPatternSearch searchAliases = CloudPatternSearch.EMPTY;
    private String aliasText = "", aliasLanguageCode = "";
    private net.minecraft.locale.Language aliasLanguage;
    private CloudPatternSearch clientSearchAliases = CloudPatternSearch.EMPTY;
    private long[] aliasItems = new long[0], aliasFluids = new long[0];
    private String aliasModes = "";
    private TextFieldWidget searchInput;
    private WidgetGroup searchGroup;
    private ButtonWidget catalystMode, catalystPrevious, catalystNext;
    private SlotWidget catalystCircuit;

    CloudPatternPanel(CloudPatternUiHost host, String title, String icon) {
        super(0, 0, WIDTH, HEIGHT);
        this.host = host;
        var network = new ImageWidget(8, 7, 8, 8, () -> new ResourceTexture(
                "gtlcore:textures/gui/wireless/status_" + (snapshot.getBoolean("networkConnected") ? "online" : "offline") + ".png"));
        network.setClientSideWidget(); network.setId("cloud.me.status"); addWidget(network);
        text(this, 20, 7, title.equals("library") ? 108 : 148, 10,
                () -> tr(snapshot.getBoolean("networkConnected") ? "me_connected" : "me_disconnected"), INK);
        setId("cloud." + title);
    }

    String status() {
        if (snapshot.getBoolean("indexing")) return tr("indexing", snapshot.getInt("indexed"), snapshot.getInt("libraryTotal"));
        String key = snapshot.getString("status");
        return key.isEmpty() ? tr("loading") : Component.translatable(key).getString();
    }
    static String tr(String key, Object... args) { return Component.translatable(PREFIX + key, args).getString(); }
    public static String display(CompoundTag row, String field) {
        if (row.contains(field + "Json")) {
            try { var value = Component.Serializer.fromJson(row.getString(field + "Json")); if (value != null) return value.getString(); }
            catch (RuntimeException ignored) { }
        }
        return row.getString(field);
    }
    static String recipeType(String value) {
        if (!value.contains(":")) return value;
        var id = net.minecraft.resources.ResourceLocation.tryParse(value);
        return id == null ? value : Component.translatable(id.getNamespace() + "." + id.getPath()).getString();
    }
    String diagnostic() {
        String value = snapshot.getString("diagnostic");
        return value.startsWith("gtl_enhancedcore.") ? Component.translatable(value).getString() : value;
    }
    static void panel(WidgetGroup parent, int x, int y, int w, int h, int color) {
        parent.addWidget(new ImageWidget(x, y, w, h, new ColorRectTexture(color)).setClientSideWidget());
    }
    static void text(WidgetGroup parent, int x, int y, int w, int h, Supplier<String> value, int color) {
        parent.addWidget(new ImageWidget(x, y, w, h,
                new TextTexture(value).setColor(color).setDropShadow(false).setWidth(w).setType(TextTexture.TextType.LEFT_HIDE)).setClientSideWidget());
    }
    ButtonWidget button(WidgetGroup parent, int x, int y, int w, int h, Supplier<String> title, Runnable action) {
        var button = new ButtonWidget(x, y, w, h,
                new GuiTextureGroup(CloudPatternTextures.BUTTON,
                        new TextTexture(title).setColor(INK).setDropShadow(false).setWidth(w - 6).setType(TextTexture.TextType.HIDE)), click -> { if (isRemote()) action.run(); });
        button.setHoverTexture(new GuiTextureGroup(CloudPatternTextures.BUTTON_HOVER,
                new TextTexture(title).setColor(INK).setDropShadow(false).setWidth(w - 6).setType(TextTexture.TextType.HIDE)));
        parent.addWidget(button);
        return button;
    }
    ButtonWidget icon(WidgetGroup parent, int x, int y, IGuiTexture image, String hint, Runnable action) {
        var button = new ButtonWidget(x, y, 18, 18, new GuiTextureGroup(CloudPatternTextures.BUTTON, image), click -> { if (isRemote()) action.run(); });
        button.setHoverTexture(new GuiTextureGroup(CloudPatternTextures.BUTTON_HOVER, image));
        button.setHoverTooltips(PREFIX + hint); parent.addWidget(button); return button;
    }
    public void fillSearch(String value) {
        if (!isRemote() || value == null || !canFillSearch()) return;
        search = value.substring(0, Math.min(96, value.length())); page = 0;
        searchInput.setCurrentString(search);
        request();
    }
    public boolean canFillSearch() {
        return getGui() != null && isVisible() && isActive() && searchInput != null
                && searchGroup.isVisible() && searchGroup.isActive();
    }
    public boolean isEditingSearch() { return searchInput != null && searchInput.isFocus(); }
    public String hoveredSearchText(double mouseX, double mouseY) {
        if (!canFillSearch()) return "";
        var entries = snapshot.getList("entries", net.minecraft.nbt.Tag.TAG_COMPOUND);
        String prefix = getId().equals("cloud.library") ? "cloud.pattern." : "cloud.buffer.pattern.";
        for (int i = 0; i < Math.min(36, entries.size()); i++) {
            var widget = getGui().getFirstWidgetById(prefix + i);
            if (widget != null && widget.isActive() && widget.isMouseOverElement(mouseX, mouseY))
                return display(entries.getCompound(i), "name");
        }
        return "";
    }
    void searchField(WidgetGroup parent, int width) {
        searchGroup = parent;
        parent.addWidget(new ImageWidget(0, 0, width, 14, CloudPatternTextures.SEARCH).setClientSideWidget());
        parent.addWidget(new ImageWidget(2, 3, 9, 9, CloudPatternGlyphTexture.SEARCH).setClientSideWidget());
        var field = new TextFieldWidget(13, 0, width - 15, 14, () -> search, value -> {
            if (isRemote()) { search = value; page = 0; searchDue = System.nanoTime() + SEARCH_DELAY_NANOS; }
        }).setMaxStringLength(96).setTextColor(INK).setBordered(false);
        searchInput = field;
        field.setClientSideWidget(); field.setId("cloud.search"); parent.addWidget(field);
    }
    void pagination(WidgetGroup parent, int y) {
        text(parent, 1, y + 2, 77, 10, () -> tr("patterns_total", snapshot.contains("total") ? snapshot.getInt("total") : snapshot.getInt("matched")), MUTED);
        glyphButton(parent, 80, y, 14, 14, CloudPatternGlyphTexture.PREVIOUS,
                () -> { if (page > 0) { page--; request(); } }).setId("cloud.page.previous");
        parent.addWidget(new ImageWidget(94, y + 2, 54, 10,
                new TextTexture(() -> tr("page_count", page + 1, Math.max(1, snapshot.getInt("pages"))))
                        .setColor(MUTED).setDropShadow(false).setWidth(54).setType(TextTexture.TextType.HIDE)).setClientSideWidget());
        glyphButton(parent, 148, y, 14, 14, CloudPatternGlyphTexture.NEXT,
                () -> { if (page + 1 < snapshot.getInt("pages")) { page++; request(); } }).setId("cloud.page.next");
    }
    ButtonWidget glyphButton(WidgetGroup parent, int x, int y, int w, int h, IGuiTexture glyph, Runnable action) {
        var button = new ButtonWidget(x, y, w, h, new GuiTextureGroup(CloudPatternTextures.BUTTON, glyph),
                click -> { if (isRemote()) action.run(); });
        button.setHoverTexture(new GuiTextureGroup(CloudPatternTextures.BUTTON_HOVER, glyph));
        parent.addWidget(button); return button;
    }
    void selectPattern(CompoundTag entry, boolean configure) {
        selected = entry.getString("id");
        if (configure) {
            pendingCatalyst = selected;
            // Reopening an already confirmed entry need not wait for an identical snapshot
            // that the server's change-only synchronization intentionally does not send.
            updated();
        }
        request();
        if (configure) {
            var payload = new CompoundTag(); payload.putString("selected", selected);
            payload.putLong("revision", entry.getLong("revision"));
            writeClientAction(211, b -> { b.writeUtf("pattern_config", 32); b.writeNbt(payload); });
        }
    }
    void closeCatalysts() {
        if (!pendingCatalyst.isEmpty() || !visibleCatalyst.isEmpty()) action("close_pattern_config");
        pendingCatalyst = ""; visibleCatalyst = "";
    }
    void catalystPage(WidgetGroup parent, Runnable back) {
        parent.setId("cloud.pattern.catalysts");
        text(parent, 9, 1, 63, 10, () -> tr("item_catalysts"), INK);
        text(parent, 99, 1, 63, 10, () -> tr("fluid_catalysts"), INK);
        catalystMode = button(parent, 65, 0, 30, 14,
                () -> tr(snapshot.getBoolean("patternCatalystsShared") ? "catalysts_shared_short" : "catalysts_private_short"),
                () -> action(snapshot.getBoolean("patternCatalystsShared") ? "pattern_catalysts_private" : "pattern_catalysts_shared"));
        catalystMode.setId("cloud.catalyst.mode"); catalystMode.setVisible(false);
        catalystPrevious = glyphButton(parent, 65, 58, 14, 14, CloudPatternGlyphTexture.PREVIOUS, () -> action("pattern_catalysts_previous"));
        catalystPrevious.setId("cloud.catalyst.previous"); catalystPrevious.setVisible(false);
        catalystNext = glyphButton(parent, 82, 58, 14, 14, CloudPatternGlyphTexture.NEXT, () -> action("pattern_catalysts_next"));
        catalystNext.setId("cloud.catalyst.next"); catalystNext.setVisible(false);
        parent.addWidget(new ImageWidget(1, 75, 16, 16, new IGuiTexture() {
            private CompoundTag previous = new CompoundTag();
            private IGuiTexture image = IGuiTexture.EMPTY;
            @net.minecraftforge.api.distmarker.OnlyIn(net.minecraftforge.api.distmarker.Dist.CLIENT)
            @Override public void draw(net.minecraft.client.gui.GuiGraphics g, int mx, int my, float x, float y, int w, int h) {
                var detail = snapshot.getCompound("detail");
                if (!previous.equals(detail)) { previous = detail.copy(); image = new CloudPatternProductTexture(detail); }
                image.draw(g, mx, my, x, y, w, h);
            }
        }).setClientSideWidget());
        text(parent, 22, 78, 136, 10, () -> display(snapshot.getCompound("detail"), "name"), INK);
        glyphButton(parent, 0, 94, 18, 14, CloudPatternGlyphTexture.BACK, back).setId("cloud.pattern.back");
        text(parent, 24, 96, 138, 10, () -> tr("back_patterns"), MUTED);
        parent.setVisible(false);
    }
    void initializeCatalysts(WidgetGroup parent) {
        var player = getGui().entityPlayer;
        var access = new CloudPatternCatalystAccess(host, player,
                () -> (!isRemote() || parent.isVisible()) && snapshot.getBoolean("patternCatalystsAllowed")
                        ? snapshot.getString("catalystEntry") + ":" + snapshot.getLong("catalystAccessEpoch") : "", () -> snapshot);
        for (int i = 0; i < 9; i++) {
            parent.addWidget(new SlotWidget(access.items(), i, 9 + i % 3 * 18, 16 + i / 3 * 18, true, true)
                    .setBackgroundTexture(CloudPatternTextures.SLOT).setId("cloud.catalyst." + i));
            parent.addWidget(new TankWidget(access.fluids(), i, 99 + i % 3 * 18, 16 + i / 3 * 18, 18, 18, true, true)
                    .setBackground(CloudPatternTextures.SLOT).setShowAmount(false).setId("cloud.catalyst.fluid." + i));
        }
        catalystCircuit = new SlotWidget(access.circuits(), 0, 72, 34, true, true);
        catalystCircuit.setBackgroundTexture(CloudPatternTextures.SLOT).setId("cloud.catalyst.shared_circuit")
                .setHoverTooltips(PREFIX + "catalysts_shared_circuit");
        if (isRemote()) catalystCircuit.setVisible(false);
        parent.addWidget(catalystCircuit);
    }
    void updateCatalystPage(WidgetGroup patterns, WidgetGroup catalysts) {
        String confirmed = snapshot.getString("catalystEntry");
        boolean allowed = snapshot.getBoolean("patternCatalystsAllowed") && !confirmed.isEmpty();
        if (!pendingCatalyst.isEmpty() && allowed && pendingCatalyst.equals(confirmed)) {
            visibleCatalyst = confirmed; pendingCatalyst = "";
        }
        if (!visibleCatalyst.isEmpty() && (!allowed || !visibleCatalyst.equals(confirmed))) visibleCatalyst = "";
        catalysts.setVisible(!visibleCatalyst.isEmpty()); patterns.setVisible(visibleCatalyst.isEmpty());
        boolean shown = !visibleCatalyst.isEmpty();
        boolean shared = snapshot.getBoolean("patternCatalystsShared");
        if (catalystMode != null) {
            catalystMode.setVisible(shown && snapshot.getBoolean("patternImportedSharedAvailable"));
            catalystMode.setHoverTooltips(Component.translatable(PREFIX + (shared ? "catalysts_shared" : "catalysts_private")),
                    Component.translatable(PREFIX + "catalysts_switch_hint"), Component.translatable(PREFIX + "catalysts_shared_hint"));
            int selectedPage = snapshot.getInt("patternCatalystsPage"), pages = Math.max(1, snapshot.getInt("patternCatalystsPages"));
            catalystPrevious.setVisible(shown && shared && pages > 1); catalystNext.setVisible(shown && shared && pages > 1);
            catalystPrevious.setActive(selectedPage > 0); catalystNext.setActive(selectedPage + 1 < pages);
            var paging = Component.translatable(PREFIX + "catalysts_shared_page", selectedPage + 1, pages);
            catalystPrevious.setHoverTooltips(paging); catalystNext.setHoverTooltips(paging);
            if (catalystCircuit != null) {
                boolean circuit = shown && shared && selectedPage < snapshot.getInt("patternSharedCircuitSlots");
                catalystCircuit.setVisible(circuit); catalystCircuit.setActive(circuit);
            }
        }
    }
    void request() {
        if (!isRemote()) return;
        searchDue = 0;
        var aliases = clientAliases(search);
        int serial = ++requestSerial;
        writeClientAction(210, b -> { b.writeUtf(search, 96); b.writeUtf(type, 128); b.writeVarInt(page); b.writeUtf(selected, 128); b.writeVarInt(recoveryPage); aliases.write(b); b.writeVarInt(serial); });
    }
    @net.minecraftforge.api.distmarker.OnlyIn(net.minecraftforge.api.distmarker.Dist.CLIENT)
    private CloudPatternSearch clientAliases(String text) {
        if (text.isBlank()) return CloudPatternSearch.EMPTY;
        var language = net.minecraft.locale.Language.getInstance();
        String languageCode = net.minecraft.client.Minecraft.getInstance().options.languageCode;
        long[] items = snapshot.getLongArray("searchItems"), fluids = snapshot.getLongArray("searchFluids");
        String modesKey = snapshot.getList("types", net.minecraft.nbt.Tag.TAG_STRING).toString() + snapshot.getString("mode");
        if (text.equals(aliasText) && language == aliasLanguage && languageCode.equals(aliasLanguageCode)
                && java.util.Arrays.equals(items, aliasItems) && java.util.Arrays.equals(fluids, aliasFluids) && modesKey.equals(aliasModes)) return clientSearchAliases;
        var names = com.gtl.enhancedcore.client.cloud.CloudPatternNameSearch.find(text, java.util.BitSet.valueOf(items), java.util.BitSet.valueOf(fluids));
        var matcher = com.gtl.enhancedcore.client.cloud.CloudSearchMatcher.compile(text);
        java.util.Set<String> types = new java.util.HashSet<>();
        var modes = snapshot.getList("types", net.minecraft.nbt.Tag.TAG_STRING);
        for (var mode : modes) { String id = mode.getAsString(); if (matcher.matches(recipeType(id))) types.add(id); }
        String currentMode = snapshot.getString("mode");
        if (!currentMode.isEmpty() && matcher.matches(recipeType(currentMode))) types.add(currentMode);
        aliasText = text; aliasLanguage = language; aliasLanguageCode = languageCode;
        aliasItems = items.clone(); aliasFluids = fluids.clone();
        aliasModes = modesKey;
        return clientSearchAliases = new CloudPatternSearch(names.items(), names.fluids(), types);
    }
    void action(String name) {
        if (!isRemote()) return;
        var payload = new CompoundTag();
        payload.putLong("revision", snapshot.getLong("revision"));
        payload.putString("selected", name.startsWith("pattern_catalysts_") ? snapshot.getString("catalystEntry") : selected);
        payload.putLong("catalystAccessEpoch", snapshot.getLong("catalystAccessEpoch"));
        payload.putString("type", type);
        payload.putString("recoveryId", recoveryId);
        payload.putString("delegate", delegate);
        writeClientAction(211, b -> { b.writeUtf(name, 32); b.writeNbt(payload); });
    }
    void transferPattern(CompoundTag entry, boolean shift) {
        if (!isRemote()) return;
        var payload = new CompoundTag();
        payload.putString("selected", entry.getString("id"));
        payload.putLong("revision", entry.getLong("revision"));
        writeClientAction(211, b -> { b.writeUtf(shift ? "withdraw" : "take_pattern", 32); b.writeNbt(payload); });
    }
    boolean importCarriedPattern() {
        if (!isRemote() || getGui() == null || getGui().entityPlayer.containerMenu.getCarried().isEmpty()) return false;
        action("import_cursor");
        return true;
    }
    private void refresh(ServerPlayer player) {
        CompoundTag next = host.cloudUiSnapshot(player, search, type, page, selected, recoveryPage, searchAliases);
        if (next == null) next = new CompoundTag();
        next.putInt("requestSerial", serverRequestSerial);
        if (!next.equals(snapshot)) {
            snapshot = next.copy();
            writeUpdateInfo(212, b -> b.writeNbt(snapshot));
        }
    }
    @Override public void writeInitialData(FriendlyByteBuf buffer) {
        // LDLib writes this before ServerPlayer.containerMenu is replaced. Opening
        // reads have their own ACL/distance check; mutation checks remain menu-bound.
        if (getGui() != null && getGui().entityPlayer instanceof ServerPlayer player) {
            CompoundTag initial = host.cloudUiInitialSnapshot(player);
            snapshot = initial == null ? new CompoundTag() : initial.copy();
            lastPoll = player.serverLevel().getGameTime();
        }
        super.writeInitialData(buffer);
        buffer.writeNbt(snapshot);
    }
    @Override public void readInitialData(FriendlyByteBuf buffer) {
        super.readInitialData(buffer);
        CompoundTag initial = buffer.readNbt();
        snapshot = initial == null ? new CompoundTag() : initial;
        updated();
    }
    @Override public void detectAndSendChanges() {
        super.detectAndSendChanges();
        if (getGui() != null && getGui().entityPlayer instanceof ServerPlayer player) {
            long now = player.serverLevel().getGameTime();
            long changed = host.cloudUiChangeToken();
            if (lastHostChange != changed || lastPoll < 0 || now - lastPoll >= 10) {
                lastHostChange = changed; lastPoll = now; refresh(player);
            }
        }
    }
    @net.minecraftforge.api.distmarker.OnlyIn(net.minecraftforge.api.distmarker.Dist.CLIENT)
    @Override public void updateScreen() {
        super.updateScreen();
        if (isRemote() && searchDue != 0 && System.nanoTime() >= searchDue) request();
    }
    @Override public void handleClientAction(int id, FriendlyByteBuf buffer) {
        if (id != 210 && id != 211) { super.handleClientAction(id, buffer); return; }
        if (getGui() == null || !(getGui().entityPlayer instanceof ServerPlayer player)) return;
        if (id == 210) {
            search = buffer.readUtf(96); type = buffer.readUtf(128);
            page = Math.max(0, Math.min(1_000_000, buffer.readVarInt())); selected = buffer.readUtf(128);
            recoveryPage = Math.max(0, Math.min(1_000_000, buffer.readVarInt()));
            searchAliases = CloudPatternSearch.read(buffer);
            serverRequestSerial = buffer.readVarInt();
        } else {
            String name = buffer.readUtf(32); CompoundTag payload = buffer.readNbt();
            if (payload != null) host.cloudUiAction(player, name, payload);
        }
        refresh(player);
    }
    @Override public void readUpdateInfo(int id, FriendlyByteBuf buffer) {
        if (id != 212) { super.readUpdateInfo(id, buffer); return; }
        CompoundTag next = buffer.readNbt();
        if (next != null && next.getInt("requestSerial") != requestSerial) return;
        snapshot = next == null ? new CompoundTag() : next;
        if (searchDue == 0) page = snapshot.getInt("page");
        // A new upload may add a localized output while a nonempty query is open.
        // Refresh aliases once for the new scope instead of keeping the old filter forever.
        if (!search.isBlank() && searchDue == 0
                && (!java.util.Arrays.equals(aliasItems, snapshot.getLongArray("searchItems"))
                || !java.util.Arrays.equals(aliasFluids, snapshot.getLongArray("searchFluids"))
                || !aliasModes.equals(snapshot.getList("types", net.minecraft.nbt.Tag.TAG_STRING).toString() + snapshot.getString("mode"))))
            searchDue = System.nanoTime() + SEARCH_DELAY_NANOS;
        updated();
    }
    void updated() {}
}
