package com.gtl.enhancedcore.client.preview;

import appeng.api.stacks.AEItemKey;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController;
import com.gregtechceu.gtceu.api.pattern.MultiblockShapeInfo;
import com.gregtechceu.gtceu.api.pattern.TraceabilityPredicate;
import com.lowdragmc.lowdraglib.gui.util.ClickData;
import com.lowdragmc.lowdraglib.utils.BlockInfo;
import com.lowdragmc.lowdraglib.utils.TrackedDummyWorld;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import org.gtlcore.gtlcore.api.gui.PatternPreviewWidget;
import org.gtlcore.gtlcore.api.machine.multiblock.IModularMachineHost;

/** Render-thread preparation. No real-world machine or game callback moves to a worker thread. */
public final class PreviewPreparation {
    public static final long FRAME_BUDGET_NANOS = 3_000_000L;
    private static final int CHECK_INTERVAL = 256;
    private static final Map<LazyMultiblockPreviewWidget, Pages> PAGES = new IdentityHashMap<>();
    private static final Map<Object, Pages> OWNERS = new IdentityHashMap<>();
    private static Scope scope;
    private static int layoutRevision;
    private static boolean layoutRefresh;

    private PreviewPreparation() {}

    /** Only our preparation shell and completed wrapper use this alternative to native CACHE.computeIfAbsent. */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static Object patterns(Map cache, Object definition, Function nativeFactory, Object widget) {
        if (scope == null) return cache.computeIfAbsent(definition, nativeFactory);
        if (scope.shell) return ((PreviewPageAccess) widget).enhanced$newPatterns(0);
        Object[] patterns = scope.pages.patterns;
        cache.put(definition, patterns);
        return patterns;
    }

    public static void begin(LazyMultiblockPreviewWidget wrapper, MultiblockMachineDefinition definition) {
        if (PAGES.containsKey(wrapper)) return;
        Pages pages = new Pages(wrapper, definition);
        PAGES.put(wrapper, pages);
    }

    /** Called by a visible wrapper, so invisible recipes never consume preparation time. */
    public static void advance(LazyMultiblockPreviewWidget wrapper, long budgetNanos) {
        Pages pages = PAGES.get(wrapper);
        if (pages == null || pages.failed || pages.requested < 0) return;
        long deadline = System.nanoTime() + Math.max(1, budgetNanos);
        try {
            if (pages.build == null) {
                long started = System.nanoTime();
                pages.build = new Build(pages, shell(pages));
                pages.phaseNanos.merge("SHELL_AND_SHAPES", System.nanoTime() - started, Long::sum);
                // Shell creation and matching-shape retrieval have their own frame boundary.
                return;
            }
            Build build = pages.build;
            String phase = build.phase.name();
            long started = System.nanoTime();
            boolean finished = build.step(deadline);
            pages.phaseNanos.merge(phase, System.nanoTime() - started, Long::sum);
            if (!finished) return;
            pages.build = null;
            disposeShell(build.shell);
            int selected = pages.requested;
            if (build.result == null) {
                // Native initializePattern filters controller-less shapes. Discover them only when visited.
                Object[] smaller = pages.patterns;
                if (!pages.shapes.isEmpty()) {
                    pages.shapes.remove(selected);
                    smaller = build.access.enhanced$newPatterns(pages.patterns.length - 1);
                    System.arraycopy(pages.patterns, 0, smaller, 0, selected);
                    System.arraycopy(pages.patterns, selected + 1, smaller, selected, smaller.length - selected);
                }
                pages.patterns = smaller;
                if (pages.widget != null) ((PreviewPageAccess) pages.widget).enhanced$replacePatterns(smaller);
                pages.requested = smaller.length == 0 ? -1 : Math.min(selected, smaller.length - 1);
                if (smaller.length != 0) return;
            } else {
                pages.patterns[selected] = build.result;
                pages.built++;
                pages.requested = -1;
            }
            if (pages.widget == null) {
                long attaching = System.nanoTime();
                scope = new Scope(pages, false);
                try { pages.widget = PatternPreviewWidget.getPatternWidget(pages.definition); }
                finally { scope = null; }
                OWNERS.put(pages.widget, pages);
                wrapper.installReady(pages.widget);
                pages.phaseNanos.merge("ATTACH_AND_NATIVE_SLOTS", System.nanoTime() - attaching, Long::sum);
            } else if (pages.patterns.length > 0) {
                pages.widget.setPage(Math.min(selected, pages.patterns.length - 1), null);
            }
            markLayoutChanged();
        } catch (RuntimeException failure) {
            pages.failed = true;
            if (pages.build != null) { pages.build.cancel(); pages.build = null; }
            com.gtl.enhancedcore.GTLEnhancedcore.LOGGER.error("Cannot prepare multiblock preview {}",
                    pages.definition.getId(), failure);
        }
    }

