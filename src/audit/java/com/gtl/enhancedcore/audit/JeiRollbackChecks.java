package com.gtl.enhancedcore.audit;

import com.google.common.collect.HashBasedTable;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.RecipeCapability;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gregtechceu.gtceu.api.recipe.content.Content;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import com.gregtechceu.gtceu.integration.GTRecipeWidget;
import com.gregtechceu.gtceu.integration.jei.recipe.GTRecipeTypeCategory;
import com.gregtechceu.gtceu.integration.jei.recipe.GTRecipeWrapper;
import com.gtl.enhancedcore.client.gui.GtRecipeGrid;
import com.gtl.enhancedcore.client.gui.RecipeViewport;
import com.lowdragmc.lowdraglib.gui.widget.ProgressWidget;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Supplier;
import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.common.Internal;
import mezz.jei.gui.elements.GuiIconButton;
import mezz.jei.gui.input.InputType;
import mezz.jei.gui.input.UserInput;
import mezz.jei.gui.recipes.IRecipeLayoutWithButtons;
import mezz.jei.gui.recipes.RecipeLayoutWithButtons;
import mezz.jei.gui.recipes.RecipesGui;
import mezz.jei.library.focus.FocusGroup;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.gtlcore.gtlcore.common.data.GTLRecipeTypes;

/** Current rollback contract: only Cosmos Simulation keeps the enhanced recipe viewport. */
public final class JeiRollbackChecks {
    private JeiRollbackChecks() {}

    public static boolean prepareScene(String id) throws Exception { return JeiStyleChecks.prepareScene(id); }

    public static List<GuiStyleClientChecks.Scene> scenes() {
        var scenes = new ArrayList<GuiStyleClientChecks.Scene>();
        for (var eye : JeiStyleChecks.scenes()) {
            if (!eye.id().startsWith("jei-runtime-")) continue;
            scenes.add(new GuiStyleClientChecks.Scene(eye.id(), eye.factory(), screen -> {
                eye.verify().verify(screen);
                verifyBookmark(screen);
            }));
        }
        for (String fixture : List.of("normal", "huge-stock", "custom-non-eye")) {
            scenes.add(new GuiStyleClientChecks.Scene("jei-native-" + fixture,
                    () -> new NativeScreen(fixture), screen -> ((NativeScreen) screen).verify()));
        }
        return scenes;
    }

    private static void require(boolean condition, String description) { GuiStyleClientChecks.require(condition, description); }

    private static void verifyBookmark(Screen screen) throws Exception {
        @SuppressWarnings("unchecked") var layouts = (List<IRecipeLayoutWithButtons<?>>) GuiStyleClientChecks
                .field(screen.getClass(), "visibleLayouts").get(screen);
        var layout = layouts.stream().filter(value -> value.getRecipeLayout() instanceof RecipeViewport<?> viewport
                && viewport.getRecipeSlotsView().getSlotViews(RecipeIngredientRole.OUTPUT).size() == 144).findFirst().orElseThrow();
        var button = ((RecipeLayoutWithButtons<?>) layout).bookmarkButton();
        var icon = (GuiIconButton) GuiStyleClientChecks.field(button.getClass(), "button").get(button);
        var area = icon.getArea();
        require(area.getX() >= 0 && area.getY() >= 0 && area.getX() + area.getWidth() <= screen.width
                && area.getY() + area.getHeight() <= screen.height, "Actual Eye bookmark control fits the rendered screen");
        require(button.getRecipeBookmark() != null, "Actual Eye bookmark retains the original recipe bookmark identity");
        var handler = button.createInputHandler();
        var input = new UserInput(InputConstants.Type.MOUSE.getOrCreate(0), area.getX() + 3, area.getY() + 3, 0, InputType.IMMEDIATE);
        boolean before = button.isBookmarked();
        require(handler.handleUserInput(screen, input, null).isPresent(), "Actual Eye bookmark button accepts its visible mouse coordinates");
        button.tick();
        require(button.isBookmarked() != before, "Actual Eye bookmark click toggles the real bookmark list");
        require(handler.handleUserInput(screen, input, null).isPresent(), "Actual Eye bookmark control accepts a second click");
        button.tick();
        require(button.isBookmarked() == before, "Actual Eye bookmark second click restores the original bookmark state");
    }

