package com.gtl.enhancedcore.audit;

import com.google.gson.GsonBuilder;
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.pattern.MultiblockState;
import com.gregtechceu.gtceu.api.pattern.TraceabilityPredicate;
import com.gregtechceu.gtceu.api.pattern.predicates.SimplePredicate;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import com.gtl.enhancedcore.common.structure.GtlMegastructurePatterns;
import com.gtl.enhancedcore.common.structure.NativeIsolationPatterns;
import com.gtl.enhancedcore.common.structure.NativeStructureHollowing;
import com.gtl.enhancedcore.mixin.gtceu.BlockPatternAccessor;
import com.lowdragmc.lowdraglib.utils.BlockInfo;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

/** Census of live controller candidates after all production structure wrappers. No world is opened. */
@Mod("enhancedcore_audit")
public final class ControllerCandidateClientChecks {
    private final List<Map<String, Object>> machines = new ArrayList<>();
    private final List<Map<String, String>> failures = new ArrayList<>();
    private final Map<Block, String> controllerBlocks = new IdentityHashMap<>();
    private final Map<SimplePredicate, Set<Block>> candidateCache = new IdentityHashMap<>();
    private List<MultiblockMachineDefinition> definitions;
    private final List<String> visualChecks = new ArrayList<>();
    private int next;
    private boolean done;

    public ControllerCandidateClientChecks() { MinecraftForge.EVENT_BUS.addListener(this::tick); }

    private void tick(TickEvent.ClientTickEvent event) {
        if (done || event.phase != TickEvent.Phase.END
                || !Boolean.getBoolean("gtl.enhancedcore.controllerCandidateAudit")) return;
        var mc = Minecraft.getInstance();
        if (definitions != null && next == definitions.size()) {
            try {
                if (Boolean.getBoolean("gtl.enhancedcore.fuxiControllerVisualAudit") && failures.isEmpty()
                        && !FuxiControllerVisualChecks.tick(visualChecks)) return;
                finish(null);
            } catch (Throwable error) { error.printStackTrace(); finish(error); }
            return;
        }
        if (mc.getOverlay() != null || !(mc.screen instanceof TitleScreen)) return;
        try {
            if (definitions == null) {
                var directory = mc.gameDirectory.toPath().toAbsolutePath().normalize();
                if (!directory.startsWith(Path.of("C:/IDEA/GTL-Enhancedcore/_audit/controller-candidates-20261103"))
                        || !Files.isRegularFile(directory.resolve("controller-candidates.marker"))
                        || mc.level != null || mc.player != null || mc.getSingleplayerServer() != null)
                    throw new IllegalStateException("Expected marked isolated title screen without a world");
                definitions = GTRegistries.MACHINES.values().stream()
                        .filter(MultiblockMachineDefinition.class::isInstance)
                        .map(MultiblockMachineDefinition.class::cast)
                        .sorted(Comparator.comparing(d -> d.getId().toString())).toList();
                for (var definition : definitions)
                    controllerBlocks.put(definition.getBlock(), definition.getId().toString());
            }
            var definition = definitions.get(next++);
            try { machines.add(scan(definition)); }
            catch (Throwable error) {
                error.printStackTrace();
                failures.add(Map.of("id", definition.getId().toString(), "error", error.toString()));
            }
        } catch (Throwable error) { error.printStackTrace(); finish(error); }
    }

