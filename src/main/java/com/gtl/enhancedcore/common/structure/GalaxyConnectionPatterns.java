package com.gtl.enhancedcore.common.structure;

import com.gregtechceu.gtceu.api.machine.multiblock.PartAbility;
import com.gregtechceu.gtceu.api.pattern.BlockPattern;
import com.gregtechceu.gtceu.api.pattern.Predicates;
import com.gregtechceu.gtceu.api.pattern.TraceabilityPredicate;
import com.gregtechceu.gtceu.api.pattern.predicates.SimplePredicate;
import com.gtl.enhancedcore.common.machine.GTLEnhancedcoreMachines;
import com.gtl.enhancedcore.common.machine.galaxy.GalaxyConnectionHatch;
import com.gtl.enhancedcore.mixin.gtceu.BlockPatternAccessor;
import java.util.IdentityHashMap;
import java.util.stream.Stream;

/** Admit the target at existing input ports without spending their material quotas. */
public final class GalaxyConnectionPatterns {
    private GalaxyConnectionPatterns() {}

    public static BlockPattern extend(BlockPattern source) {
        var target = GTLEnhancedcoreMachines.getGalaxyTargetHatch().getBlock();
        var access = (BlockPatternAccessor) source;
        var matches = access.gtlEnhancedcore$getBlockMatches();
        var rules = new IdentityHashMap<TraceabilityPredicate, TraceabilityPredicate>();
        var candidates = new IdentityHashMap<SimplePredicate, Integer>();
        // Offer the target as a selectable alternative without replacing native preview hatches.
        var targetRule = Predicates.blocks(target).setPreviewCount(0);
        for (var aisle : matches) for (var row : aisle) for (var rule : row) {
            rules.computeIfAbsent(rule, original -> {
                if (original.isController) return original;
                int flags = Stream.concat(original.common.stream(), original.limited.stream())
                        .mapToInt(part -> candidates.computeIfAbsent(part, predicate -> {
                            int found = 0;
                            if (predicate.candidates == null) return found;
                            for (var info : predicate.candidates.get()) {
                                var block = info.getBlockState().getBlock();
                                if (block == target) found |= 2;
                                else if (!GalaxyConnectionHatch.isConnectionPortBlock(block)
                                        && (PartAbility.IMPORT_ITEMS.isApplicable(block)
                                        || PartAbility.IMPORT_FLUIDS.isApplicable(block))
                                        // Dual-purpose ME parts also occur in output-only predicates.
                                        && !PartAbility.EXPORT_ITEMS.isApplicable(block)
                                        && !PartAbility.EXPORT_FLUIDS.isApplicable(block)) found |= 1;
                            }
                            return found;
                        })).reduce(0, (left, right) -> left | right);
                return flags == 1 ? original.or(targetRule) : original;
            });
        }
        if (rules.entrySet().stream().noneMatch(entry -> entry.getKey() != entry.getValue())) return source;
        var copy = matches.clone();
        for (int z = 0; z < copy.length; z++) {
            copy[z] = copy[z].clone();
            for (int y = 0; y < copy[z].length; y++) {
                copy[z][y] = copy[z][y].clone();
                for (int x = 0; x < copy[z][y].length; x++) copy[z][y][x] = rules.get(copy[z][y][x]);
            }
        }
        // Original SimplePredicate identities carry all shared global/layer limits and IO metadata.
        return new BlockPattern(copy, source.structureDir, source.aisleRepetitions,
                access.enhanced$getCenterOffset().clone());
    }
}
