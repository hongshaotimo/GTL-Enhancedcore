package com.gtl.enhancedcore.client.renderer.celestial;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.Random;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Stellar Vortex's layered nebula, optical dust and branching filaments, fitted to each chamber. */
@OnlyIn(Dist.CLIENT)
public final class CelestialVfxRenderer {
    private static final double TAU = Math.PI * 2;
    private static final BufferBuilder VERTICES = new BufferBuilder(2 * 1024 * 1024);
    private static RenderType activeType;
    private static final CachedMesh EYE = new CachedMesh(false), ASTROGRAPH = new CachedMesh(true);
    private static final float[][] STARS = {new float[9000 * 5], new float[9000 * 5]};
    private static final double[] COS = new double[193], SIN = new double[193];
    private static final Vector3f RIGHT = new Vector3f(), UP = new Vector3f(), EYE_POSITION = new Vector3f();
    private static final Vector3f P = new Vector3f(), Q = new Vector3f(), SIDE = new Vector3f();
    private static final double[] DIMENSION_RADII = {3.12, 5.616, 8.832};
    static {
        for (int j = 0; j <= 192; j++) { COS[j] = Math.cos(TAU * j / 192); SIN[j] = Math.sin(TAU * j / 192); }
        for (int profile = 0; profile < 2; profile++) {
            var random = new Random(0x43454C4553544941L + profile);
            for (int i = 0; i < 9000; i++) {
                double t = Math.pow(random.nextDouble(), .78), radius = 2.6 + 9.45 * t;
                int arms = profile == 0 ? 4 : 6;
                double angle = i % arms * TAU / arms + (profile == 0 ? 2.8 : 3.9) * t
                        + random.nextGaussian() * (.035 + .085 * Math.sin(t * Math.PI));
                int p = i * 5;
                STARS[profile][p] = (float) (radius * Math.cos(angle));
                STARS[profile][p + 1] = (float) (random.nextGaussian() * (.09 + .13 * t));
                STARS[profile][p + 2] = (float) (radius * Math.sin(angle));
                STARS[profile][p + 3] = i % 193 == 0 ? .085F : .012F + random.nextFloat() * .027F;
                STARS[profile][p + 4] = random.nextFloat();
            }
        }
    }
    private CelestialVfxRenderer() {}

    public static void eye(float time, PoseStack pose, MultiBufferSource buffers) { render(false, 3, 0, time, pose); }
    public static void astrograph(int count, long seed, float time, PoseStack pose, MultiBufferSource buffers) {
        ArcanicSingularityRenderer.render(count, seed, time, pose);
    }

    private static void setup(PoseStack pose, float time, boolean astrograph) {
        var shader = CelestialVfxRenderTypes.shader();
        // World BER poses already contain the camera view, as in StellarVortexRenderer.
        var view = new Matrix4f(pose.last().pose());
        var inverse = new Matrix4f(view).invert();
        inverse.transformDirection(RIGHT.set(1, 0, 0)).normalize();
        inverse.transformDirection(UP.set(0, 1, 0)).normalize();
        inverse.transformPosition(EYE_POSITION.set(0, 0, 0));
        uniforms(shader, view, time, astrograph);
        uniforms(CelestialVfxRenderTypes.coreShader(), view, time, astrograph);
    }

    private static void uniforms(net.minecraft.client.renderer.ShaderInstance shader, Matrix4f view, float time, boolean astrograph) {
        shader.safeGetUniform("FieldViewMat").set(view);
        shader.safeGetUniform("LocalEye").set(EYE_POSITION);
        shader.safeGetUniform("PhaseTime").set(time / 20F % 24000);
        shader.safeGetUniform("Profile").set(astrograph ? 1 : 0);
    }

