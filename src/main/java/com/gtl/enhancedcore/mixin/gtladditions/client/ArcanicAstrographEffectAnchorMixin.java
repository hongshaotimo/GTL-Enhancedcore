package com.gtl.enhancedcore.mixin.gtladditions.client;

import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine;
import com.gtl.enhancedcore.common.structure.CelestialEffectAnchors;
import com.gtl.enhancedcore.common.structure.CelestialEffectSizing;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Mixin(targets = "com.gtladd.gtladditions.client.render.machine.ArcanicAstrographRender", remap = false)
public abstract class ArcanicAstrographEffectAnchorMixin {
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
    }

    @WrapOperation(method = "renderStar", at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/vertex/PoseStack;m_85841_(FFF)V", remap = false),
            require = 1, allow = 1)
    private void enhanced$largerStar(PoseStack pose, float x, float y, float z, Operation<Void> original) {
        original.call(pose, x * CelestialEffectSizing.STAR_SCALE, y * CelestialEffectSizing.STAR_SCALE,
                z * CelestialEffectSizing.STAR_SCALE);
    }

    @WrapOperation(method = "renderOrbitObjects", at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/vertex/PoseStack;m_85841_(FFF)V", remap = false),
            require = 1, allow = 1)
    private void enhanced$largerPlanets(PoseStack pose, float x, float y, float z, Operation<Void> original) {
        original.call(pose, x * CelestialEffectSizing.ASTROGRAPH_PLANET_SCALE,
                y * CelestialEffectSizing.ASTROGRAPH_PLANET_SCALE, z * CelestialEffectSizing.ASTROGRAPH_PLANET_SCALE);
    }

    @ModifyConstant(method = "<clinit>", constant = @Constant(doubleValue = 2.8D), require = 1, allow = 1)
    private static double enhanced$innerOrbit(double original) {
        return CelestialEffectSizing.ASTROGRAPH_INNER_ORBIT;
    }
}
