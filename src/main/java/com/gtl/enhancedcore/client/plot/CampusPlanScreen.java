package com.gtl.enhancedcore.client.plot;

import com.gtl.enhancedcore.common.gui.GuiStyle;
import com.gtl.enhancedcore.common.world.campus.CampusPlanAccess;
import com.gtl.enhancedcore.common.world.campus.CampusTeleportNetwork;
import com.gtl.enhancedcore.common.world.plot.CompactPlannedCampus;
import com.gtl.enhancedcore.common.world.plot.DensePlannedCampus;
import com.gtl.enhancedcore.common.world.plot.LegacyPlannedCampus;
import com.gtl.enhancedcore.common.world.plot.ParkPlannedCampus;
import com.gtl.enhancedcore.common.world.plot.PlannedCampus;
import com.gtl.enhancedcore.common.world.plot.PlotSettings;
import com.gtl.enhancedcore.common.world.plot.PlotPreviewRaster;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** A movable atlas; live device mode keeps its server-owned session separate from preview mode. */
public final class CampusPlanScreen extends Screen {
    private record TeleportSession(BlockPos origin, ResourceLocation dimension, UUID id) {}
    private final Screen parent;
    private final PlotSettings settings;
    private final CampusPlanAccess.Plan plan;
    private final PlannedCampus.Bounds worldBounds, aeBounds;
    private final TeleportSession session;
    private final PlotPreviewRenderer.AtlasCached map = new PlotPreviewRenderer.AtlasCached();
    private double zoom = 1, centerX, centerZ;
    private int mapLeftEdge, mapRightEdge, mapTop, mapBottom, baseSize;
    private CampusPlanAccess.Target selected, pressedTarget;
    private long lastClickAt;
    private int lastClickedIndex = -1;
    private double pressX, pressY;
    private boolean pointerPressed, dragged, pending, sessionClosed, sessionInvalid, statusVisible;
    private Component status;
    private EditBox searchBox;
    private Button previousMatch, nextMatch;
    private String searchText = "";
    private List<CampusPlanAccess.Target> searchMatches = List.of();
    private final Map<Integer, List<String>> searchAliases = new HashMap<>();
    private int searchIndex = -1, searchStatusY;

