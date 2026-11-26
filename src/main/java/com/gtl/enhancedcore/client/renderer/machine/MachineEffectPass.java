package com.gtl.enhancedcore.client.renderer.machine;

import com.gtl.enhancedcore.client.renderer.AddSceneEffects;
import com.gtl.enhancedcore.client.renderer.MachineShaders;
import com.gtl.enhancedcore.client.renderer.celestial.CelestialVfxState;
import com.gtladd.gtladditions.client.render.machine.deferred.DeferredMachineRenderer;
import com.gtladd.gtladditions.client.render.machine.deferred.DeferredRenderContext;
import com.gtladd.gtladditions.client.render.machine.deferred.DeferredRenderEntry;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.List;
import java.util.function.Consumer;
import kotlin.Unit;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/** A custom field belongs on the final scene colour, not in a shader pack's geometry or shadow buffer. */
interface MachineEffectPass<T> {
    void drawEffect(BlockEntity entity, T machine, float partial, PoseStack pose, MultiBufferSource buffers);

    default boolean additiveBatch() { return false; }

    int getViewDistance();

    default void renderEffect(BlockEntity entity, T machine, float partial, PoseStack pose, MultiBufferSource buffers) {
        // World draws use the same isolated queue on both sides of an Oculus toggle.
        // Only preview scenes own their immediate pose and buffer.
        if (entity.getLevel() != Minecraft.getInstance().level) {
            drawEffect(entity, machine, partial, pose, buffers);
        } else if (!MachineShaders.shadow()) {
            if (AddSceneEffects.available()) {
                var position = new Matrix4f(pose.last().pose());
                var normal = new Matrix3f(pose.last().normal());
                AddSceneEffects.submit(entity, 0, getViewDistance(), () -> {
                    var captured = new PoseStack();
                    captured.last().pose().set(position);
                    captured.last().normal().set(normal);
                    WorldBuffers.flush(world -> drawEffect(entity, machine, partial, captured, world));
                });
            } else {
                Legacy.enqueue(entity, machine, partial, this);
            }
        }
    }

    final class WorldBuffers {
        private static final MultiBufferSource.BufferSource BUFFERS = MultiBufferSource.immediate(new BufferBuilder(8192));

        private static void flush(Consumer<MultiBufferSource> draw) {
            try (var state = new CelestialVfxState()) {
                // Both deferred paths supply a pose with the view already applied.
                PoseStack modelView = RenderSystem.getModelViewStack();
                modelView.pushPose();
                try {
                    modelView.setIdentity();
                    RenderSystem.applyModelViewMatrix();
                    draw.accept(BUFFERS);
                    BUFFERS.endBatch();
                } finally {
                    modelView.popPose();
                    RenderSystem.applyModelViewMatrix();
                }
            }
        }
    }

    final class Legacy {
        private static <T> void enqueue(BlockEntity entity, T machine, float partial, MachineEffectPass<T> effect) {
            DeferredMachineRenderer.INSTANCE.enqueue$gtladditions(new Entry<>(entity, machine, partial, effect));
        }

        private record Entry<T>(BlockEntity entity, T machine, float partial, MachineEffectPass<T> effect) implements DeferredRenderEntry {
            @Override public BlockEntity getBlockEntity() { return entity; }
            @Override public int getRenderOrder() { return 2; }
            @Override public Object getBatchKey() { return effect.additiveBatch() ? effect : null; }
            @Override public boolean isVisible(DeferredRenderContext ctx) {
                return !entity.isRemoved() && entity.getLevel() == Minecraft.getInstance().level;
            }
            @Override public void render(DeferredRenderContext ctx) {
                WorldBuffers.flush(buffers -> draw(ctx, buffers));
            }
            @Override public void renderBatch(DeferredRenderContext ctx, List<? extends DeferredRenderEntry> entries) {
                WorldBuffers.flush(buffers -> {
                    for (var entry : entries) ((Entry<?>) entry).draw(ctx, buffers);
                });
            }
            private void draw(DeferredRenderContext ctx, MultiBufferSource buffers) {
                ctx.renderRelativeToCamera(entity, pose -> {
                    effect.drawEffect(entity, machine, partial, pose, buffers);
                    return Unit.INSTANCE;
                });
            }
        }
    }
}
