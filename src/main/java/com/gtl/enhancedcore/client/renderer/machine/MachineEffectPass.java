package com.gtl.enhancedcore.client.renderer.machine;

import com.gtl.enhancedcore.client.renderer.EnhancedMachineScene;
import com.gtl.enhancedcore.client.renderer.MachineShaders;
import com.gtl.enhancedcore.client.renderer.celestial.CelestialVfxState;
import com.gtladd.gtladditions.api.client.scene.PartBlend;
import com.gtladd.gtladditions.api.client.scene.PartBounds;
import com.gtladd.gtladditions.api.client.scene.PartRenderer;
import com.gtladd.gtladditions.api.client.scene.ScenePart;
import com.gtladd.gtladditions.api.client.scene.SceneRendering;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.level.block.entity.BlockEntity;

/** World effects join ADD's composition; previews keep their own pose and buffers. */
interface MachineEffectPass<T> {
    void drawEffect(BlockEntity entity, T machine, float partial, PoseStack pose, MultiBufferSource buffers);
    PartBounds effectBounds(BlockEntity entity, T machine);

    default PartBlend effectBlend() { return PartBlend.STRAIGHT; }
    default boolean additiveBatch() { return false; }

    default void renderEffect(BlockEntity entity, T machine, float partial, PoseStack pose, MultiBufferSource buffers) {
        if (entity.getLevel() != Minecraft.getInstance().level) {
            drawEffect(entity, machine, partial, pose, buffers);
            return;
        }
        if (MachineShaders.shadow()) return;
        var frame = SceneRendering.frame();
        if (frame == null) return;
        var part = ScenePart.builder(EnhancedMachineScene.machine(entity.getBlockPos()), Composition.RENDERER,
                new Entry<>(entity, machine, partial, this))
                .blend(effectBlend()).bounds(effectBounds(entity, machine));
        if (additiveBatch()) part.batchKey(this);
        frame.submit(part.build());
    }

    static PartBounds sphere(BlockEntity entity, double[] offset, double radius) {
        var pos = entity.getBlockPos();
        return PartBounds.sphere(pos.getX() + .5 + offset[0], pos.getY() + .5 + offset[1],
                pos.getZ() + .5 + offset[2], radius);
    }

    record Entry<T>(BlockEntity entity, T machine, float partial, MachineEffectPass<T> effect) {
        private void draw(PoseStack pose, MultiBufferSource buffers) {
            effect.drawEffect(entity, machine, partial, pose, buffers);
        }
    }

    final class Composition {
        private static final MultiBufferSource.BufferSource BUFFERS = MultiBufferSource.immediate(new BufferBuilder(8192));
        private static final PartRenderer<Entry<?>> RENDERER = (context, parts) -> {
            var frame = context.getFrame();
            PoseStack pose = context.getPoseStack();
            var level = Minecraft.getInstance().level;
            try (var state = new CelestialVfxState()) {
                for (var part : parts) {
                    var entry = part.getPayload();
                    var entity = entry.entity();
                    if (entity.isRemoved() || entity.getLevel() != level) continue;
                    var pos = entity.getBlockPos();
                    pose.pushPose();
                    try {
                        // ADD supplies the camera view and an identity RenderSystem model-view.
                        pose.translate(pos.getX() - frame.getCameraX(), pos.getY() - frame.getCameraY(),
                                pos.getZ() - frame.getCameraZ());
                        entry.draw(pose, BUFFERS);
                    } finally {
                        pose.popPose();
                    }
                }
                BUFFERS.endBatch();
            }
        };

        private Composition() {}
    }
}
