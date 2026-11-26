package com.gtl.enhancedcore.audit;

import com.gregtechceu.gtceu.integration.jei.multipage.MultiblockInfoCategory;
import com.gregtechceu.gtceu.integration.jei.multipage.MultiblockInfoWrapper;
import com.gtl.enhancedcore.GTLEnhancedcore;
import com.gtl.enhancedcore.client.preview.LazyMultiblockPreviewWidget;
import com.lowdragmc.lowdraglib.jei.ModularWrapper;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.gui.recipes.layouts.IRecipeLayoutList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.gtlcore.gtlcore.api.gui.PatternPreviewWidget;
import org.gtlcore.gtlcore.client.preview.FullscreenPreviewScreen;

/** Real JEI lifecycle probe; enabled only in an isolated client by an explicit property. */
@Mod.EventBusSubscriber(modid = "enhancedcore_audit", value = Dist.CLIENT)
public final class LazyPreviewMemoryChecks {
    private static final boolean ENABLED = Boolean.getBoolean("gtl.enhancedcore.lazyPreviewAudit");
    private static List<MultiblockInfoWrapper> recipes;
    private static List<MultiblockInfoWrapper> selected;
    private static PatternPreviewWidget previous;
    private static Screen parent;
    private static int ticks, phase, checks;
    private static boolean done;
    private static boolean passed;
    private static String failure;
    private static final Runnable SENTINEL = () -> {};
    private static int listenersBefore;

    private LazyPreviewMemoryChecks() {}

