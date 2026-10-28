package com.gtl.enhancedcore.client.preview;

import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.pattern.MultiblockShapeInfo;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import com.lowdragmc.lowdraglib.gui.widget.SceneWidget;
import com.lowdragmc.lowdraglib.gui.modular.ModularUI;
import com.lowdragmc.lowdraglib.utils.BlockInfo;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.world.item.ItemStack;
import org.gtlcore.gtlcore.api.gui.PatternPreviewWidget;

/**
 * Lightweight JEI wrapper widget. GTLCore's preview constructor expands every
 * shape and creates a virtual machine world; this placeholder defers that work
 * until JEI is actually laying out a visible recipe.
 */
public final class LazyMultiblockPreviewWidget extends WidgetGroup {
    private static final Set<LazyMultiblockPreviewWidget> INSTANCES =
            Collections.newSetFromMap(new WeakHashMap<>());
    private static int generation;

    private final MultiblockMachineDefinition definition;
    private PatternPreviewWidget delegate;
    private String footprintText;
    private final Map<ModularUI, Set<Runnable>> delegateCloseListeners = new IdentityHashMap<>();

    /** Implemented by a client Mixin without exposing the Mixin class at runtime. */
    public interface CloseListenerAccess {
        List<Runnable> enhanced$previewCloseListeners();
    }

    private LazyMultiblockPreviewWidget(MultiblockMachineDefinition definition) {
        super(0, 0, 160, 160);
        this.definition = definition;
        setClientSideWidget();
        synchronized (INSTANCES) {
            INSTANCES.add(this);
        }
    }

    /** Called from the merged GTLCore MultiblockInfoWrapper constructor. */
    public static Widget create(MultiblockMachineDefinition definition) {
        return new LazyMultiblockPreviewWidget(definition);
    }

    public synchronized void ensureLoaded() {
        if (delegate != null) return;
        delegate = PatternPreviewWidget.getPatternWidget(definition);
        footprintText = estimateFootprint(definition);
        Set<Runnable> before = snapshotCloseListeners(getGui());
        addWidget(delegate);
        rememberCloseListeners(getGui(), before);
    }

    @Override
    public synchronized void setGui(ModularUI gui) {
        Set<Runnable> before = delegate == null ? null : snapshotCloseListeners(gui);
        super.setGui(gui);
        if (delegate != null) rememberCloseListeners(gui, before);
    }

    private static Set<Runnable> snapshotCloseListeners(ModularUI gui) {
        if (gui == null) return null;
        Set<Runnable> before = Collections.newSetFromMap(new IdentityHashMap<>());
        before.addAll(((CloseListenerAccess) (Object) gui).enhanced$previewCloseListeners());
        return before;
    }

    private void rememberCloseListeners(ModularUI gui, Set<Runnable> before) {
        if (gui == null || before == null) return;
        for (Runnable callback : ((CloseListenerAccess) (Object) gui).enhanced$previewCloseListeners()) {
            if (!before.contains(callback)) {
                delegateCloseListeners.computeIfAbsent(gui,
                        ignored -> Collections.newSetFromMap(new IdentityHashMap<>())).add(callback);
            }
        }
    }

    private void removeDelegateCloseListeners() {
        for (var entry : delegateCloseListeners.entrySet()) {
            List<Runnable> callbacks = ((CloseListenerAccess) (Object) entry.getKey()).enhanced$previewCloseListeners();
            callbacks.removeIf(entry.getValue()::contains);
            if (callbacks instanceof ArrayList<?> list) list.trimToSize();
        }
        delegateCloseListeners.clear();
    }

    /**
     * Called before the merged category setRecipe method. The JEI ingredient
     * supplier is used while indexing every recipe, so it receives a compact
     * material list without constructing PatternPreviewWidget. The real layout
     * path returns false and lets the original method enumerate all widgets.
     */
    public static boolean prepare(Object builder, Object wrapper) {
        if (!(wrapper instanceof com.gregtechceu.gtceu.integration.jei.multipage.MultiblockInfoWrapper info)) {
            return false;
        }
        if (builder != null && builder.getClass().getName().equals(
                "mezz.jei.library.gui.recipes.supplier.builder.IngredientSupplierBuilder")) {
            addIndexedMaterials((mezz.jei.api.gui.builder.IRecipeLayoutBuilder) builder,
                    info.definition);
            return true;
        }
        // Its source generic type is GTCEu's old PatternPreviewWidget. The
        // installed GTLCore already substitutes a different Widget subtype.
        Widget widget = ((com.lowdragmc.lowdraglib.jei.ModularWrapper<?>) info).getWidget();
        if (widget instanceof LazyMultiblockPreviewWidget lazy) {
            lazy.ensureLoaded();
        }
        return false;
    }

