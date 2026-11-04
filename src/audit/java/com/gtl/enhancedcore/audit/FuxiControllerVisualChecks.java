package com.gtl.enhancedcore.audit;

import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import com.gregtechceu.gtceu.integration.jei.multipage.MultiblockInfoCategory;
import com.gregtechceu.gtceu.integration.jei.multipage.MultiblockInfoWrapper;
import com.gtl.enhancedcore.client.preview.LazyMultiblockPreviewWidget;
import com.gtl.enhancedcore.client.preview.PreviewPreparation;
import com.lowdragmc.lowdraglib.gui.widget.SlotWidget;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraftforge.registries.ForgeRegistries;
import org.gtlcore.gtlcore.api.gui.PatternPreviewWidget;

/** Real JEI material strip and virtual formation in a newly created disposable world. */
public final class FuxiControllerVisualChecks {
    private static int stage, ticks, stableTicks, size;
    private static LazyMultiblockPreviewWidget lazy;
    private static MultiblockMachineDefinition definition;
    private static final int[][] SIZES = {{960, 600, 2}, {1280, 720, 3}, {640, 480, 2}};

    private FuxiControllerVisualChecks() {}

    public static boolean tick(List<String> checks) throws Exception {
        var mc = Minecraft.getInstance();
        if (++ticks > 7200) throw new AssertionError("Fuxi real JEI timeout at stage " + stage);
        if (stage == 0) {
            var directory = mc.gameDirectory.toPath().toAbsolutePath().normalize();
            if (!Files.isRegularFile(directory.resolve("controller-candidates.marker"))
                    || mc.level != null || mc.getSingleplayerServer() != null
                    || Files.exists(directory.resolve("saves/fuxi-controller-jei")))
                throw new AssertionError("New marked isolated world required");
            stage = 1;
            mc.options.pauseOnLostFocus = false;
            mc.createWorldOpenFlows().createFreshLevel("fuxi-controller-jei",
                    new LevelSettings("Fuxi Controller JEI Audit", GameType.CREATIVE, false, Difficulty.PEACEFUL,
                            true, new GameRules(), WorldDataConfiguration.DEFAULT),
                    new WorldOptions(20261103L, false, false), registry -> registry.registryOrThrow(Registries.WORLD_PRESET)
                            .getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions());
            return false;
        }
        if (stage == 1) {
            if (mc.level == null || mc.player == null || mezz.jei.common.Internal.getOptionalJeiRuntime().isEmpty()) return false;
            definition = (MultiblockMachineDefinition) GTRegistries.MACHINES.get(
                    new ResourceLocation("gtladditions:fuxi_bagua_heaven_forging_furnace"));
            mc.setScreen(null);
            open(checks);
            stage = 2;
            return false;
        }
        if (stage == 2) {
            if (PreviewPreparation.failed(lazy)) throw new AssertionError("Fuxi staged JEI preparation failed");
            if (!lazy.isReady()) { stableTicks = 0; return false; }
            var state = PreviewPreparation.snapshot(lazy);
            if (state.containsKey("requested") && ((Number) state.get("requested")).intValue() >= 0) {
                stableTicks = 0; return false;
            }
            var widget = (PatternPreviewWidget) field(LazyMultiblockPreviewWidget.class, "delegate").get(lazy);
            if (widget.getPreviewControls().scene().getRenderer().isCompiling()) { stableTicks = 0; return false; }
            if (++stableTicks < 20) return false;
            if (!(mc.screen instanceof mezz.jei.gui.recipes.RecipesGui)) throw new AssertionError("Expected actual RecipesGui");
            @SuppressWarnings("unchecked")
            var materials = (List<SlotWidget>) field(PatternPreviewWidget.class, "slotWidgets").get(widget);
            var controllerItem = definition.asStack().getItem();
            long controllerCount = 0;
            for (var material : materials) {
                var stack = material.getHandler().getItem();
                if (stack.getItem() == controllerItem) controllerCount += stack.getCount();
            }
            require(controllerCount == 1, "Actual JEI material strip has exactly one Fuxi controller at size " + size, checks);
            var patterns = (Object[]) field(PatternPreviewWidget.class, "patterns").get(widget);
            require(patterns.length == 1 && patterns[0] != null, "Fuxi has one prepared preview pattern", checks);
            var controller = (IMultiController) field(patterns[0].getClass(), "controllerBase").get(patterns[0]);
            require(controller.isFormed() && !controller.getMultiblockState().hasError(),
                    "Full Fuxi virtual JEI structure forms without pattern error", checks);
            require(!controller.getParts().isEmpty(), "Fuxi formed virtual preview has real bound hatch parts", checks);
            var casing = ForgeRegistries.BLOCKS.getValue(new ResourceLocation("gtlcore:dimension_injection_casing"));
            require(materials.stream().anyMatch(slot -> slot.getHandler().getItem().getItem() == casing.asItem()),
                    "Fuxi real material strip contains dimension-injection casing", checks);
            String filename = "fuxi-controller-fixed-" + SIZES[size][0] + "x" + SIZES[size][1] + ".png";
            try (var image = net.minecraft.client.Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
                image.writeToFile(mc.gameDirectory.toPath().resolve(filename));
            }
            checks.add("Actual RecipesGui screenshot=" + filename + " logical=" + mc.getWindow().getGuiScaledWidth()
                    + "x" + mc.getWindow().getGuiScaledHeight());
            mc.screen.onClose();
            lazy.clearLoaded();
            lazy = null;
            if (++size < SIZES.length) {
                open(checks);
                return false;
            }
            mc.level.disconnect();
            mc.clearLevel();
            mc.setScreen(new TitleScreen());
            stage = 3;
            return false;
        }
        return mc.level == null && mc.getSingleplayerServer() == null;
    }

    private static void open(List<String> checks) {
        var mc = Minecraft.getInstance();
        mc.options.guiScale().set(SIZES[size][2]);
        org.lwjgl.glfw.GLFW.glfwSetWindowSize(mc.getWindow().getWindow(), SIZES[size][0], SIZES[size][1]);
        mc.resizeDisplay();
        var wrapper = new MultiblockInfoWrapper(definition);
        lazy = (LazyMultiblockPreviewWidget) ((com.lowdragmc.lowdraglib.jei.ModularWrapper<?>) wrapper).getWidget();
        var runtime = mezz.jei.common.Internal.getJeiRuntime();
        runtime.getRecipesGui().showRecipes(runtime.getRecipeManager().getRecipeCategory(
                MultiblockInfoCategory.RECIPE_TYPE), List.of(wrapper), List.of());
        stableTicks = 0;
        checks.add("Open exact Fuxi machine through actual RecipesGui at size " + size);
    }

    private static Field field(Class<?> type, String name) throws Exception {
        var result = type.getDeclaredField(name); result.setAccessible(true); return result;
    }

    private static void require(boolean valid, String label, List<String> checks) {
        if (!valid) throw new AssertionError(label);
        checks.add(label);
    }
}
