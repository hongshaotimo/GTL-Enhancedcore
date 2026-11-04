package com.gtl.enhancedcore.common.gui;

import com.gtl.enhancedcore.common.cloud.ui.CloudPatternTextures;
import com.lowdragmc.lowdraglib.gui.widget.DraggableScrollableWidgetGroup;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import java.util.LinkedHashMap;
import java.util.Map;
import org.gtlcore.gtlcore.api.gui.MEPatternCatalystUIManager;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/** Fixed common-side geometry; native pattern slots, pagination and catalyst actions stay intact. */
public final class BufferViewportWidget extends DraggableScrollableWidgetGroup {
    private boolean scrollbarDragging;
    public BufferViewportWidget(int x, int y, int width, int height) {
        super(x, y, width, height);
        setId("enhancedcore.buffer.viewport");
        setXScrollBarHeight(6).setXBarStyle(CloudPatternTextures.RECESS, CloudPatternTextures.BUTTON);
        setYScrollBarWidth(6).setYBarStyle(CloudPatternTextures.RECESS, CloudPatternTextures.BUTTON);
        setUseScissor(true).setDraggable(false);
    }

    public int maximumX() { return Math.max(0, getMaxWidth() - getSizeWidth()); }
    public int maximumY() { return Math.max(0, getMaxHeight() - getSizeHeight()); }

    private boolean overContent(double x, double y) {
        return isMouseOver(getPositionX(), getPositionY(), getSizeWidth() - yBarWidth,
                getSizeHeight() - xBarHeight, x, y);
    }

    // WidgetGroup includes visible descendants in its hit area; a viewport must not.
    @Override public boolean isMouseOverElement(double x, double y) {
        return isMouseOver(getPositionX(), getPositionY(), getSizeWidth(), getSizeHeight(), x, y);
    }

    @Override public Widget getHoverElement(double x, double y) {
        if (!isMouseOverElement(x, y)) return null;
        return overContent(x, y) ? super.getHoverElement(x, y) : this;
    }

    @OnlyIn(Dist.CLIENT)
    @Override public void drawInForeground(net.minecraft.client.gui.GuiGraphics graphics, int x, int y, float partialTicks) {
        if (overContent(x, y)) super.drawInForeground(graphics, x, y, partialTicks);
    }

    @OnlyIn(Dist.CLIENT)
    @Override public boolean mouseClicked(double x, double y, int button) {
        scrollbarDragging = isMouseOverElement(x, y) && !overContent(x, y);
        return super.mouseClicked(x, y, button);
    }

    @OnlyIn(Dist.CLIENT)
    @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        return (scrollbarDragging || overContent(x, y)) && super.mouseDragged(x, y, button, dx, dy);
    }

    @OnlyIn(Dist.CLIENT)
    @Override public boolean mouseReleased(double x, double y, int button) {
        boolean dispatch = scrollbarDragging || overContent(x, y);
        scrollbarDragging = false;
        // End native panel/button drag state without releasing onto a clipped slot.
        boolean handled = super.mouseReleased(dispatch ? x : -Double.MAX_VALUE,
                dispatch ? y : -Double.MAX_VALUE, button);
        return dispatch && handled;
    }

    @Override public WidgetGroup addWidget(int index, Widget widget) {
        boolean visible = widget.isVisible();
        var result = super.addWidget(index, widget);
        if (widget instanceof MEPatternCatalystUIManager) widget.setVisible(visible);
        return result;
    }

    private Map<Widget, Boolean> catalystVisibility() {
        var visibility = new LinkedHashMap<Widget, Boolean>();
        for (var widget : widgets) if (widget instanceof MEPatternCatalystUIManager) visibility.put(widget, widget.isVisible());
        return visibility;
    }

    @Override public void computeMax() {
        var visibility = catalystVisibility();
        super.computeMax();
        visibility.forEach(Widget::setVisible);
    }

    @Override public void setScrollXOffset(int offset) {
        var visibility = catalystVisibility();
        super.setScrollXOffset(offset);
        visibility.forEach(Widget::setVisible);
    }

    @Override public void setScrollYOffset(int offset) {
        var visibility = catalystVisibility();
        super.setScrollYOffset(offset);
        visibility.forEach(Widget::setVisible);
    }
}
