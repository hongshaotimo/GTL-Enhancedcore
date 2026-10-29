package com.gtl.enhancedcore.common.structure;

import com.gregtechceu.gtceu.api.pattern.BlockPattern;
import com.gregtechceu.gtceu.api.pattern.TraceabilityPredicate;
import com.gregtechceu.gtceu.api.pattern.predicates.SimplePredicate;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import com.gtl.enhancedcore.common.recipe.iv.IvNativeHatches;
import com.gtl.enhancedcore.mixin.gtceu.BlockPatternAccessor;
import com.lowdragmc.lowdraglib.utils.BlockInfo;
import java.util.*;
import net.minecraft.resources.ResourceLocation;

/** Add the cloud endpoint to each existing material port's own quota and slot identity. */
public final class AssemblyLineCloudPorts {
    private AssemblyLineCloudPorts() {}
    public static BlockPattern copy(BlockPattern source) {
        var access = (BlockPatternAccessor) source;
        var matches = access.gtlEnhancedcore$getBlockMatches().clone();
        var predicates = new IdentityHashMap<SimplePredicate, SimplePredicate>();
        var rules = new IdentityHashMap<TraceabilityPredicate, TraceabilityPredicate>();
        var cloud = GTRegistries.MACHINES.get(new ResourceLocation("gtl_enhancedcore", "cloud_pattern_buffer")).getBlock();
        for (int z = 0; z < matches.length; z++) {
            matches[z] = matches[z].clone();
            for (int y = 0; y < matches[z].length; y++) {
                matches[z][y] = matches[z][y].clone();
                for (int x = 0; x < matches[z][y].length; x++) {
                    matches[z][y][x] = rules.computeIfAbsent(matches[z][y][x], rule -> {
                        var result = new TraceabilityPredicate(rule);
                        for (var parts : List.of(result.common, result.limited)) parts.replaceAll(part -> predicates.computeIfAbsent(part, original -> {
                            if (original.candidates == null || Arrays.stream(original.candidates.get())
                                    .noneMatch(candidate -> IvNativeHatches.materials(candidate.getBlockState().getBlock()))) return original;
                            var extended = new SimplePredicate(world -> world.getBlockState().is(cloud) || original.predicate.test(world), () ->
                                    java.util.stream.Stream.concat(Arrays.stream(original.candidates.get()),
                                            java.util.stream.Stream.of(new BlockInfo(cloud.defaultBlockState()))).toArray(BlockInfo[]::new));
                            extended.minCount = original.minCount; extended.maxCount = original.maxCount;
                            extended.minLayerCount = original.minLayerCount; extended.maxLayerCount = original.maxLayerCount;
                            extended.previewCount = original.previewCount; extended.disableRenderFormed = original.disableRenderFormed;
                            extended.io = original.io; extended.slotName = original.slotName; extended.nbtParser = original.nbtParser;
                            extended.toolTips = original.toolTips;
                            return extended;
                        }));
                        return result;
                    });
                }
            }
        }
        return new BlockPattern(matches, source.structureDir, source.aisleRepetitions, access.enhanced$getCenterOffset().clone());
    }
}
