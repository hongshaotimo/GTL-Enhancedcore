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
import java.util.Random;

/** Five parallax nebula slices, shared spiral stars, eight optical emitters and flared polar jets. */
@Mod.EventBusSubscriber(modid=GTLEnhancedcore.MOD_ID,bus=Mod.EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class StellarVortexRenderer extends RenderType {
    private static ShaderInstance shader;
    private static final BufferBuilder VERTICES=new BufferBuilder(4*1024*1024);
    private static final RenderType FIELD=create("stellar_vortex_field",DefaultVertexFormat.POSITION_COLOR_TEX,VertexFormat.Mode.QUADS,262144,false,false,
            CompositeState.builder().setShaderState(new ShaderStateShard(()->shader)).setTransparencyState(ADDITIVE_TRANSPARENCY)
                    .setDepthTestState(LEQUAL_DEPTH_TEST).setCullState(NO_CULL).setWriteMaskState(COLOR_WRITE).createCompositeState(false));
    private static final Vector3f P=new Vector3f(),Q=new Vector3f(),SIDE=new Vector3f(),VERTICAL=new Vector3f(0,1,0);
    /** 银盘倾角是常量；每帧旋转角的三角函数只算一次，避免每个顶点重复 4 次 sin/cos。 */
    private static final double TILT_COS=Math.cos(Math.PI/10),TILT_SIN=Math.sin(Math.PI/10);
    /** 云层 192 段与喷流鞘 64 段的角度固定，查表结果与逐次调用 Math.cos/sin 完全一致。 */
    private static final double[] CLOUD_COS=new double[193],CLOUD_SIN=new double[193];
    private static final double[] PLUME_COS=new double[65],PLUME_SIN=new double[65];
    private static final float[][] STARS=new float[12000][];
    static {
        for(int j=0;j<=192;j++){double a=(float)(j*Math.PI/96);CLOUD_COS[j]=Math.cos(a);CLOUD_SIN[j]=Math.sin(a);}
        for(int k=0;k<=64;k++){double a=k*Math.PI/32;PLUME_COS[k]=Math.cos(a);PLUME_SIN[k]=Math.sin(a);}
    }
    static {
        var rng=new Random(0x5354415253L);
        for(int i=0;i<STARS.length;i++) {
            double t=rng.nextDouble(),r=126+88*t,angle=(i%4)*Math.PI/2+2.65*t;
            double spread=1.4+Math.sin(t*Math.PI)*5.8;
            r+=rng.nextGaussian()*spread; angle+=rng.nextGaussian()*.025;
            STARS[i]=new float[]{(float)(r*Math.cos(angle)),(float)(rng.nextGaussian()*2.7),(float)(r*Math.sin(angle)),
                    (float)(i%179==0?1.0:.10+rng.nextDouble()*.25),(float)rng.nextDouble()};
            if(i>=11520) {
                double a=((i-11520)/40)*Math.PI/6+.15;
                STARS[i][0]=(float)(Math.cos(a)*221+rng.nextGaussian()*2);
                STARS[i][1]=(float)(rng.nextGaussian()*.65);
                STARS[i][2]=(float)(Math.sin(a)*221+rng.nextGaussian()*2);
                if(i%40==0) STARS[i][3]=.8F;
            }
        }
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
        FIELD.setupRenderState();
        try {
            double rotation=StellarVortexMotion.rotationAngle(seconds);
            double rc=Math.cos(rotation),rs=Math.sin(rotation);
            // Far-to-near cloud layers; additive cloud scattering avoids slice ordering discontinuities.
            shader.safeGetUniform("Kind").set(0);begin();
            int layers=quality==1||distance>500?3:5;
            for(int layer=0;layer<layers;layer++) {
                float y=(layer-(layers-1)*.5F)*2.3F;
                for(int j=0;j<192;j++) {
                    cloudVertex(120,j,y,rc,rs,layer);cloudVertex(230,j,y,rc,rs,layer);
                    cloudVertex(230,j+1,y,rc,rs,layer);cloudVertex(120,j+1,y,rc,rs,layer);
                }
            }end();
            shader.safeGetUniform("Kind").set(1);begin();
            int stride=distance>500?12:quality==1?8:quality==2?3:1;
            var inverse=new Matrix4f(view).invert(); var eye=inverse.transformPosition(new Vector3f());
            var right=inverse.transformDirection(new Vector3f(1,0,0)).normalize();
            var up=inverse.transformDirection(new Vector3f(0,1,0)).normalize();
            for(int i=0;i<STARS.length;i+=stride) {
                float[] s=STARS[i]; var p=galaxy(s[0],s[1],s[2],rc,rs,P);
                float brightness=.40F+.6F*s[4];
                billboard(p,right,up,s[3],s[4]>.985F?1F:.48F,.64F,1F,brightness);
            }
            for(int port=0;port<8;port++) {
                double angle=port*Math.PI/4,radius=(port%2==0?98:114)+2.5;
                var center=P.set((float)(radius*Math.cos(angle)),.5F,(float)(radius*Math.sin(angle)));
                billboard(center,right,up,2.5F,.36F,.66F,1F,.22F);
                billboard(center,right,up,.95F,.82F,.94F,1F,.9F);
            }
            end();
            shader.safeGetUniform("Kind").set(2);begin();
            // Thin branched ridges, not uniform neon tubes.
            for(int arm=0;arm<4;arm++)for(int branch=0;branch<3;branch++)for(int j=0;j<160;j++) {
                var a=spiral(arm,branch,j/160.0,rc,rs,P);var b=spiral(arm,branch,(j+1)/160.0,rc,rs,Q);
                ribbon(a,b,eye,.035F,branch==1?.72F:.37F,.54F,1F,.36F,j*.6F);
            }
            // Three broken outer constraint arcs.
            for(int ring=0;ring<3;ring++)for(int arc=0;arc<4;arc++)for(int j=0;j<56;j++) {
                double a=arc*Math.PI/2+ring*.19+j*.014+seconds*.001;
                ribbon(galaxy((222+ring*6)*Math.cos(a),(ring-1)*2.5,(222+ring*6)*Math.sin(a),1,0,P),
                        galaxy((222+ring*6)*Math.cos(a+.014),(ring-1)*2.5,(222+ring*6)*Math.sin(a+.014),1,0,Q),eye,.035F,.35F,.48F,.85F,.28F,0);
            }
            double pulse=(seconds%8)/8;
            for(int port=0;port<8;port++) {
                double angle=port*Math.PI/4,ca=Math.cos(angle),sa=Math.sin(angle),mouth=port%2==0?98:114;
                var side=SIDE.set((float)-sa,0,(float)ca);var vertical=VERTICAL;
                for(int ring=0;ring<3;ring++)for(int arc=0;arc<3;arc++)for(int j=0;j<52;j++) {
                    double a=arc*Math.PI*2/3+j*.028+ring*.4+seconds*(ring%2==0?.17:-.23)+port*.4;
                    float r=4.8F+ring*2.15F;
                    var p=P.set((float)((mouth+2.5)*ca),.5F,(float)((mouth+2.5)*sa)).fma((float)Math.cos(a)*r,side).fma((float)Math.sin(a)*r,vertical);
                    var q=Q.set((float)((mouth+2.5)*ca),.5F,(float)((mouth+2.5)*sa)).fma((float)Math.cos(a+.028)*r,side).fma((float)Math.sin(a+.028)*r,vertical);
                    ribbon(p,q,eye,.05F,.48F,.78F,1F,.55F,0);
                }
                for(int strand=-1;strand<3;strand++)for(int j=0;j<96;j++) {
                    double t=j/96.0,u=(j+1)/96.0;
                    ribbon(beam(ca,sa,mouth,t,strand,seconds,P),beam(ca,sa,mouth,u,strand,seconds,Q),eye,strand<0?.17F:.055F,
                            .50F,.76F,1F,strand<0?.72F:.48F,(float)(t*40-seconds*9+port));
                }
            }
            // Depth test hides the internal axis, while both exterior plumes remain visible.
            for(int sign=-1;sign<=1;sign+=2) {
                ribbon(P.set(0,sign*106,0),Q.set(0,sign*161.5F,0),eye,.65F,.72F,.88F,1F,.8F,(float)-seconds*8);
                for(int strand=0;strand<6;strand++)for(int j=0;j<120;j++) {
                    ribbon(jet(sign,strand,j/120.0,seconds,P),jet(sign,strand,(j+1)/120.0,seconds,Q),eye,.06F,.47F,.62F,1F,
                            (float)Math.sin(Math.PI*j/120)*.6F,(float)(j*.3-seconds*5));
                }
            }
            // Eight-second resonance traverses only the exterior field.
            if(pulse>.36&&pulse<.80) {
                double r=120+120*(pulse-.36)/.44;
                for(int j=0;j<256;j++) {
                    double a=j*Math.PI/128,b=(j+1)*Math.PI/128;
                    ribbon(galaxy(r*Math.cos(a),0,r*Math.sin(a),1,0,P),galaxy(r*Math.cos(b),0,r*Math.sin(b),1,0,Q),eye,.18F,.55F,.62F,1F,
                            (float)(Math.sin((pulse-.36)/.44*Math.PI)*.5),0);
                }
            }
            end();
            // Shader-warped translucent jet sheaths, no solid cone caps.
            shader.safeGetUniform("Kind").set(3);begin();
            for(int sign=-1;sign<=1;sign+=2)for(int layer=0;layer<2;layer++)for(int j=0;j<40;j++)for(int k=0;k<64;k++) {
                double t=j/40.0,u=(j+1)/40.0;
                plume(sign,t,k,layer);plume(sign,u,k,layer);plume(sign,u,k+1,layer);plume(sign,t,k+1,layer);
            }end();
        } finally { FIELD.clearRenderState();RenderSystem.setShader(()->old); }
    }
    private static void begin(){VERTICES.begin(VertexFormat.Mode.QUADS,DefaultVertexFormat.POSITION_COLOR_TEX);}
    private static void end(){var data=VERTICES.end();if(data.drawState().vertexCount()>0)BufferUploader.drawWithShader(data);else data.release();}
    /** c/s 为旋转角的 cos/sin；不旋转时传 1,0。 */
    private static Vector3f galaxy(double x,double y,double z,double c,double s,Vector3f out){
        double xx=x*c-z*s,zz=x*s+z*c;
        return out.set((float)xx,(float)(y*TILT_COS-zz*TILT_SIN),(float)(y*TILT_SIN+zz*TILT_COS));
    }
    private static void cloudVertex(float r,int segment,float y,double rc,double rs,int layer){
        double cos=CLOUD_COS[segment],sin=CLOUD_SIN[segment];
        var p=galaxy(r*cos,y,r*sin,rc,rs,P);vertex(p,1,1,1,.3F+layer*.1F,r*(float)cos,r*(float)sin);
    }
    private static Vector3f spiral(int arm,int branch,double t,double rc,double rs,Vector3f out){
        double a=arm*Math.PI/2+t*2.65,r=126+88*t+(branch-1)*3,ripple=Math.sin(t*17+branch*2)*1.3+Math.sin(t*41+branch)*.35;
        return galaxy(r*Math.cos(a)+Math.sin(a)*ripple,(branch-1)*2.2,r*Math.sin(a)-Math.cos(a)*ripple,rc,rs,out);
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
