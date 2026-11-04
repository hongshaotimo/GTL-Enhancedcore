package com.gtl.enhancedcore.client.gui;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.Optional;
import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.gui.inputs.IJeiInputHandler;
import mezz.jei.api.gui.inputs.IJeiUserInput;
import mezz.jei.api.gui.inputs.RecipeSlotUnderMouse;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/** A JEI layout keeps its natural font/slot sizes inside a bounded, scrollable viewport. */
public final class RecipeViewport<R> implements IRecipeLayoutDrawable<R> {
    private static final int BAR = 7;
    private final IRecipeLayoutDrawable<R> content;
    private final int naturalWidth, naturalHeight, border;
    private final boolean resizedContent;
    private final IJeiInputHandler input = new ViewportInput();
    private int x, y, scrollX, scrollY;
    private int availableWidth = Integer.MAX_VALUE, availableHeight = Integer.MAX_VALUE;
    private int reservedWidth, reservedHeight;
    private boolean dragHorizontal, dragVertical;
    private double dragOffsetX, dragOffsetY;

    public RecipeViewport(IRecipeLayoutDrawable<R> content) {
        this.content = content;
        Rect2i rect = content.getRect();
        if (content.getRecipe() instanceof com.gregtechceu.gtceu.integration.jei.recipe.GTRecipeWrapper wrapper) {
            naturalWidth = wrapper.getWidget().getSizeWidth();
            naturalHeight = wrapper.getWidget().getSizeHeight();
        } else {
            naturalWidth = rect.getWidth();
            naturalHeight = rect.getHeight();
        }
        resizedContent = naturalWidth != rect.getWidth() || naturalHeight != rect.getHeight();
        border = Math.max(0, (content.getRectWithBorder().getWidth() - rect.getWidth()) / 2);
        x = rect.getX();
        y = rect.getY();
    }

    public static <T> IRecipeLayoutDrawable<T> wrap(IRecipeLayoutDrawable<T> layout) {
        if (layout instanceof RecipeViewport<?>) return layout;
        if (!(layout.getRecipe() instanceof com.gregtechceu.gtceu.integration.jei.recipe.GTRecipeWrapper wrapper)
                || wrapper.recipe.recipeType != org.gtlcore.gtlcore.common.data.GTLRecipeTypes.COSMOS_SIMULATION_RECIPES)
            return layout;
        return new RecipeViewport<>(layout);
    }

    private int width() {
        return Math.min(Math.max(naturalWidth, reservedWidth), Math.min(availableWidth, Math.max(80, Minecraft.getInstance().getWindow().getGuiScaledWidth() - 80)));
    }

    private int height() {
        return Math.min(Math.max(naturalHeight, reservedHeight), Math.min(availableHeight, Math.max(56, Minecraft.getInstance().getWindow().getGuiScaledHeight() - 114)));
    }

    /** JEI's configured panel can be shorter than the window; use its actual content area. */
    public void fitWithin(int width, int height) {
        reservedWidth = reservedHeight = 0;
        availableWidth = Math.max(16, width - border * 2);
        availableHeight = Math.max(16, height - border * 2);
        positionContent();
    }

    /** Native JEI spaces every recipe using the first card, so cards on a page need one outer size. */
    public void reserveSizeWithBorder(int width, int height) {
        reservedWidth = Math.max(0, width - border * 2);
        reservedHeight = Math.max(0, height - border * 2);
        positionContent();
    }

    public void resetViewport() {
        reservedWidth = reservedHeight = 0;
        availableWidth = availableHeight = Integer.MAX_VALUE;
        positionContent();
    }

    private boolean verticalBar() {
        return naturalHeight > height() || naturalWidth > width() && naturalHeight > height() - BAR;
    }
    private boolean horizontalBar() {
        return naturalWidth > width() || naturalHeight > height() && naturalWidth > width() - BAR;
    }
    public int contentWidth() { return width() - (verticalBar() ? BAR : 0); }
    public int contentHeight() { return height() - (horizontalBar() ? BAR : 0); }
    public int maxScrollX() { return Math.max(0, naturalWidth - contentWidth()); }
    public int maxScrollY() { return Math.max(0, naturalHeight - contentHeight()); }
    public int scrollX() { return scrollX; }
    public int scrollY() { return scrollY; }

    private void positionContent() {
        scrollX = Mth.clamp(scrollX, 0, maxScrollX());
        scrollY = Mth.clamp(scrollY, 0, maxScrollY());
        content.setPosition(x - scrollX, y - scrollY);
    }

    private boolean containsContent(double mouseX, double mouseY) {
        return mouseX >= x && mouseY >= y && mouseX < x + contentWidth() && mouseY < y + contentHeight();
    }

    @Override public void setPosition(int x, int y) {
        this.x = x;
        this.y = y;
        positionContent();
    }

