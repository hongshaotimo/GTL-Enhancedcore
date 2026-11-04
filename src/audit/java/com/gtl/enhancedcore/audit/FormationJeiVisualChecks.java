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
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import org.gtlcore.gtlcore.api.gui.PatternPreviewWidget;

/** Real RecipesGui screenshots in the fresh disposable formation world. No event registration. */
public final class FormationJeiVisualChecks {
    private static Run active;
    private static boolean completed;

    private FormationJeiVisualChecks() {}

    /** Call on client END ticks after the disposable world and JEI runtime have loaded. */
    public static boolean tick(List<String> checks) throws Exception {
        if (completed) return true;
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) throw new IllegalStateException("Fresh disposable world required");
        if (mezz.jei.common.Internal.getOptionalJeiRuntime().isEmpty()) return false;
        try {
            if (active == null) {
                active = new Run(checks);
                active.open();
            }
            if (!active.tick()) return false;
            close();
            completed = true;
            return true;
        } catch (Throwable failure) {
            try { close(); } catch (Throwable cleanup) { failure.addSuppressed(cleanup); }
            throw failure;
        }
    }

    /** Also call from the host runner's failure cleanup. */
    public static void close() throws Exception {
        if (active == null) return;
        try { active.close(); }
        finally { active = null; }
    }

    private static final class Run {
        final List<String> checks;
        final mezz.jei.api.runtime.IJeiRuntime runtime;
        final int beforeWidth;
        final int beforeHeight;
        final int beforeScale;
        MultiblockInfoWrapper wrapper;
        LazyMultiblockPreviewWidget lazy;
        int ticks;
        int stableTicks;
        int phase;

        Run(List<String> checks) {
            this.checks = checks;
            runtime = mezz.jei.common.Internal.getJeiRuntime();
            var mc = Minecraft.getInstance();
            beforeWidth = mc.getWindow().getWidth();
            beforeHeight = mc.getWindow().getHeight();
            beforeScale = mc.options.guiScale().get();
        }

        void open() throws Exception {
            var mc = Minecraft.getInstance();
            mc.options.guiScale().set(2);
            org.lwjgl.glfw.GLFW.glfwSetWindowSize(mc.getWindow().getWindow(), 960, 600);
            mc.resizeDisplay();
            var definition = (MultiblockMachineDefinition) GTRegistries.MACHINES.get(
                    new ResourceLocation("gtceu:mage_assembler"));
            require(definition != null, "mage_jei_exact_reported_machine_registered", checks);
            wrapper = new MultiblockInfoWrapper(definition);
            var widget = ((com.lowdragmc.lowdraglib.jei.ModularWrapper<?>) wrapper).getWidget();
            require(widget instanceof LazyMultiblockPreviewWidget, "mage_jei_real_wrapper_uses_staged_widget", checks);
            lazy = (LazyMultiblockPreviewWidget) widget;
            runtime.getRecipesGui().showRecipes(runtime.getRecipeManager().getRecipeCategory(
                    MultiblockInfoCategory.RECIPE_TYPE), List.of(wrapper), List.of());
            require(!lazy.isReady(), "mage_jei_real_first_layout_is_pending", checks);
        }

        boolean tick() throws Exception {
            if (++ticks > 1800) throw new AssertionError("mage real JEI visual timeout " + PreviewPreparation.snapshot(lazy));
            if (PreviewPreparation.failed(lazy)) throw new AssertionError("mage real JEI preparation failed");
            if (!lazy.isReady()) { stableTicks = 0; return false; }
            var snapshot = PreviewPreparation.snapshot(lazy);
            if (snapshot.containsKey("requested") && ((Number) snapshot.get("requested")).intValue() >= 0) {
                stableTicks = 0; return false;
            }
            var ready = (PatternPreviewWidget) field(LazyMultiblockPreviewWidget.class, "delegate").get(lazy);
            var scene = ready.getPreviewControls().scene();
            if (scene.getRenderer().isCompiling()) { stableTicks = 0; return false; }
            if (++stableTicks < 16) return false;
            var layout = layout();
            @SuppressWarnings("unchecked")
            var materials = (List<SlotWidget>) field(PatternPreviewWidget.class, "slotWidgets").get(ready);
            var inputSlots = layout.getRecipeSlotsView().getSlotViews(mezz.jei.api.recipe.RecipeIngredientRole.INPUT);
            require(!materials.isEmpty() && materials.size() == inputSlots.size(),
                    "mage_jei_phase" + phase + "_native_material_slots_bound=" + materials.size(), checks);
            require(!scene.getCore().isEmpty(), "mage_jei_phase" + phase + "_rendered_core_nonempty", checks);
            assertController(ready, phase != 1, checks);
            capture(phase == 0 ? "formed" : phase == 1 ? "unformed" : "reformed");
            if (phase == 2) return true;
            var toggle = PatternPreviewWidget.class.getDeclaredMethod("onFormedSwitch", boolean.class);
            toggle.setAccessible(true);
            toggle.invoke(ready, phase == 1);
            phase++;
            stableTicks = 0;
            return false;
        }

        private void capture(String state) throws Exception {
            var mc = Minecraft.getInstance();
            require(mc.getWindow().getWidth() == 960 && mc.getWindow().getHeight() == 600,
                    "mage_jei_capture_actual_960x600_" + state, checks);
            String name = "formation-jei-mage-assembler-960x600-" + state + ".png";
            try (var image = net.minecraft.client.Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
                image.writeToFile(mc.gameDirectory.toPath().resolve(name));
            }
            checks.add("mage_jei_actual_RecipesGui_screenshot=" + name + " logical="
                    + mc.getWindow().getGuiScaledWidth() + "x" + mc.getWindow().getGuiScaledHeight());
        }

        private mezz.jei.api.gui.IRecipeLayoutDrawable<?> layout() throws Exception {
            var screen = Minecraft.getInstance().screen;
            if (!(screen instanceof mezz.jei.gui.recipes.RecipesGui)) throw new AssertionError("Expected real RecipesGui");
            Object layouts = field(mezz.jei.gui.recipes.RecipesGui.class, "layouts").get(screen);
            var list = (List<?>) field(layouts.getClass(), "recipeLayoutsWithButtons").get(layouts);
            require(list.size() == 1, "mage_jei_single_actual_recipe_layout", checks);
            return ((mezz.jei.gui.recipes.IRecipeLayoutWithButtons<?>) list.getFirst()).getRecipeLayout();
        }

        void close() throws Exception {
            var mc = Minecraft.getInstance();
            if (mc.screen instanceof mezz.jei.gui.recipes.RecipesGui) mc.screen.onClose();
            if (lazy != null) lazy.clearLoaded();
            mc.options.guiScale().set(beforeScale);
            org.lwjgl.glfw.GLFW.glfwSetWindowSize(mc.getWindow().getWindow(), beforeWidth, beforeHeight);
            mc.resizeDisplay();
        }
    }

    private static void assertController(PatternPreviewWidget widget, boolean formed, List<String> checks) throws Exception {
        var patterns = (Object[]) field(PatternPreviewWidget.class, "patterns").get(widget);
        require(patterns.length == 1 && patterns[0] != null, "mage_jei_one_real_preview_pattern", checks);
        var controller = (IMultiController) field(patterns[0].getClass(), "controllerBase").get(patterns[0]);
        require(controller.isFormed() == formed, "mage_jei_controller_formed=" + formed, checks);
        if (!formed) {
            require(controller.getParts().isEmpty(), "mage_jei_unformed_releases_bound_parts", checks);
            return;
        }
        require(!controller.getMultiblockState().hasError() && !controller.getParts().isEmpty(),
                "mage_jei_formed_has_actual_parts_without_error", checks);
        var ability = com.gtladd.gtladditions.api.machine.GTLAddPartAbility.INSTANCE.getTHREAD_MODIFIER();
        long engines = controller.getParts().stream().filter(part ->
                ability.isApplicable(part.self().getBlockState().getBlock())).count();
        require(engines == 1, "mage_jei_formed_exactly_one_bound_engine", checks);
        for (var part : controller.getParts()) require(part.hasController(controller.self().getPos()),
                "mage_jei_part_backlink_" + part.self().getDefinition().getId(), checks);
    }

    private static Field field(Class<?> type, String name) throws Exception {
        Field result = type.getDeclaredField(name); result.setAccessible(true); return result;
    }

    private static void require(boolean valid, String label, List<String> checks) {
        if (!valid) throw new AssertionError(label);
        checks.add(label);
    }
}
