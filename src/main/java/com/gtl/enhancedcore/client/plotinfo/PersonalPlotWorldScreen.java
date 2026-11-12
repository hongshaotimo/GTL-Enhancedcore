package com.gtl.enhancedcore.client.plotinfo;

import com.gtl.enhancedcore.client.plot.CreateWorldPlotScreen;
import com.gtl.enhancedcore.client.plot.CampusPlanScreen;
import com.gtl.enhancedcore.client.plot.PlotPreviewRenderer;
import com.gtl.enhancedcore.common.item.PlotControllerEnvironment;
import com.gtl.enhancedcore.common.world.personalplot.PersonalPlotNetwork;
import com.gtl.enhancedcore.common.world.personalplot.PersonalPlotNetwork.Snapshot;
import com.gtl.enhancedcore.common.world.personalplot.PersonalPlotWorlds.State;
import com.gtl.enhancedcore.common.world.plot.PlotSettings;
import com.gtl.enhancedcore.common.world.plot.PlotPresetShare;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/** The controller is a server-authoritative personal-world dashboard, never a client-side permission gate. */
public final class PersonalPlotWorldScreen extends Screen {
    private static final int INK = 0xffe8e1d3, MUTED = 0xffa7a69f, GOLD = 0xffd6bc81;
    private Snapshot snapshot;
    private final PlotPreviewRenderer.Cached single = new PlotPreviewRenderer.Cached();
    private final PlotPreviewRenderer.Cached four = new PlotPreviewRenderer.Cached();
    private final List<ActionButton> actions = new ArrayList<>();
    private int left, top, bodyWidth, bodyHeight, sidebarX, sidebarWidth, contentY, contentBottom, previewX, previewWidth;
    private boolean pending;
    private boolean copied;
    private int pendingTicks;
    private Consumer<Runnable> transport = Runnable::run;

    private PersonalPlotWorldScreen(Snapshot snapshot) { super(tr("title")); this.snapshot = snapshot; }