    @Override public void drawRecipe(GuiGraphics graphics, int mouseX, int mouseY) {
        positionContent();
        if (maxScrollX() == 0 && maxScrollY() == 0 && !resizedContent && width() == naturalWidth && height() == naturalHeight) {
            content.drawRecipe(graphics, mouseX, mouseY);
            return;
        }
        graphics.fill(x - border, y - border, x + width() + border, y + height() + border, 0xff22262b);
        graphics.enableScissor(x, y, x + contentWidth(), y + contentHeight());
        content.drawRecipe(graphics, containsContent(mouseX, mouseY) ? mouseX : -10000,
                containsContent(mouseX, mouseY) ? mouseY : -10000);
        graphics.disableScissor();
        if (maxScrollY() > 0) drawBar(graphics, false);
        if (maxScrollX() > 0) drawBar(graphics, true);
    }

    private void drawBar(GuiGraphics graphics, boolean horizontal) {
        int length = horizontal ? contentWidth() : contentHeight();
        int maximum = horizontal ? maxScrollX() : maxScrollY();
        int offset = horizontal ? scrollX : scrollY;
        int thumb = thumbLength(horizontal);
        int start = maximum == 0 ? 0 : (length - thumb) * offset / maximum;
        int left = horizontal ? x : x + contentWidth() + 1;
        int top = horizontal ? y + contentHeight() + 1 : y;
        int right = horizontal ? left + length : left + BAR - 2;
        int bottom = horizontal ? top + BAR - 2 : top + length;
        graphics.fill(left, top, right, bottom, 0xff343a43);
        graphics.fill(left + (horizontal ? start : 0), top + (horizontal ? 0 : start),
                horizontal ? left + start + thumb : right, horizontal ? bottom : top + start + thumb, 0xffb8c0cc);
    }

    private int thumbLength(boolean horizontal) {
        int length = horizontal ? contentWidth() : contentHeight();
        int maximum = horizontal ? maxScrollX() : maxScrollY();
        int minimum = Math.min(16, Math.max(1, length / 3));
        return Math.min(length, Math.max(minimum, (int)((long)length * length / (length + maximum))));
    }

    /** Absolute thumb geometry also lets root-input audits grab the currently visible thumb. */
    public Rect2i getScrollThumb(boolean horizontal) {
        int length = horizontal ? contentWidth() : contentHeight();
        int maximum = horizontal ? maxScrollX() : maxScrollY();
        int offset = horizontal ? scrollX : scrollY;
        int thumb = thumbLength(horizontal);
        int start = maximum == 0 ? 0 : (int)((long)(length - thumb) * offset / maximum);
        return horizontal ? new Rect2i(x + start, y + contentHeight() + 1, thumb, BAR - 2)
                : new Rect2i(x + contentWidth() + 1, y + start, BAR - 2, thumb);
    }

    @Override public void drawOverlays(GuiGraphics graphics, int mouseX, int mouseY) {
        positionContent();
        if (containsContent(mouseX, mouseY)) content.drawOverlays(graphics, mouseX, mouseY);
        else if (isMouseOver(mouseX, mouseY)) graphics.renderTooltip(Minecraft.getInstance().font,
                Component.translatable("gtl_enhancedcore.gui.recipe_viewport_hint"), mouseX, mouseY);
    }

    @Override public boolean isMouseOver(double mouseX, double mouseY) {
        return mouseX >= x && mouseY >= y && mouseX < x + width() && mouseY < y + height();
    }

    @Override public <T> Optional<T> getIngredientUnderMouse(int mouseX, int mouseY, IIngredientType<T> type) {
        return getSlotUnderMouse(mouseX, mouseY).flatMap(slot -> slot.slot().getDisplayedIngredient(type));
    }

    @Override public Optional<IRecipeSlotDrawable> getRecipeSlotUnderMouse(double mouseX, double mouseY) {
        return getSlotUnderMouse(mouseX, mouseY).map(RecipeSlotUnderMouse::slot);
    }

    @Override public Optional<RecipeSlotUnderMouse> getSlotUnderMouse(double mouseX, double mouseY) {
        positionContent();
        return containsContent(mouseX, mouseY) ? content.getSlotUnderMouse(mouseX, mouseY) : Optional.empty();
    }

    @Override public Rect2i getRect() { return new Rect2i(x, y, width(), height()); }
    @Override public Rect2i getRectWithBorder() {
        return new Rect2i(x - border, y - border, width() + 2 * border, height() + 2 * border);
    }

