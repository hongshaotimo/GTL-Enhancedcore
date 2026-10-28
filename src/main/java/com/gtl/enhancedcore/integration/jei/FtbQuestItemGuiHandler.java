package com.gtl.enhancedcore.integration.jei;

import com.gtl.enhancedcore.mixin.ftblibrary.client.QuestModalPanelsAccessor;
import dev.ftb.mods.ftblibrary.ui.Panel;
import dev.ftb.mods.ftblibrary.ui.ScreenWrapper;
import dev.ftb.mods.ftblibrary.ui.Widget;
import dev.ftb.mods.ftblibrary.util.client.PositionedIngredient;
import dev.ftb.mods.ftbquests.client.gui.quests.QuestScreen;
import dev.ftb.mods.ftbquests.client.gui.quests.RewardButton;
import dev.ftb.mods.ftbquests.client.gui.quests.TaskButton;
import java.util.Optional;
import mezz.jei.api.gui.builder.IClickableIngredientFactory;
import mezz.jei.api.gui.handlers.IGlobalGuiHandler;
import mezz.jei.api.gui.handlers.IGuiProperties;
import mezz.jei.api.gui.handlers.IScreenHandler;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import mezz.jei.api.runtime.IClickableIngredient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.item.ItemStack;

/** Exposes actual quest item ingredients to JEI's normal (rebindable) input handling. */
public final class FtbQuestItemGuiHandler implements IGlobalGuiHandler, IScreenHandler<ScreenWrapper> {
    private FtbQuestItemGuiHandler() {}

    public static void register(IGuiHandlerRegistration registration) {
        FtbQuestItemGuiHandler handler = new FtbQuestItemGuiHandler();
        registration.addGlobalGuiHandler(handler);
        registration.addGuiScreenHandler(ScreenWrapper.class, handler);
    }

    @Override
    public IGuiProperties apply(ScreenWrapper screen) {
        if (!(screen.getGui() instanceof QuestScreen quests) || !quests.isViewingQuest()) return null;
        // JEI gates its keyboard input on GUI properties, even when a global ingredient exists.
        // Occupy the entire screen so the quest map keeps its layout without JEI side overlays.
        return new QuestScreenProperties(screen.getClass(), screen.width, screen.height);
    }

    private record QuestScreenProperties(Class<? extends Screen> screenClass, int width, int height)
            implements IGuiProperties {
        @Override public Class<? extends Screen> getScreenClass() { return screenClass; }
        @Override public int getGuiLeft() { return 0; }
        @Override public int getGuiTop() { return 0; }
        @Override public int getGuiXSize() { return width; }
        @Override public int getGuiYSize() { return height; }
        @Override public int getScreenWidth() { return width; }
        @Override public int getScreenHeight() { return height; }
    }

    @Override
    public Optional<? extends IClickableIngredient<?>> getClickableIngredientUnderMouse(
            IClickableIngredientFactory factory, double mouseX, double mouseY) {
        if (!(Minecraft.getInstance().screen instanceof ScreenWrapper wrapper)
                || !(wrapper.getGui() instanceof QuestScreen quests)
                || !quests.isViewingQuest()
                || !(quests instanceof QuestModalPanelsAccessor access)
                || access.enhanced$getQuestModalPanels().peekFirst() != quests.viewQuestPanel) {
            return Optional.empty();
        }
        // JEI supplies GUI-scaled coordinates. Refresh FTB's hover/clipping state for this query,
        // rather than reusing the last rendered frame after the mouse moves or the panel scrolls.
        quests.updateMouseOver((int) mouseX, (int) mouseY);
        return findItem(factory, quests.viewQuestPanel, mouseX, mouseY);
    }

    private static Optional<? extends IClickableIngredient<?>> findItem(
            IClickableIngredientFactory factory, Panel panel, double mouseX, double mouseY) {
        if (!panel.isEnabled() || !panel.isMouseOver()) return Optional.empty();
        boolean wasOffset = panel.isOffset();
        panel.setOffset(true);
        try {
            var widgets = panel.getWidgets();
            for (int i = widgets.size() - 1; i >= 0; i--) {
                Widget widget = widgets.get(i);
                if (!widget.isEnabled() || !widget.shouldDraw() || !widget.isMouseOver()) continue;
                if (widget instanceof Panel child) {
                    var ingredient = findItem(factory, child, mouseX, mouseY);
                    if (ingredient.isPresent()) return ingredient;
                } else {
                    Optional<PositionedIngredient> ingredient;
                    if (widget instanceof TaskButton task) {
                        ingredient = task.getIngredientUnderMouse();
                    } else if (widget instanceof RewardButton reward) {
                        // 2001.4.22 RewardButton wraps this Optional a second time; use the
                        // reward's public API so a PositionedIngredient is never a JEI item.
                        ingredient = reward.reward.getIngredient(reward);
                    } else {
                        continue;
                    }
                    if (ingredient.isPresent() && ingredient.get().ingredient() instanceof ItemStack stack
                            && !stack.isEmpty()
                            && mouseX >= widget.getX() && mouseX < widget.getX() + widget.width
                            && mouseY >= widget.getY() && mouseY < widget.getY() + widget.height) {
                        // PositionedIngredient.area uses local posX/Y in this FTB Library version.
                        // Resolve absolute coordinates while all scrolling ancestor offsets apply.
                        return factory.createBuilder(stack.copy()).buildWithArea(
                                widget.getX(), widget.getY(), widget.width, widget.height);
                    }
                }
            }
            return Optional.empty();
        } finally {
            panel.setOffset(wasOffset);
        }
    }
}
