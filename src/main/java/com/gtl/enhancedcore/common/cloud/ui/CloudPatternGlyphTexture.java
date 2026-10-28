package com.gtl.enhancedcore.common.cloud.ui;

import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;

/** Small unscaled terminal glyphs, kept on the same pixel grid as AE's 18px slots. */
final class CloudPatternGlyphTexture implements IGuiTexture {
    static final IGuiTexture SEARCH = new CloudPatternGlyphTexture("011100000", "100010000", "100010000", "100010000", "011100000", "000011000", "000001100", "000000000", "000000000");
    static final IGuiTexture PREVIOUS = new CloudPatternGlyphTexture(1);
    static final IGuiTexture NEXT = new CloudPatternGlyphTexture(2);
    static final IGuiTexture UPLOAD = new CloudPatternGlyphTexture(3);
    static final IGuiTexture UNDO = new CloudPatternGlyphTexture(4);
    static final IGuiTexture BACK = PREVIOUS;
    private final String[] pixels;
    private final int nativeIcon;
    private CloudPatternGlyphTexture(String... pixels) { this.pixels = pixels; nativeIcon = 0; }
    private CloudPatternGlyphTexture(int nativeIcon) { pixels = new String[0]; this.nativeIcon = nativeIcon; }
    @net.minecraftforge.api.distmarker.OnlyIn(net.minecraftforge.api.distmarker.Dist.CLIENT)
    @Override public void draw(net.minecraft.client.gui.GuiGraphics graphics, int mouseX, int mouseY, float x, float y, int width, int height) {
        if (nativeIcon != 0) {
            var icon = switch (nativeIcon) {
                case 1 -> appeng.client.gui.Icon.ARROW_LEFT;
                case 2 -> appeng.client.gui.Icon.ARROW_RIGHT;
                case 4 -> appeng.client.gui.Icon.ARROW_DOWN;
                default -> appeng.client.gui.Icon.ARROW_UP;
            };
            int size = Math.min(16, Math.min(width - 2, height - 2));
            icon.getBlitter().dest((int) x + (width - size) / 2, (int) y + (height - size) / 2, size, size).blit(graphics);
            return;
        }
        int left = (int) x + (width - 9) / 2, top = (int) y + (height - 9) / 2;
        for (int row = 0; row < pixels.length; row++) for (int col = 0; col < pixels[row].length(); col++)
            if (pixels[row].charAt(col) == '1') graphics.fill(left + col, top + row + 1, left + col + 1, top + row + 2, 0xfff1f0f7);
        for (int row = 0; row < pixels.length; row++) for (int col = 0; col < pixels[row].length(); col++)
            if (pixels[row].charAt(col) == '1') graphics.fill(left + col, top + row, left + col + 1, top + row + 1, 0xff655e76);
    }
}
