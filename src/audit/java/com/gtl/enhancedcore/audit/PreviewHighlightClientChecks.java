package com.gtl.enhancedcore.audit;

import org.gtlcore.gtlcore.client.preview.PreviewMaterialHighlights;
import com.lowdragmc.lowdraglib.gui.widget.DraggableScrollableWidgetGroup;
import com.lowdragmc.lowdraglib.gui.widget.SlotWidget;
import com.mojang.blaze3d.platform.NativeImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.gtlcore.gtlcore.client.preview.FullscreenPreviewScreen;
import org.gtlcore.gtlcore.api.gui.PatternPreviewWidget;
import org.gtlcore.gtlcore.integration.jei.SlotRecipeWidget;

/** Runs the actual AE error renderer and checks framebuffer pixels at live material slots. */
@Mod.EventBusSubscriber(modid = "enhancedcore_audit", value = Dist.CLIENT)
public final class PreviewHighlightClientChecks {
    private static int checks;
    private static PatternPreviewWidget pending;
    private static Screen expectedScreen;
    private static int machineIndex, result, recipeX, recipeY;
    private static String screenMode;
    private static Throwable failure;

    static void queue(PatternPreviewWidget preview, int machine) {
        pending = preview;
        expectedScreen = Minecraft.getInstance().screen;
        machineIndex = machine;
        result = -1;
        failure = null;
    }

    static int result() {
        if (failure != null) throw new IllegalStateException("Highlight render test failed", failure);
        if (result < 0) throw new IllegalStateException("Highlight render callback did not run");
        return result;
    }

    @SubscribeEvent
    public static void render(ScreenEvent.Render.Post event) {
        if (pending == null || event.getScreen() != expectedScreen) return;
        try {
            event.getGuiGraphics().flush();
            result = run(pending, machineIndex, event.getGuiGraphics());
        } catch (Throwable error) {
            failure = error;
        } finally {
            pending = null;
        }
    }