    public CampusPlanScreen(Screen parent, PlotSettings settings) { this(parent, settings, null); }
    private CampusPlanScreen(Screen parent, PlotSettings settings, TeleportSession session) {
        super(session == null ? tr("campus_open_plan") : tp("title"));
        this.parent = parent; this.settings = settings; this.session = session;
        plan = CampusPlanAccess.of(settings); worldBounds = plan.bounds(); aeBounds = plan.aeBounds();
        overview(); status = tp("hint");
    }
    public static void openTeleporter(PlotSettings settings, BlockPos origin, ResourceLocation dimension, UUID sessionId) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || !minecraft.level.dimension().location().equals(dimension)) {
            CampusTeleportNetwork.close(sessionId); return;
        }
        minecraft.setScreen(new CampusPlanScreen(null, settings, new TeleportSession(origin, dimension, sessionId)));
    }
    public static void receiveTeleportResult(UUID sessionId, CampusTeleportNetwork.Result result) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!(minecraft.screen instanceof CampusPlanScreen screen) || screen.session == null || !screen.session.id.equals(sessionId)) return;
        screen.pending = false;
        if (result == CampusTeleportNetwork.Result.SUCCESS) {
            screen.sessionClosed = true; minecraft.setScreen(screen.parent); return;
        }
        screen.status = tp("result." + result.name().toLowerCase(java.util.Locale.ROOT));
        screen.sessionInvalid = result == CampusTeleportNetwork.Result.EXPIRED || result == CampusTeleportNetwork.Result.INVALID_DEVICE;
        screen.statusVisible = true;
    }
    private static Component tr(String key, Object... args) {
        return Component.translatable("gtl_enhancedcore.create_world_plot." + key, args);
    }
    private static Component tp(String key, Object... args) {
        return Component.translatable("gtl_enhancedcore.campus.teleport." + key, args);
    }
    @Override protected void init() {
        pointerPressed = false; dragged = false; pressedTarget = null; lastClickAt = 0;
        if (session != null) {
            baseSize = Math.max(20, Math.min((int) (Math.min(width, height) * .75), height - 60));
            mapLeftEdge = (width - baseSize) / 2; mapRightEdge = mapLeftEdge + baseSize;
            mapTop = Math.max(48, (height - baseSize) / 2); mapBottom = mapTop + baseSize;
            initSearch(8);
            return;
        }
        mapLeftEdge = 12; mapRightEdge = width - 12;
        mapTop = 72; mapBottom = height - 66;
        baseSize = Math.max(20, Math.min(width - 24, mapBottom - mapTop - 8));
        String[] keys = {"campus_overview_short", "campus_ae_short", "campus_early", "campus_mid", "campus_late_north", "campus_late_south"};
        int buttonWidth = Math.min(100, (width - 24) / keys.length);
        for (int i = 0; i < keys.length; i++) {
            int index = i;
            addRenderableWidget(Button.builder(tr(keys[i]), button -> {
                selected = null; lastClickAt = 0;
                if (index == 0) overview();
                else if (index == 1) focus(aeBounds);
                else focusStage(new String[] {"early", "mid", "late_north", "late_south"}[index - 2]);
            }).bounds(12 + i * buttonWidth, 8, buttonWidth - 3, GuiStyle.CONTROL).build());
        }
        addRenderableWidget(Button.builder(Component.translatable("gui.back"), button -> onClose())
                .bounds(width - 66, height - 25, 54, GuiStyle.CONTROL).build());
        initSearch(34);
    }
    private void initSearch(int y) {
        searchAliases.clear();
        Map<String, List<String>> members = new HashMap<>();
        String catalogPath = switch (settings.roadStyle()) {
            case CAMPUS -> LegacyPlannedCampus.CATALOG;
            case CAMPUS_PARK -> ParkPlannedCampus.CATALOG;
            case CAMPUS_COMPACT -> CompactPlannedCampus.CATALOG;
            case CAMPUS_DENSE -> DensePlannedCampus.CATALOG;
            case CAMPUS_WOODLAND -> PlannedCampus.CATALOG;
            default -> throw new IllegalArgumentException("Not a campus preset");
        };
        try (var reader = new InputStreamReader(CampusPlanScreen.class.getResourceAsStream(catalogPath), StandardCharsets.UTF_8)) {
            var catalog = JsonParser.parseReader(reader).getAsJsonObject();
            for (var entry : catalog.getAsJsonArray(catalog.has("reservations") ? "reservations" : "machines")) {
                var reservation = entry.getAsJsonObject();
                if (!reservation.has("memberIds")) continue;
                members.put(reservation.get("id").getAsString(), reservation.getAsJsonArray("memberIds").asList().stream()
                        .map(value -> value.getAsString()).toList());
            }
        } catch (java.io.IOException error) { throw new IllegalStateException("Cannot read campus search names", error); }
        for (var target : plan.targets()) {
            List<String> aliases = new ArrayList<>(); aliases.add(name(target).getString());
            if (target.plot() != null) {
                for (String id : members.getOrDefault(target.plot().id(), List.of(target.plot().id()))) {
                    aliases.add(id); aliases.add(BuiltInRegistries.BLOCK.get(new ResourceLocation(id)).getName().getString());
                }
            }
            searchAliases.put(target.index(), List.copyOf(aliases));
        }
        searchBox = addRenderableWidget(new EditBox(font, 12, y, width - 76, GuiStyle.CONTROL, tp("search")));
        searchBox.setMaxLength(128); searchBox.setHint(tp("search"));
        searchBox.setTooltip(Tooltip.create(tp("search_hint"))); searchBox.setValue(searchText);
        searchBox.setResponder(value -> updateSearch(value, false));
        previousMatch = addRenderableWidget(Button.builder(Component.literal("<"), button -> cycleSearch(-1))
                .bounds(width - 60, y, 22, GuiStyle.CONTROL).tooltip(Tooltip.create(tp("search_previous"))).build());
        nextMatch = addRenderableWidget(Button.builder(Component.literal(">"), button -> cycleSearch(1))
                .bounds(width - 34, y, 22, GuiStyle.CONTROL).tooltip(Tooltip.create(tp("search_next"))).build());
        searchStatusY = y + 24; updateSearch(searchText, true);
    }
    private void updateSearch(String query, boolean preserveSelection) {
        searchText = query;
        searchMatches = query.isBlank() ? List.of() : plan.targets().stream()
                .filter(target -> CampusPlotSearch.matches(query, searchAliases.get(target.index()))).toList();
        searchIndex = preserveSelection ? searchMatches.indexOf(selected) : -1;
        if (searchIndex < 0 && !searchMatches.isEmpty()) { searchIndex = 0; focusSearchMatch(); }
        previousMatch.active = nextMatch.active = searchMatches.size() > 1;
        if (!preserveSelection && searchMatches.isEmpty()) selected = null;
        lastClickAt = 0; lastClickedIndex = -1;
    }
    private void cycleSearch(int delta) {
        searchIndex = CampusPlotSearch.cycle(searchIndex, delta, searchMatches.size());
        focusSearchMatch();
    }
    private void focusSearchMatch() {
        if (searchIndex < 0) return;
        selected = searchMatches.get(searchIndex); focus(selected.bounds());
        lastClickAt = 0; lastClickedIndex = -1;
    }
    private Component searchStatus() {
        return searchText.isBlank() ? session == null ? tp("search_hint") : Component.empty() : searchMatches.isEmpty() ? tp("search_none")
                : tp("search_count", searchIndex + 1, searchMatches.size());
    }
    private void overview() {
        centerX = (worldBounds.minX() + worldBounds.maxX()) / 2.0;
        centerZ = (worldBounds.minZ() + worldBounds.maxZ()) / 2.0; zoom = 1;
    }
    private void teleport() {
        if (session == null || selected == null || pending || sessionInvalid) return;
        pending = true; status = tp("pending"); statusVisible = true;
        CampusTeleportNetwork.requestTeleport(session.id, selected.index());
    }
    private void focusStage(String stage) {
        int minX = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (var plot : plan.plots()) if (stageMatches(plot, stage)) {
            minX = Math.min(minX, plot.x() - 8); minZ = Math.min(minZ, plot.z() - 8);
            maxX = Math.max(maxX, plot.maxX() + 8); maxZ = Math.max(maxZ, plot.maxZ() + 8);
        }
        if (minX != Integer.MAX_VALUE) focus(new PlannedCampus.Bounds(minX, minZ, maxX, maxZ));
    }
    private static boolean stageMatches(PlannedCampus.Plot plot, String stage) {
        return plot.stage().equals(stage) || stage.startsWith("late_") && plot.stage().equals("late")
                && (stage.equals("late_north") ? plot.z() < 0 : plot.z() >= 0);
    }
    private void focus(PlannedCampus.Bounds bounds) {
        centerX = (bounds.minX() + bounds.maxX()) / 2.0; centerZ = (bounds.minZ() + bounds.maxZ()) / 2.0;
        zoom = Math.max(1, Math.min(32, .85 * Math.min((double) worldBounds.width() / bounds.width(), (double) worldBounds.depth() / bounds.depth())));
    }
    private double side() { return baseSize * zoom; }
    private double mapLeft() { return (mapLeftEdge + mapRightEdge) / 2.0 - (centerX - worldBounds.minX()) * side() / worldBounds.width(); }
    private double mapY() { return (mapTop + mapBottom) / 2.0 - (centerZ - worldBounds.minZ()) * side() / worldBounds.depth(); }
    private boolean inMap(double x, double y) { return x >= mapLeftEdge && x < mapRightEdge && y >= mapTop && y < mapBottom; }
    private CampusPlanAccess.Target hovered(double mx, double my) {
        if (!inMap(mx, my)) return null;
        int x = worldBounds.minX() + (int) Math.floor((mx - mapLeft()) * worldBounds.width() / side());
        int z = worldBounds.minZ() + (int) Math.floor((my - mapY()) * worldBounds.depth() / side());
        return plan.targetAt(x, z);
    }
    private static Component name(CampusPlanAccess.Target target) {
        return switch (target.kind()) {
            case MACHINE -> target.plot().displayNameKey().isEmpty()
                    ? BuiltInRegistries.BLOCK.get(new ResourceLocation(target.plot().id())).getName()
                    : Component.translatable(target.plot().displayNameKey());
            case AE_CORE -> tr("campus_ae_short");
            case WORKSHOP -> tp("workshop_name", target.ordinal());
        };
    }
    private Component sessionName(CampusPlanAccess.Target target) {
        if (target.plot() != null) {
            if (target.plot().id().equals("gtceu:assembly_line") && !target.plot().displayNameKey().isEmpty()) return tp("assembly_line_name");
            if (target.plot().id().equals("gtceu:nano_forge_1") && !target.plot().displayNameKey().isEmpty()) return tp("nano_forge_name");
        }
        return name(target);
    }
    private Component details(CampusPlanAccess.Target target) {
        if (target.kind() != CampusPlanAccess.Kind.MACHINE) return tp(
                target.kind() == CampusPlanAccess.Kind.AE_CORE ? "ae_use" : "workshop_use")
                .copy().append("\n").append(tp("court_detail", target.bounds().width(), target.bounds().depth(),
                        target.arrivalX(), settings.surfaceY() + 1, target.arrivalZ()));
        var plot = target.plot();
        var detail = tr("campus_machine_detail", plot.width(), plot.height(), plot.depth(), plot.controllerX(),
                settings.surfaceY() + plot.controllerY(), plot.controllerZ()).copy();
        int tier = plan.planningTier(plot);
        if (tier >= 0) detail = tr("campus_planning_tier", CampusPlanAccess.PLANNING_TIERS.get(tier)).copy().append("\n").append(detail);
        return detail.append("\n" + plot.id() + "  #" + (plot.slotIndex() + 1)).append("\n").append(tp("machine_use"));
    }
    @Override public void render(GuiGraphics g, int mx, int my, float partial) {
        renderBackground(g);
        g.fill(session == null ? 6 : mapLeftEdge - 3, mapTop - 3,
                session == null ? width - 6 : mapRightEdge + 3, mapBottom + 2, 0xff171d22);
        g.enableScissor(mapLeftEdge, mapTop, mapRightEdge, mapBottom);
        double minX = worldBounds.minX() + (mapLeftEdge - mapLeft()) * worldBounds.width() / side();
        double minZ = worldBounds.minZ() + (mapTop - mapY()) * worldBounds.depth() / side();
        var viewport = new PlotPreviewRaster.CampusViewport(minX, minZ,
                (mapRightEdge - mapLeftEdge) * worldBounds.width() / side(), (mapBottom - mapTop) * worldBounds.depth() / side());
        map.draw(g, settings, mapLeftEdge, mapTop, mapRightEdge - mapLeftEdge, mapBottom - mapTop, viewport);
        if (session == null) mapLabel(g, tr("campus_ae_short"), (aeBounds.minX() + aeBounds.maxX()) / 2.0, (aeBounds.minZ() + aeBounds.maxZ()) / 2.0);
        for (var target : plan.targets()) if (session == null && target.kind() == CampusPlanAccess.Kind.WORKSHOP) {
            Component label = tp("workshop_short", target.ordinal());
            if (target.bounds().width() * side() / worldBounds.width() >= font.width(label) + 8
                    && target.bounds().depth() * side() / worldBounds.depth() >= font.lineHeight + 8)
                mapLabel(g, label, (target.bounds().minX() + target.bounds().maxX()) / 2.0,
                        (target.bounds().minZ() + target.bounds().maxZ()) / 2.0);
        }
        CampusPlanAccess.Target hover = hovered(mx, my), shown = hover == null ? selected : hover;
        if (selected != null) outline(g, selected, GuiStyle.DARK_ACCENT);
        if (hover != null && hover != selected) outline(g, hover, GuiStyle.DARK_TEXT);
        g.disableScissor();
        Component searchStatus = searchStatus();
        g.drawString(font, font.plainSubstrByWidth(searchStatus.getString(), width - 24), 12, searchStatusY,
                !searchText.isBlank() && searchMatches.isEmpty() ? GuiStyle.DARK_ERROR : GuiStyle.DARK_SECONDARY, false);
        if (session != null) {
            if (statusVisible && !pending) {
                var lines = font.split(status, baseSize - 16);
                int y = mapBottom - 8 - lines.size() * (font.lineHeight + 2);
                g.fill(mapLeftEdge + 4, y - 4, mapRightEdge - 4, mapBottom - 4, 0xe01a2026);
                for (var line : lines) {
                    g.drawString(font, line, mapLeftEdge + 8, y, pending ? GuiStyle.DARK_SECONDARY : GuiStyle.DARK_ERROR, false);
                    y += font.lineHeight + 2;
                }
            }
            super.render(g, mx, my, partial);
            if (inMap(mx, my) && !dragged && hover != null) {
                g.renderTooltip(font, font.split(sessionName(hover), Math.min(320, width - 20)), mx, my);
            }
            return;
        }
        g.drawString(font, tr("campus_north"), 17, mapTop + 5, GuiStyle.DARK_TEXT, false);
        int textY = mapBottom + 7;
        Component heading = shown == null ? tr("campus_atlas_hint") : name(shown);
        g.drawString(font, font.plainSubstrByWidth(heading.getString(), width - 28), 14, textY, GuiStyle.DARK_TEXT, false);
        Component description = shown == null ? tr("campus_atlas_ae") : details(shown);
        for (var line : font.split(description, width - 28)) {
            textY += 11; if (textY > height - 30) break;
            g.drawString(font, line, 14, textY, GuiStyle.DARK_SECONDARY, false);
        }
        super.render(g, mx, my, partial);
        CampusPlanAccess.Target tooltip = hover != null ? hover : selected != null && my >= mapBottom + 5
                && my < height - 26 ? selected : null;
        if (tooltip != null && !dragged) g.renderTooltip(font,
                font.split(name(tooltip).copy().append("\n").append(details(tooltip)), Math.min(320, width - 20)), mx, my);
    }
    private void outline(GuiGraphics g, CampusPlanAccess.Target target, int color) {
        var b = target.bounds();
        int x = (int) (mapLeft() + (b.minX() - worldBounds.minX()) * side() / worldBounds.width());
        int z = (int) (mapY() + (b.minZ() - worldBounds.minZ()) * side() / worldBounds.depth());
        int w = Math.max(2, (int) (b.width() * side() / worldBounds.width()));
        int d = Math.max(2, (int) (b.depth() * side() / worldBounds.depth()));
        g.renderOutline(x, z, w, d, color);
    }
    private void mapLabel(GuiGraphics g, Component label, double x, double z) {
        int px = (int) (mapLeft() + (x - worldBounds.minX()) * side() / worldBounds.width());
        int pz = (int) (mapY() + (z - worldBounds.minZ()) * side() / worldBounds.depth());
        int w = font.width(label);
        g.fill(px - w / 2 - 4, pz - 6, px + w / 2 + 4, pz + 7, 0xd01a2026);
        g.drawString(font, label, px - w / 2, pz - 4, GuiStyle.DARK_TEXT, false);
    }
    private void clampCenter() {
        centerX = Math.max(worldBounds.minX(), Math.min(worldBounds.maxX(), centerX));
        centerZ = Math.max(worldBounds.minZ(), Math.min(worldBounds.maxZ(), centerZ));
    }
    @Override public boolean mouseClicked(double x, double y, int button) {
        if (button == 0 && inMap(x, y)) {
            searchBox.setFocused(false);
            pointerPressed = true; dragged = false; pressX = x; pressY = y; pressedTarget = hovered(x, y); return true;
        }
        return super.mouseClicked(x, y, button);
    }
    @Override public boolean mouseReleased(double x, double y, int button) {
        if (button == 0 && pointerPressed) {
            pointerPressed = false;
            if (!dragged && inMap(x, y) && pressedTarget == hovered(x, y)) {
                selected = pressedTarget;
                long now = Util.getMillis(); int index = selected == null ? -1 : selected.index();
                if (index >= 0 && index == lastClickedIndex && now - lastClickAt <= 250) { teleport(); lastClickAt = 0; }
                else { lastClickAt = now; lastClickedIndex = index; }
            } else lastClickAt = 0;
            dragged = false; return true;
        }
        return super.mouseReleased(x, y, button);
    }
    @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        if (button == 0 && pointerPressed) {
            if (!dragged && Math.hypot(x - pressX, y - pressY) > 4) dragged = true;
            if (dragged) {
                lastClickAt = 0;
                centerX -= dx * worldBounds.width() / side(); centerZ -= dy * worldBounds.depth() / side(); clampCenter();
            }
            return true;
        }
        return super.mouseDragged(x, y, button, dx, dy);
    }
    @Override public boolean mouseScrolled(double x, double y, double amount) {
        if (inMap(x, y)) {
            lastClickAt = 0;
            double oldSide = side(), oldX = centerX, oldZ = centerZ;
            zoom = Math.max(1, Math.min(32, zoom * Math.pow(1.25, amount)));
            centerX = oldX + (x - (mapLeftEdge + mapRightEdge) / 2.0) * worldBounds.width() * (1 / oldSide - 1 / side());
            centerZ = oldZ + (y - (mapTop + mapBottom) / 2.0) * worldBounds.depth() * (1 / oldSide - 1 / side());
            clampCenter(); return true;
        }
        return super.mouseScrolled(x, y, amount);
    }
    @Override public void tick() { if (searchBox != null) searchBox.tick(); }
    @Override public boolean keyPressed(int key, int scan, int modifiers) {
        if (searchBox.isFocused() && (key == 257 || key == 335)) {
            cycleSearch((modifiers & 1) != 0 ? -1 : 1); return true;
        }
        if (searchBox.isFocused() && key == 256 && !searchBox.getValue().isEmpty()) {
            searchBox.setValue(""); return true;
        }
        return super.keyPressed(key, scan, modifiers);
    }
    @Override public boolean isPauseScreen() { return session == null; }
    @Override public void onClose() { minecraft.setScreen(parent); }
    @Override public void removed() {
        map.close();
        if (session != null && !sessionClosed) {
            sessionClosed = true;
            if (minecraft.getConnection() != null) CampusTeleportNetwork.close(session.id);
        }
    }
}
