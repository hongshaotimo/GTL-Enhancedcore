package com.gtl.enhancedcore.client.renderer.machine;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.client.renderer.machine.WorkableCasingMachineRenderer;
import com.gtl.enhancedcore.GTLEnhancedcore;
import com.gtl.enhancedcore.client.renderer.NeutronStarRenderTypes;
import com.gtl.enhancedcore.common.machine.CausalityTerminalMachine;
import com.gtl.enhancedcore.common.machine.WeatherAnchorMachine;
import com.lowdragmc.lowdraglib.utils.TrackedDummyWorld;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.model.data.ModelData;
import org.gtlcore.gtlcore.client.ClientUtil;
import org.gtlcore.gtlcore.utils.RenderUtil;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import java.util.function.Consumer;

/** Fields are anchored to the same local coordinates used by the structure predicates. */
@OnlyIn(Dist.CLIENT)
public final class MachineFieldRenderer extends WorkableCasingMachineRenderer {
    private static final ResourceLocation WEATHER = GTLEnhancedcore.id("obj/weather_phase_field");
    private static final ResourceLocation WEATHER_CORE = GTLEnhancedcore.id("obj/weather_phase_core");
    private static final ResourceLocation YANG = GTLEnhancedcore.id("obj/causality_yang");
    private static final ResourceLocation YIN = GTLEnhancedcore.id("obj/causality_yin");
    private static final ResourceLocation EYES = GTLEnhancedcore.id("obj/causality_eyes");
    private static final ResourceLocation IRIS = GTLEnhancedcore.id("obj/causality_iris_glow");
    private static final ResourceLocation ORBITS = GTLEnhancedcore.id("obj/causality_orbits");
    private static final ResourceLocation STREAMS = GTLEnhancedcore.id("obj/causality_streams");
    private static final float[][] MODE_COLORS = {
            {1.0F, 0.82F, 0.38F}, {0.40F, 0.54F, 1.0F}, {0.42F, 1.0F, 0.86F},
            {0.38F, 0.70F, 1.0F}, {0.78F, 0.57F, 1.0F}
    };
    private final boolean causality;

    public MachineFieldRenderer(boolean causality) {
        super(GTCEu.id("block/casings/hpca/high_power_casing"),
                GTCEu.id("block/multiblock/multiblock_workable"));
        this.causality = causality;
    }

    @Override
    public void render(BlockEntity entity, float partialTicks, PoseStack pose,
                       MultiBufferSource buffer, int light, int overlay) {
        if (!(entity instanceof IMachineBlockEntity holder)
                || !(holder.getMetaMachine() instanceof WorkableElectricMultiblockMachine machine)
                || !machine.isFormed()) return;
        boolean preview = entity.getLevel() instanceof TrackedDummyWorld;
        if (!causality && (!(machine instanceof WeatherAnchorMachine weather)
                || (!weather.isAnchoring() && !preview))) return;
        float time = RenderUtil.getSmoothTick(machine, partialTicks);
        pose.pushPose();
        double[] offset = offset(machine, 0, causality ? 73 : 17, causality ? -12 : -11);
        pose.translate(offset[0] + 0.5, offset[1] + 0.5, offset[2] + 0.5);
        Vector3f x = vector(offset(machine,1,0,0));
        Vector3f y = vector(offset(machine,0,1,0));
        Vector3f z = vector(offset(machine,0,0,1));
        Matrix3f basis = new Matrix3f().setColumn(0,x).setColumn(1,y).setColumn(2,z);
        boolean mirrored = basis.determinant() < 0;
        if (mirrored) basis.setColumn(0,x.negate());
        pose.mulPose(new Quaternionf().setFromNormalized(basis));
        if (mirrored) pose.scale(-1,1,1);
        if (causality) {
            boolean running = machine instanceof CausalityTerminalMachine terminal
                    && terminal.getProgressPercent() > 0;
            // Boundary flow stays aligned with the physical S-channel; only the orb spins.
            renderModel(YIN,pose,buffer,NeutronStarRenderTypes.ENERGY,1,1,1);
            renderModel(YANG,pose,buffer,NeutronStarRenderTypes.ENERGY,1,1,1);
            renderModel(IRIS,pose,buffer,NeutronStarRenderTypes.ENERGY,1,1,1);
            if (running || preview) renderModel(STREAMS,pose,buffer,NeutronStarRenderTypes.ENERGY,1,1,1);
            pose.pushPose();
            pose.translate(0,0,17);
            pose.mulPose(new Quaternionf().fromAxisAngleDeg(0,1,0,(time * 0.10F) % 360));
            renderModel(EYES,pose,buffer,RenderType.translucent(),1,1,1);
            renderModel(ORBITS,pose,buffer,NeutronStarRenderTypes.ENERGY,1,1,1);
            pose.popPose();
        } else {
            float[] color = MODE_COLORS[Math.floorMod(machine.getActiveRecipeType(),MODE_COLORS.length)];
            pose.mulPose(new Quaternionf().fromAxisAngleDeg(0,1,0,(time * 0.75F) % 360));
            renderModel(WEATHER_CORE,pose,buffer,NeutronStarRenderTypes.ENERGY,color[0],color[1],color[2]);
            renderModel(WEATHER,pose,buffer,NeutronStarRenderTypes.ENERGY,color[0],color[1],color[2]);
        }
        pose.popPose();
    }

    private static double[] offset(WorkableElectricMultiblockMachine machine,double x,double y,double z) {
        return NeutronStarInfinityRenderer.toActualRelativeOffset(machine.getPattern(),x,y,z,
                machine.getFrontFacing(),machine.getUpwardsFacing(),machine.isFlipped());
    }

    private static Vector3f vector(double[] v) {
        return new Vector3f((float)v[0],(float)v[1],(float)v[2]);
    }

    private static void renderModel(ResourceLocation model,PoseStack pose,MultiBufferSource buffer,
                                    RenderType type,float r,float g,float b) {
        ClientUtil.modelRenderer().renderModel(pose.last(),buffer.getBuffer(type),null,
                ClientUtil.getBakedModel(model),r,g,b,0xF000F0,
                OverlayTexture.NO_OVERLAY,ModelData.EMPTY,type);
    }

    @Override
    public void onAdditionalModel(Consumer<ResourceLocation> registry) {
        super.onAdditionalModel(registry);
        registry.accept(causality ? YANG : WEATHER);
        registry.accept(causality ? STREAMS : WEATHER_CORE);
        if (causality) {
            registry.accept(YIN);
            registry.accept(EYES);
            registry.accept(IRIS);
            registry.accept(ORBITS);
        }
    }

    @Override public boolean hasTESR(BlockEntity entity) { return true; }
    @Override public boolean isGlobalRenderer(BlockEntity entity) { return true; }
    @Override public int getViewDistance() { return causality ? 320 : 128; }
}
