package com.gtl.enhancedcore.client.gui;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;
import com.gregtechceu.gtceu.api.capability.recipe.FluidRecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.capability.recipe.RecipeCapability;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.content.Content;
import com.gregtechceu.gtceu.api.recipe.ui.GTRecipeTypeUI;
import com.gtl.enhancedcore.common.gui.GuiStyle;
import com.lowdragmc.lowdraglib.gui.texture.GuiTextureGroup;
import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib.gui.widget.LabelWidget;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.function.DoubleSupplier;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/** A display-only grid for oversized default GT templates; every slot remains a native capability widget. */
public final class GtRecipeGrid extends WidgetGroup {
    public static final String ID = "enhancedcore.recipe.grid";
    private static final int CELL = 18, MARGIN = 4, HEADING = 14, SECTION_GAP = 5;
    private final GTRecipe recipe;
    private final GTRecipeTypeUI.RecipeHolder holder;
    private final List<Binding> bindings = new ArrayList<>();
    private final int columns;

    public record Binding(IO io, RecipeCapability<?> capability, int index, Content content, boolean perTick,
                          Widget widget, Object storage) {}

    public GtRecipeGrid(GTRecipe recipe, GTRecipeTypeUI typeUI, DoubleSupplier progress) {
        super(0, 0, 1, 1);
        this.recipe = recipe;
        columns = Math.max(4, Math.min(16, (Minecraft.getInstance().getWindow().getGuiScaledWidth() - 100) / CELL));
        setId(ID); setClientSideWidget();
        Table<IO, RecipeCapability<?>, Object> storages = HashBasedTable.create();
        holder = new GTRecipeTypeUI.RecipeHolder(progress, storages, recipe.data.copy(), recipe.conditions, false, false);
        int y = MARGIN;
        for (IO io : new IO[]{IO.IN, IO.OUT}) {
            var capabilities = new LinkedHashSet<RecipeCapability<?>>();
            capabilities.addAll((io == IO.IN ? recipe.inputs : recipe.outputs).keySet());
            capabilities.addAll((io == IO.IN ? recipe.tickInputs : recipe.tickOutputs).keySet());
            for (RecipeCapability<?> cap : capabilities.stream().sorted(RecipeCapability.COMPARATOR).toList()) {
                if (!cap.doRenderSlot || cap.getWidgetClass() == null) continue;
                List<Content> contents = combined(recipe, io, cap);
                if (contents.isEmpty()) continue;
                Object storage = cap.createXEIContainer(cap.createXEIContainerContents(contents, recipe, io));
                if (storage != null) storages.put(io, cap, storage);
                int count = contents.size();
                String key = "gtl_enhancedcore.gui.recipe_grid." + (io == IO.IN ? "inputs" : "outputs");
                var title = new LabelWidget(MARGIN, y, () -> Component.translatable(key, cap.getName(), count).getString().replace("%", "%%"))
                        .setTextColor(GuiStyle.DARK_SECONDARY).setDropShadow(false);
                title.setClientSideWidget(); addWidget(title); y += HEADING;
                int nonTickCount = (io == IO.IN ? recipe.getInputContents(cap) : recipe.getOutputContents(cap)).size();
                for (int index = 0; index < count; index++) {
                    Widget slot = cap.createWidget();
                    slot.setSelfPosition(MARGIN + index % columns * CELL, y + index / columns * CELL);
                    slot.setId(cap.slotName(io, index));
                    IGuiTexture base = cap == FluidRecipeCapability.CAP ? GuiTextures.FLUID_SLOT : GuiTextures.SLOT;
                    byte overlayKey = (byte)((io == IO.OUT ? 2 : 0) + (cap == FluidRecipeCapability.CAP ? 1 : 0) + (index == (io == IO.IN ? recipe.recipeType.getMaxInputs(cap) : recipe.recipeType.getMaxOutputs(cap)) - 1 ? 4 : 0));
                    IGuiTexture overlay = typeUI.getSlotOverlays().get(overlayKey);
                    slot.setBackground(overlay == null ? base : new GuiTextureGroup(base, overlay));
                    Content content = contents.get(index);
                    bindings.add(new Binding(io, cap, index, content, index >= nonTickCount, slot, storage));
                    addWidget(slot);
                }
                y += (count + columns - 1) / columns * CELL + SECTION_GAP;
            }
        }
        setSize(Math.max(150, columns * CELL + MARGIN * 2), Math.max(8, y - SECTION_GAP + MARGIN));
        rebind();
    }

    public int columns() { return columns; }
    public List<Binding> bindings() { return List.copyOf(bindings); }

    /** Native addSlots runs after template construction and can replace tooltips with its overwritten tick table. */
    public void rebind() {
        for (Binding binding : bindings) {
            binding.capability.applyWidgetInfo(binding.widget, binding.index, true, binding.io, holder,
                    recipe.recipeType, recipe, binding.content, binding.storage);
            binding.widget.setOverlay(binding.content.createOverlay(binding.perTick));
        }
    }

    public static boolean hasOverflow(GTRecipe recipe) {
        for (IO io : new IO[]{IO.IN, IO.OUT}) {
            var capabilities = new LinkedHashSet<RecipeCapability<?>>();
            capabilities.addAll((io == IO.IN ? recipe.inputs : recipe.outputs).keySet());
            capabilities.addAll((io == IO.IN ? recipe.tickInputs : recipe.tickOutputs).keySet());
            for (RecipeCapability<?> cap : capabilities.stream().sorted(RecipeCapability.COMPARATOR).toList()) {
                if (!cap.doRenderSlot || cap.getWidgetClass() == null) continue;
                int declared = io == IO.IN ? recipe.recipeType.getMaxInputs(cap) : recipe.recipeType.getMaxOutputs(cap);
                if (combined(recipe, io, cap).size() > declared) return true;
            }
        }
        return false;
    }

    private static List<Content> combined(GTRecipe recipe, IO io, RecipeCapability<?> cap) {
        var result = new ArrayList<>(io == IO.IN ? recipe.getInputContents(cap) : recipe.getOutputContents(cap));
        result.addAll((io == IO.IN ? recipe.tickInputs : recipe.tickOutputs).getOrDefault(cap, List.of()));
        return result;
    }
}
