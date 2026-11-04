package com.gtl.enhancedcore.mixin.gtceu;

import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController;
import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine;
import com.gtl.enhancedcore.common.performance.structure.StructureSearchAccess;
import com.gtl.enhancedcore.common.performance.structure.StructureSearchBackoff;
import com.gtl.enhancedcore.common.performance.structure.StructureSearchScheduler;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Applies after GTLCore's concrete asyncCheckPattern merge. */
@Mixin(value = MultiblockControllerMachine.class, remap = false, priority = 900)
public abstract class StructureSearchControllerMixin implements StructureSearchAccess {
    @Unique private final StructureSearchBackoff enhancedcore$backoff = new StructureSearchBackoff();

    @Override public StructureSearchBackoff enhancedcore$searchBackoff() { return enhancedcore$backoff; }

    @Inject(method = "asyncCheckPattern", at = @At("HEAD"), cancellable = true, require = 1)
    private void enhancedcore$backoffSearch(long period, CallbackInfo ci) {
        StructureSearchScheduler.search((IMultiController) this, period);
        ci.cancel();
    }

    @Inject(method = "onLoad", at = @At("HEAD"), require = 1)
    private void enhancedcore$loadSearch(CallbackInfo ci) { enhancedcore$backoff.load(); }

    @Inject(method = "onUnload", at = @At("HEAD"), require = 1)
    private void enhancedcore$unloadSearch(CallbackInfo ci) { enhancedcore$backoff.unload(); }

    @Inject(method = "onPartUnload", at = @At("HEAD"), require = 1)
    private void enhancedcore$wakePart(CallbackInfo ci) { enhancedcore$backoff.wake(); }

    @Inject(method = "onStructureFormed", at = @At("RETURN"), require = 1)
    private void enhancedcore$formationCompleted(CallbackInfo ci) { enhancedcore$backoff.formationCompleted(); }

    @Inject(method = "onRotated", at = @At("HEAD"), require = 1)
    private void enhancedcore$wakeRotation(Direction oldFacing, Direction facing, CallbackInfo ci) {
        if (oldFacing != facing) enhancedcore$backoff.wake();
    }
}
