package com.gtl.enhancedcore.client.renderer;

import com.gtl.enhancedcore.GTLEnhancedcore;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import java.io.IOException;
import java.util.List;

/** Analytic photosphere plus depth-tested, bounded optical ribbons. No entities or particles. */
@Mod.EventBusSubscriber(modid=GTLEnhancedcore.MOD_ID,bus=Mod.EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class StellarForgeRenderer extends RenderType {
    private static ShaderInstance star, flow;
    private static final BufferBuilder VERTICES = new BufferBuilder(32768);
    private static final RenderType CORE = type("stellar_core",false,true);
    private static final RenderType CORONA = type("stellar_corona",false,false);
    private static final RenderType FLOW = type("stellar_flow",true,false);
    private static final float[][][] BOX = {
            {{1,-1,-1},{1,-1,1},{1,1,1},{1,1,-1}},{{-1,-1,1},{-1,-1,-1},{-1,1,-1},{-1,1,1}},
            {{-1,1,-1},{1,1,-1},{1,1,1},{-1,1,1}},{{-1,-1,1},{1,-1,1},{1,-1,-1},{-1,-1,-1}},
            {{1,-1,1},{-1,-1,1},{-1,1,1},{1,1,1}},{{-1,-1,-1},{1,-1,-1},{1,1,-1},{-1,1,-1}}};
    private StellarForgeRenderer() {
        super("stellar_unused",DefaultVertexFormat.POSITION,VertexFormat.Mode.QUADS,256,false,false,()->{},()->{});
    }
    private static RenderType type(String name,boolean ribbon,boolean opaque) {
        return create("gtl_enhancedcore_"+name,ribbon?DefaultVertexFormat.POSITION_COLOR_TEX:DefaultVertexFormat.POSITION,
                VertexFormat.Mode.QUADS,32768,false,false,CompositeState.builder()
                        .setShaderState(new ShaderStateShard(()->ribbon?flow:star))
                        .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                        .setDepthTestState(LEQUAL_DEPTH_TEST).setCullState(ribbon?NO_CULL:CULL)
                        .setWriteMaskState(opaque?COLOR_DEPTH_WRITE:COLOR_WRITE).createCompositeState(false));
    }
    @SubscribeEvent public static void register(RegisterShadersEvent event) throws IOException {
        event.registerShader(new ShaderInstance(event.getResourceProvider(),GTLEnhancedcore.id("stellar_forge"),
                DefaultVertexFormat.POSITION),loaded->star=loaded);
        event.registerShader(new ShaderInstance(event.getResourceProvider(),GTLEnhancedcore.id("stellar_flow"),
                DefaultVertexFormat.POSITION_COLOR_TEX),loaded->flow=loaded);
    }
    public static boolean ready() { return star != null && flow != null; }
    public static void render(Matrix4f fieldView,double seconds,double distance) {
        if (!ready() || Math.abs(fieldView.determinant()) < 1e-7F) return;
        float phase = StellarForgeCycle.phase(seconds), opacity = StellarForgeCycle.visibility(distance);
        if (opacity <= 0) return;
        var inverse = new Matrix4f(fieldView).invert();
        var eye = inverse.transformPosition(new Vector3f());
        ShaderInstance previous = RenderSystem.getShader();
        try {
            star.safeGetUniform("FieldViewMat").set(fieldView);
            star.safeGetUniform("InverseFieldViewMat").set(inverse);
            star.safeGetUniform("PhaseTime").set((float)seconds);
            star.safeGetUniform("Pressure").set(StellarForgeCycle.pressure(phase));
            star.safeGetUniform("Pulse").set(StellarForgeCycle.pulse(phase));
            star.safeGetUniform("Visibility").set(opacity);
            star.safeGetUniform("Detail").set(distance < 150 ? 1 : 0);
            drawStar(CORE,0,fieldView.determinant()<0);
            drawStar(CORONA,1,fieldView.determinant()<0);
            flow.safeGetUniform("FieldViewMat").set(fieldView);
            flow.safeGetUniform("PhaseTime").set((float)seconds);
            flow.safeGetUniform("CyclePhase").set(phase);
            flow.safeGetUniform("Detail").set(distance < 150 ? 1 : 0);
            FLOW.setupRenderState();
            try {
                int stride = StellarForgeCycle.stride(distance);
                begin(0);
                for (var path : StellarForgePaths.ARCS)
                    ribbon(path,eye,.23F,.18F,.88F,1F,opacity*(.5F+.38F*StellarForgeCycle.pressure(phase)),stride);
                end();
                begin(1);
                for (var path : StellarForgePaths.FEEDS) ribbon(path,eye,.18F,1F,.87F,.5F,opacity*.92F,1);
                end();
                begin(2);
                for (var path : StellarForgePaths.CHANNELS) ribbon(path,eye,.21F,1F,.70F,.22F,opacity,1);
                end();
                begin(3);
                for (var wave : StellarForgePaths.WAVES) {
                    float alpha = opacity*StellarForgeCycle.wave(phase,wave.delay());
                    if (alpha > .001F) ribbon(wave.path(),eye,.23F,1F,.64F,.13F,alpha,1);
                }
                end();
            } finally { FLOW.clearRenderState(); }
        } finally { RenderSystem.setShader(()->previous); }
    }
    private static void drawStar(RenderType type,int pass,boolean mirrored) {
        type.setupRenderState();
        try {
            star.safeGetUniform("Pass").set(pass);
            VERTICES.begin(VertexFormat.Mode.QUADS,DefaultVertexFormat.POSITION);
            for (var face : BOX) for (int i=0;i<4;i++) {
                var p = face[mirrored?3-i:i]; VERTICES.vertex(p[0]*15.1F,p[1]*15.1F,p[2]*15.1F).endVertex();
            }
            BufferUploader.drawWithShader(VERTICES.end());
        } finally { type.clearRenderState(); }
    }
    private static void begin(int kind) {
        flow.safeGetUniform("Kind").set(kind);
        VERTICES.begin(VertexFormat.Mode.QUADS,DefaultVertexFormat.POSITION_COLOR_TEX);
    }
    private static void end() {
        var data = VERTICES.end();
        if (data.drawState().vertexCount()>0) BufferUploader.drawWithShader(data); else data.release();
    }
    private static void ribbon(List<Vector3f> points,Vector3f eye,float width,float r,float g,float b,float alpha,int stride) {
        float distance=0;
        for (int i=0;i<points.size()-1;) {
            int next=Math.min(points.size()-1,i+stride);
            var a=points.get(i);var c=points.get(next);
            var tangent=new Vector3f(c).sub(a);
            var side=new Vector3f(tangent).cross(new Vector3f(eye).sub(a));
            if (side.lengthSquared()<1e-9F) side.set(0,1,0).cross(tangent);
            if (side.lengthSquared()<1e-9F) side.set(1,0,0);
            side.normalize(width);
            float stop=distance+a.distance(c);
            vertex(a,side,-1,distance,r,g,b,alpha);vertex(a,side,1,distance,r,g,b,alpha);
            vertex(c,side,1,stop,r,g,b,alpha);vertex(c,side,-1,stop,r,g,b,alpha);
            distance=stop;i=next;
        }
    }
    private static void vertex(Vector3f p,Vector3f side,int sign,float u,float r,float g,float b,float alpha) {
        VERTICES.vertex(p.x+side.x*sign,p.y+side.y*sign,p.z+side.z*sign)
                .color(r,g,b,alpha).uv(u,sign).endVertex();
    }
}
