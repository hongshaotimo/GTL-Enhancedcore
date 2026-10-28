package com.gtl.enhancedcore.client.preview;

import com.gregtechceu.gtceu.api.pattern.MultiblockState;

/** Implemented only on GTLCore's private client preview data object. */
public interface PreviewPatternMemory {
    void enhanced$releasePreviewMemory();
    java.util.List<net.minecraft.core.BlockPos> enhanced$chamberPositions();

    /** Call only after verifying that the controller belongs to a preview dummy world. */
    static void releaseState(MultiblockState state) {
        if (state == null) return;
        state.getMatchContext().reset();
        // GTCEu initializes these three collections only when pattern checking starts.
        // A preview may be closed before that, or be cleared more than once.
        var cache = state.cache;
        if (cache != null) {
            cache.clear();
            cache.trim();
        }
        var globalCount = state.getGlobalCount();
        if (globalCount != null) globalCount.clear();
        var layerCount = state.getLayerCount();
        if (layerCount != null) layerCount.clear();
    }
}