    private static void addIndexedMaterials(mezz.jei.api.gui.builder.IRecipeLayoutBuilder builder,
                                            MultiblockMachineDefinition definition) {
        // The preview material strip is input-only. Preserve each distinct stack
        // (including machine NBT) as a separate JEI ingredient slot.
        Map<String, List<ItemStack>> byItem = new LinkedHashMap<>();
        Set<BlockInfo> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        try {
            for (MultiblockShapeInfo shape : definition.getMatchingShapes()) {
                if (shape == null) continue;
                BlockInfo[][][] blocks = shape.getBlocks();
                for (BlockInfo[][] aisle : blocks) {
                    if (aisle == null) continue;
                    for (BlockInfo[] row : aisle) {
                        if (row == null) continue;
                        for (BlockInfo info : row) {
                            // Candidate BlockInfo objects are shared across cells. Do not
                            // allocate stacks, coordinates or hash keys for every voxel.
                            if (info == null || !seen.add(info) || info.getBlockState().isAir()) continue;
                            ItemStack stack = info.getBlockState().getBlock()
                                    instanceof com.gregtechceu.gtceu.common.block.LampBlock
                                    ? com.gtl.enhancedcore.integration.terminal.LampPlacement.returnedStack(info.getBlockState())
                                    : info.getItemStackForm();
                            if (stack == null || stack.isEmpty()) continue;
                            String itemKey = String.valueOf(net.minecraft.core.registries.BuiltInRegistries.ITEM
                                    .getKey(stack.getItem()));
                            List<ItemStack> sameItem = byItem.computeIfAbsent(itemKey, ignored -> new ArrayList<>());
                            boolean known = false;
                            for (ItemStack previous : sameItem) {
                                if (ItemStack.isSameItemSameTags(previous, stack)) {
                                    known = true;
                                    break;
                                }
                            }
                            if (!known) sameItem.add(stack);
                        }
                    }
                }
            }
        } catch (RuntimeException error) {
            com.gtl.enhancedcore.GTLEnhancedcore.LOGGER.warn(
                    "Cannot index multiblock preview materials for {}", definition.getId(), error);
        } finally {
            // Indexing only retains the small ingredient list. Raw shape arrays
            // are rebuilt when a real layout needs them, without preview BEs.
            org.gtlcore.gtlcore.client.preview.PreviewShapeCache.clear();
        }
        for (List<ItemStack> stacks : byItem.values()) {
            for (ItemStack stack : stacks) {
                builder.addSlot(mezz.jei.api.recipe.RecipeIngredientRole.INPUT).addItemStack(stack);
            }
        }
    }

    /** Release delegate widgets and their references to the shared dummy world. */
    public synchronized void clearLoaded() {
        if (delegate != null) {
            // LDLib keeps SceneWidget::releaseCacheBuffer callbacks after
            // removeWidget. Remove exactly the callbacks added by this delegate.
            removeDelegateCloseListeners();
            for (Widget child : delegate.widgets) {
                if (child instanceof SceneWidget scene && scene.getRenderer() != null) {
                    ChamberPreviewOverlay.clear(scene);
                    scene.getRenderer().deleteCacheBuffer();
                    if (scene.getRenderer() instanceof org.gtlcore.gtlcore.client.preview.PreviewRendererAccess access) {
                        // A renderer's key strongly identifies the real client
                        // world even after its mesh has been disposed.
                        access.gtlcore$previewKey(null);
                    }
                    scene.getRenderer().renderedBlocksMap.clear();
                    scene.getRenderer().setBlocked(Set.of());
                    // SceneWidget copies rendered positions into its own set.
                    // Old JEI slot callbacks may retain this detached widget.
                    Set<net.minecraft.core.BlockPos> core = scene.getCore();
                    core.clear();
                    if (core instanceof it.unimi.dsi.fastutil.objects.ObjectOpenCustomHashSet<?> set) set.trim();
                    if (core instanceof it.unimi.dsi.fastutil.objects.ObjectOpenHashSet<?> set) set.trim();
                }
            }
            removeWidget(delegate);
            delegate = null;
        }
    }

    public static void clearAll() {
        synchronized (INSTANCES) {
            generation++;
            for (LazyMultiblockPreviewWidget widget : List.copyOf(INSTANCES)) widget.clearLoaded();
        }
    }

    public static int generation() {
        return generation;
    }

    public String footprintText() {
        return footprintText;
    }

    private static String estimateFootprint(MultiblockMachineDefinition definition) {
        int chunksX = 1;
        int chunksZ = 1;
        try {
            for (MultiblockShapeInfo shape : definition.getMatchingShapes()) {
                if (shape == null) continue;
                BlockInfo[][][] blocks = shape.getBlocks();
                if (blocks.length == 0 || blocks[0] == null || blocks[0].length == 0
                        || blocks[0][0] == null) continue;
                chunksX = Math.max(chunksX, (blocks[0][0].length + 15) / 16);
                chunksZ = Math.max(chunksZ, (blocks.length + 15) / 16);
            }
        } catch (RuntimeException ignored) {
            return net.minecraft.network.chat.Component.translatable(
                    "gtl_enhancedcore.jei.footprint_unknown").getString();
        }
        return net.minecraft.network.chat.Component.translatable(
                "gtl_enhancedcore.jei.footprint", chunksX, chunksZ).getString();
    }

    public static void clearShared() {
        org.gtlcore.gtlcore.client.preview.PreviewLifecycle.clear();
        PatternPreviewWidget.clearSharedPreviewState();
    }
}
