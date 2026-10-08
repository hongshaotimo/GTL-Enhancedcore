package com.gtl.enhancedcore.audit.mixin;

import com.gtl.enhancedcore.audit.ChunkUnloadDiagnostics;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Collection;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Forge 47.4.16 runtime SRG selectors, verified with javap against the deployed client jar.
 * Register in enhancedcore_audit.mixins.json "mixins", never the production mixin configuration.
 */
@Mixin(value = LevelChunk.class, remap = false)
public abstract class ChunkUnloadAuditMixin {
    @Unique
    private final ChunkUnloadDiagnostics audit$chunkUnload = ChunkUnloadDiagnostics.createIfEnabled();

    // Ordinals 0/1 are blockEntities; ordinal 2 traverses tickers and is deliberately untouched.
    @Redirect(method = "m_187957_()V", at = @At(value = "INVOKE",
            target = "Ljava/util/Collection;forEach(Ljava/util/function/Consumer;)V", ordinal = 0),
            require = 1, expect = 1, allow = 1)
    private void audit$onChunkUnloaded(Collection<BlockEntity> values, Consumer<BlockEntity> action) {
        audit$forEach(values, action, "onChunkUnloaded");
    }

    @Redirect(method = "m_187957_()V", at = @At(value = "INVOKE",
            target = "Ljava/util/Collection;forEach(Ljava/util/function/Consumer;)V", ordinal = 1),
            require = 1, expect = 1, allow = 1)
    private void audit$setRemoved(Collection<BlockEntity> values, Consumer<BlockEntity> action) {
        audit$forEach(values, action, "setRemoved");
    }

    @Unique
    private void audit$forEach(Collection<BlockEntity> values, Consumer<BlockEntity> action, String phase) {
        if (audit$chunkUnload == null) {
            values.forEach(action);
            return;
        }
        LevelChunk chunk = (LevelChunk) (Object) this;
        audit$chunkUnload.forEach(chunk.getBlockEntities(), values, action, phase,
                () -> chunk.getLevel().dimension().location() + ":" + chunk.getPos());
    }

    // getBlockEntity has two removes: ordinal 0 is blockEntities, ordinal 1 is pending NBT.
    @Redirect(method = "m_5685_(Lnet/minecraft/core/BlockPos;"
            + "Lnet/minecraft/world/level/chunk/LevelChunk$EntityCreationType;)"
            + "Lnet/minecraft/world/level/block/entity/BlockEntity;", at = @At(value = "INVOKE",
            target = "Ljava/util/Map;remove(Ljava/lang/Object;)Ljava/lang/Object;", ordinal = 0),
            require = 1, expect = 1, allow = 1)
    private Object audit$getRemoved(Map<Object, Object> map, Object key) {
        return audit$chunkUnload == null ? map.remove(key)
                : audit$chunkUnload.remove(map, key, "getBlockEntity.removed/m_5685_");
    }

    @Redirect(method = "m_142169_(Lnet/minecraft/world/level/block/entity/BlockEntity;)V",
            at = @At(value = "INVOKE",
                    target = "Ljava/util/Map;put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"),
            require = 1, expect = 1, allow = 1)
    private Object audit$put(Map<Object, Object> map, Object key, Object value) {
        return audit$chunkUnload == null ? map.put(key, value)
                : audit$chunkUnload.put(map, key, value, "setBlockEntity/m_142169_");
    }

    @Redirect(method = "m_8114_(Lnet/minecraft/core/BlockPos;)V", at = @At(value = "INVOKE",
            target = "Ljava/util/Map;remove(Ljava/lang/Object;)Ljava/lang/Object;"),
            require = 1, expect = 1, allow = 1)
    private Object audit$remove(Map<Object, Object> map, Object key) {
        return audit$chunkUnload == null ? map.remove(key)
                : audit$chunkUnload.remove(map, key, "removeBlockEntity/m_8114_");
    }

    // Ordinal 0 is blockEntities.clear, ordinal 1 is ticker cleanup.
    // Normal cleanup has no active phase; a nested clear is visible to its enclosing traversal.
    @Redirect(method = "m_187957_()V", at = @At(value = "INVOKE",
            target = "Ljava/util/Map;clear()V", ordinal = 0),
            require = 1, expect = 1, allow = 1)
    private void audit$clear(Map<?, ?> map) {
        if (audit$chunkUnload == null) map.clear();
        else audit$chunkUnload.clear(map, "clearAllBlockEntities/m_187957_");
    }
}
