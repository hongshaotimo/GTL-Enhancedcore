package com.gtl.enhancedcore.mixin.gtceu;

import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController;
import com.gregtechceu.gtceu.api.pattern.MultiblockState;
import com.gregtechceu.gtceu.api.pattern.MultiblockWorldSavedData;
import com.gtl.enhancedcore.common.performance.structure.StructureChunkMembership;
import com.gtl.enhancedcore.common.performance.structure.StructureSearchAccess;
import com.gtl.enhancedcore.common.performance.structure.StructureSearchWatches;
import com.gtl.enhancedcore.common.performance.structure.StructureWorldAccess;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = MultiblockWorldSavedData.class, remap = false, priority = 900)
public abstract class StructureWorldIndexMixin implements StructureWorldAccess {
    @Shadow public Map<BlockPos, MultiblockState> mapping;
    @Shadow public Map<ChunkPos, Set<MultiblockState>> chunkPosMapping;
    @Shadow @Final private CopyOnWriteArrayList<IMultiController> controllers;
    @Shadow public abstract void createExecutorService();
    @Shadow public abstract void releaseExecutorService();
    @Unique private final StructureChunkMembership<MultiblockState> enhancedcore$memberships =
            new StructureChunkMembership<>();
    @Unique private final StructureSearchWatches enhancedcore$watches = new StructureSearchWatches();

    @Override public StructureSearchWatches enhancedcore$searchWatches() { return enhancedcore$watches; }

    @Inject(method = "addMapping", at = @At("HEAD"), cancellable = true, require = 1)
    private void enhancedcore$indexUniqueChunks(MultiblockState state, CallbackInfo ci) {
        MultiblockState previous = mapping.put(state.controllerPos, state);
        if (previous != null && previous != state) enhancedcore$removeChunkMembership(previous);
        enhancedcore$removeChunkMembership(state);
        LongOpenHashSet chunks = enhancedcore$memberships.add(state, state.cache.iterator());
        for (var iterator = chunks.iterator(); iterator.hasNext();) {
            ChunkPos chunk = new ChunkPos(iterator.nextLong());
            chunkPosMapping.computeIfAbsent(chunk, ignored -> ConcurrentHashMap.newKeySet()).add(state);
        }
        ci.cancel();
    }

    @Inject(method = "removeMapping", at = @At("HEAD"), cancellable = true, require = 1)
    private void enhancedcore$removeOnlyIndexedChunks(MultiblockState state, CallbackInfo ci) {
        mapping.remove(state.controllerPos, state);
        enhancedcore$removeChunkMembership(state);
        ci.cancel();
    }

    @Unique private void enhancedcore$removeChunkMembership(MultiblockState state) {
        LongOpenHashSet chunks = enhancedcore$memberships.remove(state);
        if (chunks == null) return;
        for (var iterator = chunks.iterator(); iterator.hasNext();) {
            ChunkPos chunk = new ChunkPos(iterator.nextLong());
            Set<MultiblockState> members = chunkPosMapping.get(chunk);
            members.remove(state);
            if (members.isEmpty()) chunkPosMapping.remove(chunk, members);
        }
    }

    @Inject(method = "addAsyncLogic", at = @At("HEAD"), cancellable = true, require = 1)
    private void enhancedcore$uniqueSearch(IMultiController controller, CallbackInfo ci) {
        if (controller instanceof StructureSearchAccess access) access.enhancedcore$searchBackoff().wake();
        synchronized (controllers) {
            boolean present = false;
            for (var existing : controllers) if (existing == controller) { present = true; break; }
            if (!present) controllers.add(controller);
            createExecutorService();
        }
        ci.cancel();
    }

    @Inject(method = "removeAsyncLogic", at = @At("HEAD"), cancellable = true, require = 1)
    private void enhancedcore$removeDuplicateSearches(IMultiController controller, CallbackInfo ci) {
        synchronized (controllers) {
            controllers.removeIf(existing -> existing == controller);
            if (controllers.isEmpty()) releaseExecutorService();
        }
        if (controller instanceof StructureSearchAccess access) {
            access.enhancedcore$searchBackoff().wake();
            enhancedcore$watches.remove(access.enhancedcore$searchBackoff());
        }
        ci.cancel();
    }
}
