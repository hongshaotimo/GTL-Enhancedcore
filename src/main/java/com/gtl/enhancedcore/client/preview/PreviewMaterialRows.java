package com.gtl.enhancedcore.client.preview;

import appeng.api.stacks.AEItemKey;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.pattern.TraceabilityPredicate;
import com.lowdragmc.lowdraglib.gui.texture.ColorRectTexture;
import com.lowdragmc.lowdraglib.gui.widget.DraggableScrollableWidgetGroup;
import com.lowdragmc.lowdraglib.gui.widget.ImageWidget;
import com.lowdragmc.lowdraglib.gui.widget.SlotWidget;
import com.lowdragmc.lowdraglib.jei.IngredientIO;
import com.lowdragmc.lowdraglib.utils.CycleItemStackHandler;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

public final class PreviewMaterialRows {
    private PreviewMaterialRows() {}

    public record Material(AEItemKey item, List<AEItemKey> alternatives, int section) {}

    public static List<AEItemKey> alternatives(TraceabilityPredicate predicate, AEItemKey item) {
        if (predicate == null) return List.of();
        var result = new LinkedHashSet<AEItemKey>();
        var predicates = new LinkedHashSet<>(predicate.common);
        predicates.addAll(predicate.limited);
        for (var simple : predicates) {
            if (simple == null || simple.candidates == null) continue;
            var options = simple.getCandidates().stream().filter(stack -> !stack.isEmpty())
                    .map(AEItemKey::of).distinct().toList();
            if (options.contains(item)) result.addAll(options);
        }
        return List.copyOf(result);
    }

    public static void populate(PreviewMaterialAccess pattern, DraggableScrollableWidgetGroup row,
                                DraggableScrollableWidgetGroup candidates, List<SlotWidget> slots) {
        row.clearAllWidgets();
        candidates.clearAllWidgets();
        candidates.setVisible(false);
        candidates.setActive(false);
        slots.clear();
        var amounts = new LinkedHashMap<Material, Integer>();
        var optionsByPredicate = new LinkedHashMap<TraceabilityPredicate, LinkedHashMap<AEItemKey, List<AEItemKey>>>();
        pattern.enhanced$materials().forEach((pos, item) -> {
            var predicate = pattern.enhanced$predicates().get(pos);
            var options = optionsByPredicate.computeIfAbsent(predicate, key -> new LinkedHashMap<>())
                    .computeIfAbsent(item, key -> alternatives(predicate, key));
            int section = pos.equals(pattern.enhanced$controllerPosition()) ? 0 : options.size() > 1 ? 1 : 2;
            var material = new Material(item, options, section);
            amounts.merge(material, 1, Integer::sum);
        });
        var entries = new ArrayList<>(amounts.entrySet());
        entries.sort(Comparator.comparingInt(entry -> entry.getKey().section()));
        var stacks = entries.stream().map(entry -> List.of(entry.getKey().item().toStack(entry.getValue()))).toList();
        var handler = new CycleItemStackHandler(stacks);
        int x = 4;
        int section = 0;
        Material[] selected = {null};
        for (int i = 0; i < entries.size(); i++) {
            var material = entries.get(i).getKey();
            while (section < material.section()) {
                row.addWidget(new ImageWidget(x + 2, 1, 1, 16, new ColorRectTexture(0xffdddddd)));
                x += 6;
                section++;
            }
            var slot = new SlotWidget(handler, i, x, 0, false, false) {
                @Override
                public List<Object> getXEIIngredients() {
                    return material.section() == 1 ? List.of() : super.getXEIIngredients();
                }

                @Override
                public Object getXEIIngredientOverMouse(double mouseX, double mouseY) {
                    return material.section() == 1 ? null : super.getXEIIngredientOverMouse(mouseX, mouseY);
                }

                @Override
                public void drawInBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
                    super.drawInBackground(graphics, mouseX, mouseY, partialTicks);
                    if (material.equals(selected[0])) {
                        int left = getPositionX();
                        int top = getPositionY();
                        graphics.fill(left, top, left + 18, top + 1, 0xff80ff80);
                        graphics.fill(left, top + 17, left + 18, top + 18, 0xff80ff80);
                        graphics.fill(left, top, left + 1, top + 18, 0xff80ff80);
                        graphics.fill(left + 17, top, left + 18, top + 18, 0xff80ff80);
                    }
                }

                @Override
                public boolean mouseClicked(double mouseX, double mouseY, int button) {
                    if (button == 0 && material.section() == 1 && isMouseOverElement(mouseX, mouseY)) {
                        boolean show = !material.equals(selected[0]);
                        selected[0] = show ? material : null;
                        candidates.clearAllWidgets();
                        candidates.setScrollXOffset(0);
                        candidates.setVisible(show);
                        candidates.setActive(show);
                        if (show) populateCandidates(candidates, material.alternatives());
                        return true;
                    }
                    return super.mouseClicked(mouseX, mouseY, button);
                }
            };
            slot.setBackgroundTexture(new ColorRectTexture(0x4fffffff));
            slot.setIngredientIO(material.section() == 1 ? IngredientIO.RENDER_ONLY : IngredientIO.INPUT);
            slot.setOnAddedTooltips((widget, tips) -> {
                tips.add(Component.translatable("gtceu.machine.quantum_chest.items_stored")
                        .append(Component.literal(String.valueOf(amounts.get(material)))));
                if (material.section() == 1) tips.add(Component.translatable("gtl_enhancedcore.preview.expand_material"));
            });
            slots.add(slot);
            row.addWidget(slot);
            x += 18;
        }
    }

    private static void populateCandidates(DraggableScrollableWidgetGroup row, List<AEItemKey> alternatives) {
        var handler = new CycleItemStackHandler(alternatives.stream().map(item -> List.of(item.toStack())).toList());
        for (int i = 0; i < alternatives.size(); i++) {
            row.addWidget(new SlotWidget(handler, i, 4 + i * 18, 0, false, false)
                    .setBackgroundTexture(GuiTextures.SLOT).setIngredientIO(IngredientIO.RENDER_ONLY));
        }
    }
}
