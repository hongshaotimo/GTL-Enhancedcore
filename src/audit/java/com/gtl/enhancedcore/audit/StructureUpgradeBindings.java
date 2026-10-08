package com.gtl.enhancedcore.audit;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.pattern.TraceabilityPredicate;
import com.gregtechceu.gtceu.api.pattern.predicates.SimplePredicate;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import com.gtl.enhancedcore.mixin.gtceu.BlockPatternAccessor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.TreeSet;
import java.util.stream.Stream;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.ForgeRegistries;

/** Audit-only snapshot of the installed upstream predicates before importing geometry. */
public final class StructureUpgradeBindings {
    public static final List<String> NAMES = List.of("mega_fluid_heater", "advanced_sps_crafting",
            "dimensionally_transcendent_mixer", "star_ultimate_material_forge_factory", "qft", "nano_core",
            "gravitation_shockburst", "advanced_neutron_activator",
            "dimensionally_transcendent_chemical_plant", "antientropy_condensation_center");

    private StructureUpgradeBindings() {}

    public static void capture() throws Exception {
        capture(NAMES, "structure-upgrade-bindings.json");
    }

    public static void captureSeptember27() throws Exception {
        capture(List.of("advanced_integrated_ore_processor", "atomic_energy_excitation_plant",
                "component_assembly_line", "mage_assembler", "super_blast_smelter",
                "superconducting_electromagnetism"), "structure-upgrade-20260927-bindings.json");
    }

    private static void capture(List<String> names, String file) throws Exception {
        var result = new JsonObject();
        for (String name : names) {
            MultiblockMachineDefinition definition = null;
            for (String namespace : List.of("gtceu", "gtladditions", "gtlcore")) {
                var candidate = GTRegistries.MACHINES.get(new ResourceLocation(namespace, name));
                if (candidate instanceof MultiblockMachineDefinition multi) {
                    if (definition != null) throw new IllegalStateException("Ambiguous machine " + name);
                    definition = multi;
                }
            }
            if (definition == null) throw new IllegalStateException("Unknown machine " + name);
            var pattern = definition.getPatternFactory().get();
            var grid = ((BlockPatternAccessor) pattern).gtlEnhancedcore$getBlockMatches();
            var spec = new JsonObject();
            spec.addProperty("id", definition.getId().toString());
            spec.add("size", integers(grid[0][0].length, grid[0].length, grid.length));
            var predicates = new JsonArray();
            var seen = new IdentityHashMap<TraceabilityPredicate, JsonObject>();
            for (int z = 0; z < grid.length; z++) for (int y = 0; y < grid[z].length; y++)
                for (int x = 0; x < grid[z][y].length; x++) {
                    var predicate = grid[z][y][x];
                    var item = seen.get(predicate);
                    if (item == null) {
                        item = new JsonObject();
                        item.add("sample", integers(z, y, x));
                        item.addProperty("controller", predicate.isController);
                        item.addProperty("any", predicate.isAny());
                        item.addProperty("count", 0);
                        var simple = new JsonArray();
                        Stream.concat(predicate.common.stream(), predicate.limited.stream()).distinct()
                                .forEach(value -> simple.add(describe(value)));
                        item.add("simple", simple);
                        predicates.add(item);
                        seen.put(predicate, item);
                    }
                    item.addProperty("count", item.get("count").getAsInt() + 1);
                    if (predicate.isController) spec.add("controller", integers(z, y, x));
                }
            spec.add("predicates", predicates);
            result.add(name, spec);
        }
        Files.writeString(Path.of("kubejs", file),
                new GsonBuilder().setPrettyPrinting().create().toJson(result));
    }

    public static JsonObject describe(SimplePredicate value) {
        var item = new JsonObject();
        item.addProperty("type", value.type);
        item.addProperty("min", value.minCount);
        item.addProperty("max", value.maxCount);
        item.addProperty("minLayer", value.minLayerCount);
        item.addProperty("maxLayer", value.maxLayerCount);
        item.addProperty("preview", value.previewCount);
        item.addProperty("io", value.io == null ? "" : value.io.name());
        var blocks = new TreeSet<String>();
        if (value.candidates != null) {
            var candidates = value.candidates.get();
            if (candidates != null) for (var candidate : candidates) {
                if (candidate != null) blocks.add(ForgeRegistries.BLOCKS.getKey(candidate.getBlockState().getBlock()).toString());
            }
        }
        var array = new JsonArray();
        blocks.forEach(array::add);
        item.add("blocks", array);
        return item;
    }

    private static JsonArray integers(int... values) {
        var array = new JsonArray();
        for (int value : values) array.add(value);
        return array;
    }
}
