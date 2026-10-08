package com.gtl.enhancedcore.mixin.mc;

import com.gtl.enhancedcore.common.util.BlockEntityRemovalSnapshot;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Collection;
import java.util.List;
import java.util.Map;

@Mixin(LevelChunk.class)
public abstract class ChunkBlockEntityRemovalMixin {
    @Unique
    private List<BlockEntity> gtlEnhancedcore$unloadedEntities;

    @Redirect(method = "clearAllBlockEntities()V", at = @At(value = "INVOKE",
            target = "Ljava/util/Map;values()Ljava/util/Collection;", ordinal = 0, remap = false),
            require = 1, expect = 1, allow = 1)
    private Collection<BlockEntity> gtlEnhancedcore$unloadSnapshot(Map<?, BlockEntity> entities) {
        return BlockEntityRemovalSnapshot.forUnload(entities,
                unloaded -> gtlEnhancedcore$unloadedEntities = unloaded);
    }

    @Redirect(method = "clearAllBlockEntities()V", at = @At(value = "INVOKE",
            target = "Ljava/util/Map;values()Ljava/util/Collection;", ordinal = 1, remap = false),
            require = 1, expect = 1, allow = 1)
    private Collection<BlockEntity> gtlEnhancedcore$removalSnapshot(Map<?, BlockEntity> entities) {
        List<BlockEntity> unloaded = gtlEnhancedcore$unloadedEntities;
        gtlEnhancedcore$unloadedEntities = null;
        return BlockEntityRemovalSnapshot.forRemoval(entities, unloaded);
    }
}