    @Override public Rect2i getRecipeTransferButtonArea() {
        return maxScrollX() == 0 && maxScrollY() == 0 && !resizedContent ? content.getRecipeTransferButtonArea()
                : new Rect2i(width() + border + 2, height() - 13, 13, 13);
    }
    @Override public Rect2i getRecipeBookmarkButtonArea() { return getSideButtonArea(1); }
    @Override public Rect2i getSideButtonArea(int index) {
        if (maxScrollX() == 0 && maxScrollY() == 0 && !resizedContent) return content.getSideButtonArea(index);
        int rows = Math.max(1, (height() + 2 * border + 2) / 15);
        return new Rect2i(width() + border + 2 + index / rows * 15, height() - 13 - index % rows * 15, 13, 13);
    }
    @Override public IRecipeSlotsView getRecipeSlotsView() { return content.getRecipeSlotsView(); }
    @Override public IRecipeCategory<R> getRecipeCategory() { return content.getRecipeCategory(); }
    @Override public R getRecipe() { return content.getRecipe(); }
    @Override public IJeiInputHandler getInputHandler() { return input; }
    @Override public void tick() { content.tick(); }

    private final class ViewportInput implements IJeiInputHandler {
        @Override public ScreenRectangle getArea() { return new ScreenRectangle(x, y, width(), height()); }

        @Override public boolean handleMouseScrolled(double mouseX, double mouseY, double delta) {
            if (!isMouseOver(mouseX, mouseY) || delta == 0) return false;
            if (maxScrollX() > 0 || maxScrollY() > 0) {
                int amount = (int) Math.copySign(Math.max(1, Math.round(Math.abs(delta) * 20)), delta);
                if (maxScrollX() > 0 && (Screen.hasShiftDown() || maxScrollY() == 0)) scrollX -= amount;
                else scrollY -= amount;
                positionContent();
                return true;
            }
            return content.getInputHandler().handleMouseScrolled(mouseX, mouseY, delta);
        }

        @Override public boolean handleInput(double mouseX, double mouseY, IJeiUserInput userInput) {
            dragHorizontal = dragVertical = false;
            if (!isMouseOver(mouseX, mouseY)) return false;
            boolean horizontal = maxScrollX() > 0 && mouseY >= y + contentHeight() && mouseX < x + contentWidth();
            boolean vertical = maxScrollY() > 0 && mouseX >= x + contentWidth() && mouseY < y + contentHeight();
            if (horizontal || vertical) {
                if (!userInput.getKey().equals(InputConstants.Type.MOUSE.getOrCreate(0))) return false;
                if (!userInput.isSimulate()) {
                    dragHorizontal = horizontal;
                    dragVertical = vertical;
                    Rect2i thumb = getScrollThumb(horizontal);
                    double coordinate = horizontal ? mouseX : mouseY;
                    int start = horizontal ? thumb.getX() : thumb.getY();
                    int length = horizontal ? thumb.getWidth() : thumb.getHeight();
                    boolean onThumb = coordinate >= start && coordinate < start + length;
                    int trackLength = horizontal ? contentWidth() : contentHeight();
                    int maximum = horizontal ? maxScrollX() : maxScrollY();
                    int current = horizontal ? scrollX : scrollY;
                    // Keep the fractional thumb position, avoiding a one-pixel jump from rendered rounding.
                    double offset = onThumb ? coordinate - (horizontal ? x : y)
                            - (double)(trackLength - length) * current / maximum : length / 2d;
                    if (horizontal) dragOffsetX = offset; else dragOffsetY = offset;
                    moveThumb(mouseX, mouseY);
                }
                return true;
            }
            if (!containsContent(mouseX, mouseY)) return false;
            positionContent();
            return content.getInputHandler().handleInput(mouseX, mouseY, userInput);
        }

        private void moveThumb(double mouseX, double mouseY) {
            if (dragHorizontal) {
                int travel = contentWidth() - thumbLength(true);
                if (travel > 0) scrollX = (int)Math.round(Mth.clamp((mouseX - x - dragOffsetX) / travel, 0, 1) * maxScrollX());
            }
            if (dragVertical) {
                int travel = contentHeight() - thumbLength(false);
                if (travel > 0) scrollY = (int)Math.round(Mth.clamp((mouseY - y - dragOffsetY) / travel, 0, 1) * maxScrollY());
            }
            positionContent();
        }

        @Override public boolean handleMouseDragged(double mouseX, double mouseY, InputConstants.Key key,
                                                   double dragX, double dragY) {
            if ((dragHorizontal || dragVertical) && key.equals(InputConstants.Type.MOUSE.getOrCreate(0))) {
                moveThumb(mouseX, mouseY);
                return true;
            }
            positionContent();
            return containsContent(mouseX, mouseY)
                    && content.getInputHandler().handleMouseDragged(mouseX, mouseY, key, dragX, dragY);
        }

        @Override public void handleMouseMoved(double mouseX, double mouseY) {
            positionContent();
            content.getInputHandler().handleMouseMoved(containsContent(mouseX, mouseY) ? mouseX : -10000,
                    containsContent(mouseX, mouseY) ? mouseY : -10000);
        }
    }
}
