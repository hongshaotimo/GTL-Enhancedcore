package com.gtl.enhancedcore.common.structure;

import com.gregtechceu.gtceu.api.block.IMachineBlock;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gregtechceu.gtceu.api.machine.multiblock.part.MultiblockPartMachine;
import com.gregtechceu.gtceu.api.pattern.TraceabilityPredicate;
import com.gregtechceu.gtceu.api.pattern.predicates.SimplePredicate;
import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;

/** Candidate classification adapted with permission from dishanhai/gtl_shanhai-dishanhai,
 * ShanhaiChamberClassifier, revision 9f2ad31188c0b7f91676f7d5e177b55783c80808.
 * Inspect every predicate alternative, never only the casing selected by the preview. */
public final class ChamberPositions {
    private final Map<MachineDefinition, Boolean> definitions = new IdentityHashMap<>();
    private final Map<SimplePredicate, Boolean> simple = new IdentityHashMap<>();
    private final Map<TraceabilityPredicate, Boolean> predicates = new IdentityHashMap<>();
    public boolean isChamber(TraceabilityPredicate predicate) {
        if (predicate == null || predicate.isController || predicate.isAny()) return false;
        return predicates.computeIfAbsent(predicate, p -> p.common.stream().anyMatch(this::part)
                || p.limited.stream().anyMatch(this::part));
    }
    private boolean part(SimplePredicate predicate) {
        return simple.computeIfAbsent(predicate, p -> {
            if (p.candidates == null) return false;
            var candidates = p.candidates.get();
            if (candidates == null) return false;
            for (var info : candidates) {
                if (info == null || !(info.getBlockState().getBlock() instanceof IMachineBlock block)) continue;
                var definition = block.getDefinition();
                if (definition instanceof com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition) continue;
                if (definitions.computeIfAbsent(definition, d -> {
                    var entity = d.getBlockEntityType().create(BlockPos.ZERO, d.defaultBlockState());
                    return entity instanceof IMachineBlockEntity holder && holder.getMetaMachine() instanceof MultiblockPartMachine;
                })) return true;
            }
            return false;
        });
    }
}
