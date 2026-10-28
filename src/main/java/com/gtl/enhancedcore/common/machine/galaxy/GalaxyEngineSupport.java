package com.gtl.enhancedcore.common.machine.galaxy;

import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.pattern.BlockPattern;
import com.gregtechceu.gtceu.api.pattern.TraceabilityPredicate;
import com.gregtechceu.gtceu.api.pattern.Predicates;
import com.gtladd.gtladditions.api.machine.GTLAddPartAbility;
import com.gtl.enhancedcore.mixin.gtceu.BlockPatternAccessor;
import java.util.IdentityHashMap;
import java.util.stream.Stream;

/** Structure capabilities are the source of truth for engine installation tips. */
public final class GalaxyEngineSupport {
    private static final java.util.Map<MultiblockMachineDefinition, Boolean> SUPPORT = java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());
    private GalaxyEngineSupport() {}
    public static boolean supports(MultiblockMachineDefinition definition) {
        return SUPPORT.computeIfAbsent(definition, GalaxyEngineSupport::inspect);
    }
    private static boolean inspect(MultiblockMachineDefinition definition) {
        if (definition.getPatternFactory() == null) return false;
        var matches = ((BlockPatternAccessor)definition.getPatternFactory().get()).gtlEnhancedcore$getBlockMatches();
        var seen = java.util.Collections.newSetFromMap(new IdentityHashMap<TraceabilityPredicate, Boolean>());
        for (var aisle : matches) for (var row : aisle) for (var predicate : row)
            if (seen.add(predicate) && Stream.concat(predicate.common.stream(), predicate.limited.stream()).anyMatch(simple -> {
                if (simple.candidates == null) return false;
                var candidates = simple.candidates.get();
                if (candidates == null) return false;
                for (var candidate : candidates) if (candidate != null
                        && GTLAddPartAbility.INSTANCE.getTHREAD_MODIFIER().isApplicable(candidate.getBlockState().getBlock())) return true;
                return false;
            })) return true;
        return false;
    }
    public static BlockPattern addMageEngine(BlockPattern pattern) {
        var access = (BlockPatternAccessor)pattern;
        var matches = access.gtlEnhancedcore$getBlockMatches().clone();
        var replacements = new IdentityHashMap<TraceabilityPredicate, TraceabilityPredicate>();
        var engine = Predicates.abilities(GTLAddPartAbility.INSTANCE.getTHREAD_MODIFIER()).setMaxGlobalLimited(1).setPreviewCount(1);
        for (int z=0; z<matches.length; z++) {
            matches[z] = matches[z].clone();
            for (int y=0; y<matches[z].length; y++) {
                matches[z][y] = matches[z][y].clone();
                for (int x=0; x<matches[z][y].length; x++) matches[z][y][x] = replacements.computeIfAbsent(matches[z][y][x], source -> {
                    boolean material = !source.isController && Stream.concat(source.common.stream(), source.limited.stream()).anyMatch(simple -> {
                        if (simple.candidates == null) return false;
                        var candidates = simple.candidates.get();
                        if (candidates == null) return false;
                        for (var candidate : candidates) if (candidate != null && com.gtl.enhancedcore.common.recipe.iv.IvNativeHatches.materials(candidate.getBlockState().getBlock())) return true;
                        return false;
                    });
                    return material ? new TraceabilityPredicate(source).or(engine) : source;
                });
            }
        }
        return new BlockPattern(matches, pattern.structureDir, pattern.aisleRepetitions, access.enhanced$getCenterOffset().clone());
    }
}
