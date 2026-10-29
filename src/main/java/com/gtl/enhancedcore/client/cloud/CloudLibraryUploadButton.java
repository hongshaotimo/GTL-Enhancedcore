package com.gtl.enhancedcore.client.cloud;

import appeng.client.gui.Icon;
import appeng.client.gui.widgets.IconButton;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.List;
import java.util.ArrayList;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;

public final class CloudLibraryUploadButton extends IconButton {
    public static final int SIZE = 12;
    private String diagnostic = "";
    public CloudLibraryUploadButton(Runnable action) {
        super(button -> action.run());
        setWidth(SIZE); setHeight(SIZE);
        setMessage(Component.translatable("gtl_enhancedcore.cloud.terminal.upload"));
    }
    public void updateDiagnostic(String value) {
        diagnostic = value == null ? "" : value;
    }
    @Override protected Icon getIcon() { return Icon.ARROW_UP; }
    @Override public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (!visible) return;
        RenderSystem.disableDepthTest();
        RenderSystem.enableBlend();
        if (isHoveredOrFocused()) {
            int x = getX(), y = getY();
            graphics.fill(x - 1, y - 1, x + SIZE + 1, y, 0xffffffff);
            graphics.fill(x - 1, y, x, y + SIZE, 0xffffffff);
            graphics.fill(x + SIZE, y, x + SIZE + 1, y + SIZE, 0xffffffff);
            graphics.fill(x - 1, y + SIZE, x + SIZE + 1, y + SIZE + 1, 0xffffffff);
        }
        // IconButton paints a fixed 16px raster regardless of setWidth/setHeight.
        // Match Plus's 12px selector in all three surfaces: paint, hitbox and tooltip.
        if (!isDisableBackground()) Icon.TOOLBAR_BUTTON_BACKGROUND.getBlitter()
                .dest(getX(), getY(), SIZE, SIZE).blit(graphics);
        getIcon().getBlitter().opacity(active ? 1 : 0.5f)
                .dest(getX(), getY(), SIZE, SIZE).blit(graphics);
        RenderSystem.enableDepthTest();
    }
    @Override public Rect2i getTooltipArea() { return new Rect2i(getX(), getY(), SIZE, SIZE); }
    @Override public List<Component> getTooltipMessage() {
        // AEBaseScreen already renders ITooltip. A vanilla Tooltip on this widget
        // schedules a second tooltip at the same mouse position.
        var lines = new ArrayList<Component>();
        lines.add(Component.translatable("gtl_enhancedcore.cloud.terminal.upload"));
        lines.add(Component.translatable("gtl_enhancedcore.cloud.terminal.upload_hint"));
        if (!diagnostic.isEmpty()) {
            lines.add(Component.translatable(diagnostic.contains("Cloud snapshot write failed")
                    ? "gtl_enhancedcore.cloud.terminal.retry_commit" : "gtl_enhancedcore.cloud.status.review"));
        }
        return lines;
    }
}
