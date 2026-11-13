package com.gtl.enhancedcore.mixin.gtladditions.client;

import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine;
import com.gtl.enhancedcore.common.structure.CelestialEffectAnchors;
import com.gtl.enhancedcore.common.structure.CelestialEffectSizing;
import com.gtl.enhancedcore.client.renderer.celestial.CelestialVfxDeferred;
import com.gtl.enhancedcore.mixin.gtceu.BlockPatternAccessor;
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
import org.joml.Matrix4f;
import org.joml.Vector4f;

@Mixin(targets = "com.gtladd.gtladditions.client.render.machine.ArcanicAstrographRender", remap = false)
public abstract class ArcanicAstrographEffectAnchorMixin {
    @Inject(method = "renderStar", at = @At("HEAD"), cancellable = true, require = 1, allow = 1)
    private void enhanced$astralDetail(PoseStack pose, float time, MultiBufferSource buffers, CallbackInfo ci) {
        ci.cancel();
    }

    @WrapOperation(method = "render", at = @At(value = "INVOKE", target =
            "Lcom/gtladd/gtladditions/client/render/machine/ArcanicAstrographRender;renderOrbitObjects(Lcom/mojang/blaze3d/vertex/PoseStack;FLnet/minecraft/client/renderer/MultiBufferSource;IJ)V"),
            require = 1, allow = 1)
    private void enhanced$astralWorlds(@Coerce Object renderer, PoseStack transformedPose, float time,
            MultiBufferSource originalBuffers, int count, long seed, Operation<Void> original,
            BlockEntity entity, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        CelestialVfxDeferred.dispatch(entity, true, count, seed, time, transformedPose);
    }

    @Inject(method = "renderOuterSpaceShell", at = @At("HEAD"), cancellable = true, require = 1, allow = 1)
    private void enhanced$openAstralChamber(PoseStack pose, MultiBufferSource buffers, CallbackInfo ci) {
        ci.cancel();
    }

    @WrapOperation(method = "render", at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/vertex/PoseStack;m_85837_(DDD)V", ordinal = 0, remap = false),
            require = 1, allow = 1)
    private void enhanced$blueprintCenter(PoseStack translatedPose, double x, double y, double z,
            Operation<Void> original, BlockEntity entity, float partialTick,
            PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        var machine = (MultiblockControllerMachine) ((IMachineBlockEntity) entity).getMetaMachine();
        var center = CelestialEffectAnchors.astrograph(machine);
        original.call(translatedPose, center.x, center.y, center.z);
        translatedPose.scale(CelestialEffectSizing.ASTROGRAPH_SCALE, CelestialEffectSizing.ASTROGRAPH_SCALE,
                CelestialEffectSizing.ASTROGRAPH_SCALE);
        // Unlike a spherical star, the new outer hoops must follow the complete blueprint frame.
        var pattern = (BlockPatternAccessor) machine.getPattern();
        var right = pattern.enhanced$chamberOffset(1, 0, 0, machine.getFrontFacing(), machine.getUpwardsFacing(), machine.isFlipped());
        var up = pattern.enhanced$chamberOffset(0, 1, 0, machine.getFrontFacing(), machine.getUpwardsFacing(), machine.isFlipped());
        var depth = pattern.enhanced$chamberOffset(0, 0, 1, machine.getFrontFacing(), machine.getUpwardsFacing(), machine.isFlipped());
        translatedPose.mulPoseMatrix(new Matrix4f()
                .setColumn(0, new Vector4f(right.getX(), right.getY(), right.getZ(), 0))
                .setColumn(1, new Vector4f(up.getX(), up.getY(), up.getZ(), 0))
                .setColumn(2, new Vector4f(depth.getX(), depth.getY(), depth.getZ(), 0)));
    }

}
