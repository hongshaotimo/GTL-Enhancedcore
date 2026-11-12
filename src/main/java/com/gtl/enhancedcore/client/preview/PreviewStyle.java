package com.gtl.enhancedcore.client.preview;

import com.gtl.enhancedcore.common.gui.GuiStyle;
import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib.gui.texture.ResourceTexture;
import com.lowdragmc.lowdraglib.gui.widget.SceneWidget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/** Small, shared surfaces for the fullscreen preview; native slots and item rendering stay intact. */
public final class PreviewStyle {
    public static final int BUTTON_SIZE = 16;
    public static final int BUTTON_GAP = 2;
    public static final int TEXT_BUTTON_WIDTH = 34;
    public static final int TOOLBAR_Y = 11;

    private static final int OUTSIDE = 0xff202427;
    private static final int FRAME = 0xff62686a;
    private static final int VIEW = 0xff383e42;
    private static final int PANEL_FILL = 0xffb7bab5;
    private static final int PANEL_HEADER = 0xffc9cbc4;
    private static final int INSET_FILL = 0xffa6aba6;
    private static final int EDGE_LIGHT = 0xffe3e5dc;
    private static final int EDGE_DARK = 0xff727a7b;
    private static final int BUTTON_FILL = 0xffbfc4bc;
    private static final int BUTTON_HOVER = 0xffd5d9cf;
    private static final int BUTTON_SELECTED = 0xffa4b4b2;
    private static final int SELECTION = 0xff48696b;

    public static final IGuiTexture INFO = new ResourceTexture("jei:textures/jei/atlas/gui/icons/info.png");
    public static final IGuiTexture PATTERN = new ResourceTexture("jei:textures/jei/atlas/gui/icons/recipe_transfer.png");

    public static final IGuiTexture PANEL = (graphics, mouseX, mouseY, x, y, width, height) -> {
        int left = (int) x;
        int top = (int) y;
        surface(graphics, left, top, width, height, PANEL_FILL, FRAME);
        graphics.fill(left + 1, top + 1, left + width - 1, top + 21, PANEL_HEADER);
        graphics.fill(left + 1, top + 1, left + width - 1, top + 2, EDGE_LIGHT);
        graphics.fill(left + 1, top + 20, left + width - 1, top + 21, EDGE_DARK);
    };

    public static final IGuiTexture CANDIDATE_GROUP = (graphics, mouseX, mouseY, x, y, width, height) -> {
        int left = (int) x;
        int top = (int) y;
        graphics.fill(left, top, left + width, top + height, INSET_FILL);
        graphics.fill(left, top, left + width, top + 1, 0xff949d99);
    };

    public static final IGuiTexture SCROLL_TRACK = (graphics, mouseX, mouseY, x, y, width, height) ->
            graphics.fill((int) x + 2, (int) y, (int) x + width - 2, (int) y + height, 0xff8b9591);

    public static final IGuiTexture SCROLL_THUMB = (graphics, mouseX, mouseY, x, y, width, height) ->
            surface(graphics, (int) x + 1, (int) y, width - 2, height, BUTTON_FILL, EDGE_DARK);

    private PreviewStyle() {}

    /** Replaces only the native fullscreen screen's broad, repeatedly bevelled background. */
    public static void drawFrame(GuiGraphics graphics, int x, int y, int width, int height, SceneWidget scene) {
        graphics.fill(x - 8, y - 8, x + width + 8, y + height + 8, OUTSIDE);
        surface(graphics, x, y, width, height, VIEW, FRAME);
        graphics.fill(x + 1, y + 1, x + width - 1, y + 23, PANEL_HEADER);
        graphics.fill(x + 1, y + 1, x + width - 1, y + 2, EDGE_LIGHT);
        graphics.fill(x + 1, y + 22, x + width - 1, y + 23, EDGE_DARK);
        surface(graphics, scene.getPositionX() - 1, scene.getPositionY() - 1,
                scene.getSizeWidth() + 2, scene.getSizeHeight() + 2, VIEW, 0xff252b2e);
    }

    public static IGuiTexture iconButton(IGuiTexture icon) {
        return iconButton(() -> icon, () -> false);
    }

    public static IGuiTexture iconButton(Supplier<IGuiTexture> icon, BooleanSupplier selected) {
        return (graphics, mouseX, mouseY, x, y, width, height) -> {
            button(graphics, mouseX, mouseY, x, y, width, height, selected.getAsBoolean());
            int size = Math.min(12, Math.min(width, height) - 2);
            icon.get().draw(graphics, mouseX, mouseY,
                    x + (width - size) / 2f, y + (height - size) / 2f, size, size);
        };
    }

    /** The supplier remains the native page/layer/module label, including its live state. */
    public static IGuiTexture textButton(Supplier<String> label) {
        return (graphics, mouseX, mouseY, x, y, width, height) -> {
            button(graphics, mouseX, mouseY, x, y, width, height, false);
            var font = Minecraft.getInstance().font;
            String text = font.plainSubstrByWidth(label.get(), Math.max(0, width - 4));
            graphics.drawString(font, text, (int) x + (width - font.width(text)) / 2,
                    (int) y + (height - font.lineHeight) / 2, GuiStyle.LIGHT_TEXT, false);
        };
    }

    /** Keep the compact texture's tick hook and its entire original draw path. */
    public static IGuiTexture textControl(IGuiTexture original, Supplier<String> label, BooleanSupplier fullscreen) {
        IGuiTexture button = textButton(label);
        return new IGuiTexture() {
            @Override
            public void draw(GuiGraphics graphics, int mouseX, int mouseY, float x, float y, int width, int height) {
                (fullscreen.getAsBoolean() ? button : original).draw(graphics, mouseX, mouseY, x, y, width, height);
            }

            @Override
            public void updateTick() {
                original.updateTick();
            }
        };
    }

    private static void button(GuiGraphics graphics, int mouseX, int mouseY, float x, float y,
                               int width, int height, boolean selected) {
        boolean hover = mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
        int left = (int) x;
        int top = (int) y;
        surface(graphics, left, top, width, height,
                hover ? BUTTON_HOVER : selected ? BUTTON_SELECTED : BUTTON_FILL, EDGE_DARK);
        graphics.fill(left + 1, top + 1, left + width - 1, top + 2, EDGE_LIGHT);
        if (selected) graphics.fill(left + 1, top + height - 2, left + width - 1, top + height - 1, SELECTION);
    }

    private static void surface(GuiGraphics graphics, int x, int y, int width, int height, int fill, int border) {
        graphics.fill(x, y, x + width, y + height, border);
        graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, fill);
    }
}
