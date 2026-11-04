package com.gtl.enhancedcore.mixin.jei;

import com.gregtechceu.gtceu.integration.jei.multipage.MultiblockInfoCategory;
import com.gtl.enhancedcore.client.preview.LazyMultiblockPreviewWidget;
import com.gtl.enhancedcore.client.preview.PreviewPreparation;
import com.llamalad7.mixinextras.sugar.Local;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Objects;
import java.util.Set;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.common.config.RecipeSorterStage;
import mezz.jei.gui.bookmarks.RecipeBookmark;
import mezz.jei.gui.bookmarks.BookmarkList;
import mezz.jei.gui.recipes.IRecipeLayoutWithButtons;
import mezz.jei.gui.recipes.lookups.IFocusedRecipes;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * JEI's ordinary lazy list fills every remaining recipe layout in tick(). For
 * multiblock previews, retain just the requested page and leave ordinary recipe
 * sorting and layout caching unchanged.
 */
@Mixin(targets = "mezz.jei.gui.recipes.layouts.LazyRecipeLayoutList", remap = false)
public abstract class LazyMultiblockRecipeLayoutMixin<T> {
    @Shadow @Final private IRecipeCategory<T> recipeCategory;
    @Unique private List<T> enhanced$recipes;
    @Unique private BookmarkList enhanced$bookmarks;
    @Unique private List<IRecipeLayoutWithButtons<?>> enhanced$page;
    @Unique private int enhanced$first = -1;
    @Unique private int enhanced$end = -1;
    @Unique private int enhanced$generation = -1;
    @Unique private int enhanced$layoutRevision = -1;

    @Invoker("createRecipeLayoutWithButtons")
    protected abstract IRecipeLayoutWithButtons<T> enhanced$createLayout(T recipe, RecipeBookmark<?, ?> bookmark);

    @ModifyVariable(method = "<init>", at = @At("HEAD"), argsOnly = true, ordinal = 0,
            require = 1, allow = 1)
    private static Set<RecipeSorterStage> enhanced$skipPreviewPrewarm(Set<RecipeSorterStage> stages,
            @Local(argsOnly = true) IFocusedRecipes<?> focused) {
        // Sorting by crafting availability necessarily builds every layout.
        // A structure information page has no ordinary crafting recipe to sort.
        return focused.getRecipeCategory() instanceof MultiblockInfoCategory ? Set.of() : stages;
    }

    @Inject(method = "<init>", at = @At("RETURN"), require = 1, allow = 1)
    private void enhanced$capturePreviewRecipes(CallbackInfo callback,
            @Local(argsOnly = true) IFocusedRecipes<T> focused,
            @Local(argsOnly = true) BookmarkList bookmarks) {
        if (recipeCategory instanceof MultiblockInfoCategory) {
            enhanced$recipes = List.copyOf(focused.getRecipes());
            enhanced$bookmarks = bookmarks;
        }
    }

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true, require = 1, allow = 1)
    private void enhanced$stopPreviewPrewarm(AbstractContainerMenu menu, CallbackInfo callback) {
        if (enhanced$recipes != null) callback.cancel();
    }

    @Inject(method = "subList", at = @At("HEAD"), cancellable = true, require = 1, allow = 1)
    private void enhanced$requestedPreviewPage(int first, int end, AbstractContainerMenu menu,
            CallbackInfoReturnable<List<IRecipeLayoutWithButtons<?>>> callback) {
        if (enhanced$recipes == null) return;
        callback.setReturnValue(enhanced$page(first, end));
    }

    @Inject(method = "findFirst", at = @At("HEAD"), cancellable = true, require = 1, allow = 1)
    private void enhanced$firstPreview(AbstractContainerMenu menu,
            CallbackInfoReturnable<Optional<IRecipeLayoutWithButtons<?>>> callback) {
        if (enhanced$recipes == null) return;
        // JEI measures a layout before requesting the visible subList on every
        // refresh. Reuse the current page for that measurement: switching to
        // recipe zero here cancels the visible preview as soon as it is ready.
        List<IRecipeLayoutWithButtons<?>> page = enhanced$page != null ? enhanced$page
                : enhanced$page(0, Math.min(1, enhanced$recipes.size()));
        callback.setReturnValue(page.isEmpty() ? Optional.empty() : Optional.of(page.getFirst()));
    }

    @Unique
    private List<IRecipeLayoutWithButtons<?>> enhanced$page(int first, int end) {
        Objects.checkFromToIndex(first, end, enhanced$recipes.size());
        boolean samePage = enhanced$page != null && first == enhanced$first && end == enhanced$end
                && enhanced$generation == LazyMultiblockPreviewWidget.generation();
        if (samePage && enhanced$layoutRevision == PreviewPreparation.layoutRevision()) return enhanced$page;
        if (!samePage) LazyMultiblockPreviewWidget.clearShared();
        List<IRecipeLayoutWithButtons<?>> page = new ArrayList<>(Math.max(0, end - first));
        for (int index = first; index < end; index++) {
            T recipe = enhanced$recipes.get(index);
            var bookmark = enhanced$bookmarks.getMatchingBookmark(recipeCategory.getRecipeType(), recipe);
            var layout = enhanced$createLayout(recipe, bookmark);
            // JEI normally supplies its error drawable when layout creation
            // fails. Tolerate extensions whose factory instead returns null.
            if (layout != null) page.add(layout);
        }
        enhanced$first = first;
        enhanced$end = end;
        enhanced$generation = LazyMultiblockPreviewWidget.generation();
        enhanced$layoutRevision = PreviewPreparation.layoutRevision();
        enhanced$page = List.copyOf(page);
        return enhanced$page;
    }
}
