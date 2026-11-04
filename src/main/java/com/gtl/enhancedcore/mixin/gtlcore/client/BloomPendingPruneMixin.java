package com.gtl.enhancedcore.mixin.gtlcore.client;

import com.gtl.enhancedcore.client.performance.bloom.DeferredQueuePruner;
import java.util.PriorityQueue;
import java.util.function.Predicate;
import net.minecraft.client.multiplayer.ClientLevel;
import org.gtlcore.gtlcore.client.bloom.BloomConfig;
import org.gtlcore.gtlcore.config.BloomOptions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** GTLCore fix4: remove dead pending entries once per service/observation, not per chunk. */
@Mixin(targets = "org.gtlcore.gtlcore.client.bloom.SectionMeshes", remap = false)
public abstract class BloomPendingPruneMixin {
    @Shadow private PriorityQueue<Object> pending;
    @Shadow public abstract void clear();
    @Unique private final DeferredQueuePruner<Object> enhancedcore$pendingPruner = new DeferredQueuePruner<>();
    @Unique private boolean enhancedcore$offSuspended;

    @Inject(method = "setLevel(Lnet/minecraft/client/multiplayer/ClientLevel;)V",
            at = @At("HEAD"), cancellable = true, require = 1, allow = 1, remap = false)
    private void enhancedcore$suspendOffLevel(ClientLevel newLevel, CallbackInfo ci) {
        if (enhancedcore$suspendIfOff()) ci.cancel();
        else enhancedcore$offSuspended = false;
        // clear() drops the old level. The first enabled setLevel rediscovers the currently
        // loaded chunks, including every chunk received while producers were suspended.
    }

    @Inject(method = {
            "loadChunk(Lnet/minecraft/world/level/chunk/LevelChunk;)V", "unloadChunk(II)V",
            "dirty(III)V", "dirtyBlock(III)V", "dirtyArea(IIIIII)V", "rebuildAll()V"
    }, at = @At("HEAD"), cancellable = true, require = 6, allow = 6, remap = false)
    private void enhancedcore$stopOffProduction(CallbackInfo ci) {
        if (enhancedcore$suspendIfOff()) ci.cancel();
    }

    @Unique
    private boolean enhancedcore$suspendIfOff() {
        if (BloomConfig.mode() != BloomOptions.Mode.OFF) return false;
        if (!enhancedcore$offSuspended) {
            enhancedcore$offSuspended = true;
            // Cancel the active job, close meshes/uploads and release references once.
            // Merely suppressing pruning would retain the still-growing dirty queue.
            clear();
        }
        return true;
    }

    @Redirect(method = "unloadChunk(II)V",
            at = @At(value = "INVOKE", target = "Ljava/util/PriorityQueue;removeIf(Ljava/util/function/Predicate;)Z"),
            require = 1, allow = 1, remap = false)
    private boolean enhancedcore$deferPrune(PriorityQueue<Object> queue, Predicate<Object> predicate) {
        enhancedcore$pendingPruner.defer(queue, predicate);
        // Upstream discards this boolean. Mesh disposal and active-job cancellation stay immediate.
        return false;
    }

    @Inject(method = "buildSome(Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/client/renderer/culling/Frustum;)V",
            at = @At("HEAD"), require = 1, allow = 1, remap = false)
    private void enhancedcore$pruneBeforeService(CallbackInfo ci) {
        enhancedcore$pendingPruner.flush(pending);
    }

    @Inject(method = "setLevel(Lnet/minecraft/client/multiplayer/ClientLevel;)V",
            at = @At("RETURN"), require = 2, allow = 2, remap = false)
    private void enhancedcore$pruneAtFrameBoundary(CallbackInfo ci) {
        // BloomClient.beginFrame calls setLevel every initialized world-render frame before active().
        // Cover its same-level early return too: shader suppression can skip buildSome
        // indefinitely while chunk events continue to enqueue and invalidate entries.
        enhancedcore$pendingPruner.flush(pending);
    }

    @Inject(method = "pendingCount()I", at = @At("HEAD"), require = 1, allow = 1, remap = false)
    private void enhancedcore$pruneBeforeCount(CallbackInfoReturnable<Integer> cir) {
        enhancedcore$pendingPruner.flush(pending);
    }

    @Inject(method = "clear()V", at = @At("HEAD"), require = 1, allow = 1, remap = false)
    private void enhancedcore$resetPrune(CallbackInfo ci) {
        // clear already releases all entries and clears the queue; do not sweep it again.
        enhancedcore$pendingPruner.reset();
    }
}
