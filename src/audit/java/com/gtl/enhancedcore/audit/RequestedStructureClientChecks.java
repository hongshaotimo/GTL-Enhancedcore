package com.gtl.enhancedcore.audit;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.gregtechceu.gtceu.api.GTCEuAPI;
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.pattern.MultiblockState;
import com.gregtechceu.gtceu.api.pattern.TraceabilityPredicate;
import com.gregtechceu.gtceu.api.pattern.predicates.SimplePredicate;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import com.gregtechceu.gtceu.common.block.CoilBlock;
import com.gtl.enhancedcore.common.structure.StructureData;
import com.gtl.enhancedcore.common.structure.HollowStructureResources;
import com.gtl.enhancedcore.mixin.gtceu.BlockPatternAccessor;
import com.lowdragmc.lowdraglib.utils.BlockInfo;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;
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

/** Actual transformed registrations, quotas, coil context and previews; no saved world. */
@Mod("enhancedcore_audit")
public final class RequestedStructureClientChecks {
    private static final String ROOT = "/data/gtl_enhancedcore/structures/";
    private static final List<String> IDS = List.of("gtladditions:antientropy_condensation_center",
            "gtl_enhancedcore:stellar_confinement_fusion_reactor", "gtl_enhancedcore:hyperstructural_chemical_distorter",
            "gtladditions:dimensionally_transcendent_chemical_plant", "gtladditions:lucid_etchdreamer",
            "gtceu:advanced_sps_crafting", "gtceu:super_particle_collider",
            "gtladditions:macro_atomic_resonant_fragment_stripper", "gtladditions:quantum_syphon_matrix",
            "gtceu:huge_incubator", "gtladditions:titan_crip_earthbore", "gtceu:eye_of_harmony",
            "gtladditions:arcanic_astrograph", "gtladditions:heart_of_the_universe",
            "gtladditions:fuxi_bagua_heaven_forging_furnace",
            "gtladditions:space_infinity_integrated_ore_processor", "gtladditions:apocalyptic_torsion_quantum_matrix",
            "gtl_enhancedcore:stellar_vortex_space_station", "gtl_enhancedcore:lanthanide_refining_hub");
    private final List<String> checks = new ArrayList<>();
    private final List<String> failures = new ArrayList<>();
    private final List<Map<String, Object>> machines = new ArrayList<>();
    private int next;
    private boolean done;

    public RequestedStructureClientChecks() { MinecraftForge.EVENT_BUS.addListener(this::tick); }

    private void tick(TickEvent.ClientTickEvent event) {
        if (done || event.phase != TickEvent.Phase.END || !Boolean.getBoolean("gtl.enhancedcore.requestedStructureAudit")) return;
        var mc = Minecraft.getInstance();
        if (mc.getOverlay() != null || !(mc.screen instanceof TitleScreen)) return;
        try {
            if (next == 0) {
                Path directory = mc.gameDirectory.toPath().toAbsolutePath().normalize();
                check(directory.startsWith(Path.of("C:/IDEA/GTL-Enhancedcore/_audit/structures-20261102"))
                        && Files.isRegularFile(directory.resolve("requested-structures.marker"))
                        && mc.level == null && mc.getSingleplayerServer() == null, "Marked isolated client without a player world");
            }
            if (next < IDS.size()) {
                String id = IDS.get(next++);
                try { verify(id); }
                catch (Throwable failure) { failures.add(id + ": " + failure); failure.printStackTrace(); }
            } else {
                try { LanthanideRecipeRuntimeChecks.run(checks); }
                catch (Throwable failure) { failures.add("lanthanide recipes: " + failure); failure.printStackTrace(); }
                try { CelestialEffectAnchorChecks.run(checks); }
                catch (Throwable failure) { failures.add("celestial anchors: " + failure); failure.printStackTrace(); }
                finish(failures.isEmpty() ? null : new AssertionError(String.join("; ", failures)));
            }
        } catch (Throwable failure) { failure.printStackTrace(); finish(failure); }
    }