    /** True means this page is pending and the native array must not be dereferenced yet. */
    public static boolean requestPage(Object widget, int requested, ClickData click) {
        Pages pages = OWNERS.get(widget);
        if (pages == null) return false;
        if (click != null && click.isShiftClick) requested = 0;
        else if (click != null && click.isCtrlClick) requested = pages.patterns.length - 1;
        if (requested < 0 || requested >= pages.patterns.length) return false;
        if (pages.build != null && requested != pages.requested) {
            pages.build.cancel();
            pages.build = null;
        }
        if (pages.patterns[requested] != null) {
            pages.requested = -1;
            markLayoutChanged();
            return false;
        }
        pages.requested = requested;
        return true;
    }

    public static void cancel(LazyMultiblockPreviewWidget wrapper) {
        Pages pages = PAGES.remove(wrapper);
        if (pages == null) return;
        if (pages.build != null) { pages.build.cancel(); pages.build = null; }
        if (pages.widget != null) OWNERS.remove(pages.widget);
        pages.shapes.clear();
        // Completed MBPatterns remain in native CACHE until its existing lifecycle cleanup releases them.
    }

    public static int layoutRevision() { return layoutRevision; }

    public static boolean consumeLayoutRefresh() {
        boolean refresh = layoutRefresh;
        layoutRefresh = false;
        return refresh;
    }

    public static Map<String, Object> snapshot(LazyMultiblockPreviewWidget wrapper) {
        Pages pages = PAGES.get(wrapper);
        if (pages == null) return Map.of("active", false);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("active", true);
        result.put("pages", pages.patterns == null ? 0 : pages.patterns.length);
        result.put("built", pages.built);
        result.put("requested", pages.requested);
        result.put("phase", pages.build == null ? "IDLE" : pages.build.phase.name());
        result.put("failed", pages.failed);
        result.put("phaseNanos", Map.copyOf(pages.phaseNanos));
        return result;
    }

    public static boolean failed(LazyMultiblockPreviewWidget wrapper) {
        Pages pages = PAGES.get(wrapper);
        return pages != null && pages.failed;
    }

    private static void markLayoutChanged() { layoutRevision++; layoutRefresh = true; }

    private static PatternPreviewWidget shell(Pages pages) {
        scope = new Scope(pages, true);
        PatternPreviewWidget shell;
        try { shell = PatternPreviewWidget.getPatternWidget(pages.definition); }
        finally { scope = null; }
        if (pages.patterns == null) {
            pages.shapes.addAll(pages.definition.getMatchingShapes().stream().filter(java.util.Objects::nonNull).toList());
            pages.patterns = ((PreviewPageAccess) shell).enhanced$newPatterns(pages.shapes.size());
        }
        return shell;
    }

    private static void disposeShell(PatternPreviewWidget shell) {
        LazyMultiblockPreviewWidget.releaseScene(shell);
        shell.clearAllWidgets();
    }

    private record Scope(Pages pages, boolean shell) {}

    private static final class Pages {
        final LazyMultiblockPreviewWidget wrapper;
        final MultiblockMachineDefinition definition;
        final List<MultiblockShapeInfo> shapes = new ArrayList<>();
        final Map<String, Long> phaseNanos = new LinkedHashMap<>();
        Object[] patterns;
        PatternPreviewWidget widget;
        Build build;
        int requested = 0;
        int built;
        boolean failed;
        Pages(LazyMultiblockPreviewWidget wrapper, MultiblockMachineDefinition definition) {
            this.wrapper = wrapper;
            this.definition = definition;
        }
    }

