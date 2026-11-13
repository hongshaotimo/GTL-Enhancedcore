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

/** An axial spacetime rift linked to the station's eight optical ports. */
@Mod.EventBusSubscriber(modid=GTLEnhancedcore.MOD_ID,bus=Mod.EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class StellarVortexRenderer extends RenderType {
    private static ShaderInstance shader;
    private static final BufferBuilder VERTICES=new BufferBuilder(4*1024*1024);
    private static final RenderType FIELD=create("stellar_vortex_field",DefaultVertexFormat.POSITION_COLOR_TEX,VertexFormat.Mode.QUADS,262144,false,false,
            CompositeState.builder().setShaderState(new ShaderStateShard(()->shader)).setTransparencyState(ADDITIVE_TRANSPARENCY)
                    .setDepthTestState(LEQUAL_DEPTH_TEST).setCullState(NO_CULL).setWriteMaskState(COLOR_WRITE).createCompositeState(false));
    private static final RenderType MIST=create("stellar_vortex_mist",DefaultVertexFormat.POSITION_COLOR_TEX,VertexFormat.Mode.QUADS,262144,false,false,
            CompositeState.builder().setShaderState(new ShaderStateShard(()->shader)).setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setDepthTestState(LEQUAL_DEPTH_TEST).setCullState(NO_CULL).setWriteMaskState(COLOR_WRITE).createCompositeState(false));
    private static final Vector3f P=new Vector3f(),Q=new Vector3f(),SIDE=new Vector3f(),VERTICAL=new Vector3f(0,1,0);
    /** Keep the optical connectors aligned with the station's existing inclined frame. */
    private static final double TILT_COS=Math.cos(Math.PI/10),TILT_SIN=Math.sin(Math.PI/10);
    /** The sheath's fixed angular samples are shared by both poles. */
    private static final double[] PLUME_COS=new double[65],PLUME_SIN=new double[65];
    static {
        for(int k=0;k<=64;k++){double a=k*Math.PI/32;PLUME_COS[k]=Math.cos(a);PLUME_SIN[k]=Math.sin(a);}
    }
    private StellarVortexRenderer() { super("unused_galaxy",DefaultVertexFormat.POSITION_COLOR_TEX,VertexFormat.Mode.QUADS,256,false,false,()->{},()->{}); }
    @SubscribeEvent public static void register(RegisterShadersEvent event) throws IOException {
        event.registerShader(new ShaderInstance(event.getResourceProvider(),GTLEnhancedcore.id("stellar_vortex"),DefaultVertexFormat.POSITION_COLOR_TEX),s->shader=s);
    }
    public static void render(Matrix4f view,double seconds,double distance,float opacity) {
        int quality=com.gtl.enhancedcore.client.config.GalaxyEffectSettings.quality();
        if(shader==null||quality==0)return;
        var old=RenderSystem.getShader();
        shader.safeGetUniform("FieldViewMat").set(view);
        shader.safeGetUniform("PhaseTime").set((float)(seconds%24000));
        shader.safeGetUniform("Visibility").set(opacity);
        try {
            var inverse=new Matrix4f(view).invert(); var eye=inverse.transformPosition(new Vector3f());
            shader.safeGetUniform("LocalEye").set(eye);
            var right=inverse.transformDirection(new Vector3f(1,0,0)).normalize();
            var up=inverse.transformDirection(new Vector3f(0,1,0)).normalize();
            int layers=quality==1||distance>500?2:3;
            // One view-facing envelope ray-marches a three-dimensional pinched cosmic rupture.
            MIST.setupRenderState();
            shader.safeGetUniform("Kind").set(0);begin();
            float cameraDistance=eye.length();
            float envelope;
            if(cameraDistance>190) {
                envelope=190*cameraDistance/(float)Math.sqrt(cameraDistance*cameraDistance-190*190);
                P.set(0,0,0);
            } else {
                inverse.transformDirection(P.set(0,0,-1)).normalize().add(eye);
                envelope=2;
            }
            billboard(P,right,up,envelope,1,1,1,quality==1||distance>500?.5F:1F);
            end();
            MIST.clearRenderState();
            FIELD.setupRenderState();
            shader.safeGetUniform("Kind").set(1);begin();
            for(int port=0;port<8;port++) {
                double angle=port*Math.PI/4,radius=(port%2==0?98:114)+2.5;
                var center=P.set((float)(radius*Math.cos(angle)),.5F,(float)(radius*Math.sin(angle)));
                billboard(center,right,up,2.5F,.36F,.66F,1F,.22F);
                billboard(center,right,up,.95F,.82F,.94F,1F,.9F);
            }
            end();
            shader.safeGetUniform("Kind").set(2);begin();
            // Axial gravitational filaments converge on the aperture from every optical port.
            for(int port=0;port<8;port++) {
                double angle=port*Math.PI/4,ca=Math.cos(angle),sa=Math.sin(angle),mouth=port%2==0?98:114;
                var side=SIDE.set((float)-sa,0,(float)ca);var vertical=VERTICAL;
                for(int ring=0;ring<3;ring++)for(int arc=0;arc<3;arc++)for(int j=0;j<52;j++) {
                    double a=arc*Math.PI*2/3+j*.028+ring*.4+seconds*(ring%2==0?.17:-.23)+port*.4;
                    float r=4.8F+ring*2.15F;
                    var p=P.set((float)((mouth+2.5)*ca),.5F,(float)((mouth+2.5)*sa)).fma((float)Math.cos(a)*r,side).fma((float)Math.sin(a)*r,vertical);
                    var q=Q.set((float)((mouth+2.5)*ca),.5F,(float)((mouth+2.5)*sa)).fma((float)Math.cos(a+.028)*r,side).fma((float)Math.sin(a+.028)*r,vertical);
                    ribbon(p,q,eye,.24F,.34F,.62F,.88F,.32F,0);
                }
                for(int strand=-1;strand<3;strand++)for(int j=0;j<72;j++) {
                    double t=j/72.0,u=(j+1)/72.0;
                    ribbon(beam(ca,sa,mouth,t,strand,seconds,P),beam(ca,sa,mouth,u,strand,seconds,Q),eye,strand<0?.42F:.24F,
                            .38F,.65F,.92F,strand<0?.38F:.23F,(float)(t*32-seconds*7+port));
                }
            }
            // The rupture emits two directional sheaths along the station axis.
            for(int sign=-1;sign<=1;sign+=2) {
                ribbon(P.set(0,sign*96,0),Q.set(0,sign*161.5F,0),eye,.9F,.54F,.64F,.9F,.22F,(float)-seconds*8);
                for(int strand=0;strand<4;strand++)for(int j=0;j<80;j++) {
                    ribbon(jet(sign,strand,j/80.0,seconds,P),jet(sign,strand,(j+1)/80.0,seconds,Q),eye,.28F,.35F,.58F,.9F,
                            (float)Math.sin(Math.PI*j/80)*.24F,(float)(j*.3-seconds*5));
                }
            }
            // Successive outward-moving wavefronts make the aperture feel active rather than orbital.
            for(int wave=0;wave<3;wave++) {
                double phase=(seconds*.115+wave/3.0)%1.0;
                double radius=20+phase*210;
                float alpha=(float)(Math.sin(phase*Math.PI)*.28);
                for(int j=0;j<128;j++) {
                    double a=j*Math.PI/64,b=(j+1)*Math.PI/64;
                    ribbon(galaxy(radius*Math.cos(a),0,radius*Math.sin(a),1,0,P),
                            galaxy(radius*Math.cos(b),0,radius*Math.sin(b),1,0,Q),eye,.48F,.38F,.62F,.88F,alpha,0);
                }
            }
            end();
            // Shader-warped translucent jet sheaths, no solid cone caps.
            shader.safeGetUniform("Kind").set(3);begin();
            for(int sign=-1;sign<=1;sign+=2)for(int layer=0;layer<layers-1;layer++)for(int j=0;j<28;j++)for(int k=0;k<64;k++) {
                double t=j/28.0,u=(j+1)/28.0;
                plume(sign,t,k,layer);plume(sign,u,k,layer);plume(sign,u,k+1,layer);plume(sign,t,k+1,layer);
            }end();
        } finally { FIELD.clearRenderState();MIST.clearRenderState();RenderSystem.setShader(()->old); }
    }
    private static void begin(){VERTICES.begin(VertexFormat.Mode.QUADS,DefaultVertexFormat.POSITION_COLOR_TEX);}
    private static void end(){
        var data=VERTICES.end();
        if(data.drawState().vertexCount()>0)BufferUploader.drawWithShader(data);else data.release();
    }
    /** c/s 为旋转角的 cos/sin；不旋转时传 1,0。 */
    private static Vector3f galaxy(double x,double y,double z,double c,double s,Vector3f out){
        double xx=x*c-z*s,zz=x*s+z*c;
        return out.set((float)xx,(float)(y*TILT_COS-zz*TILT_SIN),(float)(y*TILT_SIN+zz*TILT_COS));
    }
    private static Vector3f beam(double ca,double sa,double r,double t,int strand,double seconds,Vector3f out){
        double radius=r+(148-r)*t,dy=-sa*148*TILT_SIN*t*t;
        double y=.5*(1-t)+dy,z=radius*sa*(1-(1-TILT_COS)*t*t),x=radius*ca;
        if(strand>=0){double a=t*Math.PI*9+strand*Math.PI*2/3-seconds*.5,w=Math.sin(t*Math.PI)*.8;x-=sa*Math.sin(a)*w;z+=ca*Math.sin(a)*w;y+=Math.cos(a)*w;}
        return out.set((float)x,(float)y,(float)z);
    }
    private static Vector3f jet(int sign,int strand,double t,double seconds,Vector3f out){
        double r=.7+Math.pow(t,.8)*8.5,a=t*10+strand*Math.PI/3+seconds*.25*(strand%2==0?1:-1);
        return out.set((float)(Math.cos(a)*r),(float)(sign*(106+t*55.5)),(float)(Math.sin(a)*r));
    }
    private static void plume(int sign,double t,int segment,int layer){
        double r=1.1+(9+layer*2)*t,a=segment*Math.PI/32;
        vertex((float)(PLUME_COS[segment]*r),(float)(sign*(106+t*55.5)),(float)(PLUME_SIN[segment]*r),1,1,1,1,(float)(a/Math.PI/2),(float)t);
    }
    private static void billboard(Vector3f p,Vector3f right,Vector3f up,float radius,float r,float g,float b,float alpha){
        float rx=right.x*radius,ry=right.y*radius,rz=right.z*radius;
        float ux=up.x*radius,uy=up.y*radius,uz=up.z*radius;
        vertex(p.x-rx-ux,p.y-ry-uy,p.z-rz-uz,r,g,b,alpha,-1,-1);
        vertex(p.x+rx-ux,p.y+ry-uy,p.z+rz-uz,r,g,b,alpha,1,-1);
        vertex(p.x+rx+ux,p.y+ry+uy,p.z+rz+uz,r,g,b,alpha,1,1);
        vertex(p.x-rx+ux,p.y-ry+uy,p.z-rz+uz,r,g,b,alpha,-1,1);
    }
    private static void ribbon(Vector3f a,Vector3f b,Vector3f eye,float width,float r,float g,float blue,float alpha,float phase){
        float dx=b.x-a.x,dy=b.y-a.y,dz=b.z-a.z,ex=eye.x-a.x,ey=eye.y-a.y,ez=eye.z-a.z;
        float sx=dy*ez-dz*ey,sy=dz*ex-dx*ez,sz=dx*ey-dy*ex,len=sx*sx+sy*sy+sz*sz;
        if(len<1e-9F)return;float scale=width/(float)Math.sqrt(len);sx*=scale;sy*=scale;sz*=scale;
        float end=phase+(float)Math.sqrt(dx*dx+dy*dy+dz*dz);
        vertex(a.x-sx,a.y-sy,a.z-sz,r,g,blue,alpha,phase,-1);vertex(a.x+sx,a.y+sy,a.z+sz,r,g,blue,alpha,phase,1);
        vertex(b.x+sx,b.y+sy,b.z+sz,r,g,blue,alpha,end,1);vertex(b.x-sx,b.y-sy,b.z-sz,r,g,blue,alpha,end,-1);
    }
    private static void vertex(Vector3f p,float r,float g,float b,float a,float u,float v){vertex(p.x,p.y,p.z,r,g,b,a,u,v);}
    private static void vertex(float x,float y,float z,float r,float g,float b,float a,float u,float v){VERTICES.vertex(x,y,z).color(r,g,b,a).uv(u,v).endVertex();}
}
