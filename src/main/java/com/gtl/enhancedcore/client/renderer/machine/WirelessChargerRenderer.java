package com.gtl.enhancedcore.client.renderer.machine;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.client.renderer.machine.TieredHullMachineRenderer;
import com.gtl.enhancedcore.GTLEnhancedcore;
import com.gtl.enhancedcore.common.machine.WirelessChargerMachine;
import com.lowdragmc.lowdraglib.client.renderer.impl.IModelRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import java.util.List;
import java.util.function.Consumer;

/** Top-coil induction field; cached light ribbons and soft core remain entirely client-side. */
@OnlyIn(Dist.CLIENT)
public final class WirelessChargerRenderer extends TieredHullMachineRenderer implements MachineEffectPass<WirelessChargerMachine> {
    private final boolean mv;
    private final ResourceLocation idleId;
    private final ResourceLocation activeId;
    private final IModelRenderer idle;
    private final IModelRenderer active;
    private final FrameMesh near;
    private final FrameMesh far;
    private final FieldWriter writer = new FieldWriter();
    private final Quaternionf orientation = new Quaternionf();

    public WirelessChargerRenderer(int tier) {
        super(tier, com.gregtechceu.gtceu.GTCEu.id("block/machine/hull_machine"));
        mv = tier == GTValues.MV;
        String path = "block/machine/" + (mv ? "mv" : "lv") + "_wireless_charger";
        idleId = GTLEnhancedcore.id(path);
        activeId = GTLEnhancedcore.id(path + "_active");
        idle = new IModelRenderer(idleId);
        active = new IModelRenderer(activeId);
        near = new FrameMesh(mv, false);
        far = new FrameMesh(mv, true);
    }

    @Override
    public void renderMachine(List<BakedQuad> quads, MachineDefinition definition, MetaMachine machine,
                              Direction front, Direction side, RandomSource random,
                              Direction modelFacing, ModelState rotation) {
        super.renderMachine(quads, definition, machine, front, side, random, modelFacing, rotation);
        IModelRenderer overlay = machine instanceof WirelessChargerMachine charger && charger.isActive() ? active : idle;
        quads.addAll(overlay.getRotatedModel(front).getQuads(definition.defaultBlockState(), side, random));
    }

    @Override
    public void onAdditionalModel(Consumer<ResourceLocation> registry) {
        super.onAdditionalModel(registry);
        registry.accept(idleId);
        registry.accept(activeId);
    }

    @Override
    public void render(BlockEntity entity, float partial, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        if (!(entity instanceof IMachineBlockEntity holder)
                || !(holder.getMetaMachine() instanceof WirelessChargerMachine charger)
                || entity.getLevel() == null) return;
        renderEffect(entity, charger, partial, pose, buffers);
    }

    @Override
    public void drawEffect(BlockEntity entity, WirelessChargerMachine charger, float partial,
                           PoseStack pose, MultiBufferSource buffers) {
        // Decorative field is always visible while loaded, independently of power and work state.
        var camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        double distance = camera.getPosition().distanceToSqr(entity.getBlockPos().getX() + 0.5,
                entity.getBlockPos().getY() + 1.02 + WirelessChargerField.coreCenterHeight(mv), entity.getBlockPos().getZ() + 0.5);
        double time = (double) entity.getLevel().getGameTime() + partial;
        WirelessChargerField.Mesh mesh = (distance > 32 * 32 ? far : near).at(time);
        VertexConsumer lightField = buffers.getBuffer(WirelessChargerRenderTypes.FIELD);
        pose.pushPose();
        pose.translate(0.5, 1.02, 0.5);
        pose.pushPose();
        // A stable orientation varies adjacent machines without retaining any machine/world objects.
        long seed = entity.getBlockPos().asLong();
        seed = (seed ^ (seed >>> 33)) * 0xff51afd7ed558ccdL;
        float angle = (float) ((seed >>> 40) * (Math.PI * 2 / 16777216.0));
        pose.mulPose(orientation.rotationY(angle));
        writer.matrix = pose.last().pose();
        writer.consumer = lightField;
        try { mesh.emit(writer); }
        finally { writer.matrix = null; writer.consumer = null; }
        pose.popPose();
        pose.translate(0, WirelessChargerField.coreCenterHeight(mv), 0);
        pose.mulPose(camera.rotation());
        float pulse = (float) (1 + 0.045 * Math.sin(time * 0.035));
        softCore(pose.last().pose(), lightField, (mv ? 0.65F : 0.46F) * pulse,
                mv ? 50 : 112, mv ? 126 : 203, 255, mv ? 116 : 91);
        softCore(pose.last().pose(), lightField, (mv ? 0.15F : 0.11F) * pulse, 210, 240, 255, 215);
        pose.popPose();
    }

    @Override public boolean additiveBatch() { return true; }

    private static void softCore(Matrix4f matrix, VertexConsumer out, float radius, int r, int g, int b, int a) {
        coreVertex(out, matrix, -radius, -radius, 32.5F / 128, 111.5F / 128, r, g, b, a);
        coreVertex(out, matrix, radius, -radius, 127.5F / 128, 111.5F / 128, r, g, b, a);
        coreVertex(out, matrix, radius, radius, 127.5F / 128, 16.5F / 128, r, g, b, a);
        coreVertex(out, matrix, -radius, radius, 32.5F / 128, 16.5F / 128, r, g, b, a);
    }

    private static void coreVertex(VertexConsumer out, Matrix4f matrix, float x, float y, float u, float v,
                                   int r, int g, int b, int a) {
        out.vertex(matrix, x, y, 0).color(r, g, b, a).uv(u, v).endVertex();
    }

    private static final class FrameMesh {
        private final WirelessChargerField.Mesh mesh;
        private double updated = Double.NaN;
        private FrameMesh(boolean mv, boolean far) { mesh = WirelessChargerField.create(mv, far); }
        private WirelessChargerField.Mesh at(double time) {
            if (time != updated) {
                WirelessChargerField.update(mesh, time);
                updated = time;
            }
            return mesh;
        }
    }

    private static final class FieldWriter implements WirelessChargerField.VertexSink {
        private Matrix4f matrix;
        private VertexConsumer consumer;
        @Override public void vertex(float x, float y, float z, float u, float v, int r, int g, int b, int a) {
            // The left atlas strip is seamless along U; the right square supplies the soft core.
            consumer.vertex(matrix, x, y, z).color(r, g, b, a)
                    .uv((0.5F + 30 * u) / 128, (0.5F + 127 * v) / 128).endVertex();
        }
    }

    @Override public boolean hasTESR(BlockEntity entity) { return true; }
    @Override public boolean isGlobalRenderer(BlockEntity entity) { return true; }
    @Override public int getViewDistance() { return 112; }
}