    private Map<String, Object> scan(MultiblockMachineDefinition definition) {
        var id = definition.getId();
        var pattern = definition.getPatternFactory().get();
        var grid = ((BlockPatternAccessor) pattern).gtlEnhancedcore$getBlockMatches();
        var occurrences = new IdentityHashMap<TraceabilityPredicate, long[]>();
        for (int z = 0; z < grid.length; z++) for (var row : grid[z]) for (var predicate : row) {
            var count = occurrences.computeIfAbsent(predicate, ignored -> new long[2]);
            count[0] += pattern.aisleRepetitions[z][0];
            count[1] += pattern.aisleRepetitions[z][1];
        }
        long anchors = 0, ownCandidates = 0;
        var components = new ArrayList<Map<String, Object>>();
        var fuxiPorts = new ArrayList<Map<String, Object>>();
        for (var occurrence : occurrences.entrySet()) {
            var predicate = occurrence.getKey();
            if (predicate == null) throw new IllegalStateException("null pattern predicate");
            if (predicate.isController) { anchors += occurrence.getValue()[0]; continue; }
            var candidates = Collections.newSetFromMap(new IdentityHashMap<Block, Boolean>());
            for (var simple : predicate.common) candidates.addAll(candidates(simple));
            for (var simple : predicate.limited) candidates.addAll(candidates(simple));
            if (id.getPath().equals("fuxi_bagua_heaven_forging_furnace")
                    && occurrence.getValue()[0] == 186 && candidates.size() > 1) {
                var casing = ForgeRegistries.BLOCKS.getValue(new ResourceLocation("gtlcore:dimension_injection_casing"));
                boolean acceptsCasing = predicate.test(new Cell(casing.defaultBlockState(), predicate));
                boolean acceptsController = predicate.test(new Cell(definition.defaultBlockState(), predicate));
                if (!acceptsCasing || acceptsController || candidates.contains(definition.getBlock()))
                    throw new AssertionError("Fuxi empty ports must accept dimension-injection casing and reject its controller");
                fuxiPorts.add(Map.of("cells", occurrence.getValue()[0], "acceptsDimensionInjectionCasing", acceptsCasing,
                        "acceptsOwnControllerBlock", acceptsController));
            }
            var controllerIds = candidates.stream().filter(controllerBlocks::containsKey)
                    .map(controllerBlocks::get).sorted().toList();
            if (controllerIds.isEmpty()) continue;
            var entry = new LinkedHashMap<String, Object>();
            entry.put("minimumCells", occurrence.getValue()[0]);
            entry.put("maximumCells", occurrence.getValue()[1]);
            entry.put("controllerCandidates", controllerIds);
            entry.put("allCandidates", candidates.stream().map(b -> ForgeRegistries.BLOCKS.getKey(b).toString()).sorted().toList());
            entry.put("ownControllerCandidate", candidates.contains(definition.getBlock()));
            if (candidates.contains(definition.getBlock())) {
                ownCandidates += occurrence.getValue()[0];
                if (id.getPath().equals("fuxi_bagua_heaven_forging_furnace")) {
                    var casing = ForgeRegistries.BLOCKS.getValue(new ResourceLocation("gtlcore:dimension_injection_casing"));
                    entry.put("acceptsOwnControllerBlock", predicate.test(new Cell(definition.defaultBlockState(), predicate)));
                    entry.put("acceptsDimensionInjectionCasing", predicate.test(new Cell(casing.defaultBlockState(), predicate)));
                }
            }
            components.add(entry);
        }
        var result = new LinkedHashMap<String, Object>();
        result.put("id", id.toString());
        result.put("thirdParty", !id.getNamespace().equals("gtl_enhancedcore"));
        result.put("geometryReplaced", GtlMegastructurePatterns.targets(id));
        result.put("nativeIsolationWrapper", NativeIsolationPatterns.targets(id));
        result.put("removedFixedHiddenCells", NativeStructureHollowing.removedCellsByMachine().getOrDefault(id, 0));
        result.put("size", List.of(grid[0][0].length, grid[0].length, grid.length));
        result.put("minimumStructuralControllerAnchors", anchors);
        result.put("minimumOwnControllerCandidateCellsOutsideAnchor", ownCandidates);
        result.put("nonAnchorControllerCandidates", components);
        if (GtlMegastructurePatterns.targets(id) && ownCandidates != 0)
            throw new AssertionError(id + " replacement contains own-controller candidates outside its anchor");
        if (id.getPath().equals("fuxi_bagua_heaven_forging_furnace")) {
            if (anchors != 1 || fuxiPorts.size() != 1) throw new AssertionError("Fuxi anchor/port identity changed");
            result.put("fuxiPortPolicy", fuxiPorts);
        }
        if (ownCandidates > 0 || id.getPath().equals("fuxi_bagua_heaven_forging_furnace")) {
            var previewCounts = new ArrayList<Map<String, Integer>>();
            for (var shape : definition.getMatchingShapes()) {
                var counts = new TreeMap<String, Integer>();
                for (var plane : shape.getBlocks()) for (var row : plane) for (BlockInfo info : row) {
                    if (info == null) continue;
                    var controller = controllerBlocks.get(info.getBlockState().getBlock());
                    if (controller != null) counts.merge(controller, 1, Integer::sum);
                }
                previewCounts.add(counts);
                if (id.getPath().equals("fuxi_bagua_heaven_forging_furnace") && counts.getOrDefault(id.toString(), 0) != 1)
                    throw new AssertionError("Fuxi registered preview must contain exactly one controller");
            }
            result.put("actualRegisteredPreviewControllerCounts", previewCounts);
        }
        System.out.println("[CONTROLLER_CENSUS] " + id + " anchors=" + anchors + " nonAnchorSelfCandidates=" + ownCandidates);
        return result;
    }

    private Set<Block> candidates(SimplePredicate predicate) {
        return candidateCache.computeIfAbsent(predicate, simple -> {
            var blocks = Collections.newSetFromMap(new IdentityHashMap<Block, Boolean>());
            var infos = simple.candidates == null ? null : simple.candidates.get();
            if (infos != null) for (var info : infos) if (info != null) blocks.add(info.getBlockState().getBlock());
            return blocks;
        });
    }

    private void finish(Throwable failure) {
        done = true;
        var report = new LinkedHashMap<String, Object>();
        report.put("status", failure == null && failures.isEmpty() ? "PASS" : "FAIL");
        report.put("scope", "All registered multiblock patterns with production structure replacement and enhancements enabled; real previews for own-controller fallback findings");
        report.put("worldOpened", Boolean.getBoolean("gtl.enhancedcore.fuxiControllerVisualAudit"));
        report.put("registeredDefinitions", definitions == null ? 0 : definitions.size());
        report.put("scannedDefinitions", machines.size());
        report.put("machines", machines);
        report.put("failures", failures);
        report.put("visualChecks", visualChecks);
        if (failure != null) report.put("error", failure.toString());
        try {
            Files.writeString(Minecraft.getInstance().gameDirectory.toPath().resolve("controller-candidates.json"),
                    new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(report), StandardCharsets.UTF_8);
        } catch (Exception error) { error.printStackTrace(); }
        Minecraft.getInstance().stop();
    }

    private static final class Cell extends MultiblockState {
        private final BlockState state;
        Cell(BlockState state, TraceabilityPredicate predicate) {
            super(null, BlockPos.ZERO); clean(); this.state = state; this.predicate = predicate; setError(null);
        }
        @Override public BlockState getBlockState() { return state; }
        @Override public net.minecraft.world.level.block.entity.BlockEntity getTileEntity() { return null; }
        @Override public BlockPos getPos() { return BlockPos.ZERO; }
    }
}
