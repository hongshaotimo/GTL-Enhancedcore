package com.gtl.enhancedcore.client.renderer.machine;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.client.renderer.machine.WorkableCasingMachineRenderer;
import com.gtl.enhancedcore.GTLEnhancedcore;
import com.gtl.enhancedcore.common.machine.galaxy.StellarVortexSpaceStationMachine;
import com.gtl.enhancedcore.client.renderer.StellarVortexRenderer;
import com.lowdragmc.lowdraglib.utils.TrackedDummyWorld;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import java.util.WeakHashMap;

public final class StellarVortexMachineRenderer extends WorkableCasingMachineRenderer {
    private record Fade(float visibility, long nanos) {}
    private final WeakHashMap<BlockEntity, Fade> visibility = new WeakHashMap<>();
    public StellarVortexMachineRenderer() {
        super(new ResourceLocation("gtlcore", "block/casings/dimensionally_transcendent_casing"), GTLEnhancedcore.id("block/multiblock/stellar_vortex"));
    }
    @Override public void render(BlockEntity entity,float partial,PoseStack pose,MultiBufferSource buffers,int light,int overlay) {
        if (!(entity instanceof IMachineBlockEntity holder) || !(holder.getMetaMachine() instanceof StellarVortexSpaceStationMachine station)
                || entity.getLevel() instanceof TrackedDummyWorld) return;
        long now=System.nanoTime();
        Fade previous=visibility.getOrDefault(entity,new Fade(0,now));
        float old = previous.visibility();
        float target = station.isFieldActive() ? 1F : 0F;
        float step=(float)(1-Math.exp(-Math.min(.25,(now-previous.nanos())/1e9)*3.2));
        float fade = old + (target-old)*step;
        if (fade < .001F && target == 0) { visibility.remove(entity); return; }
        visibility.put(entity,new Fade(fade,now));
        // Source C=(98,58,60), center block-index=(98.5,107.5,98.5).
        // MIN_Z import maps source +X to pattern -X and source +Z to pattern -Z.
        var center=offset(station,-.5,49.5,-38.5);
        var world=Vec3.atCenterOf(entity.getBlockPos()).add(center[0],center[1],center[2]);
        double distance=Minecraft.getInstance().gameRenderer.getMainCamera().getPosition().distanceTo(world);
        if (distance>768) return;
        var right=vector(offset(station,-1,0,0)); var up=vector(offset(station,0,1,0)); var front=vector(offset(station,0,0,-1));
        var basis=new Matrix4f().setColumn(0,new Vector4f(right,0)).setColumn(1,new Vector4f(up,0)).setColumn(2,new Vector4f(front,0));
        var frame=new Matrix4f(pose.last().pose()).translate((float)center[0]+.5F,(float)center[1]+.5F,(float)center[2]+.5F).mul(basis);
        StellarVortexRenderer.render(frame,(entity.getLevel().getGameTime()-station.getFieldStart()+partial)/20.0,distance,fade);
    }
    private static double[] offset(StellarVortexSpaceStationMachine m,double x,double y,double z) {
        return NeutronStarInfinityRenderer.toActualRelativeOffset(m.getPattern(),x,y,z,m.getFrontFacing(),m.getUpwardsFacing(),m.isFlipped());
    }
    private static Vector3f vector(double[] p) { return new Vector3f((float)p[0],(float)p[1],(float)p[2]); }
    @Override public boolean hasTESR(BlockEntity entity) { return true; }
    @Override public boolean isGlobalRenderer(BlockEntity entity) { return true; }
    @Override public int getViewDistance() { return 768; }
}