    private enum Phase { SCAN, POPULATE, MATCH, PREDICATES, MATERIALS, MODULES, MERGE_MODULES,
        RESTORE_HOST, MATERIAL_KEYS, MATERIAL_CHOICES, FINISH }

    private static final class Build {
        final Pages pages;
        final PatternPreviewWidget shell;
        final PreviewPageAccess access;
        final TrackedDummyWorld world;
        final BlockPos region;
        final BlockInfo[][][] blocks;
        final Map<BlockPos, BlockInfo> blockMap = new Object2ObjectOpenHashMap<>();
        final Map<BlockPos, BlockInfo> hostBlocks = new Object2ObjectOpenHashMap<>();
        final Set<BlockPos> moduleBlocks = new ObjectOpenHashSet<>();
        final Map<BlockPos, TraceabilityPredicate> predicates = new Object2ObjectOpenHashMap<>();
        final Map<Object, Object> parts = new Object2ObjectOpenHashMap<>();
        final Set<BlockPos> modulePositions = new ObjectOpenHashSet<>();
        final Map<BlockPos, AEItemKey> materialKeys = new LinkedHashMap<>();
        final Map<TraceabilityPredicate, Set<AEItemKey>> materialChoices = new IdentityHashMap<>();
        final Set<BlockPos> replaceable = new ObjectOpenHashSet<>();
        List<IMultiController> modules = List.of();
        IMultiController controller;
        BlockPos controllerInShape;
        Iterator<Map.Entry<BlockPos, BlockInfo>> entries;
        Iterator<Map.Entry<BlockPos, TraceabilityPredicate>> predicateEntries;
        int x, y, z, moduleIndex;
        Phase phase = Phase.SCAN;
        Object result;

        Build(Pages pages, PatternPreviewWidget shell) {
            this.pages = pages;
            this.shell = shell;
            access = (PreviewPageAccess) shell;
            world = access.enhanced$previewWorld();
            region = access.enhanced$nextRegion();
            blocks = pages.shapes.isEmpty() ? new BlockInfo[0][][] : pages.shapes.get(pages.requested).getBlocks();
        }