    private static void render(boolean astrograph, int count, long seed, float time, PoseStack pose) {
        if (CelestialVfxRenderTypes.shader() == null) return;
        double seconds = time / 20.0, rotation = -seconds * .025;
        double c = Math.cos(rotation), s = Math.sin(rotation);
        var cache = astrograph ? ASTROGRAPH : EYE;
        try (var state = new CelestialVfxState()) {
            setup(pose, time, astrograph);
            // Finish opaque surfaces before any additive field, so foreground light survives and background light is occluded.
            core(P.set(0, 0, 0), astrograph ? 1.6F : 2.15F, 1, .95F, 1);
            for (int i = 0; i < count; i++) {
                worldPosition(astrograph, i, seed, time);
                core(P, astrograph ? .16F + i * .012F : .24F + i * .09F,
                        i % 3 == 0 ? .73F : .45F, i % 3 == 1 ? .92F : .65F, 1);
            }
            begin(0, false);
            int layers = EYE_POSITION.lengthSquared() > 130 * 130 ? 3 : 5;
            for (int layer = 0; layer < layers; layer++) {
                double y = (layer - (layers - 1) * .5) * .16;
                for (int j = 0; j < 192; j++) {
                    cloud(2.2, j, y, c, s, layer, astrograph); cloud(12.4, j, y, c, s, layer, astrograph);
                    cloud(12.4, j + 1, y, c, s, layer, astrograph); cloud(2.2, j + 1, y, c, s, layer, astrograph);
                }
            }
            end();
            begin(1, false);
            int stride = EYE_POSITION.lengthSquared() > 130 * 130 ? 4 : 1;
            var stars = STARS[astrograph ? 1 : 0];
            for (int i = 0; i < 9000; i += stride) {
                int p = i * 5;
                transform(stars[p], stars[p + 1], stars[p + 2], c, s, astrograph, P);
                float v = stars[p + 4];
                billboard(P, stars[p + 3], v > .985 ? .9F : .43F, .57F + v * .23F, 1, .33F + .43F * v);
            }
            billboard(P.set(0, 0, 0), astrograph ? 3.7F : 4.4F, .37F, .5F, 1, .21F);
            for (int i = 0; i < count; i++) {
                worldPosition(astrograph, i, seed, time);
                billboard(P, astrograph ? .38F : .58F, .43F, .66F, 1, .32F);
            }
            end();
            begin(2, false);
            int arms = astrograph ? 6 : 4;
            // Like the station, the ridges branch and ripple rather than forming uniformly bright tubes.
            for (int arm = 0; arm < arms; arm++) for (int branch = 0; branch < 3; branch++) for (int j = 0; j < 128; j++) {
                spiral(arm, branch, j / 128.0, c, s, astrograph, P);
                spiral(arm, branch, (j + 1) / 128.0, c, s, astrograph, Q);
                ribbon(P, Q, .012F, .42F, .51F, 1, .21F, (float) (j * .18 - seconds * .7));
            }
            if (cache.time != time) { cache.mesh.update(time); cache.time = time; }
            cache.mesh.emit((x, y, z, u, v, rgba) -> vertex(x, y, z,
                    (rgba >>> 24) / 255F, (rgba >>> 16 & 255) / 255F,
                    (rgba >>> 8 & 255) / 255F, (rgba & 255) / (astrograph ? 430F : 630F), u, v * 2 - 1));
            if (!astrograph) {
                for (int sign = -1; sign <= 1; sign += 2) for (int strand = 0; strand < 6; strand++) {
                    for (int j = 0; j < 64; j++) {
                        jet(sign, strand, j / 64.0, seconds, c, s, P);
                        jet(sign, strand, (j + 1) / 64.0, seconds, c, s, Q);
                        ribbon(P, Q, .018F, .43F, .64F, 1, (float) Math.sin(Math.PI * j / 64) * .27F,
                                (float) (j * .15 - seconds * .8));
                    }
                }
            }
            end();
            if (!astrograph) {
                begin(5, false);
                for (int sign = -1; sign <= 1; sign += 2) for (int layer = 0; layer < 2; layer++) {
                    for (int j = 0; j < 24; j++) for (int k = 0; k < 32; k++) {
                        plume(sign, j / 24.0, k, layer, c, s); plume(sign, (j + 1) / 24.0, k, layer, c, s);
                        plume(sign, (j + 1) / 24.0, k + 1, layer, c, s); plume(sign, j / 24.0, k + 1, layer, c, s);
                    }
                }
                end();
            }
            begin(4, false);
            billboard(P.set(0, 0, 0), astrograph ? 3.2F : 4.3F, .35F, .57F, 1, .72F);
            end();
        }
    }

    /** The eye keeps its three dimension orbits; the star atlas retains the upstream world count. */
    private static void worldPosition(boolean astrograph, int i, long seed, float time) {
        double radius = astrograph ? 3.2 + i * .55 : DIMENSION_RADII[i];
        if (astrograph) {
            double angle = time * (.006 + i * .0012) + i * 2.3999632297 + (seed & 255) * .024;
            double tilt = .26 + i * .115;
            P.set((float) (radius * Math.cos(angle)), (float) (radius * Math.sin(angle) * Math.sin(tilt)),
                    (float) (radius * Math.sin(angle) * Math.cos(tilt)));
        } else {
            double angle = time * (i + 1) / 80.0 + .4, spin = Math.toRadians(time * 1.5 / (i + 1) % 360);
            double sx = radius * Math.sin(angle), sz = radius * Math.cos(angle);
            double cs = Math.cos(spin), hs = (1 - cs) * .5, sn = Math.sin(spin) / Math.sqrt(2);
            P.set((float) ((cs + hs) * sx + hs * sz), (float) (sn * (sx - sz)),
                    (float) (hs * sx + (cs + hs) * sz));
        }
    }

    private static void core(Vector3f center, float radius, float r, float g, float b) {
        var shader = CelestialVfxRenderTypes.coreShader();
        shader.safeGetUniform("CoreCenter").set(center);
        shader.safeGetUniform("CoreRadius").set(radius);
        begin(3, true);
        float distance = EYE_POSITION.distance(center);
        float apparentRadius = radius * distance / (float) Math.sqrt(Math.max(.001F, distance * distance - radius * radius));
        billboard(center, apparentRadius * 1.02F, r, g, b, 1);
        end();
    }

