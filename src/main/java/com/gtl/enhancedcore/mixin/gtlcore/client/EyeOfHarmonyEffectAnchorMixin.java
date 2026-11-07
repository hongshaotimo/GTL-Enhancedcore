package com.gtl.enhancedcore.mixin.gtlcore.client;

import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine;
import com.gtl.enhancedcore.common.structure.CelestialEffectAnchors;
import com.gtl.enhancedcore.common.structure.CelestialEffectSizing;
import com.gtl.enhancedcore.client.renderer.celestial.CelestialVfxDeferred;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "org.gtlcore.gtlcore.client.renderer.machine.EyeOfHarmonyRenderer", remap = false)
public abstract class EyeOfHarmonyEffectAnchorMixin {
    @Inject(method = "renderStar", at = @At("HEAD"), cancellable = true, require = 1, allow = 1)
    private static void enhanced$celestialDetail(float time, PoseStack pose, MultiBufferSource buffers, CallbackInfo ci) {
        ci.cancel();
    }

    @WrapOperation(method = "render", at = @At(value = "INVOKE", target =
            "Lorg/gtlcore/gtlcore/client/renderer/machine/EyeOfHarmonyRenderer;renderOrbitObjects(FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;DDD)V"),
            require = 1, allow = 1)
    private void enhanced$dimensionWorlds(@Coerce Object renderer, float time, PoseStack transformedPose,
            MultiBufferSource originalBuffers, double x, double y, double z, Operation<Void> original,
            BlockEntity entity, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        CelestialVfxDeferred.dispatch(entity, false, 3, 0, time, transformedPose);
    }

    @Inject(method = "renderOuterSpaceShell", at = @At("HEAD"), cancellable = true, require = 1, allow = 1)
    private void enhanced$openCelestialChamber(PoseStack pose, MultiBufferSource buffers, CallbackInfo ci) {
        ci.cancel();
    }

    // GTLCore fix4 ships this SRG call. WrapOperation does not emit its nested
    // vanilla target into our refmap when the third-party Mixin has remap=false.
    @WrapOperation(method = "render", at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/vertex/PoseStack;m_85837_(DDD)V", ordinal = 0, remap = false),
            require = 1, allow = 1)
    private void enhanced$blueprintCenter(PoseStack translatedPose, double x, double y, double z,
            Operation<Void> original, BlockEntity entity, float partialTick,
            PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        var machine = (MultiblockControllerMachine) ((IMachineBlockEntity) entity).getMetaMachine();
        var center = CelestialEffectAnchors.eye(machine);
        original.call(translatedPose, center.x, center.y, center.z);
        translatedPose.scale(CelestialEffectSizing.EYE_SCALE, CelestialEffectSizing.EYE_SCALE,
                CelestialEffectSizing.EYE_SCALE);
    }

}
