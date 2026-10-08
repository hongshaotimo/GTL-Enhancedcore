package com.gtl.enhancedcore.client.renderer.machine;

import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.client.renderer.machine.WorkableCasingMachineRenderer;
import com.gtl.enhancedcore.client.renderer.StellarForgeRenderer;
import com.gtl.enhancedcore.client.renderer.StellarForgeFrame;
import com.lowdragmc.lowdraglib.utils.TrackedDummyWorld;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/** Same casing and overlay as upstream. Only the active forge gains an optical field. */
public final class StellarForgeMachineRenderer extends WorkableCasingMachineRenderer {
    public StellarForgeMachineRenderer() {
        super(new ResourceLocation("gtlcore","block/molecular_casing"),
                new ResourceLocation("gtceu","block/multiblock/fusion_reactor"));
    }
    private static double[] offset(WorkableElectricMultiblockMachine machine,double x,double y,double z) {
        return NeutronStarInfinityRenderer.toActualRelativeOffset(machine.getPattern(),x,y,z,
                machine.getFrontFacing(),machine.getUpwardsFacing(),machine.isFlipped());
    }
    private static Vector3f vector(double[] p) { return new Vector3f((float)p[0],(float)p[1],(float)p[2]); }
    @Override public void render(BlockEntity entity,float partial,PoseStack pose,MultiBufferSource buffers,int light,int overlay) {
        if (!(entity instanceof IMachineBlockEntity holder)
                || !(holder.getMetaMachine() instanceof WorkableElectricMultiblockMachine machine)
                || !machine.isFormed() || !machine.getRecipeLogic().isWorking()) return;
        if (entity.getLevel() instanceof TrackedDummyWorld) return; // An unpowered JEI model is not a running factory.
        double[] center = offset(machine,0,30,-106);
        var world = Vec3.atCenterOf(entity.getBlockPos()).add(center[0],center[1],center[2]);
        double distance = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition().distanceTo(world);
        if (distance >= 320) return;
        var x = vector(offset(machine,1,0,0)); var y = vector(offset(machine,0,1,0)); var z = vector(offset(machine,0,0,1));
        double seconds = (entity.getLevel().getGameTime()+partial)/20.0;
        StellarForgeRenderer.render(StellarForgeFrame.view(pose.last().pose(),x,y,z),seconds,distance);
    }
    @Override public boolean hasTESR(BlockEntity entity) { return true; }
    @Override public boolean isGlobalRenderer(BlockEntity entity) { return true; }
    @Override public int getViewDistance() { return 448; }
}
