package com.gtl.enhancedcore.client.renderer.celestial;

import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine;
import com.gtl.enhancedcore.client.renderer.EnhancedMachineScene;
import com.gtl.enhancedcore.client.renderer.MachineShaders;
import com.gtl.enhancedcore.common.structure.CelestialEffectAnchors;
import com.gtl.enhancedcore.common.structure.CelestialEffectSizing;
import com.gtladd.gtladditions.api.client.scene.PartBlend;
import com.gtladd.gtladditions.api.client.scene.PartBounds;
import com.gtladd.gtladditions.api.client.scene.PartRenderer;
import com.gtladd.gtladditions.api.client.scene.ScenePart;
import com.gtladd.gtladditions.api.client.scene.SceneRendering;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.joml.Matrix4f;

/** Submits Core's Eye of Harmony to the ADD world compositor. */
@OnlyIn(Dist.CLIENT)
public final class CelestialVfxDeferred {
    private static final PartRenderer<Entry> RENDERER = (context, parts) -> {
        var level = Minecraft.getInstance().level;
        for (var part : parts) {
            var entry = part.getPayload();
            if (entry.entity().isRemoved() || entry.entity().getLevel() != level) continue;
            // The Core BER already supplied view, chamber translation and scale.
            var pose = new PoseStack();
            pose.mulPoseMatrix(entry.pose());
            CelestialVfxRenderer.eye(entry.time(), pose, null);
        }
    };

    private CelestialVfxDeferred() {}

    // Keep the call signature used by the existing Core Mixin.
    public static void dispatch(BlockEntity entity, boolean astrograph, int count, long seed,
            float time, PoseStack pose) {
        if (astrograph || entity.getLevel() != Minecraft.getInstance().level || MachineShaders.shadow()) return;
        var frame = SceneRendering.frame();
        if (frame == null) return;
        var machine = (MultiblockControllerMachine) ((IMachineBlockEntity) entity).getMetaMachine();
        var center = Vec3.atLowerCornerOf(entity.getBlockPos()).add(CelestialEffectAnchors.eye(machine));
        frame.submit(ScenePart.builder(EnhancedMachineScene.eye(entity.getBlockPos()), RENDERER,
                        new Entry(entity, time, new Matrix4f(pose.last().pose())))
                .blend(PartBlend.STRAIGHT)
                .bounds(PartBounds.sphere(center.x, center.y, center.z, 16 * CelestialEffectSizing.EYE_SCALE))
                .build());
    }

    private record Entry(BlockEntity entity, float time, Matrix4f pose) {}
}
