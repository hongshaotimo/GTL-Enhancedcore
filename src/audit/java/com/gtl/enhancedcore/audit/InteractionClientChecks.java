package com.gtl.enhancedcore.audit;

import com.google.gson.GsonBuilder;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.common.Mod;

/** Task-owned title-screen transformation smoke; excluded from production artifacts. */
@Mod("enhancedcore_audit")
public final class InteractionClientChecks {
    private static final Path ROOT = Path.of("C:/IDEA/GTL-Enhancedcore/_work/terminal-time-20261022")
            .toAbsolutePath().normalize();
    private final List<String> checks = new ArrayList<>();
    private boolean done;

    public InteractionClientChecks() {
        MinecraftForge.EVENT_BUS.addListener(this::tick);
    }

    private void tick(TickEvent.ClientTickEvent event) {
        if (done || event.phase != TickEvent.Phase.END
                || !Boolean.getBoolean("gtl.enhancedcore.interactionAudit")) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.getOverlay() != null || !(mc.screen instanceof TitleScreen)) return;
        Path directory = mc.gameDirectory.toPath().toAbsolutePath().normalize();
        // Even stop/report actions are restricted to this task's explicitly marked client.
        if (!directory.startsWith(ROOT) || directory.equals(ROOT)
                || !Files.isRegularFile(directory.resolve("interaction.marker"))
                || mc.level != null || mc.getSingleplayerServer() != null) return;
        done = true;
        var result = new LinkedHashMap<String, Object>();
        try {
            check(true, "Task-owned marked title screen; no world or integrated server");
            Class<?> terminal = load("com.glodblock.github.extendedae.client.gui.GuiExPatternTerminal");
            for (String handler : new String[]{"gtlEnhancedcore$addEmptySlotToggle", "gtlEnhancedcore$resetProjection",
                    "gtlEnhancedcore$omitEmptyProviders", "gtlEnhancedcore$visibleSize",
                    "gtlEnhancedcore$originalSlot", "gtlEnhancedcore$refreshChangedSlots"}) {
                check(Arrays.stream(terminal.getDeclaredMethods()).anyMatch(method -> method.getName().contains(handler)
                                && Modifier.isPrivate(method.getModifiers())),
                        "ExtendedAE empty-slot handler merged: " + handler);
            }
            Class<?> base = load("dev.ftb.mods.ftblibrary.ui.BaseScreen");
            Class<?> accessor = load("com.gtl.enhancedcore.mixin.ftblibrary.client.QuestModalPanelsAccessor");
            check(accessor.isAssignableFrom(base), "FTB BaseScreen implements modal accessor after Mixin");
            check(java.util.Deque.class.isAssignableFrom(
                    base.getDeclaredMethod("enhanced$getQuestModalPanels").getReturnType()),
                    "FTB modal accessor merged with expected Deque return type");
            load("com.gtl.enhancedcore.integration.jei.FtbQuestItemGuiHandler");
            Class<?> recipeLogic = load("com.gregtechceu.gtceu.api.machine.trait.RecipeLogic");
            Class<?> ivLogic = load("com.gtl.enhancedcore.common.recipe.iv.IvRecipeLogic");
            check(recipeLogic.isAssignableFrom(ivLogic), "IV recipe logic retains upstream trait hierarchy");
            load("com.gtl.enhancedcore.common.item.WirelessTimeAcceleration");
            load("com.gtl.enhancedcore.client.WirelessTimeAccelerationInput");
            result.put("status", "PASS");
        } catch (Throwable failure) {
            result.put("status", "FAIL");
            result.put("error", failure.toString());
            failure.printStackTrace();
        }
        result.put("checks", checks);
        result.put("scope", "Real Forge title-screen class loading and merged Mixin methods; no world or gameplay interaction");
        try {
            Files.writeString(directory.resolve("interaction-checks.json"),
                    new GsonBuilder().setPrettyPrinting().create().toJson(result));
        } catch (Exception error) { error.printStackTrace(); }
        mc.stop();
    }

    private Class<?> load(String name) throws ClassNotFoundException {
        Class<?> type = Class.forName(name);
        checks.add("Initialized class: " + name);
        return type;
    }

    private void check(boolean value, String label) {
        if (!value) throw new AssertionError(label);
        checks.add(label);
    }
}
