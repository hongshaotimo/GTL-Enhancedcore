package com.gtl.enhancedcore.audit;

import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import com.gtl.enhancedcore.client.preview.LazyMultiblockPreviewWidget;
import com.gtl.enhancedcore.client.preview.PreviewPageAccess;
import com.gtl.enhancedcore.client.preview.PreviewPreparation;
import com.lowdragmc.lowdraglib.gui.widget.SceneWidget;
import com.lowdragmc.lowdraglib.utils.TrackedDummyWorld;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.IRecipeSlotBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.gtlcore.gtlcore.api.gui.PatternPreviewWidget;

/** Invoked by the isolated title runner; no event registration or extra @Mod entry. */
public final class PreviewPerformanceChecks {
    private static Visual visual;
    private static Jei jei;
    private PreviewPerformanceChecks() {}

    public static void run(List<String> checks) throws Exception {
        var fixture = StellarIvGuiChecks.fixture("gtceu:suprachronal_assembly_line", true, false, false);
        var world = (TrackedDummyWorld) fixture.player().level();
        ChamberPreviewUiChecks.seedWorld(world);
        Field level = field(PatternPreviewWidget.class, "LEVEL");
        Object before = level.get(null);
        LazyMultiblockPreviewWidget lazy = null;
        try {
            PatternPreviewWidget.clearSharedPreviewState();
            level.set(null, world);
            var definition = definition("gtceu:electric_blast_furnace");
            int variants = definition.getMatchingShapes().size();
            require(variants > 1, "Real registered electric blast furnace has multiple shape variants: " + variants, checks);
            PatternPreviewWidget nativeWidget = PatternPreviewWidget.getPatternWidget(definition);
            require((Object) nativeWidget instanceof PreviewPageAccess, "Actual Forge merged widget implements opaque staged bridges", checks);
            Object[] nativePatterns = patterns(nativeWidget);
            var first = fingerprint(nativePatterns[0]);
            var last = fingerprint(nativePatterns[nativePatterns.length - 1]);
            nativeWidget.getPreviewControls().scene().releaseCacheBuffer();
            PatternPreviewWidget.clearSharedPreviewState();
            level.set(null, world);
            lazy = (LazyMultiblockPreviewWidget) LazyMultiblockPreviewWidget.create(definition);
            lazy.ensureLoaded();
            require(!lazy.isReady() && ((Number) PreviewPreparation.snapshot(lazy).get("built")).intValue() == 0,
                    "Visible preview starts without constructing any MBPattern or virtual controller", checks);
            int slices = drain(lazy);
            PatternPreviewWidget ready = delegate(lazy);
            Object[] actual = patterns(ready);
            require(slices >= 8, "First page crosses separate geometry, populate, matcher and material stage boundaries", checks);
            require(actual.length == variants && actual[0] != null
                            && java.util.Arrays.stream(actual).filter(java.util.Objects::nonNull).count() == 1,
                    "First visible variant alone has an MBPattern; unopened variants remain unconstructed", checks);
            require(first.equals(fingerprint(actual[0])), "First staged geometry, predicates and ordered material quantities equal native initialization", checks);
            ready.setPage(actual.length - 1, null);
            require(actual[actual.length - 1] == null, "Native setPage starts pending page without eager construction", checks);
            drain(lazy);
            require(last.equals(fingerprint(actual[actual.length - 1])),
                    "Last staged variant retains native geometry, predicates and material quantities", checks);
            require(java.util.Arrays.stream(actual).filter(java.util.Objects::nonNull).count() == 2,
                    "First and last navigation leaves intermediate variants unconstructed", checks);
            Object firstObject = actual[0];
            ready.setPage(0, null);
            require(actual[0] == firstObject && ((Number) PreviewPreparation.snapshot(lazy).get("built")).intValue() == 2,
                    "Revisiting a completed variant reuses its original controller and MBPattern", checks);
            var formed = PatternPreviewWidget.class.getDeclaredMethod("onFormedSwitch", boolean.class);
            formed.setAccessible(true);
            formed.invoke(ready, false);
            formed.invoke(ready, true);
            require(((SceneWidget) field(PatternPreviewWidget.class, "sceneWidget").get(ready)).getCore().size() > 0,
                    "Native formed toggle and setupScene retain visible core", checks);
            var controls = ready.getPreviewControls();
            controls.enter(320, 240); controls.layout(); controls.exit();
            require(ready.getSizeWidth() == 160 && ready.getSizeHeight() == 160,
                    "Native fullscreen restores the staged widget at minimum logical viewport", checks);
            materialIndex(definition, checks);
            // A middle page is populated, then canceled before its indivisible matcher begins.
            int cancelPage = actual.length > 2 ? 1 : 0;
            if (actual[cancelPage] == null) {
                int completedWorldPositions = world.renderedBlocks.size();
                ready.setPage(cancelPage, null);
                advanceUntil(lazy, "MATCH");
                require(world.renderedBlocks.size() > completedWorldPositions, "Pending page population really adds virtual-world positions", checks);
                PreviewPreparation.cancel(lazy);
                require(world.renderedBlocks.size() == completedWorldPositions,
                        "Cancel removes exactly unfinished virtual-world positions while retaining completed pages", checks);
                require(Boolean.FALSE.equals(PreviewPreparation.snapshot(lazy).get("active")),
                        "Canceled work has no resumable page state", checks);
            }
            // All regular cleanup hooks execute, including private MBPattern memory release.
            PatternPreviewWidget.clearSharedPreviewState();
            require(world.renderedBlocks.isEmpty() && world.blockEntities.isEmpty(),
                    "Actual shared lifecycle clears all virtual blocks and block entities", checks);
            require(((Map<?, ?>) field(actual[0].getClass(), "blockMap").get(actual[0])).isEmpty(),
                    "Stale completed MBPattern references have released geometry", checks);
            for (String phase : List.of("SCAN", "POPULATE", "PREDICATES", "MATERIALS", "MODULES", "FINISH")) {
                level.set(null, world);
                var canceled = (LazyMultiblockPreviewWidget) LazyMultiblockPreviewWidget.create(definition);
                canceled.ensureLoaded();
                advanceUntil(canceled, phase);
                canceled.clearLoaded();
                require(Boolean.FALSE.equals(PreviewPreparation.snapshot(canceled).get("active"))
                                && world.renderedBlocks.isEmpty() && world.blockEntities.isEmpty(),
                        "Cancel at " + phase + " releases pending geometry, controllers and resumable work", checks);
                PatternPreviewWidget.clearSharedPreviewState();
            }
            var result = Map.of("firstPageSlices", slices, "nativeVariants", variants,
                    "scope", "Actual merged widget and native variants in detached title fixture; no FPS claim");
            Files.writeString(Minecraft.getInstance().gameDirectory.toPath().resolve("preview-performance-details.json"),
                    new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(result));
        } finally {
            if (lazy != null) lazy.clearLoaded();
            PatternPreviewWidget.clearSharedPreviewState();
            level.set(null, before);
            fixture.holders().forEach(holder -> holder.setLevel(null));
        }
    }

