package com.gtl.enhancedcore.client.config;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * 「复制 ID / 复制配方」按键功能的开关界面。
 *
 * <p>四个开关对应 {@code config/GTL-Enhancedcore/copy_keys.json}；点击即时写回文件并生效
 * （{@link CopyKeysConfig#save} 内同步刷新内存值），因此无需重启。
 * 也可以直接手改 JSON 文件，{@link CopyKeysConfig#tick()} 会自动热重载。
 */
final class CopyKeysScreen extends ConfigPage {
    CopyKeysScreen(Screen parent) { super(parent, "copy_keys"); }

    @Override protected void init() {
        super.init();
        int half = (bodyWidth - 6) / 2;
        int y = 50;
        toggle("copy_item", left, y, half, CopyKeysConfig.copyItemId());
        toggle("copy_fluid", left + half + 6, y, half, CopyKeysConfig.copyFluidId());
        y += 24;
        toggle("copy_recipe", left, y, half, CopyKeysConfig.copyRecipe());
        toggle("live_recipe", left + half + 6, y, half, CopyKeysConfig.liveGenerateRecipe());
        y += 30;
        button("reload_copy_keys", left, y, bodyWidth,
                () -> attempt(() -> { CopyKeysConfig.reload(); notice(tr("reloaded")); rebuildWidgets(); }));
        button("back", left, height - 27, bodyWidth, this::onClose);
    }

    /** 开关按钮：文字带开/关后缀，点击后写回配置并就地刷新文字。 */
    private void toggle(String key, int x, int y, int width, boolean current) {
        final boolean[] state = {current};
        Button[] holder = new Button[1];
        holder[0] = button(stateLabel(key, state[0]), x, y, width, b -> {
            state[0] = !state[0];
            attempt(() -> CopyKeysConfig.save(
                    key.equals("copy_item") ? state[0] : CopyKeysConfig.copyItemId(),
                    key.equals("copy_fluid") ? state[0] : CopyKeysConfig.copyFluidId(),
                    key.equals("copy_recipe") ? state[0] : CopyKeysConfig.copyRecipe(),
                    key.equals("live_recipe") ? state[0] : CopyKeysConfig.liveGenerateRecipe()));
            holder[0].setMessage(stateLabel(key, state[0]));
        });
        holder[0].setTooltip(net.minecraft.client.gui.components.Tooltip.create(tr(key + "_hint")));
    }

    private Component stateLabel(String key, boolean on) {
        return tr(key).copy().append(": ").append(tr(on ? "on" : "off"));
    }

    @Override public void render(GuiGraphics graphics, int x, int y, float partial) {
        super.render(graphics, x, y, partial);
        graphics.drawCenteredString(font, tr("copy_keys.subtitle"), width / 2, 34, 0xaaaaaa);
        graphics.drawCenteredString(font, tr("copy_keys.file"),
                width / 2, height - 46, 0x808080);
    }
}
