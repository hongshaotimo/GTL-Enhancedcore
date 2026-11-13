package com.gtl.enhancedcore.client.renderer.celestial;

import com.gtl.enhancedcore.client.config.GalaxyEffectSettings;
import com.gtl.enhancedcore.common.structure.CelestialEffectSizing;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.Random;
import net.minecraft.client.renderer.RenderType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** A chamber-filling singularity and a field coupled to both faces of the blueprint's outer hoop. */
@OnlyIn(Dist.CLIENT)
final class ArcanicSingularityRenderer {
    private static final double TAU = Math.PI * 2;
    private static final BufferBuilder VERTICES = new BufferBuilder(2 * 1024 * 1024);
    private static final Vector3f EYE = new Vector3f(), RIGHT = new Vector3f(), UP = new Vector3f();
    private static final Vector3f P = new Vector3f(), Q = new Vector3f(), SIDE = new Vector3f();
    private static final float[] STARS = new float[5200 * 5];
    private static RenderType activeType;
    static {
        var random = new Random(0x415243414E49434CL);
        for (int i = 0; i < STARS.length; i += 5) {
            double azimuth = random.nextDouble() * TAU, height = random.nextDouble() * 2 - 1;
            double radius = 20 + 54 * Math.cbrt(random.nextDouble()), equator = Math.sqrt(1 - height * height);
            STARS[i] = (float) (radius * equator * Math.cos(azimuth));
            STARS[i + 1] = (float) (radius * height);
            STARS[i + 2] = (float) (radius * equator * Math.sin(azimuth) * .46);
            STARS[i + 3] = i % 185 == 0 ? .46F : .065F + random.nextFloat() * .14F;
            STARS[i + 4] = random.nextFloat();
        }
    }
    private ArcanicSingularityRenderer() {}

