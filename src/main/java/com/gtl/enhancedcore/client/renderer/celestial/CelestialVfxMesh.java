package com.gtl.enhancedcore.client.renderer.celestial;

/** Animated celestial linework in the upstream parent's 2.75x coordinate system. */
public final class CelestialVfxMesh {
    public static final double MAX_RADIUS = 12.65;
    private static final double TAU = Math.PI * 2;
    private static final int SEGMENTS = 192;
    private static final double[] COS = new double[SEGMENTS + 1], SIN = new double[SEGMENTS + 1];
    static {
        for (int i = 0; i <= SEGMENTS; i++) {
            COS[i] = Math.cos(TAU * i / SEGMENTS);
            SIN[i] = Math.sin(TAU * i / SEGMENTS);
        }
    }

    @FunctionalInterface
    public interface VertexSink {
        void vertex(float x, float y, float z, float u, float v, int rgba);
    }

    private final boolean astrograph;
    private final float[] vertices = new float[32768 * 5];
    private final int[] colors = new int[32768];
    private int size;
    // Reused orthonormal basis for a ring. No per-frame world, vector or collection allocations.
    private double ux, uy, uz, vx, vy, vz;

    public CelestialVfxMesh(boolean astrograph) { this.astrograph = astrograph; }

    public int vertexCount() { return size; }

    public void emit(VertexSink sink) {
        for (int i = 0; i < size; i++) {
            int p = i * 5;
            sink.vertex(vertices[p], vertices[p + 1], vertices[p + 2], vertices[p + 3], vertices[p + 4], colors[i]);
        }
    }

    public void update(double tick) {
        if (!Double.isFinite(tick)) throw new IllegalArgumentException("Finite celestial time required");
        size = 0;
        double time = tick % 2304000;
        corona(time);
        if (astrograph) astrolabe(time); else harmony(time);
    }

    private void corona(double time) {
        // Closed magnetic prominences, with luminous packets running around the photosphere.
        for (int ring = 0; ring < 7; ring++) {
            basis(ring * .79 + time * .0009, .37 + ring * .39);
            double radius = 2.28 + .07 * ring + .045 * Math.sin(time * .024 + ring);
            ring(radius, .065 + ring * .004, 0, 1, time * .0018 + ring * .13,
                    astrograph ? 0xAEC9FF : 0x8DCFFF, 40, 90, 2);
        }
        // Broad, very faint corona beneath the fine prominences.
        for (int ring = 0; ring < 3; ring++) {
            basis(time * .0006 + ring * 2.1, .6 + ring * .8);
            ring(2.5, .42, 0, 1, time * .001, astrograph ? 0x9067E8 : 0x6657CD, 16, 12, 3);
        }
    }

    private void harmony(double time) {
        int[] palette = {0xFF8661, 0x63E8EE, 0xB7A1FF};
        // Same radii, angular speed and (1,0,1) rotation as the three native dimension models.
        for (int a = 1; a <= 3; a++) {
            double scale = .007 + .003 * a;
            double radius = scale * (a * 100 + 160) * 1.2;
            diagonalBasis(Math.toRadians(time * 1.5 / a % 360));
            double head = (time * a / 80 + .4) / TAU;
            ring(radius, .025, 0, 1, head, palette[a - 1], 29, 110, 1);
            ring(radius, .13, head - .16, head, head, palette[a - 1], 0, 54, 1);
        }
        // Broken meridians leave large dark spaces through which the machine remains visible.
        for (int arc = 0; arc < 3; arc++) {
            basis(time * .00055 * (arc % 2 == 0 ? 1 : -1) + arc * 2.094, .52 + arc * .7);
            double start = .08 + arc * .19 + time * .000035;
            ring(11.8 + arc * .19, .032, start, start + .72, start, 0x7CAEFF, 38, 42, 3);
            ring(11.8 + arc * .19, .15, start, start + .72, start, 0x6662D9, 10, 16, 3);
            ticks(11.8 + arc * .19, 48, 0x91C9FF, time, false);
        }
        // A pair of diffuse, slowly shearing nebula streamers behind the orrery.
        for (int band = 0; band < 2; band++) {
            basis(time * .00014 + band * .3, .26 + band * .18);
            ring(10.85 + band * .6, .5, .04, .91, time * .00032 + band * .5,
                    band == 0 ? 0x4567C8 : 0x8657BD, 12, 15, 3);
        }
    }

    private void astrolabe(double time) {
        // Counter-rotating armillary circles and a graduated ecliptic, in antique gold and violet.
        for (int arm = 0; arm < 3; arm++) {
            basis(time * .00055 * (arm == 1 ? -1 : 1) + arm * 2.094, .23 + arm * .81);
            double radius = 11.55 + arm * .27;
            ring(radius, .035, 0, 1, time * .0007 + arm * .33, 0xFFD78C, 67, 72, 3);
            ring(radius - .15, .014, 0, 1, time * .0007, 0xBA8EFF, 31, 26, 3);
            ticks(radius, 72, 0xFFE4AB, time, true);
        }
        basis(time * -.0007, .13);
        // Thirteen engraved houses reflect the native thirteen-world atlas without altering its count.
        for (int house = 0; house < 13; house++) {
            double angle = TAU * house / 13;
            glyph(10.85, angle, house, 0xDDACFF);
        }
        for (int orbit = 0; orbit < 6; orbit++) {
            basis(orbit * .618 + time * .00028, .21 + orbit * .24);
            double radius = 3.5 + orbit * 1.21;
            ring(radius, .018, 0, 1, time * (.0008 + orbit * .00009),
                    orbit % 2 == 0 ? 0xA38AFF : 0x65CDFF, 24, 80, 2);
        }
        // Soft rotating survey sector with two sharply resolved leading filaments.
        basis(time * .0019, .13);
        ring(9.8, .44, .0, .12, .12, 0x9270F0, 14, 18, 1);
        for (int spoke = 0; spoke < 2; spoke++) radial(3.05, 10.3, .12 * TAU + spoke * .028, .022, 0xCDA8FF, 112);
        for (int arc = 0; arc < 4; arc++) {
            basis(time * -.00038 + arc * 1.571, .45 + arc * .4);
            ring(8.3 + arc * .48, .11, .07 + arc * .21, .24 + arc * .21,
                    time * .0009, 0x8F6FDE, 18, 34, 2);
        }
    }