    private static final class NativeScreen extends Screen {
        final String fixture;
        final GTRecipeType type;
        final GTRecipeWrapper wrapper;
        final GTRecipeWidget widget;
        final RecipesGui real;
        final IRecipeLayoutDrawable<GTRecipeWrapper> raw;
        final List<String> authoredTemplate;
        List<IRecipeLayoutWithButtons<?>> layouts;

        NativeScreen(String fixture) throws Exception {
            super(Component.literal("JEI upstream " + fixture));
            this.fixture = fixture;
            if (fixture.equals("custom-non-eye")) {
                type = GTRegistries.RECIPE_TYPES.values().stream()
                        .filter(candidate -> candidate != GTLRecipeTypes.COSMOS_SIMULATION_RECIPES
                                && candidate.getRecipeUI().hasCustomUI()
                                && candidate.getMaxInputs(ItemRecipeCapability.CAP) > 0
                                && candidate.getMaxOutputs(ItemRecipeCapability.CAP) > 0)
                        .sorted(Comparator.comparing(candidate -> candidate.registryName.toString())).findFirst().orElseThrow();
            } else {
                type = new GTRecipeType(new ResourceLocation("gtl_enhancedcore", "audit_rollback_" + fixture), "audit")
                        .setMaxIOSize(1, fixture.equals("huge-stock") ? 400 : 1, 0, 0);
                require(!type.getRecipeUI().hasCustomUI(), fixture + " uses a genuine stock GT template");
            }
            var builder = type.recipeBuilder(new ResourceLocation("gtl_enhancedcore", "rollback_fixture"))
                    .inputItems(Items.IRON_INGOT).duration(1200).EUt(32);
            int outputs = fixture.equals("huge-stock") ? 400 : 1;
            for (int i = 0; i < outputs; i++) builder.outputItems(new ItemStack(i % 2 == 0 ? Items.DIAMOND : Items.GOLD_INGOT));
            wrapper = new GTRecipeWrapper(builder.buildRawRecipe());
            widget = (GTRecipeWidget) wrapper.getWidget();
            require(widget.getWidgetsByType(GtRecipeGrid.class).isEmpty(), fixture + " never constructs the enhanced compact grid");
            var storages = HashBasedTable.<IO, RecipeCapability<?>, Object>create();
            var contents = HashBasedTable.<IO, RecipeCapability<?>, List<Content>>create();
            widget.collectStorage(storages, contents, wrapper.recipe);
            var template = type.getRecipeUI().createUITemplate(ProgressWidget.JEIProgress, storages,
                    wrapper.recipe.data.copy(), wrapper.recipe.conditions);
            authoredTemplate = geometry(template);
            require(geometry((WidgetGroup) widget.widgets.getFirst()).equals(authoredTemplate),
                    fixture + " retains every authored native template child coordinate and dimension");
            if (fixture.equals("huge-stock")) require(template.getSizeHeight() > 260,
                    "Huge-stock rollback fixture crosses the former compact-grid height threshold: " + template.getSize());
            var runtime = Internal.getOptionalJeiRuntime().orElseThrow();
            var category = new GTRecipeTypeCategory(runtime.getJeiHelpers(), type);
            raw = runtime.getRecipeManager().createRecipeLayoutDrawable(category, wrapper, FocusGroup.EMPTY).orElseThrow();
            require(!(raw instanceof RecipeViewport<?>), fixture + " RecipeManager provides its native layout");
            require(RecipeViewport.wrap(raw) == raw, fixture + " wrap returns the exact upstream layout object");
            var mc = Minecraft.getInstance();
            var previous = mc.screen;
            try {
                runtime.getRecipesGui().showRecipes(category, List.of(wrapper), List.of());
                real = (RecipesGui) mc.screen;
                GuiStyleClientChecks.field(real.getClass(), "parentScreen").set(real, null);
            } finally { mc.screen = previous; }
        }

