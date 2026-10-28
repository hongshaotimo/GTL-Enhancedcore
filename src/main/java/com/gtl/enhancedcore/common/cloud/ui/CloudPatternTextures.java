package com.gtl.enhancedcore.common.cloud.ui;

import com.lowdragmc.lowdraglib.gui.texture.*;

/** AE terminal bevels, recessed 18px cells and the lavender terminal palette. */
final class CloudPatternTextures {
    static final IGuiTexture FRAME = new ResourceTexture("gtl_enhancedcore:textures/gui/cloud/ae_terminal.png");
    static final IGuiTexture SLOT = new ResourceTexture("gtl_enhancedcore:textures/gui/cloud/ae_slot.png");
    static final IGuiTexture BUTTON = new ResourceBorderTexture("gtl_enhancedcore:textures/gui/cloud/ae_button.png", 16, 16, 3, 3);
    static final IGuiTexture BUTTON_HOVER = new ResourceBorderTexture("gtl_enhancedcore:textures/gui/cloud/ae_button_hover.png", 16, 16, 3, 3);
    static final IGuiTexture SLOT_SELECTED = new ResourceTexture("gtl_enhancedcore:textures/gui/cloud/ae_slot_selected.png");
    static final IGuiTexture SLOT_HOVER = new ResourceTexture("gtl_enhancedcore:textures/gui/cloud/ae_slot_hover.png");
    static final IGuiTexture SELECTION = new ResourceBorderTexture("gtl_enhancedcore:textures/gui/cloud/ae_selection.png", 16, 16, 2, 2);
    static final IGuiTexture SEARCH = new ResourceBorderTexture("gtl_enhancedcore:textures/gui/cloud/ae_search.png", 16, 16, 2, 2);
    static final IGuiTexture SETTINGS = new ResourceTexture("gtl_enhancedcore:textures/gui/cloud/ae_settings.png");
    private CloudPatternTextures() {}
}
