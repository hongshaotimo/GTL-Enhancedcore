package com.gtl.enhancedcore.client.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.ForgeRegistries;

final class ItemTipBrowser extends ConfigPage {
    private record Row(String id, Component name, String searchName) {}
    private List<Row> catalog = List.of(), matches = List.of();
    private final List<Button> rows = new ArrayList<>();
    private String query = "";
    private int page;
    private EditBox search;
    private Button pageNumber;

    ItemTipBrowser(Screen parent) { super(parent, "tips"); }

    @Override protected void init() {
        super.init();
        catalog = ClientTooltipConfig.itemIds().stream().map(id -> {
            var name = ForgeRegistries.ITEMS.getValue(new ResourceLocation(id)).getDescription();
            return new Row(id, name, name.getString().toLowerCase(Locale.ROOT));
        }).toList();
        search = field("search", query, left, 31, bodyWidth, 200);
        search.setHint(tr("search"));
        rows.clear();
        int capacity = Math.max(1, (height - 128) / 24);
        for (int i = 0; i < capacity; i++) {
            int slot = i;
            rows.add(button(Component.empty(), left, 59 + i * 24, bodyWidth, () ->
                    minecraft.setScreen(new ItemTipScreen(this, matches.get(page * rows.size() + slot).id()))));
        }
        button(Component.literal("<"), left, height - 65, 36, () -> showPage(page - 1));
        pageNumber = button(Component.empty(), left + 42, height - 65, bodyWidth - 84, () -> {});
        pageNumber.active = false;
        button(Component.literal(">"), left + bodyWidth - 36, height - 65, 36, () -> showPage(page + 1));
        button("back", left, height - 27, bodyWidth, this::onClose);
        search.setResponder(value -> { query = value; filter(0); });
        filter(page);
        if (!ClientTooltipConfig.error().isEmpty()) status = tr("error", ClientTooltipConfig.error());
    }

    private void filter(int requestedPage) {
        String find = query.strip().toLowerCase(Locale.ROOT);
        matches = find.isEmpty() ? catalog : catalog.stream()
                .filter(row -> row.id().contains(find) || row.searchName().contains(find)).toList();
        showPage(requestedPage);
    }

    private void showPage(int requestedPage) {
        int capacity = rows.size(), pages = Math.max(1, (matches.size() + capacity - 1) / capacity);
        page = Math.max(0, Math.min(requestedPage, pages - 1));
        pageNumber.setMessage(tr("page", page + 1, pages));
        for (int i = 0; i < capacity; i++) {
            int index = page * capacity + i;
            Button row = rows.get(i);
            row.visible = row.active = index < matches.size();
            if (row.visible) {
                var item = matches.get(index);
                var label = Component.empty().append(item.name()).append(" · " + item.id());
                row.setMessage(label);
                row.setTooltip(Tooltip.create(label));
            } else {
                row.setMessage(Component.empty());
                row.setTooltip(null);
            }
        }
    }

    @Override public void tick() { search.tick(); }
}