    private void glyph(double radius, double angle, int index, int rgb) {
        double span = .019;
        ring(radius + .19, .018, (angle - span) / TAU, (angle + span) / TAU, 0, rgb, 115, 0, 0);
        radial(radius - .26, radius + .29, angle, .018, rgb, 140);
        radial(radius - .05, radius + .17, angle - span, .015, rgb, 95);
        if (index % 2 == 0) radial(radius - .05, radius + .17, angle + span, .015, rgb, 95);
        ring(radius - .19, .016, (angle - span * .7) / TAU, (angle + span * .7) / TAU, 0, rgb, 110, 0, 0);
    }

    private void ticks(double radius, int count, int rgb, double time, boolean gold) {
        for (int i = 0; i < count; i++) {
            boolean major = i % 6 == 0;
            radial(radius - (major ? .27 : .10), radius + (major ? .12 : .035),
                    TAU * i / count, major ? .021 : .012, rgb, major ? 124 : 62);
        }
    }

    private void basis(double azimuth, double tilt) {
        double c = Math.cos(azimuth), s = Math.sin(azimuth), ct = Math.cos(tilt), st = Math.sin(tilt);
        ux = c; uy = 0; uz = s;
        vx = -s * ct; vy = st; vz = c * ct;
    }

    private void diagonalBasis(double angle) {
        double c = Math.cos(angle), s = Math.sin(angle), h = (1 - c) * .5, k = s / Math.sqrt(2);
        // U is native +Z; V is native +X, so atan starts at the same planet position.
        ux = h; uy = -k; uz = c + h;
        vx = c + h; vy = k; vz = h;
    }

    private void ring(double radius, double width, double start, double end, double head,
                      int rgb, int baseAlpha, int packetAlpha, int packets) {
        int segments = Math.max(2, (int) Math.ceil((end - start) * SEGMENTS));
        for (int i = 0; i < segments; i++) {
            double u0 = start + (end - start) * i / segments, u1 = start + (end - start) * (i + 1) / segments;
            // Whole rings reuse the trig table; short etched arcs retain their exact endpoints.
            double c0 = start == 0 && end == 1 ? COS[i] : Math.cos(TAU * u0);
            double s0 = start == 0 && end == 1 ? SIN[i] : Math.sin(TAU * u0);
            double c1 = start == 0 && end == 1 ? COS[i + 1] : Math.cos(TAU * u1);
            double s1 = start == 0 && end == 1 ? SIN[i + 1] : Math.sin(TAU * u1);
            double envelope0 = end - start < .99 ? Math.sin(Math.PI * i / segments) : 1;
            double envelope1 = end - start < .99 ? Math.sin(Math.PI * (i + 1) / segments) : 1;
            int a0 = (int) ((baseAlpha + packetAlpha * packet(u0 * packets - head)) * envelope0);
            int a1 = (int) ((baseAlpha + packetAlpha * packet(u1 * packets - head)) * envelope1);
            ringVertex(c0, s0, radius - width, u0, 0, rgb, a0);
            ringVertex(c1, s1, radius - width, u1, 0, rgb, a1);
            ringVertex(c1, s1, radius + width, u1, 1, rgb, a1);
            ringVertex(c0, s0, radius + width, u0, 1, rgb, a0);
        }
    }

    private void ringVertex(double c, double s, double radius, double u, double v, int rgb, int alpha) {
        vertex((ux * c + vx * s) * radius, (uy * c + vy * s) * radius,
                (uz * c + vz * s) * radius, fraction(u), v, rgb, alpha);
    }

    private void radial(double inner, double outer, double angle, double width, int rgb, int alpha) {
        double c = Math.cos(angle), s = Math.sin(angle);
        double dx = ux * c + vx * s, dy = uy * c + vy * s, dz = uz * c + vz * s;
        double wx = (-ux * s + vx * c) * width, wy = (-uy * s + vy * c) * width, wz = (-uz * s + vz * c) * width;
        vertex(dx * inner - wx, dy * inner - wy, dz * inner - wz, 0, 0, rgb, alpha);
        vertex(dx * outer - wx, dy * outer - wy, dz * outer - wz, 1, 0, rgb, alpha);
        vertex(dx * outer + wx, dy * outer + wy, dz * outer + wz, 1, 1, rgb, alpha);
        vertex(dx * inner + wx, dy * inner + wy, dz * inner + wz, 0, 1, rgb, alpha);
    }

    private void vertex(double x, double y, double z, double u, double v, int rgb, int alpha) {
        int p = size * 5;
        vertices[p] = (float) x; vertices[p + 1] = (float) y; vertices[p + 2] = (float) z;
        vertices[p + 3] = (float) u; vertices[p + 4] = (float) v;
        colors[size++] = rgb << 8 | Math.min(255, Math.max(0, alpha));
    }

    private static double fraction(double x) { return x - Math.floor(x); }
    private static double packet(double x) {
        double distance = fraction(x + .5) - .5;
        double p = Math.max(0, 1 - distance * distance * 100);
        return p * p;
    }
}
