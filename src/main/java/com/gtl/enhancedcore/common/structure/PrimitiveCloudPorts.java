package com.gtl.enhancedcore.common.structure;

import com.gregtechceu.gtceu.api.pattern.BlockPattern;
import com.gregtechceu.gtceu.api.pattern.Predicates;
import com.gregtechceu.gtceu.api.pattern.TraceabilityPredicate;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import com.gregtechceu.gtceu.common.data.GTBlocks;
import com.gtl.enhancedcore.mixin.gtceu.BlockPatternAccessor;
import java.util.IdentityHashMap;
import net.minecraft.resources.ResourceLocation;

/** Extend only the primitive blast furnace's existing solid-brick positions. */
public final class PrimitiveCloudPorts {
    private static final ResourceLocation TARGET = new ResourceLocation("gtceu", "primitive_blast_furnace");
    private PrimitiveCloudPorts() {}

    public static boolean targets(ResourceLocation id) { return TARGET.equals(id); }

    public static BlockPattern copy(BlockPattern source) {
        var access = (BlockPatternAccessor) source;
        var matches = access.gtlEnhancedcore$getBlockMatches().clone();
        var cloud = GTRegistries.MACHINES.get(new ResourceLocation("gtl_enhancedcore", "cloud_pattern_buffer")).getBlock();
        // Every brick position shares this exact limited predicate, so the optional
        // one-buffer limit applies to the whole furnace rather than each position.
        var endpoint = Predicates.blocks(cloud).setMaxGlobalLimited(1).setPreviewCount(1);
        var rules = new IdentityHashMap<TraceabilityPredicate, TraceabilityPredicate>();
        boolean changed = false;
        for (int z = 0; z < matches.length; z++) {
            matches[z] = matches[z].clone();
            for (int y = 0; y < matches[z].length; y++) {
                matches[z][y] = matches[z][y].clone();
                for (int x = 0; x < matches[z][y].length; x++) {
                    var original = matches[z][y][x];
                    var extended = rules.computeIfAbsent(original, rule -> solidPrimitiveBricks(rule)
                            ? new TraceabilityPredicate(rule).or(endpoint) : rule);
                    matches[z][y][x] = extended;
                    changed |= extended != original;
                }
            }
        }
        return changed ? new BlockPattern(matches, source.structureDir, source.aisleRepetitions,
                access.enhanced$getCenterOffset().clone()) : source;
    }

    private static boolean solidPrimitiveBricks(TraceabilityPredicate rule) {
        if (rule.isController || !rule.limited.isEmpty() || rule.common.size() != 1) return false;
        var predicate = rule.common.get(0);
        if (predicate.candidates == null) return false;
        var candidates = predicate.candidates.get();
        return candidates.length == 1 && candidates[0].getBlockState().is(GTBlocks.CASING_PRIMITIVE_BRICKS.get());
    }
}