    private static void begin(int kind, boolean solid) {
        (solid ? CelestialVfxRenderTypes.coreShader() : CelestialVfxRenderTypes.shader()).safeGetUniform("Kind").set(kind);
        activeType = solid ? CelestialVfxRenderTypes.CORE : CelestialVfxRenderTypes.LIGHT;
        activeType.setupRenderState();
        VERTICES.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR_TEX);
    }

    private static void end() {
        var data = VERTICES.end();
        try {
            if (data.drawState().vertexCount() == 0) data.release(); else BufferUploader.drawWithShader(data);
        } finally {
            activeType.clearRenderState();
        }
    }

    private static void cloud(double radius, int segment, double y, double c, double s, int layer, boolean astrograph) {
        double x = radius * COS[segment], z = radius * SIN[segment];
        transform(x, y, z, c, s, astrograph, P);
        vertex(P.x, P.y, P.z, 1, 1, 1, .26F + layer * .12F, (float) x, (float) z);
    }

    private static void transform(double x, double y, double z, double c, double s, boolean astrograph, Vector3f out) {
        double rx = x * c - z * s, rz = x * s + z * c;
        double tilt = astrograph ? .51 : .32;
        out.set((float) rx, (float) (y * Math.cos(tilt) - rz * Math.sin(tilt)),
                (float) (y * Math.sin(tilt) + rz * Math.cos(tilt)));
    }

    private static void spiral(int arm, int branch, double t, double c, double s, boolean astrograph, Vector3f out) {
        double radius = 2.8 + 9.1 * t + (branch - 1) * .12;
        double angle = arm * TAU / (astrograph ? 6 : 4) + t * (astrograph ? 3.9 : 2.8);
        double ripple = Math.sin(t * 27 + branch * 2) * .065 + Math.sin(t * 73) * .018;
        transform(radius * Math.cos(angle) + Math.sin(angle) * ripple, (branch - 1) * .13,
                radius * Math.sin(angle) - Math.cos(angle) * ripple, c, s, astrograph, out);
    }

    private static void jet(int sign, int strand, double t, double seconds, double c, double s, Vector3f out) {
        double angle = strand * TAU / 6 + t * 8 - seconds * .18;
        double radius = .10 + t * t * .9;
        transform(Math.cos(angle) * radius, sign * (2.4 + t * 7.7), Math.sin(angle) * radius, c, s, false, out);
    }

    private static void plume(int sign, double t, int segment, int layer, double c, double s) {
        double angle = TAU * segment / 32, radius = .14 + (1.0 + layer * .3) * Math.pow(t, .85);
        transform(radius * Math.cos(angle), sign * (2.4 + t * 7.7), radius * Math.sin(angle), c, s, false, P);
        vertex(P.x,P.y,P.z,1,1,1,1,segment / 32F,(float) t);
    }

    private static void billboard(Vector3f p, float radius, float r, float g, float b, float alpha) {
        float rx = RIGHT.x * radius, ry = RIGHT.y * radius, rz = RIGHT.z * radius;
        float ux = UP.x * radius, uy = UP.y * radius, uz = UP.z * radius;
        vertex(p.x-rx-ux,p.y-ry-uy,p.z-rz-uz,r,g,b,alpha,-1,-1);
        vertex(p.x+rx-ux,p.y+ry-uy,p.z+rz-uz,r,g,b,alpha,1,-1);
        vertex(p.x+rx+ux,p.y+ry+uy,p.z+rz+uz,r,g,b,alpha,1,1);
        vertex(p.x-rx+ux,p.y-ry+uy,p.z-rz+uz,r,g,b,alpha,-1,1);
    }

    private static void ribbon(Vector3f a, Vector3f b, float width, float r, float g, float blue, float alpha, float phase) {
        SIDE.set(b).sub(a).cross(EYE_POSITION.x-a.x,EYE_POSITION.y-a.y,EYE_POSITION.z-a.z);
        float sideLength = SIDE.lengthSquared();
        if (sideLength < 1E-12F) return; // A camera exactly on the segment sees no ribbon width.
        SIDE.mul(width / (float) Math.sqrt(sideLength));
        vertex(a.x-SIDE.x,a.y-SIDE.y,a.z-SIDE.z,r,g,blue,alpha,phase,-1);
        vertex(a.x+SIDE.x,a.y+SIDE.y,a.z+SIDE.z,r,g,blue,alpha,phase,1);
        vertex(b.x+SIDE.x,b.y+SIDE.y,b.z+SIDE.z,r,g,blue,alpha,phase+.18F,1);
        vertex(b.x-SIDE.x,b.y-SIDE.y,b.z-SIDE.z,r,g,blue,alpha,phase+.18F,-1);
    }

    private static void vertex(float x, float y, float z, float r, float g, float b, float a, float u, float v) {
        VERTICES.vertex(x,y,z).color(r,g,b,a).uv(u,v).endVertex();
    }
    private static final class CachedMesh {
        final CelestialVfxMesh mesh;
        float time = Float.NaN;
        CachedMesh(boolean astrograph) { mesh = new CelestialVfxMesh(astrograph); }
    }
}
