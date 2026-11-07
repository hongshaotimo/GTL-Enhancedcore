package com.gtl.enhancedcore.client.renderer.celestial;

import com.gtladd.gtladditions.client.render.machine.deferred.DeferredMachineRenderer;
import com.gtladd.gtladditions.client.render.machine.deferred.DeferredRenderContext;
import com.gtladd.gtladditions.client.render.machine.deferred.DeferredRenderEntry;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.joml.Matrix4f;

/** Uses ADD's verified after-translucent / after-shaderpack-final scheduling. */
@OnlyIn(Dist.CLIENT)
public final class CelestialVfxDeferred {
    private CelestialVfxDeferred() {}

    /**
     * Called once for this controller's complete celestial effect. The supplied
     * pose already contains the camera view, blueprint center and visual scale.
     * ADD owns the one-frame entry queue, keyed by the real controller position.
     */
    public static void dispatch(BlockEntity entity, boolean astrograph, int count, long seed,
            float time, PoseStack pose) {
        DeferredMachineRenderer.INSTANCE.enqueue$gtladditions(new Entry(entity, astrograph, count, seed,
                time, new Matrix4f(pose.last().pose())));
    }

    private record Entry(BlockEntity entity, boolean astrograph, int count, long seed,
            float time, Matrix4f poseMatrix) implements DeferredRenderEntry {
        @Override
        public BlockEntity getBlockEntity() { return entity; }

        @Override
        public int getRenderOrder() { return 2; }

        // Default null batch key keeps each controller's own view/seed/count.
        // ADD's queue key is block position, never a profile-wide shared identity.
        @Override
        public boolean isVisible(DeferredRenderContext context) {
            return !entity.isRemoved() && entity.getLevel() == Minecraft.getInstance().level;
        }

        @Override
        public void render(DeferredRenderContext context) {
            // The captured BER matrix is complete. Multiplying context's world
            // view again would apply the camera transformation twice.
            var pose = new PoseStack();
            pose.mulPoseMatrix(poseMatrix);
            if (astrograph) {
                CelestialVfxRenderer.astrograph(count, seed, time, pose, null);
            } else {
                CelestialVfxRenderer.eye(time, pose, null);
            }
        }
    }
}
