package com.gtl.enhancedcore.common.cloud.ui;

import com.lowdragmc.lowdraglib.gui.modular.IUIHolder;
import com.lowdragmc.lowdraglib.gui.modular.ModularUI;
import com.gregtechceu.gtceu.api.gui.fancy.FancyMachineUIWidget;
import com.gregtechceu.gtceu.api.gui.fancy.IFancyUIProvider;
import com.gregtechceu.gtceu.api.gui.fancy.TabsWidget;
import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

/** Native GT shell: GTLCore installs its own wireless page on this Fancy UI. */
public final class CloudPatternUi {
    private CloudPatternUi() {}
    public static ModularUI create(CloudPatternUiHost host, IUIHolder holder, Player player, boolean library) {
        var page = new Page(host, (IFancyUIProvider) holder, library);
        int width = library ? 218 : 176;
        return new ModularUI(width, 166, holder, player).widget(new FancyMachineUIWidget(page, width, 166));
    }

    /** A per-menu provider keeps panel state separate for every viewer. */
    private record Page(CloudPatternUiHost host, IFancyUIProvider machine, boolean library) implements IFancyUIProvider {
        @Override public Widget createMainPage(FancyMachineUIWidget ui) {
            return library ? new CloudPatternLibraryWidget(host) : new CloudPatternBufferWidget(host);
        }
        @Override public IGuiTexture getTabIcon() { return machine.getTabIcon(); }
        @Override public Component getTitle() { return machine.getTitle(); }
        @Override public void attachSideTabs(TabsWidget tabs) { tabs.setMainTab(this); }
    }
}
