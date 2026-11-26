package com.gtl.enhancedcore.client.renderer.celestial;

import com.gtl.enhancedcore.client.renderer.AddSceneEffects;
import com.gtl.enhancedcore.client.renderer.MachineShaders;
import com.gtladd.gtladditions.client.render.machine.deferred.DeferredMachineRenderer;
import com.gtladd.gtladditions.client.render.machine.deferred.DeferredRenderContext;
import com.gtladd.gtladditions.client.render.machine.deferred.DeferredRenderEntry;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.joml.Matrix4f;

/** Queues the effect with the installed ADD scene renderer. */
@OnlyIn(Dist.CLIENT)
public final class CelestialVfxDeferred {
    private CelestialVfxDeferred() {}

    /**
     * Called once for this controller's complete celestial effect. The supplied
     * pose already contains the camera view, blueprint center and visual scale.
     * ADD owns the frame queue, keyed by the real controller position.
     */
    public static void dispatch(BlockEntity entity, boolean astrograph, int count, long seed,
            float time, PoseStack pose) {
        if (MachineShaders.shadow()) return;
        var matrix = new Matrix4f(pose.last().pose());
        if (AddSceneEffects.available()) {
            AddSceneEffects.submit(entity, 1, 512, () -> render(astrograph, count, seed, time, matrix));
        } else {
            Legacy.enqueue(entity, astrograph, count, seed, time, matrix);
        }
    }

    private static void render(boolean astrograph, int count, long seed, float time, Matrix4f poseMatrix) {
        // The captured pose already includes the camera view.
        var pose = new PoseStack();
        pose.mulPoseMatrix(poseMatrix);
        if (astrograph) {
            CelestialVfxRenderer.astrograph(count, seed, time, pose, null);
        } else {
            CelestialVfxRenderer.eye(time, pose, null);
        }
    }

    private static final class Legacy {
        private static void enqueue(BlockEntity entity, boolean astrograph, int count, long seed, float time, Matrix4f matrix) {
            DeferredMachineRenderer.INSTANCE.enqueue$gtladditions(new Entry(entity, astrograph, count, seed, time, matrix));
        }

        private record Entry(BlockEntity entity, boolean astrograph, int count, long seed,
                float time, Matrix4f poseMatrix) implements DeferredRenderEntry {
            @Override public BlockEntity getBlockEntity() { return entity; }
            @Override public int getRenderOrder() { return 2; }
            @Override public boolean isVisible(DeferredRenderContext context) {
                return !entity.isRemoved() && entity.getLevel() == Minecraft.getInstance().level;
            }
            @Override public void render(DeferredRenderContext context) {
                CelestialVfxDeferred.render(astrograph, count, seed, time, poseMatrix);
            }
        }
    }
}
