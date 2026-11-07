package com.gtl.enhancedcore.audit;

import com.google.gson.GsonBuilder;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import com.gtl.enhancedcore.common.util.MachineTooltips;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/** Renders the actual registered block-item tooltip through Forge and the installed client UI. */
@Mod("enhancedcore_audit")
public final class PrimitiveVoidOreClientChecks {
    private static final int[][] SIZES = {{960, 600, 2}, {1280, 720, 3}, {960, 720, 3}};
    private static final List<String> LANGUAGES = List.of("zh_cn", "en_us");
    private final List<Map<String, Object>> scenes = new ArrayList<>();
    private Path directory;
    private CompletableFuture<Void> reload;
    private OreScreen screen;
    private int locale, size, frames;
    private boolean done;

    public PrimitiveVoidOreClientChecks() {
        MinecraftForge.EVENT_BUS.addListener(this::tick);
        MinecraftForge.EVENT_BUS.addListener(this::renderTick);
    }

    private void renderTick(TickEvent.RenderTickEvent event) {
        var mc = Minecraft.getInstance();
        if (!done && event.phase == TickEvent.Phase.END && mc.getOverlay() == null && mc.screen == screen) frames++;
    }

    private void tick(TickEvent.ClientTickEvent event) {
        if (done || !Boolean.getBoolean("gtl.primitiveVoidOre.clientAudit") || event.phase != TickEvent.Phase.END) return;
        var mc = Minecraft.getInstance();
        if (mc.getOverlay() != null) return;
        try {
            if (directory == null) {
                if (mc.screen == null) return;
                directory = mc.gameDirectory.toPath().toAbsolutePath().normalize();
                require(directory.getParent().equals(Path.of("C:/IDEA/GTL-Enhancedcore/_audit/primitive-void-ore-20261107").toAbsolutePath().normalize())
                        && Files.isRegularFile(directory.resolve("primitive-void-client.marker")), "Dedicated title-screen fixture");
                Files.createDirectories(directory.resolve("screenshots"));
            }
            require(mc.level == null && mc.getSingleplayerServer() == null, "No player world opened");
            if (reload == null) {
                mc.getLanguageManager().setSelected(LANGUAGES.get(locale));
                mc.options.languageCode = LANGUAGES.get(locale);
                reload = mc.reloadResourcePacks();
                return;
            }
            if (!reload.isDone()) return;
            reload.join();
            if (screen == null) {
                int[] viewport = SIZES[size];
                mc.options.guiScale().set(viewport[2]);
                GLFW.glfwSetWindowSize(mc.getWindow().getWindow(), viewport[0], viewport[1]);
                mc.resizeDisplay();
                screen = new OreScreen(); mc.setScreen(screen); frames = 0;
                return;
            }
            if (frames < 16) return;
            require(mc.screen == screen, "Native tooltip remains visible");
            screen.verify();
            String png = "screenshots/" + LANGUAGES.get(locale) + "-" + size + ".png";
            try (var image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) { image.writeToFile(directory.resolve(png)); }
            scenes.add(Map.of("language", LANGUAGES.get(locale), "guiWidth", screen.width, "guiHeight", screen.height,
                    "screenshot", png, "lines", screen.stack.getTooltipLines(null, TooltipFlag.Default.NORMAL).stream().map(Component::getString).toList()));
            screen = null;
            if (++size < SIZES.length) return;
            size = 0; reload = null;
            if (++locale == LANGUAGES.size()) finish(null);
        } catch (Throwable failure) { failure.printStackTrace(); finish(failure); }
    }

    private static void require(boolean valid, String message) { if (!valid) throw new AssertionError(message); }

    private static final class OreScreen extends Screen {
        final ItemStack stack = GTRegistries.MACHINES.get(new ResourceLocation("gtceu", "primitive_void_ore")).asStack();
        OreScreen() { super(Component.literal("Primitive Void Ore tooltip audit")); }
        @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partial) {
            graphics.fill(0, 0, width, height, 0xff30343a);
            graphics.renderItem(stack, 12, 18);
            graphics.renderTooltip(font, stack, 24, 44);
        }
        void verify() {
            var lines = stack.getTooltipLines(null, TooltipFlag.Default.NORMAL);
            for (String suffix : List.of("output", "dimension", "circuits", "input"))
                require(lines.stream().anyMatch(line -> MachineTooltips.containsKey(line, "tooltip.gtl_enhancedcore.primitive_void_ore." + suffix)),
                        "Actual tooltip includes circuit routing " + suffix);
            require(lines.stream().noneMatch(line -> MachineTooltips.containsKey(line, "tooltip.gtlcore.supports_dimensions")
                            || MachineTooltips.containsKey(line, "tooltip.gtlcore.primitive_void_ore_random_output")),
                    "Actual tooltip removes conflicting location-dependent rules");
        }
    }

    private void finish(Throwable failure) {
        done = true;
        var result = new LinkedHashMap<String, Object>();
        result.put("status", failure == null ? "PASS" : "FAIL"); result.put("scenes", scenes);
        result.put("worldOpened", false);
        if (failure != null) result.put("failure", failure.toString());
        try { Files.writeString(directory.resolve("primitive-void-client.json"), new GsonBuilder().setPrettyPrinting().create().toJson(result)); }
        catch (Exception error) { error.printStackTrace(); }
        Minecraft.getInstance().setScreen(new TitleScreen()); Minecraft.getInstance().stop();
    }
}
