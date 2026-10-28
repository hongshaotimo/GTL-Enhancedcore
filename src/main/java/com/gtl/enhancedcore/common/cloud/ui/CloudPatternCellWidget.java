package com.gtl.enhancedcore.common.cloud.ui;

import com.lowdragmc.lowdraglib.gui.widget.ButtonWidget;
import java.util.function.IntConsumer;

/** Preserve the dispatched button number; LDLib ClickData instead samples MouseHandler state. */
final class CloudPatternCellWidget extends ButtonWidget {
    private final IntConsumer click;
    CloudPatternCellWidget(int x, int y, IntConsumer click) {
        super(x, y, 18, 18, CloudPatternTextures.SLOT, null);
        this.click = click;
        setClientSideWidget();
        setHoverTexture(CloudPatternTextures.SLOT_HOVER);
    }
    @Override public boolean mouseClicked(double x, double y, int button) {
        if (!isMouseOverElement(x, y) || button < 0 || button > 2) return false;
        if (isRemote()) { click.accept(button); playButtonClickSound(); }
        return true;
    }
}