    private static void materialIndex(MultiblockMachineDefinition definition, List<String> checks) throws Exception {
        List<ItemStack> indexed = new ArrayList<>();
        Object[] slot = new Object[1];
        slot[0] = Proxy.newProxyInstance(PreviewPerformanceChecks.class.getClassLoader(), new Class<?>[]{IRecipeSlotBuilder.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("addItemStack")) indexed.add(((ItemStack) args[0]).copy());
                    if (method.getName().equals("addItemStacks")) for (Object value : (List<?>) args[0]) indexed.add(((ItemStack) value).copy());
                    return method.getReturnType().isInstance(proxy) ? proxy : null;
                });
        var builder = (IRecipeLayoutBuilder) Proxy.newProxyInstance(PreviewPerformanceChecks.class.getClassLoader(),
                new Class<?>[]{IRecipeLayoutBuilder.class}, (proxy, method, args) -> slot[0]);
        var add = LazyMultiblockPreviewWidget.class.getDeclaredMethod("addIndexedMaterials", IRecipeLayoutBuilder.class, MultiblockMachineDefinition.class);
        add.setAccessible(true);
        add.invoke(null, builder, definition);
        require(!indexed.isEmpty() && indexed.stream().allMatch(stack -> !stack.isEmpty()),
                "Real shape material-index path supplies JEI input stacks without building additional pages", checks);
        for (int i = 0; i < indexed.size(); i++) for (int j = i + 1; j < indexed.size(); j++)
            require(!ItemStack.isSameItemSameTags(indexed.get(i), indexed.get(j)),
                    "JEI material index deduplicates item+NBT candidates " + i + "/" + j, checks);
    }

    /** Start after synchronous checks. The host runner keeps ticking until tickVisual() returns true. */
    public static void startVisual(List<String> checks) throws Exception {
        if (visual != null) throw new IllegalStateException("Preview visual audit already active");
        visual = new Visual(checks);
        visual.open();
    }

    public static boolean tickVisual() throws Exception {
        return visual == null || visual.tick();
    }

    public static void finishVisual() throws Exception {
        if (visual != null) {
            try { visual.close(); }
            finally { visual = null; }
        }
    }

    /** Real RecipesGui route, called after the host runner creates its disposable flat world. */
    public static boolean tickJei(List<String> checks) throws Exception {
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) throw new IllegalStateException("Disposable client world required for JEI runtime");
        if (mezz.jei.common.Internal.getOptionalJeiRuntime().isEmpty()) return false;
        if (jei == null) jei = new Jei(checks, mezz.jei.common.Internal.getJeiRuntime());
        return jei.tick();
    }

    private static final class Jei {
        final List<String> checks;
        final mezz.jei.api.runtime.IJeiRuntime runtime;
        final com.gregtechceu.gtceu.integration.jei.multipage.MultiblockInfoWrapper wrapper;
        final LazyMultiblockPreviewWidget lazy;
        int ticks;
        int phase;
        int readyTicks;
        Object beforePage;
        Jei(List<String> checks, mezz.jei.api.runtime.IJeiRuntime runtime) throws Exception {
            this.checks = checks;
            this.runtime = runtime;
            var definition = definition("gtceu:electric_blast_furnace");
            require(definition.getMatchingShapes().size() > 1,
                    "Actual JEI fixture uses registered electric blast furnace with multiple variants", checks);
            wrapper = new com.gregtechceu.gtceu.integration.jei.multipage.MultiblockInfoWrapper(definition);
            var widget = ((com.lowdragmc.lowdraglib.jei.ModularWrapper<?>) wrapper).getWidget();
            require(widget instanceof LazyMultiblockPreviewWidget,
                    "Actual merged wrapper constructor installs the lightweight preview", checks);
            lazy = (LazyMultiblockPreviewWidget) widget;
            runtime.getRecipesGui().showRecipes(runtime.getRecipeManager().getRecipeCategory(
                    com.gregtechceu.gtceu.integration.jei.multipage.MultiblockInfoCategory.RECIPE_TYPE), List.of(wrapper), List.of());
            require(!lazy.isReady(), "Actual RecipesGui first layout returns with pending preview and no native MBPattern", checks);
        }

        boolean tick() throws Exception {
            if (++ticks > 1200) throw new AssertionError("Real JEI preview timed out: " + PreviewPreparation.snapshot(lazy));
            if (!lazy.isReady() || ((Number) PreviewPreparation.snapshot(lazy).get("requested")).intValue() >= 0) return false;
            if (++readyTicks < 16) return false;
            var ready = delegate(lazy);
            var layout = layout();
            @SuppressWarnings("unchecked")
            List<com.lowdragmc.lowdraglib.gui.widget.SlotWidget> materials =
                    (List<com.lowdragmc.lowdraglib.gui.widget.SlotWidget>) field(PatternPreviewWidget.class, "slotWidgets").get(ready);
            var inputs = layout.getRecipeSlotsView().getSlotViews(mezz.jei.api.recipe.RecipeIngredientRole.INPUT);
            require(!materials.isEmpty() && inputs.size() == materials.size(),
                    "Actual RecipesGui rebuilt its layout and bound every native material slot at phase " + phase, checks);
            var slot = materials.getFirst();
            int mouseX = layout.getRect().getX() + slot.getPositionX() + 9;
            int mouseY = layout.getRect().getY() + slot.getPositionY() + 9;
            var hovered = layout.getIngredientUnderMouse(mouseX, mouseY, mezz.jei.api.constants.VanillaTypes.ITEM_STACK);
            require(hovered.isPresent() && !hovered.get().isEmpty(),
                    "Actual JEI material hover resolves a typed item after layout rebind at phase " + phase, checks);
            var focus = mezz.jei.library.focus.Focus.createFromApi(runtime.getIngredientManager(),
                    mezz.jei.api.recipe.RecipeIngredientRole.INPUT, mezz.jei.api.constants.VanillaTypes.ITEM_STACK, hovered.get());
            require(runtime.getRecipeManager().createRecipeLookup(
                            com.gregtechceu.gtceu.integration.jei.multipage.MultiblockInfoCategory.RECIPE_TYPE)
                            .limitFocus(List.of(focus)).get().anyMatch(recipe -> recipe.definition == wrapper.definition),
                    "Actual JEI reverse material lookup retains the multiblock definition at phase " + phase, checks);
            try (var image = net.minecraft.client.Screenshot.takeScreenshot(Minecraft.getInstance().getMainRenderTarget())) {
                image.writeToFile(Minecraft.getInstance().gameDirectory.toPath().resolve("jei-"
                        + Minecraft.getInstance().options.languageCode + "-phase" + phase + ".png"));
            }
            if (phase++ == 0) {
                Object[] patterns = patterns(ready);
                beforePage = patterns[0];
                ready.setPage(patterns.length - 1, null);
                readyTicks = 0;
                return false;
            }
            ready.setPage(0, null);
            require(patterns(ready)[0] == beforePage, "Actual JEI native page revisit keeps its original virtual controller", checks);
            Minecraft.getInstance().screen.onClose();
            require(!lazy.isReady() && Boolean.FALSE.equals(PreviewPreparation.snapshot(lazy).get("active")),
                    "Actual RecipesGui close cancels work and releases its delegate", checks);
            return true;
        }

        private mezz.jei.api.gui.IRecipeLayoutDrawable<?> layout() throws Exception {
            Object screen = Minecraft.getInstance().screen;
            if (!(screen instanceof mezz.jei.gui.recipes.RecipesGui)) throw new AssertionError("Expected actual RecipesGui");
            Object layouts = field(mezz.jei.gui.recipes.RecipesGui.class, "layouts").get(screen);
            Field listField = field(layouts.getClass(), "recipeLayoutsWithButtons");
            var list = (List<?>) listField.get(layouts);
            if (list.size() != 1) throw new AssertionError("Expected one real JEI recipe layout");
            return ((mezz.jei.gui.recipes.IRecipeLayoutWithButtons<?>) list.getFirst()).getRecipeLayout();
        }
    }

    private static final class Visual {
        private static final int[][] SIZES = {{960, 600, 2}, {1280, 720, 3}, {960, 720, 3}};
        private final List<String> checks;
        private final StellarIvGuiChecks.Fixture fixture;
        private final TrackedDummyWorld world;
        private final Field level;
        private final Object before;
        private final net.minecraft.client.gui.screens.Screen beforeScreen;
        private final int beforeScale;
        private final int beforeWidth;
        private final int beforeHeight;
        private LazyMultiblockPreviewWidget lazy;
        private SceneWidget scene;
        private int sizeIndex;
        private int ticks;
        private int readyFrames;
        private boolean loadingCaptured;

        Visual(List<String> checks) throws Exception {
            this.checks = checks;
            var mc = Minecraft.getInstance();
            beforeScreen = mc.screen;
            beforeScale = mc.options.guiScale().get();
            beforeWidth = mc.getWindow().getWidth();
            beforeHeight = mc.getWindow().getHeight();
            fixture = StellarIvGuiChecks.fixture("gtceu:suprachronal_assembly_line", true, false, false);
            world = (TrackedDummyWorld) fixture.player().level();
            ChamberPreviewUiChecks.seedWorld(world);
            level = field(PatternPreviewWidget.class, "LEVEL");
            before = level.get(null);
        }

        void open() throws Exception {
            var mc = Minecraft.getInstance();
            PatternPreviewWidget.clearSharedPreviewState();
            level.set(null, world);
            int[] size = SIZES[sizeIndex];
            mc.options.guiScale().set(size[2]);
            org.lwjgl.glfw.GLFW.glfwSetWindowSize(mc.getWindow().getWindow(), size[0], size[1]);
            mc.resizeDisplay();
            lazy = (LazyMultiblockPreviewWidget) LazyMultiblockPreviewWidget.create(definition("gtceu:electric_blast_furnace"));
            lazy.ensureLoaded();
            var ui = new com.lowdragmc.lowdraglib.gui.modular.ModularUI(200, 210,
                    com.lowdragmc.lowdraglib.gui.modular.IUIHolder.EMPTY, fixture.player());
            ui.initWidgets();
            ui.mainGroup.addWidget(lazy);
            lazy.setSelfPosition(20, 28);
            var gui = new AuditGui(ui);
            fixture.player().containerMenu = ui.getModularUIContainer();
            mc.setScreen(new net.minecraft.client.gui.screens.Screen(net.minecraft.network.chat.Component.literal("Preview preparation audit")) {
                @Override protected void init() { gui.initialize(minecraft); }
                @Override public void render(net.minecraft.client.gui.GuiGraphics graphics, int mouseX, int mouseY, float partial) {
                    graphics.fill(0, 0, width, height, 0xff30343a);
                    gui.render(graphics, -100, -100, partial);
                    readyFrames++;
                }
            });
            ticks = 0;
            readyFrames = 0;
            loadingCaptured = false;
            scene = null;
        }

        boolean tick() throws Exception {
            if (++ticks > 1200) throw new AssertionError("Preview visual preparation timed out");
            if (!loadingCaptured && readyFrames > 0 && !lazy.isReady()) {
                capture("loading");
                loadingCaptured = true;
            }
            if (!lazy.isReady()) return false;
            require(loadingCaptured, "A genuinely pending preview rendered before readiness at viewport " + sizeIndex, checks);
            if (scene == null) {
                scene = delegate(lazy).getPreviewControls().scene();
                readyFrames = 0;
            }
            if (scene.getRenderer().isCompiling() || readyFrames < 16) return false;
            capture("ready");
            var widget = delegate(lazy);
            require(widget.widgets.stream().filter(com.lowdragmc.lowdraglib.gui.widget.ButtonWidget.class::isInstance)
                            .allMatch(button -> button.getSelfPositionX() >= 0 && button.getSelfPositionY() >= 0
                                    && button.getSelfPositionX() + button.getSizeWidth() <= widget.getSizeWidth()
                                    && button.getSelfPositionY() + button.getSizeHeight() <= widget.getSizeHeight()),
                    "Actual native controls fit the ready preview at viewport " + sizeIndex, checks);
            if (++sizeIndex == SIZES.length) return true;
            open();
            return false;
        }

        private void capture(String phase) throws Exception {
            var mc = Minecraft.getInstance();
            int[] size = SIZES[sizeIndex];
            String name = "preview-" + mc.options.languageCode + "-" + size[0] + "x" + size[1]
                    + "-gui" + size[2] + "-" + phase + ".png";
            try (var image = net.minecraft.client.Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
                image.writeToFile(mc.gameDirectory.toPath().resolve(name));
            }
            checks.add("Rendered actual ModularUI screenshot " + name + " logical="
                    + mc.getWindow().getGuiScaledWidth() + "x" + mc.getWindow().getGuiScaledHeight());
        }

        void close() throws Exception {
            var mc = Minecraft.getInstance();
            PatternPreviewWidget.clearSharedPreviewState();
            level.set(null, before);
            fixture.holders().forEach(holder -> holder.setLevel(null));
            mc.options.guiScale().set(beforeScale);
            org.lwjgl.glfw.GLFW.glfwSetWindowSize(mc.getWindow().getWindow(), beforeWidth, beforeHeight);
            mc.resizeDisplay();
            mc.setScreen(beforeScreen);
        }
    }

    private static final class AuditGui extends com.lowdragmc.lowdraglib.gui.modular.ModularUIGuiContainer {
        AuditGui(com.lowdragmc.lowdraglib.gui.modular.ModularUI ui) { super(ui, 1); }
        void initialize(Minecraft mc) {
            minecraft = mc; font = mc.font;
            width = mc.getWindow().getGuiScaledWidth(); height = mc.getWindow().getGuiScaledHeight();
            super.init();
        }
        @Override protected void containerTick() {}
        @Override public void removed() {}
        @Override public void onClose() {}
        @Override public void render(net.minecraft.client.gui.GuiGraphics graphics, int x, int y, float partial) {
            super.render(graphics, -100, -100, partial);
        }
    }

    private static int drain(LazyMultiblockPreviewWidget lazy) {
        for (int i = 1; i <= 20_000; i++) {
            PreviewPreparation.advance(lazy, PreviewPreparation.FRAME_BUDGET_NANOS);
            if (PreviewPreparation.failed(lazy)) throw new AssertionError("Preview preparation failed: " + PreviewPreparation.snapshot(lazy));
            if (((Number) PreviewPreparation.snapshot(lazy).get("requested")).intValue() < 0) return i;
        }
        throw new AssertionError("Staged preview timed out");
    }

    private static void advanceUntil(LazyMultiblockPreviewWidget lazy, String phase) {
        for (int i = 0; i < 20_000; i++) {
            if (phase.equals(PreviewPreparation.snapshot(lazy).get("phase"))) return;
            PreviewPreparation.advance(lazy, PreviewPreparation.FRAME_BUDGET_NANOS);
            if (PreviewPreparation.failed(lazy)) throw new AssertionError("Failed before cancellation phase");
        }
        throw new AssertionError("Missing preview phase " + phase);
    }

    private static List<Object> fingerprint(Object pattern) throws Exception {
        Map<?, ?> blocks = (Map<?, ?>) field(pattern.getClass(), "blockMap").get(pattern);
        var controller = (com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController) field(pattern.getClass(), "controllerBase").get(pattern);
        var origin = controller.self().getPos();
        var geometry = new java.util.TreeMap<String, String>();
        for (var entry : blocks.entrySet()) geometry.put(((net.minecraft.core.BlockPos) entry.getKey()).subtract(origin).toShortString(),
                ((com.lowdragmc.lowdraglib.utils.BlockInfo) entry.getValue()).getBlockState().toString());
        var materials = new ArrayList<String>();
        for (Object candidates : (List<?>) field(pattern.getClass(), "parts").get(pattern)) {
            List<String> stacks = new ArrayList<>();
            for (Object value : (List<?>) candidates) stacks.add(((ItemStack) value).save(new net.minecraft.nbt.CompoundTag()).toString());
            materials.add(stacks.toString());
        }
        Map<?, ?> predicates = (Map<?, ?>) field(pattern.getClass(), "predicateMap").get(pattern);
        var locations = predicates.keySet().stream().map(value -> ((net.minecraft.core.BlockPos) value).subtract(origin).toShortString()).sorted().toList();
        return List.of(geometry, materials, locations, controller.isFormed());
    }

    static PatternPreviewWidget delegate(LazyMultiblockPreviewWidget lazy) throws Exception {
        return (PatternPreviewWidget) field(LazyMultiblockPreviewWidget.class, "delegate").get(lazy);
    }

    private static Object[] patterns(PatternPreviewWidget widget) throws Exception {
        return (Object[]) field(PatternPreviewWidget.class, "patterns").get(widget);
    }

    private static MultiblockMachineDefinition definition(String id) {
        return (MultiblockMachineDefinition) GTRegistries.MACHINES.get(new ResourceLocation(id));
    }

    private static Field field(Class<?> type, String name) throws Exception {
        Field result = type.getDeclaredField(name); result.setAccessible(true); return result;
    }

    private static void require(boolean value, String message, List<String> checks) {
        if (!value) throw new AssertionError(message);
        checks.add(message);
    }
}
