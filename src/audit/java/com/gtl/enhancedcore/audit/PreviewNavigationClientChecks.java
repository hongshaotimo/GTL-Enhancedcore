package com.gtl.enhancedcore.audit;

import com.google.gson.GsonBuilder;
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import com.gregtechceu.gtceu.integration.jei.multipage.MultiblockInfoCategory;
import com.gregtechceu.gtceu.integration.jei.multipage.MultiblockInfoWrapper;
import com.gtl.enhancedcore.client.preview.LazyMultiblockPreviewWidget;
import com.gtl.enhancedcore.client.preview.PreviewPreparation;
import com.lowdragmc.lowdraglib.gui.widget.SlotWidget;
import com.lowdragmc.lowdraglib.jei.ModularWrapper;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.runtime.IJeiRuntime;
import mezz.jei.gui.elements.GuiIconButton;
import mezz.jei.gui.recipes.IRecipeLayoutWithButtons;
import mezz.jei.gui.recipes.RecipesGui;
import mezz.jei.gui.recipes.layouts.IRecipeLayoutList;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.common.Mod;
import org.gtlcore.gtlcore.api.gui.PatternPreviewWidget;

/** Full RecipesGui event route, including measurement, layout refresh and outer recipe paging. */
@Mod("enhancedcore_audit")
public final class PreviewNavigationClientChecks {
    private static final Path ROOT = Path.of(System.getProperty("gtl.enhancedcore.previewNavigationRoot",
            "C:/IDEA/GTL-Enhancedcore/_audit/preview-navigation-20261104")).toAbsolutePath().normalize();
    private static final int[][] SIZES = {{960, 600, 2}, {1280, 720, 3}, {960, 720, 3}};
    private final List<String> checks = new ArrayList<>();
    private final List<MultiblockInfoWrapper> wrappers = new ArrayList<>();
    private final List<LazyMultiblockPreviewWidget> previews = new ArrayList<>();
    private IJeiRuntime runtime;
    private PatternPreviewWidget stable;
    private Object stablePattern;
    private Object firstVariant;
    private int stableGeneration;
    private int stage, size, step, index, ticks, readyTicks;
    private boolean done;
    private PreviewMaterialRuntimeChecks materialChecks;

    public PreviewNavigationClientChecks() { MinecraftForge.EVENT_BUS.addListener(this::tick); }

