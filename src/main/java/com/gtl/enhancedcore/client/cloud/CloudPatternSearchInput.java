package com.gtl.enhancedcore.client.cloud;

import com.gtl.enhancedcore.common.cloud.ui.CloudPatternPanel;
import com.lowdragmc.lowdraglib.gui.modular.ModularUIGuiContainer;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.ModList;

/** Uses EAEP's actual configured key and hovered JEI ingredient; optional classes remain lazily resolved. */
@Mod.EventBusSubscriber(modid = "gtl_enhancedcore", value = Dist.CLIENT)
public final class CloudPatternSearchInput {
    private CloudPatternSearchInput() {}
    @SubscribeEvent public static void key(ScreenEvent.KeyPressed.Pre event) {
        if (!(event.getScreen() instanceof ModularUIGuiContainer screen) || !ModList.get().isLoaded("extendedae_plus")) return;
        var widget = screen.modularUI.getFirstWidgetById("cloud.library");
        if (!(widget instanceof CloudPatternPanel)) widget = screen.modularUI.getFirstWidgetById("cloud.buffer");
        if (!(widget instanceof CloudPatternPanel panel)) return;
        var mc = Minecraft.getInstance();
        double mouseX = mc.mouseHandler.xpos() * mc.getWindow().getGuiScaledWidth() / mc.getWindow().getScreenWidth();
        double mouseY = mc.mouseHandler.ypos() * mc.getWindow().getGuiScaledHeight() / mc.getWindow().getScreenHeight();
        if (tryFillSearch(panel, event.getKeyCode(), event.getScanCode(), mouseX, mouseY)) event.setCanceled(true);
    }
    public static boolean tryFillSearch(CloudPatternPanel panel, int keyCode, int scanCode, double mouseX, double mouseY) {
        if (!ModList.get().isLoaded("extendedae_plus") || !panel.canFillSearch() || panel.isEditingSearch()) return false;
        try {
            KeyMapping key = (KeyMapping)Class.forName("com.extendedae_plus.client.ModKeybindings").getField("FILL_SEARCH_KEY").get(null);
            if (!key.matches(keyCode, scanCode) || !key.getKeyModifier().isActive(key.getKeyConflictContext())) return false;
            String name = panel.hoveredSearchText(mouseX, mouseY);
            if (!name.isBlank()) { panel.fillSearch(name); return true; }
            Class<?> proxy = Class.forName("com.extendedae_plus.integration.jei.JeiRuntimeProxy");
            var ingredient = (java.util.Optional<?>)proxy.getMethod("getIngredientUnderMouse", double.class, double.class).invoke(null, mouseX, mouseY);
            if (ingredient.isEmpty()) ingredient = (java.util.Optional<?>)proxy.getMethod("getIngredientUnderMouse").invoke(null);
            if (ingredient.isEmpty()) return false;
            var method = proxy.getMethod("getTypedIngredientDisplayName", Class.forName("mezz.jei.api.ingredients.ITypedIngredient"));
            name = (String)method.invoke(null, ingredient.get());
            if (name != null && !name.isBlank()) { panel.fillSearch(name); return true; }
        } catch (ReflectiveOperationException | LinkageError absent) { /* Optional integration must not break native editing. */ }
        return false;
    }
}
