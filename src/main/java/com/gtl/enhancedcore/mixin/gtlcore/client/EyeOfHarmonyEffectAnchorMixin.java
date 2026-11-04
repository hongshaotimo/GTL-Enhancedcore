package com.gtl.enhancedcore.mixin.gtlcore.client;

import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine;
import com.gtl.enhancedcore.common.structure.CelestialEffectAnchors;
import com.gtl.enhancedcore.common.structure.CelestialEffectSizing;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.gtlcore.gtlcore.client.renderer.machine.EyeOfHarmonyRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(targets = "org.gtlcore.gtlcore.client.renderer.machine.EyeOfHarmonyRenderer", remap = false)
public abstract class EyeOfHarmonyEffectAnchorMixin {
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

    @WrapOperation(method = "renderStar", at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/vertex/PoseStack;m_85841_(FFF)V", remap = false),
            require = 1, allow = 1)
    private static void enhanced$largerStar(PoseStack pose, float x, float y, float z,
            Operation<Void> original) {
        original.call(pose, x * CelestialEffectSizing.STAR_SCALE, y * CelestialEffectSizing.STAR_SCALE,
                z * CelestialEffectSizing.STAR_SCALE);
    }

    @WrapOperation(method = "render", at = @At(value = "INVOKE", target =
            "Lorg/gtlcore/gtlcore/client/renderer/machine/EyeOfHarmonyRenderer;renderOrbitObjects(FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;DDD)V"),
            require = 1, allow = 1)
    private void enhanced$concentricOrbits(EyeOfHarmonyRenderer renderer, float time, PoseStack pose,
            MultiBufferSource buffers, double x, double y, double z, Operation<Void> original) {
        pose.pushPose();
        pose.scale(CelestialEffectSizing.EYE_ORBIT_SCALE, CelestialEffectSizing.EYE_ORBIT_SCALE,
                CelestialEffectSizing.EYE_ORBIT_SCALE);
        try {
            // The parent pose is already at the blueprint center. Reusing the old
            // controller offset here makes the orbits drift away from their star.
            original.call(renderer, time, pose, buffers, 0D, 0D, 0D);
        } finally {
            pose.popPose();
        }
    }
}