    private void tick(TickEvent.ClientTickEvent event) {
        if (done || event.phase != TickEvent.Phase.END
                || !Boolean.getBoolean("gtl.enhancedcore.previewNavigationAudit")) return;
        var mc = Minecraft.getInstance();
        try {
            if (stage == 0) {
                if (mc.screen == null || mc.getOverlay() != null) return;
                Path directory = mc.gameDirectory.toPath().toAbsolutePath().normalize();
                require(directory.startsWith(ROOT) && !directory.equals(ROOT)
                                && Files.isRegularFile(directory.resolve("preview-navigation.marker")),
                        "Explicit isolated navigation fixture; player saves are never opened");
                require(mc.level == null && !Files.exists(directory.resolve("saves/preview-navigation")),
                        "Fresh disposable world required");
                stage = 1;
                mc.options.pauseOnLostFocus = false;
                mc.createWorldOpenFlows().createFreshLevel("preview-navigation",
                        new LevelSettings("Preview Navigation Audit", GameType.CREATIVE, false,
                                Difficulty.PEACEFUL, true, new GameRules(), WorldDataConfiguration.DEFAULT),
                        new WorldOptions(20261104L, false, false), registry -> registry.registryOrThrow(Registries.WORLD_PRESET)
                                .getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions());
                return;
            }
            if (stage == 1) {
                if (mc.level == null || mc.player == null || mezz.jei.common.Internal.getOptionalJeiRuntime().isEmpty()) return;
                runtime = mezz.jei.common.Internal.getJeiRuntime();
                mc.setScreen(null);
                for (String id : List.of("gtceu:electric_blast_furnace", "gtceu:vacuum_freezer", "gtceu:implosion_compressor")) {
                    var definition = (MultiblockMachineDefinition) GTRegistries.MACHINES.get(new ResourceLocation(id));
                    require(definition != null, "Registered fixture controller " + id);
                    var wrapper = new MultiblockInfoWrapper(definition);
                    wrappers.add(wrapper);
                    previews.add((LazyMultiblockPreviewWidget) ((ModularWrapper<?>) wrapper).getWidget());
                }
                stage = 2;
                openSize();
                return;
            }
            if (++ticks > 600) throw new AssertionError("Navigation failed to settle at size=" + size
                    + " step=" + step + " index=" + index + " " + PreviewPreparation.snapshot(previews.get(index)));
            var lazy = previews.get(index);
            if (PreviewPreparation.failed(lazy)) throw new AssertionError("Preview preparation failed");
            if (!lazy.isReady() || ((Number) PreviewPreparation.snapshot(lazy).get("requested")).intValue() >= 0) return;
            var delegate = delegate(lazy);
            if (step == 9 && Boolean.getBoolean("gtl.enhancedcore.previewMaterialAudit")
                    && (materialChecks == null || !materialChecks.done())) {
                if (materialChecks == null) materialChecks = new PreviewMaterialRuntimeChecks(checks, size, layout());
                materialChecks.tick(delegate);
                stable = null;
                stablePattern = null;
                return;
            }
            if (stable == null) {
                stable = delegate;
                stablePattern = patterns(delegate)[page(delegate)];
                stableGeneration = LazyMultiblockPreviewWidget.generation();
            }
            // Exercise JEI's own measurement and updateLayout path after every completion.
            // On the broken version the first refresh of recipe 1 clears this delegate.
            var screen = (RecipesGui) mc.screen;
            Object logic = field(RecipesGui.class, "logic").get(screen);
            var list = (IRecipeLayoutList) field(logic.getClass(), "cachedRecipeLayoutsWithButtons").get(logic);
            list.findFirst(null);
            requireStable(lazy, "measurement");
            var refresh = RecipesGui.class.getDeclaredMethod("updateLayout");
            refresh.setAccessible(true);
            refresh.invoke(screen);
            requireStable(lazy, "layout refresh");
            if (++readyTicks < 16) return;
            validateMaterials(layout(), delegate);
            capture();
            checks.add("Stable real JEI page size=" + size + " step=" + step + " controller="
                    + wrappers.get(index).definition.getId() + " generation=" + stableGeneration);
            switch (step++) {
                case 0 -> navigate(true, 1);
                case 1 -> navigate(true, 2);
                case 2 -> navigate(false, 1);
                case 3 -> navigate(false, 0);
                case 4 -> {
                    navigate(true, 1);
                    require(!previews.get(1).isReady(), "Outer next button starts a pending second controller");
                    navigate(true, 2);
                    require(Boolean.FALSE.equals(PreviewPreparation.snapshot(previews.get(1)).get("active")),
                            "Immediate second next click cancels the unfinished previous controller");
                }
                case 5 -> navigate(false, 1);
                case 6 -> {
                    screen.onClose();
                    require(previews.stream().noneMatch(LazyMultiblockPreviewWidget::isReady),
                            "Real RecipesGui close releases every preview delegate");
                    show();
                    reset(0);
                }
                case 7 -> {
                    Object[] patterns = patterns(delegate);
                    require(patterns.length > 1, "Registered blast furnace retains multiple structure variants");
                    firstVariant = patterns[0];
                    delegate.setPage(patterns.length - 1, null);
                    reset(0);
                }
                case 8 -> {
                    delegate.setPage(0, null);
                    require(patterns(delegate)[0] == firstVariant, "Completed first variant reuses its virtual controller");
                    reset(0);
                }
                case 9 -> {
                    screen.onClose();
                    require(previews.stream().allMatch(value -> Boolean.FALSE.equals(PreviewPreparation.snapshot(value).get("active"))),
                            "Closing after navigation leaves no preparation state");
                    if (++size == SIZES.length) finish(null);
                    else openSize();
                }
                default -> throw new AssertionError("Unexpected step");
            }
        } catch (Throwable failure) {
            failure.printStackTrace();
            finish(failure);
        }
    }

    private void requireStable(LazyMultiblockPreviewWidget lazy, String operation) throws Exception {
        if (!lazy.isReady() || delegate(lazy) != stable || LazyMultiblockPreviewWidget.generation() != stableGeneration
                || patterns(stable)[page(stable)] != stablePattern)
            throw new AssertionError("JEI " + operation + " discarded active controller index=" + index + " step=" + step);
    }

    private void openSize() {
        var mc = Minecraft.getInstance();
        int[] viewport = SIZES[size];
        mc.options.guiScale().set(viewport[2]);
        org.lwjgl.glfw.GLFW.glfwSetWindowSize(mc.getWindow().getWindow(), viewport[0], viewport[1]);
        mc.resizeDisplay();
        int expectedWidth = (viewport[0] + viewport[2] - 1) / viewport[2];
        int expectedHeight = (viewport[1] + viewport[2] - 1) / viewport[2];
        require(mc.getWindow().getGuiScaledWidth() == expectedWidth
                        && mc.getWindow().getGuiScaledHeight() == expectedHeight,
                "Actual logical viewport is " + mc.getWindow().getGuiScaledWidth() + "x"
                        + mc.getWindow().getGuiScaledHeight() + " from " + mc.getWindow().getWidth() + "x"
                        + mc.getWindow().getHeight() + " at GUI scale " + viewport[2]);
        step = 0;
        materialChecks = null;
        show();
        reset(0);
    }