        private <T> T scoped(Supplier<T> action) {
            var mc = Minecraft.getInstance(); var previous = mc.screen;
            try { mc.screen = real; return action.get(); }
            finally { mc.screen = previous; }
        }

        @Override protected void init() {
            scoped(() -> { real.init(minecraft, width, height); return null; });
            try {
                var list = GuiStyleClientChecks.field(real.getClass(), "layouts").get(real);
                @SuppressWarnings("unchecked") var visible = (List<IRecipeLayoutWithButtons<?>>) GuiStyleClientChecks
                        .field(list.getClass(), "recipeLayoutsWithButtons").get(list);
                layouts = visible;
                require(!layouts.isEmpty(), fixture + " appears in actual RecipesGui");
            } catch (ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
        }

        @Override public void tick() { scoped(() -> { real.tick(); return null; }); }
        @Override public void render(GuiGraphics graphics, int x, int y, float partial) {
            scoped(() -> { real.render(graphics, -1000, -1000, partial); return null; });
        }
        @Override public void removed() { scoped(() -> { real.removed(); return null; }); }

        void verify() throws Exception {
            require(layouts.stream().noneMatch(layout -> layout.getRecipeLayout() instanceof RecipeViewport<?>),
                    fixture + " actual RecipeLayoutWithButtons uses no enhanced viewport");
            var actual = layouts.getFirst().getRecipeLayout();
            require(actual.getRecipe() == wrapper, fixture + " actual native JEI recipe identity is unchanged");
            require(actual.getRect().getWidth() == raw.getRect().getWidth()
                    && actual.getRect().getHeight() == raw.getRect().getHeight(),
                    fixture + " actual panel preserves upstream RecipeManager natural dimensions");
            require(geometry((WidgetGroup) widget.widgets.getFirst()).equals(authoredTemplate),
                    fixture + " actual JEI positioning leaves all native relative slot coordinates unchanged");
            require(widget.getWidgetsByType(GtRecipeGrid.class).isEmpty(), fixture + " remains on the upstream template after real draw/tick");
            if (fixture.equals("custom-non-eye")) require(type.getRecipeUI().hasCustomUI(),
                    "Non-Eye custom rtui retained: " + type.registryName);
            var preview = Class.forName("org.gtlcore.gtlcore.api.gui.PatternPreviewWidget");
            require(java.util.Arrays.stream(preview.getDeclaredFields()).noneMatch(field -> field.getName().equals("enhanced$showChambers")),
                    "Runtime PatternPreviewWidget has no enhanced chamber-highlight toggle state");
            var controls = Class.forName("org.gtlcore.gtlcore.client.preview.PreviewControls");
            require(java.util.Arrays.stream(controls.getDeclaredMethods()).noneMatch(method -> method.getName().contains("Footprint")
                    || method.getName().contains("includeFootprint")), "Runtime PreviewControls has no enhanced footprint header rendering");
        }
    }

    private static List<String> geometry(Widget widget) {
        var result = new ArrayList<String>();
        appendGeometry(widget, result, "");
        return result;
    }

    private static void appendGeometry(Widget widget, List<String> result, String parent) {
        result.add(parent + widget.getClass().getName() + ":" + widget.getId() + "@" + widget.getSelfPositionX() + ","
                + widget.getSelfPositionY() + ":" + widget.getSizeWidth() + "x" + widget.getSizeHeight());
        if (widget instanceof WidgetGroup group) {
            for (int index = 0; index < group.widgets.size(); index++) appendGeometry(group.widgets.get(index), result, parent + index + "/");
        }
    }
}
