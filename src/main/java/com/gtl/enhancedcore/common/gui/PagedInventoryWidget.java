package com.gtl.enhancedcore.common.gui;

import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.lowdragmc.lowdraglib.gui.texture.GuiTextureGroup;
import com.lowdragmc.lowdraglib.gui.texture.TextTexture;
import com.lowdragmc.lowdraglib.gui.widget.ButtonWidget;
import com.lowdragmc.lowdraglib.gui.widget.LabelWidget;
import com.lowdragmc.lowdraglib.gui.widget.SlotWidget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import com.lowdragmc.lowdraglib.side.item.IItemTransfer;
import java.util.function.Supplier;

/** Page state belongs to the open UI, so two players cannot move each other's slots. */
public final class PagedInventoryWidget extends WidgetGroup {
    private final IItemTransfer inventory;
    private final int columns;
    private final int slotsPerPage;
    private final int pageCount;
    private final WidgetGroup slots;
    private int page;

    public PagedInventoryWidget(IItemTransfer inventory, int columns, int rows, Supplier<String> title) {
        super(0, 0, columns * 18 + 14, rows * 18 + 36);
        this.inventory = inventory;
        this.columns = columns;
        this.slotsPerPage = columns * rows;
        this.pageCount = Math.max(1, (inventory.getSlots() + slotsPerPage - 1) / slotsPerPage);
        addWidget(new LabelWidget(4, 2, title));
        this.slots = new WidgetGroup(4, 12, columns * 18, rows * 18);
        addWidget(slots);
        int navY = rows * 18 + 16;
        addWidget(new ButtonWidget(4, navY, 30, 12,
                new GuiTextureGroup(GuiTextures.BUTTON, new TextTexture("<")), click -> showPage(page - 1)));
        addWidget(new LabelWidget(40, navY + 2, () -> (page + 1) + " / " + pageCount));
        addWidget(new ButtonWidget(columns * 18 - 20, navY, 30, 12,
                new GuiTextureGroup(GuiTextures.BUTTON, new TextTexture(">")), click -> showPage(page + 1)));
        showPage(0);
    }

    private void showPage(int requested) {
        if (requested < 0 || requested >= pageCount) return;
        page = requested;
        slots.clearAllWidgets();
        int first = page * slotsPerPage;
        for (int i = first; i < Math.min(first + slotsPerPage, inventory.getSlots()); i++) {
            int offset = i - first;
            slots.addWidget(new SlotWidget(inventory, i, offset % columns * 18, offset / columns * 18, true, true)
                    .setBackgroundTexture(GuiTextures.SLOT));
        }
    }
}