    public static void receiveSnapshot(Snapshot snapshot) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || client.player == null) return;
        PlotControllerEnvironment.accept(snapshot.dedicated());
        boolean entered = snapshot.inOwnWorld() && snapshot.state() == State.READY
                && "gtl_enhancedcore.personal_plot.travelled".equals(snapshot.notice());
        boolean returned = !snapshot.inOwnWorld()
                && "gtl_enhancedcore.personal_plot.returned".equals(snapshot.notice());
        if (entered || returned) {
            // The server sends success only after teleporting. Keep vanilla's dimension-loading screen
            // if it replaced this dashboard, and never reopen the dashboard for a successful action.
            if (client.screen instanceof PersonalPlotWorldScreen) client.setScreen(null);
            return;
        }
        if (client.screen instanceof PersonalPlotWorldScreen current) {
            current.snapshot = snapshot; current.pending = false; current.copied = false; current.pendingTicks = 0; current.rebuildWidgets();
        } else client.setScreen(new PersonalPlotWorldScreen(snapshot));
    }

    private static Component tr(String suffix, Object... args) {
        return Component.translatable("screen.gtl_enhancedcore.personal_plot." + suffix, args);
    }

    @Override protected void init() {
        actions.clear();
        bodyWidth = Math.min(820, width - 16); bodyHeight = Math.min(440, height - 16);
        left = (width - bodyWidth) / 2; top = (height - bodyHeight) / 2;
        sidebarX = left + 12; sidebarWidth = (bodyWidth - 42) * 40 / 100;
        contentY = top + 43; contentBottom = top + bodyHeight - 43;
        previewX = sidebarX + sidebarWidth + 18; previewWidth = left + bodyWidth - 12 - previewX;
        int footerY = top + bodyHeight - 28;
        action(CommonComponents.GUI_DONE, left + bodyWidth - 82, footerY, 70, this::onClose, true, null);
        action(tr("refresh"), sidebarX, footerY, Math.min(90, sidebarWidth),
                () -> request(PersonalPlotNetwork::requestOpen), !pending, tr("refresh_hint"));
        action(tr("copy_preset"), sidebarX + Math.min(90, sidebarWidth) + 6, footerY,
                Math.min(110, bodyWidth - Math.min(90, sidebarWidth) - 112), () -> {
                    PlotSettings settings = previewSettings();
                    if (settings != null) { minecraft.keyboardHandler.setClipboard(PlotPresetShare.encode(settings)); copied = true; }
                }, !pending && previewSettings() != null, tr("copy_preset_hint"));
        if (previewSettings() != null && previewSettings().isCampus())
            action(Component.translatable("gtl_enhancedcore.create_world_plot.campus_map_button"),
                    previewX + previewWidth - 60, contentY - 3, 60,
                    () -> minecraft.setScreen(new CampusPlanScreen(this, previewSettings())), !pending,
                    Component.translatable("gtl_enhancedcore.create_world_plot.campus_open_plan"));

        int count = snapshot.canEditTemplate() ? 4 : 3;
        int y = contentBottom - count * 26 + 4;
        Component label = switch (snapshot.state()) {
            case NONE -> tr(snapshot.canCustomizePersonal() ? "customize_create" : "create");
            case PENDING -> tr("waiting_restart");
            case READY -> tr(snapshot.inOwnWorld() ? "already_inside" : "enter");
            case UNAVAILABLE -> tr("unavailable");
            case DELETING -> tr("deleting");
        };
        boolean primaryEnabled = !pending && (snapshot.state() == State.NONE && snapshot.canCreatePersonal()
                || snapshot.state() == State.READY && !snapshot.inOwnWorld());
        action(label, sidebarX, y, sidebarWidth, this::primaryAction, primaryEnabled, stateHint());
        action(tr("return"), sidebarX, y + 26, sidebarWidth, () -> request(PersonalPlotNetwork::requestReturn),
                !pending && snapshot.canReturn(), tr("return_hint"));
        if (snapshot.canEditTemplate()) {
            action(tr("edit_template"), sidebarX, y + 52, sidebarWidth, this::editTemplate,
                    !pending, tr("template_hint"));
        }
        action(tr(snapshot.dedicated() && snapshot.canEditTemplate() ? "manage_all" : "manage_mine"),
                sidebarX, y + (count - 1) * 26, sidebarWidth,
                () -> request(() -> PersonalPlotNetwork.requestManagement(0)),
                !pending && (snapshot.dedicated() || snapshot.canCustomizePersonal()), tr("management_hint"));
    }

    private void primaryAction() {
        if (snapshot.state() == State.READY) request(PersonalPlotNetwork::requestTravel);
        else if (snapshot.state() == State.NONE && snapshot.canCreatePersonal()) {
            if (snapshot.canCustomizePersonal()) {
                minecraft.setScreen(new CreateWorldPlotScreen(this, PlotSettings.defaults(), tr("editor_title"),
                        tr("create"), settings -> request(() -> PersonalPlotNetwork.requestCreate(settings))));
            } else request(() -> PersonalPlotNetwork.requestCreate(snapshot.activeTemplateSettings()));
        }
    }

    private void editTemplate() {
        PlotSettings initial = snapshot.templateSettings() == null ? PlotSettings.defaults() : snapshot.templateSettings();
        minecraft.setScreen(new CreateWorldPlotScreen(this, initial, tr("template_editor_title"),
                tr("save_template"), settings -> request(() -> PersonalPlotNetwork.requestTemplate(settings))));
    }

    private void request(Runnable action) {
        if (pending) return;
        pending = true; copied = false; pendingTicks = 0;
        actions.forEach(button -> button.active = false);
        transport.accept(action);
    }

    private Component stateHint() {
        if (snapshot.state() == State.NONE && !snapshot.canCreatePersonal()) {
            if (snapshot.dedicated() && !snapshot.templateReady())
                return snapshot.templateSettings() != null ? templateStatus() : tr("needs_template");
            return tr("creation_denied");
        }
        return tr("hint." + snapshot.state().name().toLowerCase(Locale.ROOT) + (snapshot.dedicated() ? ".server" : ".single"));
    }

    private Component templateStatus() {
        if (snapshot.templatePending())
            return tr(snapshot.activeTemplateSettings() == null ? "template_pending_activation" : "template_pending");
        if (snapshot.templateSettings() != null && !snapshot.templateSettings().isAvailableForCreation())
            return Component.translatable("gtl_enhancedcore.personal_plot.retired_template");
        return tr(snapshot.templateReady() ? "template_ready" : "template_missing");
    }

    @Override public void tick() {
        // A stale request must not leave a permanently disabled dashboard after a server-side failure.
        if (pending && ++pendingTicks >= 200) { pending = false; pendingTicks = 0; rebuildWidgets(); }
    }

    @Override public void render(GuiGraphics g, int mx, int my, float partial) {
        renderBackground(g);
        g.fillGradient(0, 0, width, height, 0x90161719, 0xc00c0d0f);
        panel(g, left, top, bodyWidth, bodyHeight, 0xff252a30);
        g.fill(left + 1, top + 1, left + bodyWidth - 1, top + 30, 0xff30363d);
        g.fill(left + 1, top + 30, left + bodyWidth - 1, top + 31, 0xff111519);
        g.drawString(font, title, left + 12, top + 11, INK, false);
        Component environment = tr(snapshot.dedicated() ? "environment.server" : "environment.single");
        int environmentWidth = font.width(environment);
        if (bodyWidth > font.width(title) + environmentWidth + 38)
            g.drawString(font, environment, left + bodyWidth - environmentWidth - 12, top + 11, MUTED, false);
        panel(g, sidebarX - 4, contentY - 4, sidebarWidth + 8, contentBottom - contentY + 8, 0xff20252a);
        panel(g, previewX - 4, contentY - 4, previewWidth + 8, contentBottom - contentY + 8, 0xff181d22);
        int stateColor = snapshot.state() == State.READY ? 0xffa6c794
                : snapshot.state() == State.PENDING || snapshot.state() == State.DELETING ? GOLD : MUTED;
        g.drawString(font, clipped(tr("your_world"), sidebarWidth), sidebarX + 3, contentY + 4, GOLD, false);
        g.fill(sidebarX + 3, contentY + 22, sidebarX + 7, contentY + 26, stateColor);
        g.drawString(font, clipped(tr("state." + snapshot.state().name().toLowerCase(Locale.ROOT)), sidebarWidth - 15),
                sidebarX + 13, contentY + 20, stateColor, false);
        int helpBottom = contentBottom - (snapshot.canEditTemplate() ? 4 : 3) * 26 - 4;
        g.enableScissor(sidebarX, contentY + 36, sidebarX + sidebarWidth, Math.max(contentY + 36, helpBottom));
        int textY = contentY + 37;
        for (var line : font.split(stateHint(), Math.max(20, sidebarWidth - 8))) {
            g.drawString(font, line, sidebarX + 3, textY, MUTED, false); textY += 12;
        }
        if (snapshot.dedicated() && textY + 25 < helpBottom) {
            textY += 10;
            Component templateStatus = templateStatus();
            for (var line : font.split(templateStatus, Math.max(20, sidebarWidth - 8))) {
                g.drawString(font, line, sidebarX + 3, textY, snapshot.templatePending() ? GOLD : MUTED, false); textY += 12;
            }
        }
        g.disableScissor();
        drawPreviews(g);
        Component notice = pending ? tr("working") : copied ? tr("copied")
                : snapshot.notice().isBlank() ? CommonComponents.EMPTY : Component.translatable(snapshot.notice());
        int noticeY = top + bodyHeight - 40;
        if (!notice.equals(CommonComponents.EMPTY))
            g.drawString(font, clipped(notice, bodyWidth - 24), sidebarX, noticeY, snapshot.state() == State.PENDING ? GOLD : MUTED, false);
        super.render(g, mx, my, partial);
        if (!notice.equals(CommonComponents.EMPTY) && my >= noticeY && my < noticeY + 10)
            g.renderTooltip(font, font.split(notice, Math.min(340, bodyWidth - 24)), mx, my);
    }

    private void drawPreviews(GuiGraphics g) {
        PlotSettings shown = previewSettings();
        Component heading = tr(snapshot.settings() == null ? "preview_template" : "preview_personal");
        g.drawString(font, clipped(heading, previewWidth - (shown != null && shown.isCampus() ? 66 : 6)), previewX + 3, contentY + 4, GOLD, false);
        if (shown == null) {
            g.drawWordWrap(font, tr("no_preview"), previewX + 8, contentY + 31, Math.max(20, previewWidth - 16), MUTED);
            return;
        }
        int available = contentBottom - contentY - 31;
        boolean beside = previewWidth >= 250;
        int size = Math.max(12, beside ? Math.min((previewWidth - 22) / 2, available - 46)
                : Math.min(previewWidth - 16, (available - 30) / 2));
        int px = previewX + (previewWidth - (beside ? size * 2 + 10 : size)) / 2;
        int py = contentY + 23;
        preview(g, single, shown, px, py, size, 1);
        preview(g, four, shown, beside ? px + size + 10 : px, beside ? py : py + size + 17, size, 2);
        int summaryY = py + size + 23;
        if (beside && summaryY + 22 < contentBottom) {
            Component summary = shown.isCampus() ? Component.translatable("gtl_enhancedcore.create_world_plot.campus_status",
                    shown.campusPlotCount(), shown.surfaceY())
                    : tr("size", shown.plotChunks(), shown.plotChunks(), shown.plotBlocks());
            g.drawString(font, clipped(summary, previewWidth - 6),
                    previewX + 3, summaryY, INK, false);
            g.drawString(font, clipped(tr("levels", shown.bedrockY(), shown.surfaceY()), previewWidth - 6),
                    previewX + 3, summaryY + 13, MUTED, false);
        }
    }

    private PlotSettings previewSettings() {
        if (snapshot.settings() != null) return snapshot.settings();
        return snapshot.activeTemplateSettings() == null ? snapshot.templateSettings() : snapshot.activeTemplateSettings();
    }

    private void preview(GuiGraphics g, PlotPreviewRenderer.Cached cache, PlotSettings settings,
                         int x, int y, int size, int count) {
        cache.draw(g, settings, x, y, size, count, false);
        Component caption = Component.translatable(settings.isCampus()
                ? count == 1 ? "gtl_enhancedcore.create_world_plot.campus_core_preview" : "gtl_enhancedcore.create_world_plot.campus_overview"
                : count == 1 ? "gtl_enhancedcore.create_world_plot.preview_single" : "gtl_enhancedcore.create_world_plot.preview_four");
        String text = clipped(caption, size + 4);
        g.drawString(font, text, x + (size - font.width(text)) / 2, y + size + 4, MUTED, false);
    }

    private String clipped(Component text, int max) { return font.plainSubstrByWidth(text.getString(), Math.max(1, max)); }
    private static void panel(GuiGraphics g, int x, int y, int w, int h, int fill) {
        g.fill(x, y, x + w, y + h, 0xff101316); g.fill(x + 1, y + 1, x + w - 1, y + h - 1, fill);
        g.fill(x + 1, y + 1, x + w - 1, y + 2, 0xff485057);
    }
    private void action(Component text, int x, int y, int w, Runnable action, boolean enabled, Component tooltip) {
        ActionButton button = addRenderableWidget(new ActionButton(text, x, y, w, action));
        button.active = enabled;
        if (tooltip != null) button.setTooltip(Tooltip.create(tooltip));
        actions.add(button);
    }
    private final class ActionButton extends AbstractButton {
        private final Runnable action;
        private ActionButton(Component label, int x, int y, int w, Runnable action) { super(x, y, w, 21, label); this.action = action; }
        @Override public void onPress() { action.run(); }
        @Override protected void updateWidgetNarration(NarrationElementOutput output) { defaultButtonNarrationText(output); }
        @Override protected void renderWidget(GuiGraphics g, int mx, int my, float partial) {
            panel(g, getX(), getY(), width, height, !active ? 0xff272c30 : isHoveredOrFocused() ? 0xff474e53 : 0xff343b40);
            if (active && isHoveredOrFocused()) g.renderOutline(getX(), getY(), width, height, GOLD);
            String text = clipped(getMessage(), width - 10);
            g.drawString(font, text, getX() + (width - font.width(text)) / 2, getY() + 6, active ? INK : 0xff777d7f, false);
        }
    }

    @Override public void removed() { single.close(); four.close(); }
    @Override public boolean isPauseScreen() { return false; }
}
