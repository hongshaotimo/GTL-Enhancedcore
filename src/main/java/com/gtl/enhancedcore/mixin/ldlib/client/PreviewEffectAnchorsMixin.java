package com.gtl.enhancedcore.mixin.ldlib.client;

import com.gtl.enhancedcore.client.preview.PreviewEffectAnchors;
import com.lowdragmc.lowdraglib.utils.BlockInfo;
import com.lowdragmc.lowdraglib.utils.TrackedDummyWorld;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = TrackedDummyWorld.class, remap = false)
public abstract class PreviewEffectAnchorsMixin {
    @Inject(method = "addBlock", at = @At("HEAD"))
    private void enhanced$index(BlockPos pos, BlockInfo info, CallbackInfo ci) {
        PreviewEffectAnchors.put((TrackedDummyWorld) (Object) this, pos, info);
    }

    @Inject(method = "m_6933_", at = @At("RETURN"))
    private void enhanced$replace(BlockPos pos, BlockState state, int flags, int recursion,
                                  CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ()) PreviewEffectAnchors.replace((TrackedDummyWorld) (Object) this, pos, state);
    }

    @Inject(method = "removeBlock", at = @At("HEAD"))
    private void enhanced$remove(BlockPos pos, CallbackInfoReturnable<BlockInfo> cir) {
        PreviewEffectAnchors.remove((TrackedDummyWorld) (Object) this, pos);
    }

    @Inject(method = "clear", at = @At("HEAD"))
    private void enhanced$clear(CallbackInfo ci) {
        PreviewEffectAnchors.clear((TrackedDummyWorld) (Object) this);
    }
}