    static void render(int count, long seed, float tick, PoseStack pose) {
        int quality = GalaxyEffectSettings.quality();
        if (quality == 0 || CelestialVfxRenderTypes.shader() == null) return;
        double seconds = tick / 20.0;
        // The parent scale remains shared with the original anchor; these dimensions are in blocks.
        var view = new Matrix4f(pose.last().pose()).scale(1F / CelestialEffectSizing.ASTROGRAPH_SCALE);
        var inverse = new Matrix4f(view).invert();
        inverse.transformPosition(EYE.set(0, 0, 0));
        inverse.transformDirection(RIGHT.set(1, 0, 0)).normalize();
        inverse.transformDirection(UP.set(0, 1, 0)).normalize();
        boolean distant = EYE.lengthSquared() > 380 * 380;
        int segments = quality == 1 || distant ? 96 : 160;
        try (var state = new CelestialVfxState()) {
            for (var shader : new net.minecraft.client.renderer.ShaderInstance[]{
                    CelestialVfxRenderTypes.shader(), CelestialVfxRenderTypes.coreShader()}) {
                shader.safeGetUniform("FieldViewMat").set(view);
                shader.safeGetUniform("LocalEye").set(EYE);
                shader.safeGetUniform("PhaseTime").set((float) (seconds % 24000));
                shader.safeGetUniform("Profile").set(1);
            }
            sphere(P.set(0, 0, 0), 12F, true);
            for (int i = 0; i < count; i++) {
                world(i, seed, seconds, P);
                sphere(P, 1.0F + .08F * i, false);
            }

            // Integrate gas through an ellipsoid in the shader, rather than exposing luminous shell surfaces.
            begin(7, false);
            float eyeDistance = EYE.length();
            float apparent;
            if (eyeDistance > 82) {
                apparent = 82 * eyeDistance / (float) Math.sqrt(eyeDistance * eyeDistance - 82 * 82);
                P.set(0, 0, 0);
            } else {
                inverse.transformDirection(P.set(0, 0, -1)).normalize().add(EYE);
                apparent = 2;
            }
            billboard(P, apparent, 1, 1, 1, quality == 1 || distant ? .5F : 1F);
            end();

            begin(8, false);
            // A broad, inclined accretion sheet, rather than another spiral galaxy.
            for (int band = 0; band < 7; band++) for (int j = 0; j < segments; j++) {
                diskVertex(14 + band * 7.8, TAU * j / segments, seconds);
                diskVertex(14 + (band + 1) * 7.8, TAU * j / segments, seconds);
                diskVertex(14 + (band + 1) * 7.8, TAU * (j + 1) / segments, seconds);
                diskVertex(14 + band * 7.8, TAU * (j + 1) / segments, seconds);
            }
            end();

            begin(1, false);
            int stride = quality == 1 || distant ? 4 : quality == 2 ? 2 : 1;
            double rotation = seconds * .012, c = Math.cos(rotation), s = Math.sin(rotation);
            for (int i = 0; i < STARS.length / 5; i += stride) {
                int n = i * 5;
                P.set((float) (STARS[n] * c - STARS[n + 1] * s),
                        (float) (STARS[n] * s + STARS[n + 1] * c), STARS[n + 2]);
                float color = STARS[n + 4];
                billboard(P, STARS[n + 3], color > .7 ? 1F : .65F, .59F + color * .32F,
                        color > .7 ? .67F : 1F, .7F);
            }
            for (int i = 0; i < count; i++) {
                world(i, seed, seconds, P);
                billboard(P, 2.6F, .62F, .74F, 1F, .4F);
            }
            end();

            begin(2, false);
            // Counter-precessing meridians occupy the complete central opening.
            for (int ring = 0; ring < 2; ring++) {
                double azimuth = ring * Math.PI / 2 + seconds * (ring % 2 == 0 ? .018 : -.014);
                double radius = 62 + ring * 9;
                for (int j = 0; j < segments; j++) {
                    meridian(radius, azimuth, TAU * j / segments, P);
                    meridian(radius, azimuth, TAU * (j + 1) / segments, Q);
                    ribbon(.20F, ring % 2 == 0 ? 1F : .66F, .65F, ring % 2 == 0 ? .46F : 1F,
                            .16F, (float) (j * .23 - seconds * 2));
                }
            }
            end();
            begin(10, false);
            for (int face = -1; face <= 1; face += 2) for (int j = 0; j < segments * 2; j++) {
                double a = TAU * j / (segments * 2), b = TAU * (j + 1) / (segments * 2);
                hoopVertex(136, a, face); hoopVertex(153, a, face);
                hoopVertex(153, b, face); hoopVertex(136, b, face);
            }
            end();
            begin(2, false);
            // Both faces of the physical outer hoop carry travelling packets and inward streams.
            for (int face = -1; face <= 1; face += 2) {
                for (int ring = 0; ring < 3; ring++) {
                    double radius = CelestialEffectSizing.ASTROGRAPH_OUTER_RADIUS - ring * 3.1;
                    float z = face * (4F + ring * 1.3F);
                    for (int j = 0; j < segments * 2; j++) {
                        double a = TAU * j / (segments * 2), b = TAU * (j + 1) / (segments * 2);
                        float packet = (float) Math.pow(.5 + .5 * Math.sin(a * 12 - seconds * 1.7 * face), 12);
                        P.set((float) (radius * Math.cos(a)), (float) (radius * Math.sin(a)), z);
                        Q.set((float) (radius * Math.cos(b)), (float) (radius * Math.sin(b)), z);
                        ribbon(ring == 0 ? .44F : ring == 1 ? .25F : 1.6F, ring == 1 ? 1F : .62F, .64F,
                                ring == 1 ? .48F : 1F, (ring == 2 ? .04F : .14F) + packet * .30F, (float) (a * 25));
                    }
                }
                for (int spoke = 0; spoke < 12; spoke++) {
                    double angle = spoke * TAU / 12 + Math.PI / 12;
                    for (int j = 0; j < 40; j++) {
                        inflow(angle, j / 40.0, face, seconds, P);
                        inflow(angle, (j + 1) / 40.0, face, seconds, Q);
                        float alpha = (float) (Math.sin(Math.PI * j / 40) * .5);
                        ribbon(.24F, 1F, .66F, .46F, alpha * .62F, (float) (j * .4 + seconds * 4));
                    }
                }
            }
            end();
            begin(1, false);
            for (int node = 0; node < 24; node++) {
                double a = node * TAU / 24 + seconds * .021;
                P.set((float) (145 * Math.cos(a)), (float) (145 * Math.sin(a)), (node % 2 == 0 ? 1 : -1) * 8F);
                billboard(P, 1.9F, 1F, .67F, .37F, .30F);
                billboard(P, .45F, 1F, .9F, .72F, .85F);
            }
            end();
            begin(11, false);
            billboard(P.set(0, 0, 0), 38F, 1F, .72F, .44F, 1F);
            end();
            begin(9, false);
            billboard(P.set(0, 0, 0), 27F, 1F, .72F, .44F, 1F);
            end();
        }
    }

    private static void sphere(Vector3f center, float radius, boolean singularity) {
        var shader = CelestialVfxRenderTypes.coreShader();
        shader.safeGetUniform("CoreCenter").set(center);
        shader.safeGetUniform("CoreRadius").set(radius);
        float distance = EYE.distance(center);
        if (distance <= radius) return;
        float apparent = radius * distance / (float) Math.sqrt(distance * distance - radius * radius);
        begin(singularity ? 6 : 3, true);
        billboard(center, apparent * 1.02F, .62F, .77F, 1F, 1F);
        end();
    }

