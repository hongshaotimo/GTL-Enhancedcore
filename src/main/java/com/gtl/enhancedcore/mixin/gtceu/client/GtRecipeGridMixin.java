package com.gtl.enhancedcore.mixin.gtceu.client;

import com.google.common.collect.Table;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.capability.recipe.RecipeCapability;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.RecipeCondition;
import com.gregtechceu.gtceu.api.recipe.RecipeHelper;
import com.gregtechceu.gtceu.api.recipe.ui.GTRecipeTypeUI;
import com.gregtechceu.gtceu.integration.GTRecipeWidget;
import com.gtl.enhancedcore.client.gui.GtRecipeGrid;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.lowdragmc.lowdraglib.gui.widget.SlotWidget;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import java.util.List;
import java.util.function.DoubleSupplier;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The retained Eye of Harmony recipe display is the only compact-template opt-in. */
@Mixin(value = GTRecipeWidget.class, remap = false)
public abstract class GtRecipeGridMixin {
    @Shadow @Final private GTRecipe recipe;
    @Shadow @Final @Mutable private int xOffset;
    @Shadow private int yOffset;
    @Unique private GtRecipeGrid enhancedcore$grid;

    @WrapOperation(method = "setRecipeWidget", at = @At(value = "INVOKE", target = "Lcom/gregtechceu/gtceu/api/recipe/ui/GTRecipeTypeUI;createUITemplate(Ljava/util/function/DoubleSupplier;Lcom/google/common/collect/Table;Lnet/minecraft/nbt/CompoundTag;Ljava/util/List;)Lcom/lowdragmc/lowdraglib/gui/widget/WidgetGroup;"), remap = false, require = 1)
    private WidgetGroup enhancedcore$compactLargeTemplate(GTRecipeTypeUI typeUI, DoubleSupplier progress,
            Table<IO, RecipeCapability<?>, Object> storages, CompoundTag data, List<RecipeCondition> conditions,
            Operation<WidgetGroup> original) {
        WidgetGroup template = original.call(typeUI, progress, storages, data, conditions);
        if (recipe.recipeType == org.gtlcore.gtlcore.common.data.GTLRecipeTypes.COSMOS_SIMULATION_RECIPES
                && !typeUI.hasCustomUI() && (template.getSizeHeight() > 260 || GtRecipeGrid.hasOverflow(recipe))) {
            return enhancedcore$grid = new GtRecipeGrid(recipe, typeUI, progress);
        }
        return template;
    }

    @Inject(method = "setRecipeWidget", at = @At("RETURN"), remap = false, require = 1)
    private void enhancedcore$sizeCompactPage(CallbackInfo ci) {
        if (enhancedcore$grid == null) return;
        WidgetGroup self = (WidgetGroup)(Object)this;
        int oldOffset = xOffset;
        self.setSelfPositionX(0); xOffset = 0;
        int bottom = enhancedcore$grid.getSizeHeight();
        int width = enhancedcore$grid.getSizeWidth();
        int oldMarkerY = recipe.recipeType.getRecipeUI().getJEISize().height - 32;
        for (Widget child : self.widgets) {
            if (child == enhancedcore$grid) continue;
            child.addSelfPosition(oldOffset, 0);
            // Native dimension markers use the type's old template dimensions rather than this recipe's grid.
            if (child instanceof SlotWidget && child.getSelfPositionY() == oldMarkerY) {
                child.setSelfPosition(Math.max(4, enhancedcore$grid.getSizeWidth() - 42), enhancedcore$grid.getSizeHeight() + 4);
            }
            bottom = Math.max(bottom, child.getSelfPositionY() + child.getSizeHeight());
            width = Math.max(width, child.getSelfPositionX() + child.getSizeWidth() + 3);
        }
        int paraLines = recipe.data.contains("hide_duration") ? 0 : 1;
        if (RecipeHelper.getInputEUt(recipe) > 0 || RecipeHelper.getOutputEUt(recipe) > 0) paraLines += 2;
        bottom = Math.max(bottom, yOffset + paraLines * GTRecipeWidget.LINE_HEIGHT);
        // Reserve a separate footer for the native overclock tier and developer ID button.
        self.setSize(width, bottom + 38);
    }

    @Inject(method = "<init>", at = @At("RETURN"), remap = false, require = 1)
    private void enhancedcore$restoreCombinedSlotInfo(GTRecipe recipe, CallbackInfo ci) {
        if (enhancedcore$grid != null) enhancedcore$grid.rebind();
    }
}
