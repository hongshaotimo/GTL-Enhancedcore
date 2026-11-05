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
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

public final class PreviewMaterialRows {
    private PreviewMaterialRows() {}

    public record Material(AEItemKey item, List<AEItemKey> alternatives, int section) {}

    public static final class Selection {
        private AEItemKey selected;
        private Set<AEItemKey> allowed = Set.of();

        public void toggle(AEItemKey item) { selected = item.equals(selected) ? null : item; }
        public AEItemKey selected() { return selected; }
        public int borderColor(AEItemKey item) {
            if (allowed.contains(item)) return 0xffff4040;
            return item.equals(selected) ? 0xff80ff80 : 0;
        }
        public void position(TraceabilityPredicate predicate) {
            allowed = candidates(predicate);
        }
        public void clear() { selected = null; allowed = Set.of(); }
    }

    public static Set<AEItemKey> candidates(TraceabilityPredicate predicate) {
        if (predicate == null) return Set.of();
        var result = new LinkedHashSet<AEItemKey>();
        var predicates = new LinkedHashSet<>(predicate.common);
        predicates.addAll(predicate.limited);
        for (var simple : predicates) {
            if (simple == null || simple.candidates == null) continue;
            simple.getCandidates().stream().filter(stack -> !stack.isEmpty()).map(AEItemKey::of).forEach(result::add);
        }
        return result;
    }

    public static String countLabel(int minimum, int maximum) {
        if (minimum < 0 && maximum < 0) return "";
        int lower = Math.max(0, minimum);
        return lower == maximum ? String.valueOf(lower) : lower + "~" + (maximum < 0 ? "∞" : maximum);
    }

    private static String requirement(PreviewMaterialAccess pattern, AEItemKey item) {
        var labels = new LinkedHashSet<String>();
        pattern.enhanced$predicates().values().stream().distinct().forEach(predicate -> {
            var simplePredicates = new LinkedHashSet<>(predicate.common);
            simplePredicates.addAll(predicate.limited);
            for (var simple : simplePredicates) {
                if (simple == null || simple.candidates == null) continue;
                if (simple.getCandidates().stream().filter(stack -> !stack.isEmpty()).map(AEItemKey::of).noneMatch(item::equals)) continue;
                var label = countLabel(simple.minCount, simple.maxCount);
                if (!label.isEmpty()) labels.add(label);
            }
        });
        return String.join(" / ", labels);
    }

    private static void addRequirements(PreviewMaterialAccess pattern, AEItemKey item, List<Component> tips) {
        var unique = new LinkedHashSet<Component>();
        pattern.enhanced$predicates().values().stream().distinct().forEach(predicate -> {
            var simplePredicates = new LinkedHashSet<>(predicate.common);
            simplePredicates.addAll(predicate.limited);
            for (var simple : simplePredicates) {
                if (simple == null || simple.candidates == null) continue;
                if (simple.getCandidates().stream().filter(stack -> !stack.isEmpty()).map(AEItemKey::of).noneMatch(item::equals)) continue;
                unique.addAll(simple.getToolTips(predicate));
                if (simple.minLayerCount >= 0) unique.add(Component.translatable("gui.gtlcore.preview.min_layer", simple.minLayerCount));
                if (simple.maxLayerCount >= 0) unique.add(Component.translatable("gui.gtlcore.preview.max_layer", simple.maxLayerCount));
            }
        });
        tips.addAll(unique);
    }

    private static void drawCount(GuiGraphics graphics, int x, int y, String label) {
        if (label.isEmpty()) return;
        var font = Minecraft.getInstance().font;
        graphics.pose().pushPose();
        float scale = Math.min(0.75f, 17f / font.width(label));
        graphics.pose().translate(x + 18, y + 18, 220);
        graphics.pose().scale(scale, scale, 1);
        graphics.drawString(font, label, -font.width(label), -font.lineHeight, 0xffffffff, true);
        graphics.pose().popPose();
    }

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
                                DraggableScrollableWidgetGroup candidates, List<SlotWidget> slots,
                                PreviewMaterialAccess.Interaction interaction) {
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
        var requirements = new LinkedHashMap<AEItemKey, String>();
        var stacks = entries.stream().map(entry -> List.of(entry.getKey().item().toStack(
                entry.getKey().section() == 1 ? 1 : entry.getValue()))).toList();
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
                    return super.getXEIIngredientOverMouse(mouseX, mouseY);
                }

                @Override
                public void drawInBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
                    super.drawInBackground(graphics, mouseX, mouseY, partialTicks);
                    drawBorder(graphics, getPositionX(), getPositionY(),
                            interaction.enhanced$materialSelection().borderColor(material.item()));
                    if (material.section() == 1) drawCount(graphics, getPositionX(), getPositionY(),
                            requirements.computeIfAbsent(material.item(), item -> requirement(pattern, item)));
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
                        interaction.enhanced$materialSelection().toggle(material.item());
                        interaction.enhanced$refreshVisibility();
                        if (show) populateCandidates(candidates, material.alternatives(), interaction, pattern);
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
                if (material.section() == 1) {
                    tips.add(Component.translatable("gtl_enhancedcore.preview.expand_material"));
                    addRequirements(pattern, material.item(), tips);
                }
            });
            slots.add(slot);
            row.addWidget(slot);
            x += 18;
        }
    }

    private static void drawBorder(GuiGraphics graphics, int x, int y, int color) {
        if (color == 0) return;
        graphics.fill(x, y, x + 18, y + 1, color);
        graphics.fill(x, y + 17, x + 18, y + 18, color);
        graphics.fill(x, y, x + 1, y + 18, color);
        graphics.fill(x + 17, y, x + 18, y + 18, color);
    }

    private static void populateCandidates(DraggableScrollableWidgetGroup row, List<AEItemKey> alternatives,
                                           PreviewMaterialAccess.Interaction interaction, PreviewMaterialAccess pattern) {
        var requirements = new LinkedHashMap<AEItemKey, String>();
        var handler = new CycleItemStackHandler(alternatives.stream().map(item -> List.of(item.toStack())).toList());
        for (int i = 0; i < alternatives.size(); i++) {
            var item = alternatives.get(i);
            row.addWidget(new SlotWidget(handler, i, 4 + i * 18, 0, false, false) {
                @Override public List<Object> getXEIIngredients() { return List.of(); }
                @Override public boolean mouseClicked(double mouseX, double mouseY, int button) {
                    if (button == 0 && isMouseOverElement(mouseX, mouseY)) {
                        interaction.enhanced$materialSelection().toggle(item);
                        interaction.enhanced$refreshVisibility();
                        return true;
                    }
                    return super.mouseClicked(mouseX, mouseY, button);
                }
                @Override public void drawInBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
                    super.drawInBackground(graphics, mouseX, mouseY, partialTicks);
                    drawBorder(graphics, getPositionX(), getPositionY(),
                            interaction.enhanced$materialSelection().borderColor(item));
                    drawCount(graphics, getPositionX(), getPositionY(),
                            requirements.computeIfAbsent(item, key -> requirement(pattern, key)));
                }
            }.setBackgroundTexture(GuiTextures.SLOT).setIngredientIO(IngredientIO.RENDER_ONLY)
                    .setOnAddedTooltips((slot, tips) -> addRequirements(pattern, item, tips)));
        }
    }
}
