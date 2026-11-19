package com.gtl.enhancedcore.client.renderer.machine;

import com.gtl.enhancedcore.client.renderer.celestial.CelestialVfxState;
import com.gtladd.gtladditions.client.render.machine.deferred.DeferredMachineRenderer;
import com.gtladd.gtladditions.client.render.machine.deferred.DeferredRenderContext;
import com.gtladd.gtladditions.client.render.machine.deferred.DeferredRenderEntry;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.List;
import kotlin.Unit;
import com.gtl.enhancedcore.client.renderer.MachineShaders;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.level.block.entity.BlockEntity;

/** A custom field belongs on the final scene colour, not in a shader pack's geometry or shadow buffer. */
interface MachineEffectPass<T> {
    void drawEffect(BlockEntity entity, T machine, float partial, PoseStack pose, MultiBufferSource buffers);

    default boolean additiveBatch() { return false; }

    default void renderEffect(BlockEntity entity, T machine, float partial, PoseStack pose, MultiBufferSource buffers) {
        // World draws use the same isolated queue on both sides of an Oculus toggle.
        // Only preview scenes own their immediate pose and buffer.
        if (entity.getLevel() != Minecraft.getInstance().level) {
            drawEffect(entity, machine, partial, pose, buffers);
        } else if (!MachineShaders.shadow()) {
            DeferredMachineRenderer.INSTANCE.enqueue$gtladditions(new Entry<>(entity, machine, partial, this));
        }
    }

    record Entry<T>(BlockEntity entity, T machine, float partial, MachineEffectPass<T> effect) implements DeferredRenderEntry {
        private static final MultiBufferSource.BufferSource BUFFERS = MultiBufferSource.immediate(new BufferBuilder(8192));
        @Override public BlockEntity getBlockEntity() { return entity; }
        @Override public int getRenderOrder() { return 2; }
        @Override public Object getBatchKey() { return effect.additiveBatch() ? effect : null; }
        @Override public boolean isVisible(DeferredRenderContext ctx) {
            return !entity.isRemoved() && entity.getLevel() == Minecraft.getInstance().level;
        }
        @Override public void render(DeferredRenderContext ctx) { flush(ctx, null); }
        @Override public void renderBatch(DeferredRenderContext ctx, List<? extends DeferredRenderEntry> entries) {
            flush(ctx, entries);
        }
        private void flush(DeferredRenderContext ctx, List<? extends DeferredRenderEntry> entries) {
            try (var state = new CelestialVfxState()) {
                // The context's pose already includes the view. Vanilla buffered models also read ModelViewMat.
                PoseStack modelView = RenderSystem.getModelViewStack();
                modelView.pushPose();
                try {
                    modelView.setIdentity();
                    RenderSystem.applyModelViewMatrix();
                    if (entries == null) draw(ctx);
                    else for (var entry : entries) ((Entry<?>) entry).draw(ctx);
                    BUFFERS.endBatch(); // Flush only our buffer while ADD still owns the final target.
                } finally {
                    modelView.popPose();
                    RenderSystem.applyModelViewMatrix();
                }
            }
        }
        private void draw(DeferredRenderContext ctx) {
            ctx.renderRelativeToCamera(entity, pose -> {
                effect.drawEffect(entity, machine, partial, pose, BUFFERS);
                return Unit.INSTANCE;
            });
        }
    }
}
