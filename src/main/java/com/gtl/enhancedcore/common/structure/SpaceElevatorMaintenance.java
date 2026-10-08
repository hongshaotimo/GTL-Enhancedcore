package com.gtl.enhancedcore.common.structure;

import com.gregtechceu.gtceu.api.machine.multiblock.PartAbility;
import com.gregtechceu.gtceu.api.pattern.BlockPattern;
import com.gregtechceu.gtceu.api.pattern.TraceabilityPredicate;
import com.gregtechceu.gtceu.api.pattern.predicates.SimplePredicate;
import com.gtl.enhancedcore.mixin.gtceu.BlockPatternAccessor;
import com.lowdragmc.lowdraglib.utils.BlockInfo;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;

/** Only the two elevator controllers reject maintenance parts, without changing shared patterns. */
public final class SpaceElevatorMaintenance {
    private SpaceElevatorMaintenance() {}

    public static boolean matches(ResourceLocation id) {
        return id != null && (id.toString().equals("gtceu:space_elevator")
                || id.toString().equals("gtladditions:space_elevator_mkii"));
    }

    public static BlockPattern forbidden(BlockPattern original) {
        var access = (BlockPatternAccessor) original;
        var matches = access.gtlEnhancedcore$getBlockMatches().clone();
        Map<SimplePredicate, SimplePredicate> parts = new IdentityHashMap<>();
        Map<TraceabilityPredicate, TraceabilityPredicate> rules = new IdentityHashMap<>();
        for (int z = 0; z < matches.length; z++) {
            matches[z] = matches[z].clone();
            for (int y = 0; y < matches[z].length; y++) {
                matches[z][y] = matches[z][y].clone();
                for (int x = 0; x < matches[z][y].length; x++) {
                    matches[z][y][x] = rules.computeIfAbsent(matches[z][y][x], rule -> {
                        var copy = new TraceabilityPredicate(rule);
                        copy.common.replaceAll(part -> parts.computeIfAbsent(part, SpaceElevatorMaintenance::withoutMaintenance));
                        copy.limited.replaceAll(part -> parts.computeIfAbsent(part, SpaceElevatorMaintenance::withoutMaintenance));
                        copy.common.removeIf(part -> part == null);
                        copy.limited.removeIf(part -> part == null);
                        return copy;
                    });
                }
            }
        }
        return new BlockPattern(matches, original.structureDir, original.aisleRepetitions,
                access.enhanced$getCenterOffset().clone());
    }

    private static SimplePredicate withoutMaintenance(SimplePredicate part) {
        if (part.candidates == null) return part;
        var candidates = part.candidates.get();
        if (candidates == null || candidates.length == 0) return part;
        if (Arrays.stream(candidates).noneMatch(SpaceElevatorMaintenance::maintenance)) return part;
        var retained = Arrays.stream(candidates).filter(candidate -> !maintenance(candidate)).toArray(BlockInfo[]::new);
        if (retained.length == 0) return null;
        var copy = new SimplePredicate(part.type,
                state -> !PartAbility.MAINTENANCE.isApplicable(state.getBlockState().getBlock())
                        && part.predicate.test(state),
                () -> Arrays.stream(part.candidates.get()).filter(candidate -> !maintenance(candidate)).toArray(BlockInfo[]::new));
        copy.minCount = part.minCount;
        copy.maxCount = part.maxCount;
        copy.minLayerCount = part.minLayerCount;
        copy.maxLayerCount = part.maxLayerCount;
        copy.previewCount = part.previewCount;
        copy.toolTips = part.toolTips == null ? null : new java.util.ArrayList<>(part.toolTips);
        copy.disableRenderFormed = part.disableRenderFormed;
        copy.io = part.io;
        copy.slotName = part.slotName;
        copy.nbtParser = part.nbtParser;
        return copy;
    }

    private static boolean maintenance(BlockInfo candidate) {
        return candidate != null && PartAbility.MAINTENANCE.isApplicable(candidate.getBlockState().getBlock());
    }
}
