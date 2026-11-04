package com.gtl.enhancedcore.mixin.gtceu;

import com.gtl.enhancedcore.common.performance.structure.StructureSearchScheduler;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Observes changes before GTLCore dispatches formed-machine checks; never cancels those checks. */
@Mixin(Level.class)
public abstract class StructureSearchWakeMixin {
    @Inject(method = "markAndNotifyBlock", at = @At("HEAD"), remap = false, require = 1)
    private void enhancedcore$wakeUnfinishedStructure(BlockPos pos, LevelChunk chunk, BlockState oldState,
            BlockState state, int flags, int recursionLeft, CallbackInfo ci) {
        if ((Object) this instanceof ServerLevel level && oldState != state) {
            StructureSearchScheduler.watches(level).blockChanged(pos.getX(), pos.getY(), pos.getZ());
        }
    }
}
