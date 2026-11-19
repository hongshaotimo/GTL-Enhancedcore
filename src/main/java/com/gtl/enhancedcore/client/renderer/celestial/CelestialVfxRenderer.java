package com.gtl.enhancedcore.client.renderer.celestial;

import com.gtl.enhancedcore.client.renderer.MachineEffectGeometry;
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
    private CelestialVfxRenderer() {}

    public static void eye(float time, PoseStack pose, MultiBufferSource buffers) {
        if (CelestialVfxRenderTypes.shader() != null) Eye.render(time, pose);
    }
    public static void astrograph(int count, long seed, float time, PoseStack pose, MultiBufferSource buffers) {
        ArcanicSingularityRenderer.render(count, seed, time, pose);
    }

    // The delegated astrograph entry never initializes the eye mesh, stars or vertex buffer.
    private static final class Eye {
        private static final double TAU = Math.PI * 2;
        private static RenderType activeType;
        private static final CelestialVfxMesh MESH = new CelestialVfxMesh(false);
        private static float meshTime = Float.NaN;
        private static final float[] STARS = new float[9000 * 5];
        private static final double[] COS = new double[193], SIN = new double[193];
        private static final Vector3f RIGHT = new Vector3f(), UP = new Vector3f(), EYE_POSITION = new Vector3f();
        private static final Vector3f P = new Vector3f(), Q = new Vector3f(), SIDE = new Vector3f();
        private static final double[] DIMENSION_RADII = {3.12, 5.616, 8.832};
        private static final double TILT_COS = Math.cos(.32), TILT_SIN = Math.sin(.32);
        private static final double[] SPIRAL_XZ = new double[4 * 3 * 129 * 2];
        private static final double[] PLUME_COS = new double[33], PLUME_SIN = new double[33];
        private static final double[][] PLUME_RADII = new double[2][25];
        private static final float[] JET_ALPHA = new float[64];
        private static final Vector3f[] ORBITS = {new Vector3f(), new Vector3f(), new Vector3f()};
        private static final Matrix4f INVERSE = new Matrix4f();
        static {
            for (int j = 0; j <= 192; j++) { COS[j] = Math.cos(TAU * j / 192); SIN[j] = Math.sin(TAU * j / 192); }
            var random = new Random(0x43454C4553544941L);
            for (int i = 0; i < 9000; i++) {
                double t = Math.pow(random.nextDouble(), .78), radius = 2.6 + 9.45 * t;
                double angle = i % 4 * TAU / 4 + 2.8 * t
                        + random.nextGaussian() * (.035 + .085 * Math.sin(t * Math.PI));
                int p = i * 5;
                STARS[p] = (float) (radius * Math.cos(angle));
                STARS[p + 1] = (float) (random.nextGaussian() * (.09 + .13 * t));
                STARS[p + 2] = (float) (radius * Math.sin(angle));
                STARS[p + 3] = i % 193 == 0 ? .085F : .012F + random.nextFloat() * .027F;
                STARS[p + 4] = random.nextFloat();
            }
            for (int arm = 0; arm < 4; arm++) for (int branch = 0; branch < 3; branch++) {
                for (int j = 0; j <= 128; j++) {
                    double t = j / 128.0, radius = 2.8 + 9.1 * t + (branch - 1) * .12;
                    double angle = arm * TAU / 4 + t * 2.8;
                    double ripple = Math.sin(t * 27 + branch * 2) * .065 + Math.sin(t * 73) * .018;
                    int index = ((arm * 3 + branch) * 129 + j) * 2;
                    SPIRAL_XZ[index] = radius * Math.cos(angle) + Math.sin(angle) * ripple;
                    SPIRAL_XZ[index + 1] = radius * Math.sin(angle) - Math.cos(angle) * ripple;
                }
            }
            for (int k = 0; k <= 32; k++) {
                PLUME_COS[k] = Math.cos(TAU * k / 32);
                PLUME_SIN[k] = Math.sin(TAU * k / 32);
            }
            for (int layer = 0; layer < 2; layer++) for (int j = 0; j <= 24; j++)
                PLUME_RADII[layer][j] = .14 + (1.0 + layer * .3) * Math.pow(j / 24.0, .85);
            for (int j = 0; j < 64; j++) JET_ALPHA[j] = (float) Math.sin(Math.PI * j / 64) * .27F;
        }

        private static void setup(PoseStack pose, float time) {
            var shader = CelestialVfxRenderTypes.shader();
            // World BER poses already contain the camera view, as in StellarVortexRenderer.
            var view = pose.last().pose();
            var inverse = INVERSE.set(view).invert();
            inverse.transformDirection(RIGHT.set(1, 0, 0)).normalize();
            inverse.transformDirection(UP.set(0, 1, 0)).normalize();
            inverse.transformPosition(EYE_POSITION.set(0, 0, 0));
            uniforms(shader, view, time);
            uniforms(CelestialVfxRenderTypes.coreShader(), view, time);
        }

        private static void uniforms(net.minecraft.client.renderer.ShaderInstance shader, Matrix4f view, float time) {
            shader.safeGetUniform("FieldViewMat").set(view);
            shader.safeGetUniform("LocalEye").set(EYE_POSITION);
            shader.safeGetUniform("PhaseTime").set(time / 20F % 24000);
            shader.safeGetUniform("Profile").set(0);
        }

        private static void render(float time, PoseStack pose) {
            double seconds = time / 20.0, rotation = -seconds * .025;
            double c = Math.cos(rotation), s = Math.sin(rotation);
            try (var state = new CelestialVfxState()) {
                setup(pose, time);
                // Finish opaque surfaces before any additive field, so foreground light survives and background light is occluded.
                core(P.set(0, 0, 0), 2.15F, 1, .95F, 1);
                for (int i = 0; i < ORBITS.length; i++) {
                    worldPosition(i, time, ORBITS[i]);
                    core(ORBITS[i], .24F + i * .09F,
                            i % 3 == 0 ? .73F : .45F, i % 3 == 1 ? .92F : .65F, 1);
                }
                begin(0, false);
                int layers = EYE_POSITION.lengthSquared() > 130 * 130 ? 3 : 5;
                for (int layer = 0; layer < layers; layer++) {
                    double y = (layer - (layers - 1) * .5) * .16;
                    for (int j = 0; j < 192; j++) {
                        cloud(2.2, j, y, c, s, layer); cloud(12.4, j, y, c, s, layer);
                        cloud(12.4, j + 1, y, c, s, layer); cloud(2.2, j + 1, y, c, s, layer);
                    }
                }
                end();
                begin(1, false);
                int stride = EYE_POSITION.lengthSquared() > 130 * 130 ? 4 : 1;
                for (int i = 0; i < 9000; i += stride) {
                    int p = i * 5;
                    transform(STARS[p], STARS[p + 1], STARS[p + 2], c, s, P);
                    float v = STARS[p + 4];
                    billboard(P, STARS[p + 3], v > .985 ? .9F : .43F, .57F + v * .23F, 1, .33F + .43F * v);
                }
                billboard(P.set(0, 0, 0), 4.4F, .37F, .5F, 1, .21F);
                for (int i = 0; i < ORBITS.length; i++) {
                    billboard(ORBITS[i], .58F, .43F, .66F, 1, .32F);
                }
                end();
                begin(2, false);
                for (int arm = 0; arm < 4; arm++) for (int branch = 0; branch < 3; branch++) {
                    int index = (arm * 3 + branch) * 129 * 2;
                    double y = (branch - 1) * .13;
                    transform(SPIRAL_XZ[index], y, SPIRAL_XZ[index + 1], c, s, P);
                    for (int j = 0; j < 128; j++) {
                        index += 2;
                        transform(SPIRAL_XZ[index], y, SPIRAL_XZ[index + 1], c, s, Q);
                        ribbon(P, Q, .012F, .42F, .51F, 1, .21F, (float) (j * .18 - seconds * .7));
                        P.set(Q);
                    }
                }
                if (meshTime != time) { MESH.update(time); meshTime = time; }
                MESH.emit((x, y, z, u, v, rgba) -> vertex(x, y, z,
                        (rgba >>> 24) / 255F, (rgba >>> 16 & 255) / 255F,
                        (rgba >>> 8 & 255) / 255F, (rgba & 255) / 630F, u, v * 2 - 1));
                for (int sign = -1; sign <= 1; sign += 2) for (int strand = 0; strand < 6; strand++) {
                    jet(sign, strand, 0, seconds, c, s, P);
                    for (int j = 0; j < 64; j++) {
                        jet(sign, strand, (j + 1) / 64.0, seconds, c, s, Q);
                        ribbon(P, Q, .018F, .43F, .64F, 1, JET_ALPHA[j],
                                (float) (j * .15 - seconds * .8));
                        P.set(Q);
                    }
                }
                end();
                begin(5, false);
                for (int sign = -1; sign <= 1; sign += 2) for (int layer = 0; layer < 2; layer++) {
                    for (int j = 0; j < 24; j++) for (int k = 0; k < 32; k++) {
                        plume(sign, j, k, layer, c, s); plume(sign, j + 1, k, layer, c, s);
                        plume(sign, j + 1, k + 1, layer, c, s); plume(sign, j, k + 1, layer, c, s);
                    }
                }
                end();
                begin(4, false);
                billboard(P.set(0, 0, 0), 4.3F, .35F, .57F, 1, .72F);
                end();
            }
        }

        private static void worldPosition(int i, float time, Vector3f out) {
            double radius = DIMENSION_RADII[i];
            double angle = time * (i + 1) / 80.0 + .4, spin = Math.toRadians(time * 1.5 / (i + 1) % 360);
            double sx = radius * Math.sin(angle), sz = radius * Math.cos(angle);
            double cs = Math.cos(spin), hs = (1 - cs) * .5, sn = Math.sin(spin) / Math.sqrt(2);
            out.set((float) ((cs + hs) * sx + hs * sz), (float) (sn * (sx - sz)),
                    (float) (hs * sx + (cs + hs) * sz));
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
            MachineEffectGeometry.vertices().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR_TEX);
        }

        private static void end() {
            var data = MachineEffectGeometry.vertices().end();
            try {
                if (data.drawState().vertexCount() == 0) data.release(); else BufferUploader.drawWithShader(data);
            } finally {
                activeType.clearRenderState();
            }
        }

        private static void cloud(double radius, int segment, double y, double c, double s, int layer) {
            double x = radius * COS[segment], z = radius * SIN[segment];
            transform(x, y, z, c, s, P);
            vertex(P.x, P.y, P.z, 1, 1, 1, .26F + layer * .12F, (float) x, (float) z);
        }

        private static void transform(double x, double y, double z, double c, double s, Vector3f out) {
            double rx = x * c - z * s, rz = x * s + z * c;
            out.set((float) rx, (float) (y * TILT_COS - rz * TILT_SIN),
                    (float) (y * TILT_SIN + rz * TILT_COS));
        }

        private static void jet(int sign, int strand, double t, double seconds, double c, double s, Vector3f out) {
            double angle = strand * TAU / 6 + t * 8 - seconds * .18;
            double radius = .10 + t * t * .9;
            transform(Math.cos(angle) * radius, sign * (2.4 + t * 7.7), Math.sin(angle) * radius, c, s, out);
        }

        private static void plume(int sign, int step, int segment, int layer, double c, double s) {
            double t = step / 24.0, radius = PLUME_RADII[layer][step];
            transform(radius * PLUME_COS[segment], sign * (2.4 + t * 7.7), radius * PLUME_SIN[segment], c, s, P);
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
            MachineEffectGeometry.vertices().vertex(x,y,z).color(r,g,b,a).uv(u,v).endVertex();
        }
    }
}
