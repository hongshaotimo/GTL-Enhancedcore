package com.gtl.enhancedcore.client.config;

import com.gtl.enhancedcore.common.config.*;
import com.gtl.enhancedcore.common.data.machine.InfinitySingularityRecipeLoader;
import com.gtl.enhancedcore.client.AEPinnedKeysStore;
import com.gtl.enhancedcore.client.gui.ChangelogScreen;
import java.nio.file.Files;
import java.util.Arrays;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;

/** All live files have an editor and an explicit application boundary. State files are labelled separately. */
final class ConfigFilesScreen extends ConfigPage {
    private boolean server;
    private int page;
    ConfigFilesScreen(Screen parent) { super(parent, "all_files"); }
    @Override protected void init() {
        super.init();
        int half = (bodyWidth - 6) / 2;
        button(server ? "scope_server" : "scope_local", left, 31, half, () -> {
            if (minecraft.getConnection() == null) { notice(tr("server_unavailable")); return; }
            server = !server; page = 0; rebuildWidgets();
        }).setTooltip(Tooltip.create(tr("scope_hint")));
        button("preview_changelog", left + half + 6, 31, half, () -> ChangelogScreen.open(this));
        var entries = Arrays.stream(ConfigCatalog.values()).filter(kind -> !server || kind.server).toList();
        int rows = Math.max(1, (height - 116) / 26);
        int pages = Math.max(1, (entries.size() + rows - 1) / rows);
        page = Math.min(page, pages - 1);
        for (int i = 0; i < rows && page * rows + i < entries.size(); i++) {
            var kind = entries.get(page * rows + i);
            button(tr(kind.key()).copy().append("  ").append(net.minecraft.network.chat.Component.literal(kind.file)
                    .withStyle(net.minecraft.ChatFormatting.GRAY)), left, 58 + i * 26, bodyWidth,
                    () -> attempt(() -> open(kind))).setTooltip(Tooltip.create(tr("effect." + kind.effect)));
        }
        button("previous", left, height - 61, half, () -> { page--; rebuildWidgets(); }).active = page > 0;
        button("next", left + half + 6, height - 61, half, () -> { page++; rebuildWidgets(); }).active = page + 1 < pages;
        button("back", left, height - 27, bodyWidth, this::onClose);
    }
    private void open(ConfigCatalog kind) throws Exception {
        // A singleplayer world has a real server thread too; never mutate its config on the render thread.
        if (kind.server && (server || minecraft.hasSingleplayerServer())) {
            minecraft.setScreen(new RemoteConfigScreen(this, kind)); return;
        }
        if (kind == ConfigCatalog.COPY_KEYS) { minecraft.setScreen(new CopyKeysScreen(this)); return; }
        if (kind == ConfigCatalog.TIPS) { minecraft.setScreen(new ItemTipBrowser(this)); return; }
        var path = EnhancedConfigScreen.directory().resolve(kind.file);
        if (!Files.exists(path)) {
            String initial = switch (kind) {
                case STARTUP -> StartupConfig.serialize(StartupConfig.defaults());
                case PREVIEW -> PreviewLayerConfig.serialize(true);
                case DISMANTLE -> TerminalDismantleConfig.defaultText();
                case GALAXY -> GalaxyConnectionConfig.defaultText();
                case COMMON -> "[claim_replacement_terminal]\nenabled=true\nreplace_interval_ticks=5\n";
                case PINS -> "[]\n";
                case SEEN -> "";
                default -> null;
            };
            if (kind == ConfigCatalog.CHANGELOG) ChangelogConfig.get();
            if (kind == ConfigCatalog.SINGULARITY) InfinitySingularityRecipeLoader.ensureDefaultConfig();
            if (initial != null) ConfigFiles.writeAtomically(path, initial);
        }
        var file = new EditableConfigFile(path);
        TextConfigScreen.Saver saver = text -> {
            kind.validate(text);
            if (kind == ConfigCatalog.SINGULARITY) InfinitySingularityRecipeLoader.validate(text);
            if (kind == ConfigCatalog.PINS) AEPinnedKeysStore.validate(text);
            file.save(text);
            switch (kind) {
                case COMMON -> ServerConfigEditor.applyCommon(text);
                case CHANGELOG -> ChangelogConfig.reload();
                case PINS -> AEPinnedKeysStore.applyEdited(text);
                case PREVIEW -> PreviewLayerConfig.initialize();
                default -> { }
            }
            notice(tr("saved_effect", tr("effect." + kind.effect)));
        };
        if (kind == ConfigCatalog.STARTUP || kind == ConfigCatalog.COMMON)
            minecraft.setScreen(new ConfigRulesScreen(this, kind, file.text(), saver));
        else minecraft.setScreen(new TextConfigScreen(this, kind.key(), "effect." + kind.effect, file.text(), saver));
    }
    @Override public void render(GuiGraphics g, int x, int y, float partial) {
        super.render(g, x, y, partial);
    }
}
