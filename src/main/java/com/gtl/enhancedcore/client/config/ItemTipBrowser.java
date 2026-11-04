package com.gtl.enhancedcore.client.config;

import java.util.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.ForgeRegistries;

final class ItemTipBrowser extends ConfigPage {
    private String query = "";
    private int page;
    private EditBox search;
    ItemTipBrowser(Screen parent) { super(parent, "tips"); }
    private List<String> matches() {
        String find = query.strip().toLowerCase(Locale.ROOT);
        return ClientTooltipConfig.itemIds().stream().filter(id -> id.contains(find)
                || ForgeRegistries.ITEMS.getValue(new ResourceLocation(id)).getDescription().getString()
                    .toLowerCase(Locale.ROOT).contains(find)).toList();
    }
    @Override protected void init() {
        super.init();
        search = field("search", query, left, 31, bodyWidth, 200);
        search.setHint(tr("search"));
        search.setResponder(value -> {
            query = value; page = 0;
            rebuildWidgets(); setFocused(search); search.setCursorPosition(query.length());
        });
        var items = matches(); int rows = Math.max(1, (height - 128) / 24);
        int pages = Math.max(1, (items.size() + rows - 1) / rows); page = Math.min(page, pages - 1);
        for (int i = page * rows; i < Math.min(items.size(), (page + 1) * rows); i++) {
            String id = items.get(i);
            var item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(id));
            var label = Component.empty().append(item.getDescription()).append(" · " + id);
            var b = button(label, left, 59 + (i % rows) * 24, bodyWidth,
                    () -> minecraft.setScreen(new ItemTipScreen(this, id)));
            b.setTooltip(net.minecraft.client.gui.components.Tooltip.create(label));
        }
        button(Component.literal("<"), left, height - 65, 36, () -> { page = Math.max(0, page - 1); rebuildWidgets(); });
        button(tr("page", page + 1, pages), left + 42, height - 65, bodyWidth - 84, () -> {}).active = false;
        button(Component.literal(">"), left + bodyWidth - 36, height - 65, 36,
                () -> { page = Math.min(pages - 1, page + 1); rebuildWidgets(); });
        button("back", left, height - 27, bodyWidth, this::onClose);
        if (!ClientTooltipConfig.error().isEmpty()) status = tr("error", ClientTooltipConfig.error());
    }
    @Override public void tick() { search.tick(); }
}