    private static void world(int index, long seed, double seconds, Vector3f out) {
        double radius = 22 + index * 2.55, angle = seconds * (.09 + index * .009) + index * 2.399963 + (seed & 255) * .024;
        double tilt = index * .39;
        out.set((float) (radius * Math.cos(angle)), (float) (radius * Math.sin(angle) * Math.cos(tilt)),
                (float) (radius * Math.sin(angle) * Math.sin(tilt) * .48));
    }

    private static void hoopVertex(double radius, double angle, int face) {
        vertex((float) (radius * Math.cos(angle)), (float) (radius * Math.sin(angle)), face * 6F,
                1, 1, 1, 1, (float) ((radius - 136) / 17), (float) (angle / TAU));
    }

    private static void diskVertex(double radius, double angle, double seconds) {
        double y = radius * Math.sin(angle), warp = Math.sin(angle * 3 + seconds * .3) * 1.2;
        vertex((float) (radius * Math.cos(angle)), (float) (y * .34 + warp), (float) (y * .70),
                1, 1, 1, 1, (float) ((radius - 14) / 54.6), (float) (angle / TAU));
    }

    private static void meridian(double radius, double azimuth, double angle, Vector3f out) {
        out.set((float) (radius * Math.cos(angle) * Math.cos(azimuth)), (float) (radius * Math.sin(angle)),
                (float) (radius * Math.cos(angle) * Math.sin(azimuth) * .49));
    }

    private static void inflow(double angle, double t, int face, double seconds, Vector3f out) {
        double radius = 144 - 126 * t, a = angle + t * t * 1.6 + seconds * .025;
        out.set((float) (radius * Math.cos(a)), (float) (radius * Math.sin(a)),
                (float) (face * (7 * (1 - t) + 5 * Math.sin(t * Math.PI * 3 - seconds * .5))));
    }

    private static void begin(int kind, boolean solid) {
        (solid ? CelestialVfxRenderTypes.coreShader() : CelestialVfxRenderTypes.shader()).safeGetUniform("Kind").set(kind);
        activeType = solid ? CelestialVfxRenderTypes.CORE : kind == 7 ? CelestialVfxRenderTypes.VOLUME : CelestialVfxRenderTypes.LIGHT;
        activeType.setupRenderState();
        VERTICES.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR_TEX);
    }

    private static void end() {
        var data = VERTICES.end();
        try {
            if (data.drawState().vertexCount() > 0) BufferUploader.drawWithShader(data); else data.release();
        } finally { activeType.clearRenderState(); }
    }

    private static void billboard(Vector3f center, float radius, float r, float g, float b, float alpha) {
        float rx = RIGHT.x * radius, ry = RIGHT.y * radius, rz = RIGHT.z * radius;
        float ux = UP.x * radius, uy = UP.y * radius, uz = UP.z * radius;
        vertex(center.x-rx-ux,center.y-ry-uy,center.z-rz-uz,r,g,b,alpha,-1,-1);
        vertex(center.x+rx-ux,center.y+ry-uy,center.z+rz-uz,r,g,b,alpha,1,-1);
        vertex(center.x+rx+ux,center.y+ry+uy,center.z+rz+uz,r,g,b,alpha,1,1);
        vertex(center.x-rx+ux,center.y-ry+uy,center.z-rz+uz,r,g,b,alpha,-1,1);
    }

    private static void ribbon(float width, float r, float g, float b, float alpha, float phase) {
        SIDE.set(Q).sub(P).cross(EYE.x-P.x,EYE.y-P.y,EYE.z-P.z);
        float length = SIDE.lengthSquared();
        if (length < 1E-12F) return;
        float resolvedWidth = Math.max(width, EYE.distance(P) * .00048F);
        alpha *= width / resolvedWidth;
        SIDE.mul(resolvedWidth / (float) Math.sqrt(length));
        vertex(P.x-SIDE.x,P.y-SIDE.y,P.z-SIDE.z,r,g,b,alpha,phase,-1);
        vertex(P.x+SIDE.x,P.y+SIDE.y,P.z+SIDE.z,r,g,b,alpha,phase,1);
        vertex(Q.x+SIDE.x,Q.y+SIDE.y,Q.z+SIDE.z,r,g,b,alpha,phase+.25F,1);
        vertex(Q.x-SIDE.x,Q.y-SIDE.y,Q.z-SIDE.z,r,g,b,alpha,phase+.25F,-1);
    }

    private static void vertex(float x, float y, float z, float r, float g, float b, float alpha, float u, float v) {
        VERTICES.vertex(x,y,z).color(r,g,b,alpha).uv(u,v).endVertex();
    }
}
