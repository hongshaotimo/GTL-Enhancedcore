package com.gtl.enhancedcore.client.plotinfo;

import com.gtl.enhancedcore.common.world.personalplot.PersonalPlotNetwork;
import com.gtl.enhancedcore.common.world.personalplot.PersonalPlotNetwork.Entry;
import com.gtl.enhancedcore.common.world.personalplot.PersonalPlotNetwork.ManagementSnapshot;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/** Management permissions and deletion challenges always come from the server. */
public final class PersonalPlotManagementScreen extends Screen {
    private static final int INK = 0xffe8e1d3, MUTED = 0xffa7a69f, GOLD = 0xffd6bc81;
    private final List<ActionButton> actions = new ArrayList<>();
    private ManagementSnapshot snapshot;
    private UUID preparingOwner;
    private boolean pending;
    private int pendingTicks;
    private int left, top, bodyWidth, bodyHeight, rowsTop, rowHeight, noticeY;
    private Consumer<Runnable> transport = Runnable::run;

    private PersonalPlotManagementScreen(ManagementSnapshot snapshot) {
        super(tr("management_title")); this.snapshot = snapshot;
    }

    public static void receiveSnapshot(ManagementSnapshot snapshot) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || client.player == null) return;
        PersonalPlotManagementScreen screen;
        if (client.screen instanceof PersonalPlotManagementScreen current) screen = current;
        else if (client.screen instanceof DeleteConfirmation current) { screen = current.management; client.setScreen(screen); }
        else { screen = new PersonalPlotManagementScreen(snapshot); client.setScreen(screen); }
        screen.acceptSnapshot(snapshot);
    }

    private void acceptSnapshot(ManagementSnapshot value) {
        snapshot = value; pending = false; pendingTicks = 0; rebuildWidgets();
        UUID expected = preparingOwner; preparingOwner = null;
        if (expected == null || !expected.equals(value.deleteOwner()) || value.deleteToken() == null) return;
        Entry entry = value.entries().stream().filter(row -> row.owner().equals(expected)).findFirst().orElse(null);
        if (entry == null || !entry.canDelete()) return;
        minecraft.setScreen(new DeleteConfirmation(this, entry, value.deleteToken()));
    }

    private static Component tr(String suffix, Object... args) {
        return Component.translatable("screen.gtl_enhancedcore.personal_plot." + suffix, args);
    }

    @Override protected void init() {
        actions.clear();
        bodyWidth = Math.min(720, width - 16); bodyHeight = Math.min(370, height - 16);
        left = (width - bodyWidth) / 2; top = (height - bodyHeight) / 2;
        rowsTop = top + 52; noticeY = top + bodyHeight - 42;
        rowHeight = (noticeY - 8 - rowsTop) / 4;
        int x = left + 12, footer = top + bodyHeight - 28;
        action(tr("refresh"), x, footer, 70, () -> request(() -> PersonalPlotNetwork.requestManagement(snapshot.page())), !pending);
        action(tr("previous_page"), x + 76, footer, 60,
                () -> request(() -> PersonalPlotNetwork.requestManagement(snapshot.page() - 1)), !pending && snapshot.page() > 0);
        action(tr("next_page"), x + 142, footer, 60,
                () -> request(() -> PersonalPlotNetwork.requestManagement(snapshot.page() + 1)), !pending && snapshot.page() + 1 < snapshot.pages());
        action(CommonComponents.GUI_BACK, left + bodyWidth - 82, footer, 70, this::onClose, true);
        for (int i = 0; i < Math.min(4, snapshot.entries().size()); i++) {
            Entry entry = snapshot.entries().get(i);
            int y = rowsTop + i * rowHeight + (rowHeight - 21) / 2;
            action(tr("management_create"), left + bodyWidth - 116, y, 50,
                    () -> request(() -> PersonalPlotNetwork.requestCreateFor(entry.owner(), snapshot.page())), !pending && entry.canCreate());
            action(tr("delete"), left + bodyWidth - 62, y, 50,
                    () -> {
                        if (!pending) { preparingOwner = entry.owner(); request(() -> PersonalPlotNetwork.requestPrepareDelete(entry.owner(), snapshot.page())); }
                    }, !pending && entry.canDelete());
        }
    }

    private void request(Runnable operation) {
        if (pending) return;
        pending = true; pendingTicks = 0; actions.forEach(button -> button.active = false); transport.accept(operation);
    }

    @Override public void tick() {
        if (pending && ++pendingTicks >= 200) {
            pending = false; pendingTicks = 0; preparingOwner = null; rebuildWidgets();
        }
    }

    @Override public void render(GuiGraphics g, int mx, int my, float partial) {
        renderBackground(g); g.fillGradient(0, 0, width, height, 0x90161719, 0xc00c0d0f);
        panel(g, left, top, bodyWidth, bodyHeight, 0xff252a30);
        g.fill(left + 1, top + 1, left + bodyWidth - 1, top + 30, 0xff30363d);
        g.drawString(font, title, left + 12, top + 11, INK, false);
        Component scope = tr(snapshot.canManageAll() ? "manage_all" : "manage_mine");
        g.drawString(font, scope, left + 12, top + 36, GOLD, false);
        Component page = tr("management_page", snapshot.page() + 1, Math.max(1, snapshot.pages()));
        g.drawString(font, page, left + bodyWidth - 12 - font.width(page), top + 36, MUTED, false);
        Component hovered = null;
        for (int i = 0; i < Math.min(4, snapshot.entries().size()); i++) {
            Entry entry = snapshot.entries().get(i);
            int y = rowsTop + i * rowHeight;
            panel(g, left + 12, y, bodyWidth - 24, rowHeight - 3, 0xff20252a);
            int textWidth = bodyWidth - 140, textY = y + (rowHeight - 24) / 2;
            Component state = tr("state." + entry.state().name().toLowerCase(Locale.ROOT));
            Component identity = Component.literal(entry.name()).append(" · ").append(state);
            g.drawString(font, clipped(identity, textWidth), left + 18, textY + 2, INK, false);
            g.drawString(font, clipped(Component.literal(entry.owner().toString()), textWidth), left + 18, textY + 13, MUTED, false);
            if (mx >= left + 12 && mx < left + bodyWidth - 120 && my >= y && my < y + rowHeight - 3)
                hovered = Component.literal(entry.name()).append("\n").append(entry.owner().toString()).append("\n").append(state);
        }
        if (snapshot.entries().isEmpty()) g.drawWordWrap(font, tr("management_empty"), left + 18, rowsTop + 10, bodyWidth - 36, MUTED);
        Component notice = pending ? tr("working") : snapshot.notice().isBlank()
                ? tr("management_hint") : Component.translatable(snapshot.notice());
        g.drawString(font, clipped(notice, bodyWidth - 24), left + 12, noticeY, GOLD, false);
        super.render(g, mx, my, partial);
        if (my >= noticeY && my < noticeY + 10 && mx >= left + 12 && mx < left + bodyWidth - 12) hovered = notice;
        if (hovered != null) g.renderTooltip(font, font.split(hovered, Math.min(340, bodyWidth - 24)), mx, my);
    }

    private String clipped(Component value, int maxWidth) { return font.plainSubstrByWidth(value.getString(), Math.max(1, maxWidth)); }
    private static void panel(GuiGraphics g, int x, int y, int w, int h, int fill) {
        g.fill(x, y, x + w, y + h, 0xff101316); g.fill(x + 1, y + 1, x + w - 1, y + h - 1, fill);
        g.fill(x + 1, y + 1, x + w - 1, y + 2, 0xff485057);
    }
    private void action(Component text, int x, int y, int w, Runnable operation, boolean enabled) {
        ActionButton button = addRenderableWidget(new ActionButton(text, x, y, w, operation));
        button.active = enabled; button.setTooltip(Tooltip.create(text)); actions.add(button);
    }
    private final class ActionButton extends AbstractButton {
        private final Runnable operation;
        private ActionButton(Component label, int x, int y, int w, Runnable operation) { super(x, y, w, 21, label); this.operation = operation; }
        @Override public void onPress() { operation.run(); }
        @Override protected void updateWidgetNarration(NarrationElementOutput output) { defaultButtonNarrationText(output); }
        @Override protected void renderWidget(GuiGraphics g, int mx, int my, float partial) {
            panel(g, getX(), getY(), width, height, !active ? 0xff272c30 : isHoveredOrFocused() ? 0xff474e53 : 0xff343b40);
            if (active && isHoveredOrFocused()) g.renderOutline(getX(), getY(), width, height, GOLD);
            String label = clipped(getMessage(), width - 8);
            g.drawString(font, label, getX() + (width - font.width(label)) / 2, getY() + 6, active ? INK : 0xff777d7f, false);
        }
    }
    private static final class DeleteConfirmation extends ConfirmScreen {
        private final PersonalPlotManagementScreen management;
        private DeleteConfirmation(PersonalPlotManagementScreen management, Entry entry, UUID token) {
            super(confirmed -> {
                Minecraft.getInstance().setScreen(management);
                if (confirmed) management.request(() -> PersonalPlotNetwork.requestConfirmDelete(entry.owner(), token, management.snapshot.page()));
            }, tr("delete_title"), tr("delete_warning", entry.name(), entry.owner().toString()), tr("delete_confirm"), CommonComponents.GUI_CANCEL);
            this.management = management;
        }
        @Override protected void init() { super.init(); setDelay(20); }
        @Override public boolean isPauseScreen() { return false; }
    }
    @Override public void onClose() { minecraft.setScreen(null); transport.accept(PersonalPlotNetwork::requestOpen); }
    @Override public boolean isPauseScreen() { return false; }
}
