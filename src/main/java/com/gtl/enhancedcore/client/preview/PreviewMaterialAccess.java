package com.gtl.enhancedcore.client.preview;

import appeng.api.stacks.AEItemKey;
import com.gregtechceu.gtceu.api.pattern.TraceabilityPredicate;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;

public interface PreviewMaterialAccess {
    Map<BlockPos, AEItemKey> enhanced$materials();
    Map<BlockPos, TraceabilityPredicate> enhanced$predicates();
    BlockPos enhanced$controllerPosition();
    Set<AEItemKey> enhanced$candidates(TraceabilityPredicate predicate);
    Set<BlockPos> enhanced$replaceablePositions();
    void enhanced$cacheMaterials(Map<BlockPos, AEItemKey> materials,
            Map<TraceabilityPredicate, Set<AEItemKey>> candidates, Set<BlockPos> replaceable);

    interface Rows {
        void enhanced$layoutRows(int width, int height);
        void enhanced$restoreRows();
    }

    interface Interaction {
        PreviewMaterialRows.Selection enhanced$materialSelection();
        void enhanced$refreshVisibility();
    }
}
