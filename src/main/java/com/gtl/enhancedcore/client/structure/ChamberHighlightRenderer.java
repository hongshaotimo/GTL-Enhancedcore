package com.gtl.enhancedcore.client.structure;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.BlockPos;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** One immediate batch of translucent red faces, visible through the structure. */
@Mod.EventBusSubscriber(modid="gtl_enhancedcore",value=Dist.CLIENT)
public final class ChamberHighlightRenderer {
    private static com.gtl.enhancedcore.network.ChamberPackets.Snapshot current;
    private static long expires;
    private static final BufferBuilder VERTICES=new BufferBuilder(262144);
    private static final int[][] FACES={{0,1,2,3},{4,7,6,5},{0,4,5,1},{3,2,6,7},{0,3,7,4},{1,5,6,2}};
    static { com.gtl.enhancedcore.client.memory.ClientWorldCacheCleanup.register(level -> current=null); }
    private ChamberHighlightRenderer() {}
    public static void accept(com.gtl.enhancedcore.network.ChamberPackets.Snapshot snapshot) {
        var mc=Minecraft.getInstance();
        if(mc.level==null||!mc.level.dimension().location().equals(snapshot.dimension()))return;
        current=snapshot;expires=mc.level.getGameTime()+600;
    }
    @SubscribeEvent public static void world(RenderLevelStageEvent event) {
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_PARTICLES||current==null)return;
        var mc=Minecraft.getInstance();
        if(mc.level==null||mc.level.getGameTime()>expires||!mc.level.dimension().location().equals(current.dimension())){current=null;return;}
        var camera=event.getCamera().getPosition();
        var pose=event.getPoseStack();pose.pushPose();pose.translate(-camera.x,-camera.y,-camera.z);
        draw(current.positions(),pose);pose.popPose();
    }
    public static void draw(List<BlockPos> positions,double x,double y,double z) {
        var pose=new PoseStack();pose.translate(x,y,z);draw(positions,pose);
    }
    private static void draw(List<BlockPos> positions,PoseStack pose) {
        if(positions.isEmpty())return;
        var oldShader=RenderSystem.getShader();
        boolean depth=org.lwjgl.opengl.GL11.glIsEnabled(org.lwjgl.opengl.GL11.GL_DEPTH_TEST);
        boolean cull=org.lwjgl.opengl.GL11.glIsEnabled(org.lwjgl.opengl.GL11.GL_CULL_FACE);
        boolean blend=org.lwjgl.opengl.GL11.glIsEnabled(org.lwjgl.opengl.GL11.GL_BLEND);
        boolean depthWrite=org.lwjgl.opengl.GL11.glGetBoolean(org.lwjgl.opengl.GL11.GL_DEPTH_WRITEMASK);
        RenderSystem.enableBlend();RenderSystem.defaultBlendFunc();RenderSystem.disableCull();
        RenderSystem.disableDepthTest();RenderSystem.depthMask(false);RenderSystem.setShader(GameRenderer::getPositionColorShader);
        var buffer=VERTICES;
        buffer.begin(VertexFormat.Mode.QUADS,DefaultVertexFormat.POSITION_COLOR);
        var matrix=pose.last().pose();
        for(var p:positions){
            float a=p.getX()-.005f,b=p.getY()-.005f,c=p.getZ()-.005f,d=a+1.01f,e=b+1.01f,f=c+1.01f;
            float[][] v={{a,b,c},{d,b,c},{d,e,c},{a,e,c},{a,b,f},{d,b,f},{d,e,f},{a,e,f}};
            for(int[] face:FACES)
                for(int i:face)buffer.vertex(matrix,v[i][0],v[i][1],v[i][2]).color(255,24,24,60).endVertex();
        }
        BufferUploader.drawWithShader(buffer.end());
        RenderSystem.depthMask(depthWrite);if(depth)RenderSystem.enableDepthTest();else RenderSystem.disableDepthTest();
        if(cull)RenderSystem.enableCull();else RenderSystem.disableCull();
        if(blend)RenderSystem.enableBlend();else RenderSystem.disableBlend();
        RenderSystem.setShader(()->oldShader);
    }
}
