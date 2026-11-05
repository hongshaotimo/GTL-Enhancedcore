package com.gtl.enhancedcore.client.preview;

import appeng.api.stacks.AEItemKey;
import com.gregtechceu.gtceu.api.pattern.TraceabilityPredicate;
import java.util.Map;
import net.minecraft.core.BlockPos;

public interface PreviewMaterialAccess {
    Map<BlockPos, AEItemKey> enhanced$materials();
    Map<BlockPos, TraceabilityPredicate> enhanced$predicates();
    BlockPos enhanced$controllerPosition();

    interface Interaction {
        PreviewMaterialRows.Selection enhanced$materialSelection();
        void enhanced$refreshVisibility();
    }
}