    private static int run(PatternPreviewWidget preview, int machine, GuiGraphics graphics) throws Exception {
        checks = 0;
        java.nio.file.Files.createDirectories(Minecraft.getInstance().gameDirectory.toPath().resolve("screenshots"));
        var strip = preview.widgets.stream().filter(DraggableScrollableWidgetGroup.class::isInstance)
                .map(DraggableScrollableWidgetGroup.class::cast).findFirst().orElseThrow();
        var slots = strip.widgets.stream().filter(SlotWidget.class::isInstance).map(SlotWidget.class::cast).toList();
        var slot = slots.get(0);
        var manager = PreviewJeiAudit.runtime.getRecipeManager();
        var views = new ArrayList<IRecipeSlotView>();
        recipeX = recipeY = 0;
        screenMode = expectedScreen instanceof FullscreenPreviewScreen ? "full" : "window";
        if ("window".equals(screenMode)) {
            var layoutsField = expectedScreen.getClass().getDeclaredField("layouts");
            layoutsField.setAccessible(true);
            var layouts = layoutsField.get(expectedScreen);
            var listField = layouts.getClass().getDeclaredField("recipeLayoutsWithButtons");
            listField.setAccessible(true);
            var layout = ((mezz.jei.gui.recipes.IRecipeLayoutWithButtons<?>) ((List<?>) listField.get(layouts)).get(0))
                    .getRecipeLayout();
            recipeX = layout.getRect().getX();
            recipeY = layout.getRect().getY();
            views.addAll(layout.getRecipeSlotsView().getSlotViews(RecipeIngredientRole.INPUT));
            check(views.size() == slots.size(), "Real JEI material mapping differs");
        } else {
            for (var material : slots) {
                var view = manager.createRecipeSlotDrawable(RecipeIngredientRole.INPUT, List.of(), Set.of(), 0);
                new SlotRecipeWidget(material, view);
                check(view.getAreaIncludingBackground().getX() == 0 && view.getAreaIncludingBackground().getY() == 0,
                        "JEI widget slot origin changed");
                views.add(view);
            }
        }
        var constructor = Class.forName("appeng.integration.modules.jei.transfer.EncodePatternTransferHandler$ErrorRenderer")
                .getDeclaredConstructor(List.class);
        constructor.setAccessible(true);
        var error = (IRecipeTransferError) constructor.newInstance(views);
        var mc = Minecraft.getInstance();
        var position = slot.getSelfPosition();
        boolean visible = slot.isVisible();
        int scroll = strip.getScrollXOffset();
        try {
            pixels(error, graphics, slots, machine, "materials");
            strip.setScrollXOffset(scroll + 9);
            pixels(error, graphics, slots, machine, "scrolled");
            slot.setSelfPosition(-10, position.y);
            var clipped = PreviewMaterialHighlights.bounds(slot);
            check(clipped != null && clipped.getX() == strip.getPositionX() && clipped.getWidth() == 7,
                    "Partial highlight must clip at material viewport");
            slot.setSelfPosition(-40, position.y);
            check(PreviewMaterialHighlights.bounds(slot) == null, "Offscreen material left a highlight");
            slot.setSelfPosition(position);
            slot.setVisible(false);
            check(PreviewMaterialHighlights.bounds(slot) == null, "Hidden material left a highlight");
            slot.setVisible(true);
            strip.setVisible(false);
            check(PreviewMaterialHighlights.bounds(slot) == null, "Hidden group left a highlight");
            strip.setVisible(true);
            var detached = new SlotWidget();
            check(PreviewMaterialHighlights.bounds(detached) == null, "Detached slot left a highlight");

            // A normal JEI recipe slot must retain JEI's own highlight coordinates.
            var plain = manager.createRecipeSlotDrawable(RecipeIngredientRole.INPUT, List.of(), Set.of(), 0);
            plain.setPosition(28, 60);
            try (var before = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
                var unbound = (IRecipeTransferError) constructor.newInstance(List.of(plain));
                unbound.showError(graphics, mc.screen.width - 1, 40, null, 0, 0);
                graphics.flush();
                try (var after = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
                    check(changed(before, after, new Rect2i(28, 60, 16, 16)) > 0, "Ordinary recipe highlight changed");
                }
            }
        } finally {
            strip.setScrollXOffset(scroll);
            slot.setSelfPosition(position);
            slot.setVisible(visible);
            strip.setVisible(true);
        }
        return checks;
    }

    private static void pixels(IRecipeTransferError error, GuiGraphics graphics, List<SlotWidget> slots,
            int machine, String phase) throws Exception {
        var mc = Minecraft.getInstance();
        try (var before = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            error.showError(graphics, mc.screen.width - 1, 40, null, recipeX, recipeY);
            graphics.flush();
            try (var after = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
                after.writeToFile(mc.gameDirectory.toPath().resolve(
                        "screenshots/fullscreen-" + machine + "-highlight-" + screenMode + "-" + phase + ".png"));
                int visible = 0;
                for (var slot : slots) {
                    var rect = PreviewMaterialHighlights.bounds(slot);
                    if (rect == null) continue;
                    var screenRect = new Rect2i(recipeX + rect.getX(), recipeY + rect.getY(), rect.getWidth(), rect.getHeight());
                    check(changed(before, after, screenRect) > 0, "AE highlight missing at live slot " + screenRect.getX() + "," + screenRect.getY());
                    visible++;
                }
                check(visible > 0, "No visible material slots");
                check(changed(before, after, new Rect2i(recipeX, recipeY, 16, 16)) == 0, "AE highlights still stack at origin");
            }
        }
    }

    private static int changed(NativeImage before, NativeImage after, Rect2i rect) {
        double scale = Minecraft.getInstance().getWindow().getGuiScale();
        int result = 0;
        for (int y = Math.max(0, (int) (rect.getY() * scale)); y < Math.min(before.getHeight(), (int) ((rect.getY() + rect.getHeight()) * scale)); y++) {
            for (int x = Math.max(0, (int) (rect.getX() * scale)); x < Math.min(before.getWidth(), (int) ((rect.getX() + rect.getWidth()) * scale)); x++) {
                if (before.getPixelRGBA(x, y) != after.getPixelRGBA(x, y)) result++;
            }
        }
        return result;
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
        checks++;
    }
}
