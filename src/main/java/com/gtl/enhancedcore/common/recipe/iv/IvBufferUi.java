package com.gtl.enhancedcore.common.recipe.iv;

import com.gregtechceu.gtceu.integration.ae2.gui.widget.AETextInputButtonWidget;
import com.gtl.enhancedcore.common.gui.SuperBufferNameWidget;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import org.gtlcore.gtlcore.common.machine.multiblock.part.ae.MEPatternBufferPartMachine;

/** Shared name controls for registered buffers, including a third-party UI which replaces its parent. */
public final class IvBufferUi {
    private IvBufferUi() {}

    public static void installNameControls(MEPatternBufferPartMachine buffer, Widget widget) {
        if (!IvBuffers.compatible(buffer) || !(widget instanceof WidgetGroup group)) return;
        for (var field : group.getWidgetsByType(AETextInputButtonWidget.class)) {
            var position = field.getSelfPosition();
            group.removeWidget(field);
            group.addWidget(new SuperBufferNameWidget(position.x, position.y,
                    field.getSizeWidth(), field.getSizeHeight(), buffer));
        }
    }
}
