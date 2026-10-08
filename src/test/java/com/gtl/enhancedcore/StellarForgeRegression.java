package com.gtl.enhancedcore;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import com.gtl.enhancedcore.client.renderer.StellarForgeCycle;
import com.gtl.enhancedcore.client.renderer.StellarForgeFrame;
import com.gtl.enhancedcore.client.renderer.StellarForgePaths;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

final class StellarForgeRegression {
    private static int checks;
    private static void check(boolean ok,String message) { checks++; if(!ok)throw new AssertionError(message); }
    static int run() throws Exception {
        checks=0;
        for(int t=0;t<=9000;t++) {
            float phase=t/1000F,pressure=StellarForgeCycle.pressure(phase),radius=StellarForgeCycle.radius(phase);
            check(radius>=11 && radius<=12,"Stellar radius left the 11–12 block range");
            check(pressure>=0 && pressure<=1,"Unbounded compression");
            check(Math.abs(StellarForgeCycle.pressure(phase)-StellarForgeCycle.pressure(StellarForgeCycle.phase(phase+9)))<.00001,
                    "Cycle did not repeat at nine seconds");
        }
        check(StellarForgeCycle.stride(220)>StellarForgeCycle.stride(20),"Distant density not reduced");
        check(StellarForgeCycle.visibility(320)==0,"Effect exceeds render distance");
        check(StellarForgePaths.ARCS.size()==6 && StellarForgePaths.FEEDS.size()==4,"Wrong arc/feed count");
        var endpoints=List.of(new Vector3f(22,-8,0),new Vector3f(0,-8,22),new Vector3f(-22,-8,0),new Vector3f(0,-8,-22));
        for(int i=0;i<4;i++) check(StellarForgePaths.FEEDS.get(i).getLast().equals(endpoints.get(i)),"Forge stream endpoint changed");
        var file=Path.of("src/main/resources/data/gtl_enhancedcore/structures/gtl/star_ultimate_material_forge_factory.pattern.gz");
        String[] shape;
        try(var stream=new GZIPInputStream(Files.newInputStream(file))) { shape=new String(stream.readAllBytes(),java.nio.charset.StandardCharsets.US_ASCII).split("\n"); }
        check(shape[0].equals("213 115 213"),"Structure changed; revalidate optical routes");
        for(int x=-18;x<=18;x++)for(int y=-16;y<=16;y++)for(int z=-18;z<=18;z++)
            check(block(shape,x,y,z)==' ',"The requested 37x33x37 cavity contains a block");
        var paths=new ArrayList<List<Vector3f>>();
        paths.addAll(StellarForgePaths.ARCS);paths.addAll(StellarForgePaths.FEEDS);paths.addAll(StellarForgePaths.CHANNELS);
        StellarForgePaths.WAVES.forEach(w->paths.add(w.path()));
        // Only ARCS use distance LOD. Renderer keeps every corner of feeds/channels/waves.
        for(var path:paths)for(int stride:StellarForgePaths.ARCS.contains(path)?new int[]{1,2,4}:new int[]{1})
            for(int i=0;i<path.size()-1;i+=stride) {
            var a=path.get(i);var b=path.get(Math.min(i+stride,path.size()-1));
            int samples=Math.max(1,(int)Math.ceil(a.distance(b)*12));
            for(int n=0;n<=samples;n++) {
                var p=new Vector3f(a).lerp(b,n/(float)samples);
                for(float dx:new float[]{-.25F,.25F})for(float dy:new float[]{-.25F,.25F})for(float dz:new float[]{-.25F,.25F})
                    check(block(shape,p.x+dx,p.y+dy,p.z+dz)==' ',"Optical ribbon intersects a solid at "+p);
            }
        }
        var frames=new ArrayList<Map<String,Object>>();
        var projection=new Matrix4f().perspective((float)Math.toRadians(60),16F/9F,.1F,1000);
        for(int orientation=0;orientation<4;orientation++)for(boolean flipped:new boolean[]{false,true}) {
            var rotation=new Matrix4f().rotateY((float)(orientation*Math.PI/2));
            var right=rotation.transformDirection(new Vector3f(flipped?-1:1,0,0));
            var up=new Vector3f(0,1,0);var front=rotation.transformDirection(new Vector3f(0,0,1));
            for(int yaw:new int[]{-12,0,12}) {
                var camera=new Matrix4f().rotateY((float)Math.toRadians(yaw));
                var anchor=new Vector3f(up).mul(30).fma(-106,front).add(.5F,.5F,.5F);
                var blockPose=new Matrix4f(camera).translate(new Vector3f(0,0,-100).sub(anchor));
                var copy=new Matrix4f(blockPose);
                var view=StellarForgeFrame.view(blockPose,right,up,front);
                check(blockPose.equals(copy),"Optical frame modified caller pose");
                var expected=camera.transformPosition(new Vector3f(0,0,-100));
                check(view.transformPosition(new Vector3f()).distance(expected)<.0001F,"Stellar anchor drift");
                for(var end:endpoints) {
                    var actual=view.transformPosition(new Vector3f(end));
                    var local=new Vector3f(anchor).fma(end.x,right).fma(end.y,up).fma(end.z,front);
                    check(actual.distance(blockPose.transformPosition(local))<.0001F,"Forge endpoint rotated incorrectly");
                }
                Vector4f clip=projection.transform(new Vector4f(expected,1));
                frames.add(Map.of("view",view.get(new float[16]),"projection",projection.get(new float[16]),
                        "center",new float[]{clip.x/clip.w,clip.y/clip.w},"mirrored",flipped));
            }
        }
        Path report=Path.of("build/reports/stellar-forge-camera-frames.json");
        Files.createDirectories(report.getParent());Files.writeString(report,new GsonBuilder().create().toJson(frames));
        var mixins=JsonParser.parseString(Files.readString(Path.of("src/main/resources/gtl_enhancedcore.mixins.json"))).getAsJsonObject();
        check(mixins.getAsJsonArray("client").toString().contains("StellarForgeRendererMixin"),"Missing client-only integration");
        check(!mixins.getAsJsonArray("mixins").toString().contains("StellarForgeRendererMixin"),"Renderer leaks into dedicated server");
        return checks;
    }
    private static char block(String[] shape,float x,float y,float z) {
        int bx=(int)Math.floor(x+.5F)+106,by=(int)Math.floor(y+.5F)+69,bz=(int)Math.floor(z+.5F)+106;
        return shape[1+bz*115+by].charAt(bx);
    }
}
