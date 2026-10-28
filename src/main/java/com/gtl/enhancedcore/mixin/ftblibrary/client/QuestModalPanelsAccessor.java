package com.gtl.enhancedcore.mixin.ftblibrary.client;

import dev.ftb.mods.ftblibrary.ui.BaseScreen;
import dev.ftb.mods.ftblibrary.ui.ModalPanel;
import java.util.Deque;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** FTB Library 2001.2.13: the active modal is the first entry, not part of widgets. */
@Mixin(value = BaseScreen.class, remap = false)
public interface QuestModalPanelsAccessor {
    @Accessor(value = "modalPanels", remap = false)
    Deque<ModalPanel> enhanced$getQuestModalPanels();
}