    public static boolean completed() { return done; }
    public static boolean passed() { return passed; }
    public static String failure() { return failure; }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (!ENABLED || done || event.phase != TickEvent.Phase.END) return;
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || PreviewJeiAudit.runtime == null || ++ticks < 100) return;
        try {
            if (ticks % 30 != 0) return;
            switch (phase++) {
                case 0 -> {
                    recipes = PreviewJeiAudit.runtime.getRecipeManager()
                            .createRecipeLookup(MultiblockInfoCategory.RECIPE_TYPE).get().toList();
                    check(!recipes.isEmpty(), "No multiblock recipes registered");
                    check(loaded() == 0, "JEI startup eagerly loaded preview worlds: " + loaded());
                    check(cache().isEmpty(), "JEI startup retained shared pattern cache");
                    var ids = List.of(System.getProperty("gtl.enhancedcore.lazyPreviewMachines",
                            "gtceu:coke_oven,gtl_enhancedcore:stellar_confinement_fusion_reactor").split(","));
                    selected = ids.stream().map(id -> recipes.stream()
                            .filter(recipe -> recipe.definition.getId().toString().equals(id.trim()))
                            .findFirst().orElseThrow()).toList();
                    listenersBefore = closeListeners(selected.getLast()).size();
                    selected.getLast().modularUI.registerCloseListener(SENTINEL);
                    show(selected);
                    parent = mc.screen;
                    check(loaded() > 0 && loaded() <= selected.size(), "Visible layout did not load a bounded page");
                    log("START recipes=" + recipes.size() + " loaded=" + loaded());
                }
                case 1 -> {
                    // Invoke the same native list used by the screen. Its idle tick
                    // must not build the rest, and moving to another range must
                    // release the previous world even while old layouts survive.
                    Object logic = field(parent, "logic");
                    IRecipeLayoutList layouts = (IRecipeLayoutList) field(logic, "cachedRecipeLayoutsWithButtons");
                    check(layouts.getClass().getName().endsWith("LazyRecipeLayoutList"), "Unexpected native list");
                    layouts.subList(0, 1, mc.player.containerMenu);
                    previous = delegate(selected.getFirst());
                    check(previous != null, "First requested preview was not loaded");
                    for (int i = 0; i < recipes.size() + 2; i++) layouts.tick(mc.player.containerMenu);
                    check(loaded() == 1 && cache().size() == 1, "Idle JEI tick warmed hidden previews");
                    layouts.subList(selected.size() - 1, selected.size(), mc.player.containerMenu);
                    check(loaded() == 1 && cache().size() == 1, "Moving pages retained old preview delegates");
                    check(released(previous), "Old page retains block/predicate/material maps");
                    layouts.subList(0, 1, mc.player.containerMenu);
                    check(delegate(selected.getFirst()) != previous, "Returning page reused released widget");
                    mc.screen.onClose();
                    check(loaded() == 0 && cache().isEmpty(), "JEI close retained active preview");
                    check(closeListeners(selected.getLast()).size() == listenersBefore + 1
                                    && closeListeners(selected.getLast()).contains(SENTINEL),
                            "Released delegate callbacks remain or unrelated UI callbacks were removed");
                    show(List.of(selected.getLast()));
                    parent = mc.screen;
                }
                case 2 -> {
                    previous = delegate(selected.getLast());
                    check(previous != null, "Reopen did not rebuild a preview");
                    checkIndexedMaterials(selected.getLast(), previous);
                    var controls = previous.getPreviewControls();
                    var button = controls.expandButton();
                    check(previous.mouseClicked(button.getPositionX() + 8, button.getPositionY() + 8, 0),
                            "Expand click was not consumed");
                    check(mc.screen instanceof FullscreenPreviewScreen, "Fullscreen preview failed");
                    check(delegate(selected.getLast()) == previous && !released(previous),
                            "Fullscreen borrowing cleared the original widget");
                }
                case 3 -> {
                    Screenshot.grab(mc.gameDirectory, "lazy-preview-full.png", mc.getMainRenderTarget(), message -> {});
                    mc.screen.onClose();
                    check(mc.screen == parent, "Fullscreen close did not restore JEI");
                    check(delegate(selected.getLast()) == previous && !released(previous),
                            "Fullscreen return lost the active page");
                    mc.screen.onClose();
                    check(loaded() == 0 && cache().isEmpty() && released(previous), "Final close retained preview data");
                    check(closeListeners(selected.getLast()).size() == listenersBefore + 1
                                    && closeListeners(selected.getLast()).contains(SENTINEL),
                            "Reopen/close accumulated preview callbacks or removed the sentinel");
                    closeListeners(selected.getLast()).remove(SENTINEL);
                    LazyMultiblockPreviewWidget.clearShared();
                    log("COMPLETE checks=" + checks + " recipes=" + recipes.size());
                    writeResult(null);
                    passed = true;
                    done = true;
                    releaseReferences();
                    if (!Boolean.getBoolean("gtl.enhancedcore.memoryAudit")) mc.stop();
                }
                default -> throw new IllegalStateException("Bad phase");
            }
        } catch (Throwable error) {
            failure = error.toString();
            done = true;
            GTLEnhancedcore.LOGGER.error("[LAZY_PREVIEW_CLIENT] FAIL phase=" + phase, error);
            try { writeResult(error); }
            catch (Exception reportError) { GTLEnhancedcore.LOGGER.error("[LAZY_PREVIEW_CLIENT] Cannot write result", reportError); }
            releaseReferences();
            mc.stop();
        }
    }

    private static void show(List<MultiblockInfoWrapper> wrappers) {
        var manager = PreviewJeiAudit.runtime.getRecipeManager();
        PreviewJeiAudit.runtime.getRecipesGui().showRecipes(
                manager.getRecipeCategory(MultiblockInfoCategory.RECIPE_TYPE), wrappers, List.of());
    }

    private static PatternPreviewWidget delegate(MultiblockInfoWrapper wrapper) throws Exception {
        Object widget = ((ModularWrapper<?>) wrapper).getWidget();
        check(widget instanceof LazyMultiblockPreviewWidget, "Wrapper factory was not replaced");
        return (PatternPreviewWidget) field(widget, "delegate");
    }

    private static int loaded() throws Exception {
        int count = 0;
        for (var wrapper : recipes) if (delegate(wrapper) != null) count++;
        return count;
    }

    private static Map<?, ?> cache() throws Exception { return (Map<?, ?>) field(PatternPreviewWidget.class, "CACHE"); }

    private static List<Runnable> closeListeners(MultiblockInfoWrapper wrapper) {
        return ((LazyMultiblockPreviewWidget.CloseListenerAccess) (Object) wrapper.modularUI)
                .enhanced$previewCloseListeners();
    }

    private static boolean released(PatternPreviewWidget widget) throws Exception {
        for (var child : widget.widgets) {
            if (child instanceof com.lowdragmc.lowdraglib.gui.widget.SceneWidget scene) {
                if (!scene.getCore().isEmpty()) return false;
                if (scene.getRenderer() instanceof org.gtlcore.gtlcore.client.preview.PreviewRendererAccess access
                        && access.gtlcore$previewKey() != null) return false;
            }
        }
        for (Object pattern : (Object[]) field(widget, "patterns")) {
            if (!((Map<?, ?>) field(pattern, "blockMap")).isEmpty()
                    || !((Map<?, ?>) field(pattern, "predicateMap")).isEmpty()
                    || !((List<?>) field(pattern, "parts")).isEmpty()) return false;
        }
        return true;
    }

    @SuppressWarnings("unchecked")
    private static void checkIndexedMaterials(MultiblockInfoWrapper wrapper, PatternPreviewWidget widget) throws Exception {
        Object[] patterns = (Object[]) field(widget, "patterns");
        check(patterns.length > 0, "Loaded preview contains no valid pattern");
        for (Object pattern : patterns) {
            var parts = (List<List<ItemStack>>) field(pattern, "parts");
            check(!parts.isEmpty(), "Loaded preview contains no material list");
            for (List<ItemStack> group : parts) {
                for (ItemStack stack : group) {
                    var focus = PreviewJeiAudit.runtime.getJeiHelpers().getFocusFactory()
                            .createFocus(RecipeIngredientRole.INPUT, VanillaTypes.ITEM_STACK, stack);
                    check(PreviewJeiAudit.runtime.getRecipeManager()
                                    .createRecipeLookup(MultiblockInfoCategory.RECIPE_TYPE).limitFocus(List.of(focus))
                                    .get().anyMatch(recipe -> recipe == wrapper),
                            "Material reverse lookup missing: " + stack + " tag=" + stack.getTag());
                }
            }
        }
    }

    private static Object field(Object object, String name) throws Exception {
        boolean isStatic = object instanceof Class<?>;
        for (Class<?> type = isStatic ? (Class<?>) object : object.getClass(); type != null; type = type.getSuperclass()) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(isStatic ? null : object);
            } catch (NoSuchFieldException ignored) {}
        }
        throw new NoSuchFieldException(name);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
        checks++;
    }

    private static void writeResult(Throwable error) throws Exception {
        Path directory = Path.of(System.getProperty("gtl.enhancedcore.memoryAuditDirectory", "_audit/memory-client"));
        Files.createDirectories(directory);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", error == null ? "COMPLETE" : "FAIL");
        result.put("passed", error == null);
        result.put("checks", checks);
        result.put("recipes", recipes == null ? 0 : recipes.size());
        result.put("phase", phase);
        if (error != null) result.put("error", error.toString());
        Files.writeString(directory.resolve("lazy-preview-memory-checks.json"),
                new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(result));
    }

    private static void releaseReferences() {
        recipes = null;
        selected = null;
        previous = null;
        parent = null;
    }

    private static void log(String message) { GTLEnhancedcore.LOGGER.info("[LAZY_PREVIEW_CLIENT] {}", message); }
}