    private void show() {
        runtime.getRecipesGui().showRecipes(runtime.getRecipeManager().getRecipeCategory(
                MultiblockInfoCategory.RECIPE_TYPE), wrappers, List.of());
    }

    private void navigate(boolean next, int expected) throws Exception {
        var screen = (RecipesGui) Minecraft.getInstance().screen;
        var button = (GuiIconButton) field(RecipesGui.class, next ? "nextPage" : "previousPage").get(screen);
        var area = button.getArea();
        double x = area.getX() + area.getWidth() / 2.0;
        double y = area.getY() + area.getHeight() / 2.0;
        require(screen.mouseClicked(x, y, 0), "Real RecipesGui accepted " + (next ? "next" : "previous") + " click");
        screen.mouseReleased(x, y, 0);
        require(layout().getRecipe() == wrappers.get(expected), "Native page button selected controller " + expected);
        reset(expected);
    }

    private void reset(int selected) { index = selected; ticks = 0; readyTicks = 0; stable = null; stablePattern = null; }

    private IRecipeLayoutDrawable<?> layout() throws Exception {
        Object layouts = field(RecipesGui.class, "layouts").get(Minecraft.getInstance().screen);
        var list = (List<?>) field(layouts.getClass(), "recipeLayoutsWithButtons").get(layouts);
        if (list.size() != 1) throw new AssertionError("Expected one visible structure per page: " + list.size());
        return ((IRecipeLayoutWithButtons<?>) list.getFirst()).getRecipeLayout();
    }

    @SuppressWarnings("unchecked")
    private void validateMaterials(IRecipeLayoutDrawable<?> layout, PatternPreviewWidget delegate) throws Exception {
        require(layout.getRecipe() == wrappers.get(index), "Active JEI recipe identity matches outer page " + index);
        var materials = (List<SlotWidget>) field(PatternPreviewWidget.class, "slotWidgets").get(delegate);
        var inputs = layout.getRecipeSlotsView().getSlotViews(RecipeIngredientRole.INPUT);
        long inputMaterials = materials.stream().filter(slot -> !slot.getXEIIngredients().isEmpty()).count();
        require(!materials.isEmpty() && inputMaterials == inputs.size(),
                "Ready outer page binds every input material; expandable candidates remain render-only");
        var slot = materials.getFirst();
        var hovered = layout.getIngredientUnderMouse(layout.getRect().getX() + slot.getPositionX() + 9,
                layout.getRect().getY() + slot.getPositionY() + 9, mezz.jei.api.constants.VanillaTypes.ITEM_STACK);
        require(hovered.isPresent() && !hovered.get().isEmpty(), "Ready outer page material hover resolves an item");
        var focus = mezz.jei.library.focus.Focus.createFromApi(runtime.getIngredientManager(), RecipeIngredientRole.INPUT,
                mezz.jei.api.constants.VanillaTypes.ITEM_STACK, hovered.get());
        require(runtime.getRecipeManager().createRecipeLookup(MultiblockInfoCategory.RECIPE_TYPE).limitFocus(List.of(focus))
                        .get().anyMatch(value -> value.definition == wrappers.get(index).definition),
                "Outer page material reverse lookup retains the registered controller");
    }

    private void capture() throws Exception {
        var mc = Minecraft.getInstance();
        try (var image = net.minecraft.client.Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            image.writeToFile(mc.gameDirectory.toPath().resolve("navigation-size" + size + "-step" + step + ".png"));
        }
    }

    private void finish(Throwable failure) {
        done = true;
        var mc = Minecraft.getInstance();
        try {
            Files.writeString(mc.gameDirectory.toPath().resolve("preview-navigation-checks.json"),
                    new GsonBuilder().setPrettyPrinting().create().toJson(Map.of(
                            "status", failure == null ? "PASS" : "FAIL", "error", failure == null ? "" : failure.toString(),
                            "checks", checks, "scope", "Real JEI outer page buttons, refresh and material bindings in a fresh isolated world")));
        } catch (Exception error) { error.printStackTrace(); }
        mc.stop();
    }

    private static PatternPreviewWidget delegate(LazyMultiblockPreviewWidget lazy) throws Exception {
        return (PatternPreviewWidget) field(LazyMultiblockPreviewWidget.class, "delegate").get(lazy);
    }
    private static Object[] patterns(PatternPreviewWidget delegate) throws Exception {
        return (Object[]) field(PatternPreviewWidget.class, "patterns").get(delegate);
    }
    private static int page(PatternPreviewWidget delegate) throws Exception {
        return field(PatternPreviewWidget.class, "index").getInt(delegate);
    }
    private static Field field(Class<?> type, String name) throws Exception {
        Field result = type.getDeclaredField(name); result.setAccessible(true); return result;
    }
    private void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        checks.add(message);
    }
}
