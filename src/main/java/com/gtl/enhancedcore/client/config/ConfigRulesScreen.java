package com.gtl.enhancedcore.client.config;

import com.gtl.enhancedcore.common.config.ConfigCatalog;
import com.gtl.enhancedcore.common.config.StartupConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;

/** Switches and bounded numbers for the common rules and startup settings. */
final class ConfigRulesScreen extends ConfigPage {
    private final ConfigCatalog kind;
    private final String original;
    private final TextConfigScreen.Saver saver;
    private boolean structures, enhancements, automatic, enabled, previewLayers = true;
    private String background = "0", builder = "0", mapRaster = "0", interval = "1";
    private EditBox first, second, third;
    ConfigRulesScreen(Screen parent, ConfigCatalog kind, String text, TextConfigScreen.Saver saver) {
        super(parent, kind.key()); this.kind = kind; this.original = text; this.saver = saver;
        if (kind == ConfigCatalog.STARTUP) {
            var settings = StartupConfig.parse(text);
            structures = settings.replaceOriginalStructures(); enhancements = settings.enhanceOriginalMachines();
            automatic = settings.autoThreads(); background = "" + settings.backgroundThreads();
            builder = "" + settings.builderThreads(); mapRaster = "" + settings.mapRasterThreads();
        } else if (kind == ConfigCatalog.COMMON) {
            var settings = ConfigCatalog.claimSettings(text); enabled = settings.enabled(); interval = "" + settings.interval();
        } else {
            previewLayers = com.gtl.enhancedcore.client.config.PreviewLayerConfig.parse(text);
        }
    }
    private void toggle(String label, int x, int y, int w, java.util.function.BooleanSupplier value, Runnable change) {
        button(tr(label).copy().append(": ").append(tr(value.getAsBoolean() ? "on" : "off")), x, y, w, b -> {
            change.run(); b.setMessage(tr(label).copy().append(": ").append(tr(value.getAsBoolean() ? "on" : "off")));
        });
    }
    @Override protected void init() {
        super.init(); int half = (bodyWidth - 6) / 2;
        if (kind == ConfigCatalog.STARTUP) {
            toggle("rule.structures", left, 49, half, () -> structures, () -> structures = !structures);
            toggle("rule.enhancements", left + half + 6, 49, half, () -> enhancements, () -> enhancements = !enhancements);
            toggle("rule.auto_threads", left, 75, bodyWidth, () -> automatic, () -> automatic = !automatic);
            first = field("rule.background", background, left, 114, half, 3); first.setResponder(value -> background = value);
            second = field("rule.builder", builder, left + half + 6, 114, half, 3); second.setResponder(value -> builder = value);
            third = field("rule.map_raster", mapRaster, left, 154, bodyWidth, 1); third.setResponder(value -> mapRaster = value);
        } else if (kind == ConfigCatalog.COMMON) {
            toggle("rule.claim_enabled", left, 49, bodyWidth, () -> enabled, () -> enabled = !enabled);
            first = field("rule.interval", interval, left, 95, bodyWidth, 3); first.setResponder(value -> interval = value);
        } else {
            toggle("rule.preview_layers", left, 49, bodyWidth, () -> previewLayers,
                    () -> previewLayers = !previewLayers);
        }
        button("save", left, height - 27, half, () -> attempt(() -> {
            String result;
            if (kind == ConfigCatalog.STARTUP) {
                result = StartupConfig.update(original, new StartupConfig.Settings(structures, enhancements, automatic,
                        Integer.parseInt(background), Integer.parseInt(builder), Integer.parseInt(mapRaster)));
            } else if (kind == ConfigCatalog.COMMON) {
                result = "[claim_replacement_terminal]\nenabled=" + enabled + "\nreplace_interval_ticks=" + Integer.parseInt(interval) + "\n";
            } else {
                result = com.gtl.enhancedcore.client.config.PreviewLayerConfig.update(original, previewLayers);
            }
            kind.validate(result); saver.save(result); onClose();
        }));
        button("cancel", left + half + 6, height - 27, half, this::onClose);
    }
    @Override public void tick() {
        if (first != null) first.tick();
        if (second != null) second.tick();
        if (third != null) third.tick();
    }
    @Override public void render(GuiGraphics g, int x, int y, float partial) {
        super.render(g, x, y, partial);
        g.drawString(font, clipped(tr("effect." + kind.effect), bodyWidth), left, 31, 0xaaaaaa);
        if (kind == ConfigCatalog.STARTUP) {
            g.drawString(font, tr("rule.background"), left, 101, 0xaaaaaa);
            g.drawString(font, tr("rule.builder"), left + (bodyWidth + 6) / 2, 101, 0xaaaaaa);
            g.drawString(font, tr("rule.map_raster"), left, 141, 0xaaaaaa);
        } else if (kind == ConfigCatalog.COMMON) g.drawString(font, tr("rule.interval"), left, 81, 0xaaaaaa);
    }
}
