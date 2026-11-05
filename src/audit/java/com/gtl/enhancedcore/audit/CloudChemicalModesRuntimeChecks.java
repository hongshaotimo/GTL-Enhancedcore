package com.gtl.enhancedcore.audit;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.crafting.pattern.AEProcessingPattern;
import com.google.gson.GsonBuilder;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import com.gregtechceu.gtceu.common.data.GTRecipeTypes;
import com.gtl.enhancedcore.common.cloud.CloudChemicalModes;
import com.gtl.enhancedcore.common.cloud.CloudPatternMatching;
import com.gtl.enhancedcore.common.cloud.ui.CloudPatternSearch;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Items;
import org.gtlcore.gtlcore.integration.ae2.pattern.PatternQuickUploadMetadata;

/** Actual initialized registry and processing-pattern checks; no world or machine simulation. */
public final class CloudChemicalModesRuntimeChecks {
    private CloudChemicalModesRuntimeChecks() {}
    public static void run(Path directory, List<String> checks) throws Exception {
        var small = GTRecipeTypes.CHEMICAL_RECIPES;
        var large = GTRecipeTypes.LARGE_CHEMICAL_RECIPES;
        var rows = new ArrayList<Map<String, Object>>();
        require(small != large && GTRegistries.RECIPE_TYPES.get(small.registryName) == small
                        && GTRegistries.RECIPE_TYPES.get(large.registryName) == large,
                "actual_small_and_large_chemical_maps_are_distinct_registered_types", checks);
        for (var source : List.of(small, large)) {
            var other = source == small ? large : small;
            require(CloudPatternMatching.queryTypes(source).equals(List.of(source, other)),
                    source.registryName + "_production_query_contains_both_actual_maps_with_native_first", checks);
            var original = PatternDetailsHelper.encodeProcessingPattern(
                    new GenericStack[]{new GenericStack(AEItemKey.of(Items.APPLE), 3)},
                    new GenericStack[]{new GenericStack(AEItemKey.of(Items.GOLD_INGOT), 2)});
            PatternQuickUploadMetadata.writeRecipeTypeId(original, source.registryName);
            var encoded = original.save(new CompoundTag());
            var types = PatternQuickUploadMetadata.readRecipeTypeIds(original);
            var pattern = new AEProcessingPattern(AEItemKey.of(original));
            var probe = CloudPatternMatching.probe(pattern, Map.of());
            require(types.equals(Set.of(source.registryName)) && probe != null
                            && probe.inputs().equals(Map.of(AEItemKey.of(Items.APPLE), 3L))
                            && probe.outputs().equals(Map.of(AEItemKey.of(Items.GOLD_INGOT), 2L)),
                    source.registryName + "_actual_AE_original_preserves_exact_input_output_counts_and_metadata", checks);
            require(CloudChemicalModes.allows(types, source.registryName) && CloudChemicalModes.allows(types, other.registryName),
                    source.registryName + "_tagged_original_passes_both_cloud_mode_metadata_gates", checks);
            var search = new CloudPatternSearch(new BitSet(), new BitSet(), Set.of(source.registryName.toString()));
            require(search.matchesType(other.registryName.toString())
                            && CloudChemicalModes.catalogueTypes(Set.of(source.registryName.toString())).contains(other.registryName.toString()),
                    source.registryName + "_production_search_and_catalogue_share_the_explicit_group", checks);
            require(original.save(new CompoundTag()).equals(encoded),
                    source.registryName + "_query_does_not_rewrite_the_physical_original", checks);
            rows.add(Map.of("sourceType", source.registryName.toString(), "queryTypes",
                    CloudPatternMatching.queryTypes(source).stream().map(type -> type.registryName.toString()).toList(),
                    "original", encoded.toString(), "inputs", probe.inputs().toString(), "outputs", probe.outputs().toString()));
        }
        require(CloudPatternMatching.queryTypes(GTRecipeTypes.BENDER_RECIPES).equals(List.of(GTRecipeTypes.BENDER_RECIPES))
                        && !CloudChemicalModes.allows(Set.of(small.registryName), GTRecipeTypes.BENDER_RECIPES.registryName),
                "unrelated_registered_bender_map_remains_exact_and_rejected", checks);
        var report = new LinkedHashMap<String, Object>();
        report.put("status", "PASS"); report.put("rows", rows);
        report.put("checks", checks.stream().filter(check -> check.startsWith("cloud_chemical_modes:")).toList());
        report.put("boundary", "Initialized real GT recipe-map registry, production cloud query selection, actual AE processing-pattern encoding/decoding/probe and production catalogue/search aliases. No world, leases, CPU dispatch, native processing, materials or EU execution is exercised by this registry-only check.");
        Files.writeString(directory.resolve("cloud-chemical-modes-runtime.json"), new GsonBuilder().setPrettyPrinting().create().toJson(report));
    }
    private static void require(boolean value, String message, List<String> checks) {
        if (!value) throw new AssertionError("cloud_chemical_modes:" + message);
        checks.add("cloud_chemical_modes:" + message);
    }
}
