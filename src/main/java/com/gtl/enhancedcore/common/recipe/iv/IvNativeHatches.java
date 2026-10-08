package com.gtl.enhancedcore.common.recipe.iv;

import com.gregtechceu.gtceu.api.machine.multiblock.PartAbility;
import com.gregtechceu.gtceu.api.pattern.TraceabilityPredicate;
import com.gregtechceu.gtceu.api.pattern.predicates.SimplePredicate;
import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.world.level.block.Block;

/** Retains the original energy, laser, maintenance, data and coil predicates and their limits. */
public final class IvNativeHatches {
    private final Map<TraceabilityPredicate, TraceabilityPredicate> changed = new IdentityHashMap<>();
    private final TraceabilityPredicate materialPorts = IvBufferRegistry.materialPorts(-1);

    public static boolean materials(Block block) {
        return PartAbility.IMPORT_ITEMS.isApplicable(block) || PartAbility.EXPORT_ITEMS.isApplicable(block)
                || PartAbility.IMPORT_FLUIDS.isApplicable(block) || PartAbility.EXPORT_FLUIDS.isApplicable(block);
    }
    private static boolean materials(SimplePredicate predicate) {
        if (predicate.candidates == null) return false;
        var candidates = predicate.candidates.get();
        if (candidates == null) return false;
        for (var candidate : candidates)
            if (candidate != null && materials(candidate.getBlockState().getBlock())) return true;
        return false;
    }
    public TraceabilityPredicate restrict(TraceabilityPredicate source) {
        return changed.computeIfAbsent(source, original -> {
            var result = new TraceabilityPredicate(original);
            boolean replaced = result.common.removeIf(IvNativeHatches::materials);
            replaced |= result.limited.removeIf(IvNativeHatches::materials);
            // Old material minima would require ordinary output ports even on existing super-only builds.
            // Replace only material rules; retain all non-material objects and their shared global quotas.
            return replaced ? result.or(materialPorts) : original;
        });
    }
}