        @SuppressWarnings("unchecked")
        boolean step(long deadline) {
            int work = 0;
            switch (phase) {
                case SCAN -> {
                    while (x < blocks.length) {
                        if (y == blocks[x].length) { x++; y = 0; z = 0; continue; }
                        if (z == blocks[x][y].length) { y++; z = 0; continue; }
                        BlockInfo info = blocks[x][y][z];
                        if (info != null) {
                            BlockPos position = region.offset(x, y, z);
                            var entity = access.enhanced$previewController(info, position);
                            if (entity instanceof IMachineBlockEntity holder
                                    && holder.getMetaMachine() instanceof IMultiController multi
                                    && multi.self().getDefinition() == pages.definition) {
                                holder.getSelf().setLevel(world);
                                controller = multi;
                                controllerInShape = new BlockPos(x, y, z);
                            }
                            blockMap.put(position, access.enhanced$plainBlock(info.getBlockState()));
                        }
                        z++;
                        if (++work % CHECK_INTERVAL == 0 && System.nanoTime() >= deadline) return false;
                    }
                    if (controller == null) return true;
                    entries = blockMap.entrySet().iterator();
                    phase = Phase.POPULATE;
                }
                case POPULATE -> {
                    while (entries.hasNext()) {
                        var entry = entries.next();
                        world.addBlock(entry.getKey(), entry.getValue());
                        hostBlocks.put(entry.getKey(), entry.getValue());
                        if (++work % CHECK_INTERVAL == 0 && System.nanoTime() >= deadline) return false;
                    }
                    phase = Phase.MATCH;
                }
                case MATCH -> {
                    world.setInnerBlockEntity(controller.self().holder.getSelf());
                    // The installed matcher and formation callbacks are one indivisible operation.
                    access.enhanced$formPreview(blockMap.keySet(), controller);
                    Map<BlockPos, TraceabilityPredicate> matched = controller.getMultiblockState().getMatchContext().get("predicates");
                    predicateEntries = matched == null ? java.util.Collections.emptyIterator() : matched.entrySet().iterator();
                    phase = Phase.PREDICATES;
                }
                case PREDICATES -> {
                    while (predicateEntries.hasNext()) {
                        var entry = predicateEntries.next();
                        predicates.put(entry.getKey(), entry.getValue());
                        if (++work % CHECK_INTERVAL == 0 && System.nanoTime() >= deadline) return false;
                    }
                    entries = blockMap.entrySet().iterator();
                    phase = Phase.MATERIALS;
                }
                case MATERIALS -> {
                    while (entries.hasNext()) {
                        access.enhanced$gatherDrop(parts, entries.next());
                        if (++work % CHECK_INTERVAL == 0 && System.nanoTime() >= deadline) return false;
                    }
                    phase = Phase.MODULES;
                }
                case MODULES -> {
                    if (controller instanceof IModularMachineHost<?> host) {
                        // Native module creation preserves transforms, virtual BE setup and formation timing.
                        modules = access.enhanced$initializeModules(host, region.offset(controllerInShape),
                                blockMap, hostBlocks, moduleBlocks);
                    }
                    phase = Phase.MERGE_MODULES;
                }
                case MERGE_MODULES -> {
                    if (moduleIndex < modules.size()) {
                        var module = modules.get(moduleIndex++);
                        modulePositions.add(module.self().getPos());
                        try { access.enhanced$mergeModulePredicates(module, predicates); }
                        catch (Exception failure) {
                            com.gregtechceu.gtceu.GTCEu.LOGGER.warn("Failed to merge module predicates in pattern preview", failure);
                        }
                        return false;
                    }
                    entries = hostBlocks.entrySet().iterator();
                    phase = Phase.RESTORE_HOST;
                }
                case RESTORE_HOST -> {
                    // The upstream restoration loop exists only for modular hosts.
                    if (controller instanceof IModularMachineHost<?>) {
                        while (entries.hasNext()) {
                            var entry = entries.next();
                            if (!modulePositions.contains(entry.getKey())) world.addBlock(entry.getKey(), entry.getValue());
                            if (++work % CHECK_INTERVAL == 0 && System.nanoTime() >= deadline) return false;
                        }
                    }
                    entries = blockMap.entrySet().iterator();
                    phase = Phase.MATERIAL_KEYS;
                }
                // 山海：prepare the new UI index within the existing render-thread frame budget.
                case MATERIAL_KEYS -> {
                    while (entries.hasNext()) {
                        var entry = entries.next();
                        if (!moduleBlocks.contains(entry.getKey())) {
                            var item = PreviewMaterialRows.material(world, entry.getKey(), entry.getValue());
                            if (item != null) materialKeys.put(entry.getKey(), item);
                        }
                        if (++work % CHECK_INTERVAL == 0 && System.nanoTime() >= deadline) return false;
                    }
                    predicateEntries = predicates.entrySet().iterator();
                    phase = Phase.MATERIAL_CHOICES;
                }
                case MATERIAL_CHOICES -> {
                    while (predicateEntries.hasNext()) {
                        var entry = predicateEntries.next();
                        var predicate = entry.getValue();
                        if (predicate != null && materialChoices.computeIfAbsent(predicate,
                                PreviewMaterialRows::candidates).size() > 1) replaceable.add(entry.getKey());
                        if (++work % CHECK_INTERVAL == 0 && System.nanoTime() >= deadline) return false;
                    }
                    phase = Phase.FINISH;
                }
                case FINISH -> {
                    result = access.enhanced$finishPattern(blockMap, moduleBlocks, controller, predicates, parts);
                    if (result instanceof PreviewMaterialAccess material) {
                        material.enhanced$cacheMaterials(materialKeys, materialChoices, replaceable);
                    }
                    return true;
                }
            }
            return false;
        }

        void cancel() {
            // Every position belongs to this newly allocated preview region; completed pages keep their own regions.
            for (BlockPos position : blockMap.keySet()) {
                world.removeBlock(position);
                world.blockEntities.remove(position);
            }
            for (IMultiController module : modules) PreviewPatternMemory.releaseState(module.getMultiblockState());
            if (controller != null) PreviewPatternMemory.releaseState(controller.getMultiblockState());
            blockMap.clear(); hostBlocks.clear(); predicates.clear(); parts.clear(); moduleBlocks.clear();
            materialKeys.clear(); materialChoices.clear(); replaceable.clear();
            disposeShell(shell);
        }
    }
}