    private void verify(String id) throws Exception {
        var definition = (MultiblockMachineDefinition) GTRegistries.MACHINES.get(new ResourceLocation(id));
        check(definition != null, id + " exact machine definition");
        String name = definition.getId().getPath();
        boolean own = definition.getId().getNamespace().equals("gtl_enhancedcore");
        JsonObject spec = specification(name, own);
        String resource = !own ? "gtl/" + name
                : name.equals("stellar_confinement_fusion_reactor") || name.equals("hyperstructural_chemical_distorter")
                ? name.replace("_", "") + "structure" : name;
        var pattern = definition.getPatternFactory().get();
        var grid = ((BlockPatternAccessor) pattern).gtlEnhancedcore$getBlockMatches();
        var dimensions = spec.getAsJsonArray("size");
        int w = dimensions.get(0).getAsInt(), h = dimensions.get(1).getAsInt(), d = dimensions.get(2).getAsInt();
        check(grid.length == d && grid[0].length == h && grid[0][0].length == w, id + " live dimensions " + w + "x" + h + "x" + d);
        Map<Character, TraceabilityPredicate> predicates = new HashMap<>();
        Map<Character, Integer> counts = new HashMap<>();
        int[] aisle = {0};
        String effective = HollowStructureResources.resolve(ROOT + resource + ".pattern.gz");
        StructureData.read(StructureData.class.getResourceAsStream(effective)).forEachAisle(rows -> {
            int z = aisle[0]++;
            for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
                char symbol = rows[y].charAt(x);
                counts.merge(symbol, 1, Integer::sum);
                predicates.putIfAbsent(symbol, grid[z][y][x]);
                if (predicates.get(symbol) != grid[z][y][x]) throw new AssertionError(id + " split predicate identity " + symbol);
            }
        });
        char controller = spec.get("controller").isJsonPrimitive() ? spec.get("controller").getAsString().charAt(0) : 'C';
        char hatch = spec.has("hatches") ? spec.getAsJsonArray("hatches").get(0).getAsString().charAt(0) : 'X';
        check(counts.get(controller) == 1 && predicates.get(controller).isController, id + " unique real controller predicate");
        int requestedPorts = spec.has("diamondOreMarkers") ? spec.get("diamondOreMarkers").getAsInt()
                : spec.getAsJsonArray("ports").size();
        check(counts.get(hatch) == requestedPorts, id + " every requested hatch cell retained");
        var port = predicates.get(hatch);
        String fallback = spec.has("hatchFallback") ? spec.get("hatchFallback").getAsString()
                : "gtlcore:dimensionally_transcendent_casing";
        Block casing = block(fallback);
        check(port.test(new Cell(casing.defaultBlockState(), port)), id + " requested empty port casing accepted");
        Set<Block> candidates = candidates(port);
        check(!candidates.contains(definition.getBlock()), id + " hatch fallback excludes its own controller block");
        if (name.equals("fuxi_bagua_heaven_forging_furnace"))
            check(!port.test(new Cell(definition.defaultBlockState(), port)), id + " hatch rejects controller as empty casing");
        var quotas = new ArrayList<String>();
        for (var simple : Stream.concat(port.common.stream(), port.limited.stream()).distinct().toList()) {
            var candidateBlocks = candidates(new TraceabilityPredicate(simple));
            if (candidateBlocks.size() == 1 && candidateBlocks.contains(casing)) {
                int solids = 0;
                for (var entry : predicates.entrySet()) {
                    if (entry.getKey() == hatch) continue;
                    if (entry.getValue().common.contains(simple) || entry.getValue().limited.contains(simple)) solids += counts.get(entry.getKey());
                }
                check(simple.minCount <= solids, id + " casing quota is satisfiable with fixed body: " + simple.minCount + " <= " + solids);
                if (name.equals("advanced_sps_crafting")) check(simple.minCount == 162 && solids == 162,
                        id + " new 162 casing counter shared by body and all 68 ports");
            }
            if (simple.minCount >= 0 || simple.maxCount >= 0) quotas.add(simple.type + ":" + simple.minCount + ".." + simple.maxCount);
        }
        if (name.equals("stellar_confinement_fusion_reactor")) {
            check(candidates.stream().noneMatch(b -> {
                String path = ForgeRegistries.BLOCKS.getKey(b).getPath();
                return path.contains("maintenance") || path.contains("parallel") || path.contains("thread_modifier")
                        || path.contains("energy_input") || path.contains("laser_target");
            }), id + " only material IO, with no maintenance/parallel/omega/energy/laser");
        }
        for (var entry : spec.getAsJsonObject("bindings").entrySet()) {
            var binding = entry.getValue().getAsJsonObject();
            String mode = binding.get("mode").getAsString();
            if (mode.equals("heating_coils") || mode.equals("distorter_coils")
                    || binding.has("block") && binding.get("block").getAsString().equals("gtceu:tritanium_coil_block"))
                coils(id, predicates.get(entry.getKey().charAt(0)), name.equals("hyperstructural_chemical_distorter"));
        }
        var shapes = definition.getMatchingShapes();
        check(shapes.size() == 1, id + " one actual registered preview shape");
        BlockInfo[][][] preview = shapes.getFirst().getBlocks();
        int controllers = 0, nulls = 0, shownPorts = 0;
        for (var plane : preview) for (var row : plane) for (var info : row) {
            if (info == null) { nulls++; continue; }
            if (info.getBlockState().is(definition.getBlock())) {
                controllers++;
                check(info.hasBlockEntity(), id + " preview controller retains block entity");
            }
        }
        check(controllers == 1, id + " preview contains exactly one actual controller block");
        int controllerPredicates = 0;
        for (var plane : grid) for (var row : plane) for (var predicate : row)
            if (predicate.isController) controllerPredicates++;
        check(controllerPredicates == 1, id + " exactly one structural controller anchor");
        if (name.equals("lucid_etchdreamer")) {
            var sparse = new org.gtlcore.gtlcore.client.preview.SparsePreviewShape(shapes.getFirst(), definition);
            check(sparse.controller.equals(new BlockPos(87, 87, 0)), id + " actual sparse page uses the new controller anchor");
            check(nulls == counts.get(' '), id + " all " + nulls + " wildcard cells remain null in sparse preview");
            var origin = spec.getAsJsonObject("previewPanel").getAsJsonArray("origin");
            int z = origin.get(2).getAsInt();
            for (int y = 82; y <= 92; y++) for (int x = 82; x <= 92; x++) if (grid[z][y][x] == port) {
                var info = preview[w-1-x][y][d-1-z];
                check(info != null && !info.getBlockState().isAir(), id + " actual 11x11 panel has a visible port at " + x + "," + y);
                shownPorts++;
            }
            check(shownPorts == 108, id + " all 108 preview ports supplied");
        }
        var holder = (com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity) definition.getBlockEntityType()
                .create(BlockPos.ZERO, definition.defaultBlockState());
        check(holder.getMetaMachine().getDefinition() == definition, id + " actual controller constructor retains definition");
        machines.add(Map.of("id", id, "size", List.of(w,h,d), "ports", counts.get(hatch), "quota", quotas,
                "controllerClass", holder.getMetaMachine().getClass().getName(), "previewControllers", controllers,
                "previewNullCells", nulls));
        System.out.println("[REQUESTED_STRUCTURES] PASS " + id + " checks=" + checks.size());
    }

    private static JsonObject specification(String name, boolean own) throws Exception {
        if (own) return read(name + ".json");
        for (String file : List.of("gtl/manifest.json", "gtl/upgrades.json", "gtl/upgrades_20260927.json",
                "gtl/lucid_etchdreamer.json", "gtl/super_particle_collider.json", "gtl/upgrades_20261013.json",
                "gtl/upgrades_20261102.json")) {
            var entries = read(file);
            if (entries.has(name)) return entries.getAsJsonObject(name);
        }
        throw new IllegalStateException("Missing requested structure specification: " + name);
    }

    private static JsonObject read(String file) throws Exception {
        try (var input = StructureData.class.getResourceAsStream(ROOT + file)) {
            if (input == null) throw new IllegalStateException("Missing requested manifest: " + file);
            return JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    private void coils(String id, TraceabilityPredicate predicate, boolean minimum) {
        var accepted = new ArrayList<Block>();
        for (var entry : GTCEuAPI.HEATING_COILS.entrySet()) {
            var coil = entry.getValue().get();
            var state = new Cell(coil.defaultBlockState(), predicate);
            boolean expected = !minimum || entry.getKey().getCoilTemperature() >= CoilBlock.CoilType.TRITANIUM.getCoilTemperature();
            check(predicate.test(state) == expected, id + " registered coil temperature " + entry.getKey().getName());
            if (expected) {
                check(state.getMatchContext().get("CoilType") == entry.getKey(), id + " actual CoilType stored " + entry.getKey().getName());
                accepted.add(coil);
            }
        }
        for (var left : accepted) for (var right : accepted) {
            var state = new Cell(left.defaultBlockState(), predicate);
            check(predicate.test(state), id + " first coil accepted");
            state.current = right.defaultBlockState();
            check(predicate.test(state) == (left == right), id + " mixed coils rejected");
        }
    }

    private static Set<Block> candidates(TraceabilityPredicate predicate) {
        Set<Block> blocks = new HashSet<>();
        Stream.concat(predicate.common.stream(), predicate.limited.stream()).distinct().forEach(simple -> {
            if (simple.candidates != null) for (var info : simple.candidates.get()) blocks.add(info.getBlockState().getBlock());
        });
        return blocks;
    }
    private static Block block(String id) { return ForgeRegistries.BLOCKS.getValue(new ResourceLocation(id)); }
    private void check(boolean valid, String message) { if (!valid) throw new AssertionError(message); checks.add(message); }
    private void finish(Throwable failure) {
        done = true;
        var result = new LinkedHashMap<String, Object>();
        result.put("status", failure == null ? "PASS" : "FAIL"); result.put("checks", checks);
        result.put("machines", machines); result.put("worldOpened", false);
        result.put("failures", failures);
        if (failure != null) result.put("error", failure.toString());
        try { Files.writeString(Minecraft.getInstance().gameDirectory.toPath().resolve("requested-structures.json"),
                new GsonBuilder().setPrettyPrinting().create().toJson(result), StandardCharsets.UTF_8); }
        catch (Exception error) { error.printStackTrace(); }
        Minecraft.getInstance().stop();
    }
    private static final class Cell extends MultiblockState {
        private BlockState current;
        Cell(BlockState current, TraceabilityPredicate predicate) {
            super(null, BlockPos.ZERO); clean(); this.current = current; this.predicate = predicate; setError(null);
        }
        @Override public BlockState getBlockState() { return current; }
        @Override public net.minecraft.world.level.block.entity.BlockEntity getTileEntity() { return null; }
        @Override public BlockPos getPos() { return BlockPos.ZERO; }
    }
}
