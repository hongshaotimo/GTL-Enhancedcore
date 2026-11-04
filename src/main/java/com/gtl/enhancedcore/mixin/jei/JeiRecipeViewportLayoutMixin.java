package com.gtl.enhancedcore.mixin.jei;

import com.gtl.enhancedcore.client.gui.RecipeViewport;
import java.util.List;
import mezz.jei.common.util.ImmutableRect2i;
import mezz.jei.gui.recipes.IRecipeLayoutWithButtons;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "mezz.jei.gui.recipes.RecipeGuiLayouts", remap = false)
public abstract class JeiRecipeViewportLayoutMixin {
    @Shadow @Final private List<IRecipeLayoutWithButtons<?>> recipeLayoutsWithButtons;

    @Inject(method = "setRecipeLayoutsWithButtons", at = @At("RETURN"), require = 1)
    private void enhanced$resetForWindowChange(List<IRecipeLayoutWithButtons<?>> layouts, CallbackInfo ci) {
        for (var buttons : recipeLayoutsWithButtons) {
            if (buttons.getRecipeLayout() instanceof RecipeViewport<?> viewport) viewport.resetViewport();
        }
    }

    @Inject(method = "updateLayout", at = @At("HEAD"), require = 1)
    private void enhanced$fitConfiguredPanel(ImmutableRect2i area, int recipesPerPage, CallbackInfo ci) {
        if (recipeLayoutsWithButtons.stream().noneMatch(buttons -> buttons.getRecipeLayout() instanceof RecipeViewport<?>)) return;
        int count = Math.max(1, recipesPerPage);
        int gap = Math.min(4, Math.max(0, area.getHeight() / (count + 1) / 4));
        int rowHeight = Math.max(16, (area.getHeight() - gap * (count + 1)) / count);
        int controls = recipeLayoutsWithButtons.stream().mapToInt(buttons ->
                Math.max(0, buttons.totalWidth() - buttons.getRecipeLayout().getRectWithBorder().getWidth())).max().orElse(0);
        // Height can move side buttons into another column. A second pass reserves their final width.
        for (int pass = 0; pass < 2; pass++) {
            int width = 0, height = 0;
            for (var buttons : recipeLayoutsWithButtons) {
                if (buttons.getRecipeLayout() instanceof RecipeViewport<?> viewport) viewport.fitWithin(area.getWidth() - controls, rowHeight);
                var bounds = buttons.getRecipeLayout().getRectWithBorder();
                width = Math.max(width, bounds.getWidth()); height = Math.max(height, bounds.getHeight());
            }
            for (var buttons : recipeLayoutsWithButtons) {
                if (buttons.getRecipeLayout() instanceof RecipeViewport<?> viewport) viewport.reserveSizeWithBorder(width, height);
            }
            controls = recipeLayoutsWithButtons.stream().mapToInt(buttons ->
                    Math.max(0, buttons.totalWidth() - buttons.getRecipeLayout().getRectWithBorder().getWidth())).max().orElse(0);
        }
    }

    @Inject(method = "getRecipeXOffset", at = @At("HEAD"), cancellable = true, require = 1)
    private void enhanced$centerAllSideButtons(ImmutableRect2i layout, ImmutableRect2i area, CallbackInfoReturnable<Integer> cir) {
        if (recipeLayoutsWithButtons.stream().noneMatch(buttons -> buttons.getRecipeLayout() instanceof RecipeViewport<?>)) return;
        int firstWidth = recipeLayoutsWithButtons.getFirst().totalWidth();
        int widest = recipeLayoutsWithButtons.stream().mapToInt(IRecipeLayoutWithButtons::totalWidth).max().orElse(firstWidth);
        if (widest > firstWidth) cir.setReturnValue(area.getX() + Math.max(0, (area.getWidth() - widest) / 2));
    }
}
