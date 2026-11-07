#version 150

uniform float Time;
uniform vec3 Center;
uniform vec3 CameraPosition;
uniform vec3 FacingAxis;
uniform float BlackHoleAndDiskRadius;
uniform float SpaceSolidRadius;
uniform float SpaceFadeRadius;
uniform float RotationSpeed;
uniform vec3 BlackHoleRotation;
uniform float PassMode;

#moj_import <gtl_enhancedcore:heart/volume_common.glsl>

in vec3 volumeSurfacePosition;
in vec3 volumeModelPosition;

out vec4 fragColor;

const float PI = 3.14159265359;
const float TAU = 6.28318530718;
const float RS = 1.0;
const float ISCO = 3.0;
const float DISK_IN = 2.2;
const float DISK_OUT = 14.0;
const float SMOOTH_TICK_SECONDS = 0.05;
const float LENS_ALPHA_SCALE = 1.0;
const float BACKGROUND_ALPHA_SCALE = 1.0;
const float SHADOW_CORE_RADIUS_SCALE = 1.0;
const float EMISSION_ALPHA_VISIBILITY = 0.0;
// Shared visual language with stellar_vortex: cobalt, violet and cool dust.
const vec3 NEBULA_BLUE = vec3(0.085, 0.32, 0.80);
const vec3 NEBULA_VIOLET = vec3(0.44, 0.09, 0.76);
const vec3 NEBULA_DUST = vec3(0.52, 0.70, 1.0);
const float HEART_INTENSITY = 1.35;
// Radiant-projected ratio: event horizon / full accretion disk is PROJECTED_SHADOW_RADIUS / PROJECTED_DISK_RADIUS.
const float PROJECTED_SHADOW_RADIUS = 0.07464;
const float PROJECTED_SHADOW_CONTACT_RADIUS = 0.132;
const float PROJECTED_DISK_RADIUS = 0.457;
const float SHADOW_CONTACT_RING_WIDTH = 0.012;
const int TRACE_STEPS = 200;

vec3 getModelForward() {
    vec3 forward = vec3(FacingAxis.x, 0.0, FacingAxis.z);
    if (dot(forward, forward) < 0.0001) {
        return vec3(0.0, 0.0, 1.0);
    }
    return normalize(forward);
}

vec3 getModelRight(vec3 forward) {
    return normalize(cross(vec3(0.0, 1.0, 0.0), forward));
}

float getSpin(vec3 forward) {
    return dot(forward, vec3(1.0, 0.0, 1.0)) < 0.0 ? -1.0 : 1.0;
}

vec3 toModelSpace(vec3 worldVector, vec3 modelRight, vec3 modelUp, vec3 modelForward) {
    return vec3(
        dot(worldVector, modelRight),
        dot(worldVector, modelUp),
        dot(worldVector, modelForward)
    );
}

vec3 fromModelSpace(vec3 modelVector, vec3 modelRight, vec3 modelUp, vec3 modelForward) {
    return modelRight * modelVector.x + modelUp * modelVector.y + modelForward * modelVector.z;
}

vec3 rotateAroundAxis(vec3 vector, vec3 axis, float angle) {
    float c = cos(angle);
    float s = sin(angle);
    return vector * c + cross(axis, vector) * s + axis * dot(axis, vector) * (1.0 - c);
}

void rotateBasis(inout vec3 modelRight, inout vec3 modelUp, inout vec3 modelForward, vec3 axis, float angle) {
    vec3 normalizedAxis = normalize(axis);
    modelRight = rotateAroundAxis(modelRight, normalizedAxis, angle);
    modelUp = rotateAroundAxis(modelUp, normalizedAxis, angle);
    modelForward = rotateAroundAxis(modelForward, normalizedAxis, angle);
}

vec2 sphericalUv(vec3 direction) {
    vec3 rd = normalize(direction);
    return vec2(
        atan(rd.z, rd.x) / TAU + 0.5,
        asin(clamp(rd.y, -0.999, 0.999)) / PI + 0.5
    );
}

float hash(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

float gNoise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    vec2 u = f * f * f * (f * (f * 6.0 - 15.0) + 10.0);
    return mix(
        mix(hash(i), hash(i + vec2(1.0, 0.0)), u.x),
        mix(hash(i + vec2(0.0, 1.0)), hash(i + vec2(1.0, 1.0)), u.x),
        u.y
    );
}

float fbmLite(vec2 p) {
    float v = 0.5 * gNoise(p);
    p = mat2(0.866, 0.5, -0.5, 0.866) * p * 2.03 + vec2(47.0, 13.0);
    v += 0.25 * gNoise(p);
    return v;
}

float filteredDiskCloud(vec2 p, float footprint) {
    float value = 0.0;
    float amplitude = 0.5;
    mat2 rotation = mat2(0.866, 0.5, -0.5, 0.866);
    for (int octave = 0; octave < 4; octave++) {
        float resolved = 1.0 - smoothstep(0.35, 1.1, footprint);
        value += amplitude * mix(0.5, gNoise(p), resolved);
        p = rotation * p * 2.03 + vec2(47.0, 13.0);
        footprint *= 2.03;
        amplitude *= 0.49;
    }
    return value;
}

vec4 shadeDisk(vec3 hit, vec3 vel, float time, int crossing, float spin, float footprint) {
    float r = length(hit.xz);
    if (r < DISK_IN * 0.55 || r > DISK_OUT * 1.04) {
        return vec4(0.0);
    }

    float phi = atan(hit.z, hit.x);
    float radial = clamp((r - DISK_IN) / (DISK_OUT - DISK_IN), 0.0, 1.0);
    float logR = log2(max(r, 0.1));
    // Absolute time times a radial orbital frequency winds any texture into ever
    // finer concentric moire. Keep local shear bounded while the complete cloud turns.
    float rotation = time * 0.12 + sin(logR * 1.5 - time * 0.07) * 0.13;
    float c = cos(rotation), s = sin(rotation);
    vec2 advected = mat2(c, -s, s, c) * hit.xz;

    // Two eroded spiral arms and offshoots, using five native noise octaves in total.
    // Differential advection supplies depth through the actual lensed disk intersections.
    float coarse = filteredDiskCloud(advected * 0.48 + vec2(logR * 0.65, time * 0.025),
        footprint * 0.55);
    float fine = mix(0.5, gNoise(advected * 2.1 + vec2(coarse * 2.4, time * 0.045)),
        1.0 - smoothstep(0.35, 1.1, footprint * 2.1));
    float phase = phi - radial * 3.8 + rotation;
    float warp = (coarse - 0.5) * 0.88;
    float armDistance = sin((phase + warp) * 2.0);
    float width = 0.26 + 0.22 * sin(radial * PI);
    float arm = exp(-armDistance * armDistance / (width * width));
    float branchDistance = sin((phase + warp * 0.54 + 0.14 +
        sin(radial * 14.0 + coarse * 2.0) * 0.08) * 2.0);
    float branch = exp(-branchDistance * branchDistance / 0.018);
    float dust = (0.22 + 0.78 * smoothstep(0.14, 0.76, coarse)) * (0.48 + 0.52 * fine);
    float filaments = pow(max(fine - 0.12, 0.0), 1.35) * 1.65;
    float interarm = 0.23 * smoothstep(0.12, 0.74, coarse);
    float density = (arm * dust * 0.76 + branch * filaments * 0.24 + interarm) *
        (0.52 + 0.48 * fine);
    float edge = smoothstep(DISK_IN * 0.72, ISCO * 1.08, r) *
        (1.0 - smoothstep(DISK_OUT * 0.60, DISK_OUT, r));

    float orbSpeed = sqrt(0.5 * RS / max(r, DISK_IN));
    vec3 orbDir = normalize(vec3(-hit.z, 0.0, hit.x));
    float doppler = max(0.15, 1.0 + 2.0 * dot(normalize(vel), orbDir) * orbSpeed * spin);
    float approaching = smoothstep(0.76, 1.42, doppler);
    float xr = ISCO / max(r, 0.01);
    float temp = pow(ISCO / r, 0.75) * pow(max(0.001, 1.0 - sqrt(xr)), 0.25);
    temp *= sqrt(max(0.02, 1.0 - RS / r));
    float intensity = temp * density * edge * 8.4;

    float chroma = 0.5 + 0.5 * sin(phase * 2.0 + radial * 7.0 + coarse * 2.0);
    vec3 color = mix(NEBULA_BLUE, NEBULA_VIOLET, chroma);
    color = mix(color, NEBULA_DUST, clamp(filaments * arm * 0.62, 0.0, 0.63));
    color *= mix(vec3(0.83, 0.66, 1.0), vec3(0.70, 1.04, 1.12), approaching);
    // Pale gold is restricted to a narrow, broken hot inner rim.
    float innerGold = exp(-pow((r - 3.35) / 0.65, 2.0)) *
        smoothstep(0.46, 0.78, fine) * 0.24;
    color = mix(color, vec3(1.0, 0.84, 0.58), innerGold);
    float dopplerBoost = pow(doppler, 2.35);
    color *= intensity * dopplerBoost * HEART_INTENSITY;
    float alpha = clamp(edge * (0.12 + density * 1.12), 0.0, 0.90);
    if (crossing >= 2) {
        color *= 0.20;
        alpha *= 0.20;
    }
    return vec4(color, alpha);
}

vec3 flowStarDirection(vec3 rd, float seconds, vec3 axis, float speed, float sway, float swaySpeed) {
    float angle = seconds * speed + sin(seconds * swaySpeed) * sway;
    return rotateAroundAxis(rd, normalize(axis), angle);
}

vec3 starField(vec3 rd, float time) {
    float seconds = time * SMOOTH_TICK_SECONDS;
    vec3 direction = flowStarDirection(rd, seconds, vec3(0.18, 0.53, -0.83),
        0.004, 0.035, 0.011);
    vec2 uv = sphericalUv(direction);
    vec2 cloudPosition = uv * vec2(13.0, 8.0) + vec2(seconds * 0.007, 0.0);
    float cloud = fbmLite(cloudPosition);
    float fine = fbmLite(cloudPosition * 3.4 + cloud * 2.0);
    float nebulaBand = exp(-pow((direction.y + sin(uv.x * TAU * 2.0) * 0.18) / 0.43, 2.0));
    float wisps = smoothstep(0.28, 0.65, cloud) * (0.22 + fine * 0.78) * nebulaBand;
    vec3 col = mix(NEBULA_BLUE, NEBULA_VIOLET, fine) * wisps * 0.19;
    col += NEBULA_DUST * pow(max(fine - 0.38, 0.0), 2.0) * nebulaBand * 0.18;

    vec2 uvA = sphericalUv(flowStarDirection(rd, seconds, vec3(0.35, 0.91, 0.22),
        0.004, 0.025, 0.017));
    vec2 cellA = floor(uvA * vec2(94.0, 65.0));
    vec2 pointA = fract(uvA * vec2(94.0, 65.0));
    vec2 seedA = vec2(hash(cellA), hash(cellA + 127.1));
    float distanceA = length(pointA - seedA);
    col += mix(vec3(0.64, 0.58, 1.0), NEBULA_DUST, seedA.y) *
        pow(seedA.x, 11.0) * exp(-distanceA * distanceA * 760.0) * 1.10;

    vec2 uvB = sphericalUv(flowStarDirection(rd, seconds, vec3(-0.62, 0.19, 0.76),
        -0.007, 0.020, 0.023));
    vec2 cellB = floor(uvB * vec2(290.0, 190.0));
    vec2 pointB = fract(uvB * vec2(290.0, 190.0));
    vec2 seedB = vec2(hash(cellB + 43.0), hash(cellB + 91.0));
    float distanceB = length(pointB - seedB);
    float sparkle = 0.82 + 0.18 * sin(seconds * (0.30 + seedB.x * 0.24) + seedB.y * TAU);
    col += vec3(0.46, 0.65, 1.0) * pow(seedB.x, 13.0) *
        exp(-distanceB * distanceB * 1150.0) * sparkle * 0.62;
    return col;
}

vec3 polarEmission(vec3 eye, vec3 ray, float maxT, float time) {
    // Analytic ray/axis closest point: a faint confined bipolar outflow, no volume march.
    float radialRay = max(dot(ray.xz, ray.xz), 0.0001);
    float t = clamp(-dot(eye.xz, ray.xz) / radialRay, 0.0, maxT);
    vec3 closest = eye + ray * t;
    float height = abs(closest.y);
    float width = 0.15 + height * 0.055;
    vec2 bending = vec2(sin(height * 0.80 - time * 0.24),
        cos(height * 0.67 + time * 0.17)) * height * 0.016;
    closest.xz -= bending;
    float core = exp(-dot(closest.xz, closest.xz) / (width * width));
    float sheath = exp(-dot(closest.xz, closest.xz) / (width * width * 5.0));
    float ends = smoothstep(1.6, 3.1, height) * (1.0 - smoothstep(9.0, 13.8, height));
    float knots = 0.70 + 0.20 * sin(height * 2.8 - time * 0.8) +
        0.10 * sin(height * 7.3 + time * 0.36);
    vec3 jet = (NEBULA_DUST * core * 0.28 +
        mix(NEBULA_BLUE, NEBULA_VIOLET, height / 14.0) * sheath * 0.17) * ends * knots;
    return jet;
}

vec3 toneMap(vec3 color) {
    color *= 1.05;
    vec3 a = color * (color + 0.0245786) - 0.000090537;
    vec3 b = color * (0.983729 * color + 0.4329510) + 0.238081;
    color = a / b;
    return pow(max(color, vec3(0.0)), vec3(0.92));
}

void main() {
    float blackHoleAndDiskRadius = max(BlackHoleAndDiskRadius, 0.001);
    float spaceSolidRadius = max(SpaceSolidRadius, 0.0);
    float spaceFadeRadius = max(SpaceFadeRadius, spaceSolidRadius + 0.001);
    float diskAnimationTime = Time * SMOOTH_TICK_SECONDS * RotationSpeed;
    float volumeRadius = getVolumeProxyRadius(Time, blackHoleAndDiskRadius, spaceFadeRadius);
    float fadeRadius = blackHoleAndDiskRadius * spaceFadeRadius;
    vec3 baseModelForward = getModelForward();
    vec3 baseModelRight = getModelRight(baseModelForward);
    vec3 baseModelUp = vec3(0.0, 1.0, 0.0);
    vec3 modelForward = rotateAroundAxis(baseModelForward, baseModelUp, BlackHoleRotation.y);
    vec3 modelRight = normalize(cross(baseModelUp, modelForward));
    vec3 modelUp = baseModelUp;
    vec3 pitchAxis = modelRight;
    rotateBasis(modelRight, modelUp, modelForward, pitchAxis, BlackHoleRotation.x);
    vec3 rollAxis = modelUp;
    rotateBasis(modelRight, modelUp, modelForward, rollAxis, BlackHoleRotation.z);
    modelRight = normalize(modelRight);
    modelUp = normalize(modelUp);
    modelForward = normalize(modelForward);
    float spin = getSpin(baseModelForward);
    float solidRadius = blackHoleAndDiskRadius * spaceSolidRadius;

    vec3 rayToSurface = volumeSurfacePosition - CameraPosition;
    if (dot(rayToSurface, rayToSurface) < 0.000001) {
        discard;
    }
    vec3 worldRay = normalize(rayToSurface);

    float entryT = 0.0;
    float exitT = 0.0;
    if (!intersectSphere(CameraPosition, worldRay, Center, volumeRadius, entryT, exitT)) {
        discard;
    }

    float traceEntryT = max(entryT, 0.0);
    float effectFade = getSpaceVolumeAlpha(
        CameraPosition,
        worldRay,
        Center,
        entryT,
        exitT,
        solidRadius,
        fadeRadius
    );
    if (effectFade < 0.0005) {
        discard;
    }
    if (PassMode > 0.5) {
        fragColor = vec4(effectFade, effectFade, effectFade, effectFade);
        return;
    }

    vec3 cameraToCenter = Center - CameraPosition;
    float closestT = clamp(dot(cameraToCenter, worldRay), traceEntryT, exitT);
    vec3 closestPoint = CameraPosition + worldRay * closestT;
    float impactDistance = length(closestPoint - Center);
    float impactRatio = impactDistance / blackHoleAndDiskRadius;
    float impactProjectedRadius = impactRatio * PROJECTED_DISK_RADIUS;
    float worldToModel = DISK_OUT / blackHoleAndDiskRadius;
    vec3 entryWorld = CameraPosition + worldRay * traceEntryT;
    vec3 traceEye = toModelSpace(entryWorld - Center, modelRight, modelUp, modelForward) * worldToModel;
    vec3 rd = normalize(toModelSpace(worldRay, modelRight, modelUp, modelForward));
    // Derivatives are captured before the divergent ray loop. Filter unresolved
    // cloud detail using this ray cone rather than differentiating inside crossings.
    float rayCone = max(length(dFdx(rd)), length(dFdy(rd)));

    vec3 pos = traceEye;
    vec3 vel = rd;
    vec3 angularMomentum = cross(pos, vel);
    float l2 = dot(angularMomentum, angularMomentum);
    float gravCoeff = -1.5 * RS * l2;
    float minR = 1000.0;
    bool absorbed = false;
    int crossings = 0;
    vec4 diskAccum = vec4(0.0);
    vec3 glow = vec3(0.0);

    for (int i = 0; i < TRACE_STEPS; i++) {
        float r = length(pos);
        float h = 0.16 * clamp(r - 0.4 * RS, 0.06, 3.5);
        float invR2 = 1.0 / (r * r);
        float invR5 = invR2 * invR2 / r;
        vec3 acc = (gravCoeff * invR5) * pos;
        vec3 nextPos = pos + vel * h + 0.5 * acc * h * h;
        float nextR = length(nextPos);
        float invNextR2 = 1.0 / (nextR * nextR);
        float invNextR5 = invNextR2 * invNextR2 / nextR;
        vec3 nextAcc = (gravCoeff * invNextR5) * nextPos;
        vec3 nextVel = vel + 0.5 * (acc + nextAcc) * h;

        minR = min(minR, nextR);

        if (pos.y * nextPos.y < 0.0 && diskAccum.a < 0.97) {
            float t = pos.y / (pos.y - nextPos.y);
            vec3 hit = mix(pos, nextPos, t);
            float footprint = max(0.015, rayCone * length(hit - traceEye) * 1.8);
            vec4 disk = shadeDisk(hit, vel, diskAnimationTime, crossings, spin, footprint);
            diskAccum.rgb += disk.rgb * disk.a * (1.0 - diskAccum.a);
            diskAccum.a += disk.a * (1.0 - diskAccum.a);
            float bright = dot(disk.rgb, vec3(0.30, 0.50, 0.20)) * disk.a;
            glow += disk.rgb * 0.025 * max(bright - 0.22, 0.0);
            crossings++;
        }

        if (nextR < 6.0) {
            float photonDist = abs(nextR - 1.5 * RS);
            glow += vec3(0.24, 0.43, 0.82) * (1.0 / (1.0 + photonDist * photonDist * 22.0)) * h * 0.0009 / max(nextR * nextR, 0.2);
            glow += vec3(0.38, 0.12, 0.64) * max(exp(-(nextR - RS) * 3.5) * h * 0.002, 0.0);
        }

        if (nextR < RS * 0.35) {
            absorbed = true;
            break;
        }
        if (nextR > 25.0 && nextR > r) {
            break;
        }
        if (nextR > 55.0) {
            break;
        }

        pos = nextPos;
        vel = nextVel;
    }

    float photonLens = exp(-abs(minR - 1.5 * RS) * 5.0);
    float nearLens = 1.0 - smoothstep(2.4, 7.2, minR);
    float lensAlpha = clamp(nearLens * 0.34 + photonLens * 0.18, 0.0, 0.58) * effectFade * LENS_ALPHA_SCALE;
    float spaceShellAlpha = effectFade;
    vec3 escapedDirection = normalize(vel);
    vec3 escapedWorldDirection = fromModelSpace(escapedDirection, modelRight, modelUp, modelForward);
    vec3 backgroundDirection = toModelSpace(
        escapedWorldDirection,
        baseModelRight,
        baseModelUp,
        baseModelForward
    );
    vec3 backgroundColor = absorbed ? vec3(0.0) : starField(normalize(backgroundDirection), Time);

    float shadowAlphaRaw = absorbed ? 1.0 : 0.0;
    float shadowCoreRadius = max(PROJECTED_SHADOW_CONTACT_RADIUS * SHADOW_CORE_RADIUS_SCALE, PROJECTED_SHADOW_RADIUS);
    float shadowCoreAlpha = 1.0 - smoothstep(shadowCoreRadius * 0.82, shadowCoreRadius, impactProjectedRadius);
    backgroundColor *= 1.0 - shadowCoreAlpha;
    float shadowAlpha = max(shadowAlphaRaw, shadowCoreAlpha) * effectFade;

    float ringDist = abs(minR - 1.5 * RS);
    float tracedPhotonRing = exp(-ringDist * ringDist * 34.0);
    float contactRingDist = abs(impactProjectedRadius - shadowCoreRadius);
    float contactPhotonRing = exp(
        -(contactRingDist * contactRingDist) /
        max(SHADOW_CONTACT_RING_WIDTH * SHADOW_CONTACT_RING_WIDTH, 0.000001)
    );
    float photonRing = max(tracedPhotonRing, contactPhotonRing * 0.85) * effectFade;
    float contactRadius = max(shadowCoreRadius, 0.0001);
    float outerRingDistance = (impactProjectedRadius - contactRadius * 1.085) /
        max(SHADOW_CONTACT_RING_WIDTH * 0.38, 0.0001);
    float fineOuterRing = exp(-outerRingDistance * outerRingDistance) * effectFade;
    float azimuth = atan(rd.y, rd.x);
    float ringFlow = 0.82 + 0.18 * sin(azimuth * 5.0 - diskAnimationTime * 0.7);
    vec3 ringColor = vec3(0.58, 0.75, 1.0) * photonRing * 0.23 * ringFlow +
        vec3(0.48, 0.72, 1.0) * fineOuterRing * 0.075;
    vec3 jets = polarEmission(traceEye, rd, (exitT - traceEntryT) * worldToModel,
        diskAnimationTime) * effectFade * (1.0 - shadowAlpha);

    float diskAlpha = diskAccum.a * effectFade;
    vec3 diskLight = diskAccum.rgb * effectFade;
    float shellBackgroundAlpha = clamp(spaceShellAlpha * BACKGROUND_ALPHA_SCALE, 0.0, 1.0);
    float lensBackgroundAlpha = clamp(lensAlpha + photonRing * 0.20, 0.0, 0.72);
    float backgroundAlpha = max(max(shellBackgroundAlpha, lensBackgroundAlpha), shadowAlpha);
    float emissionAlpha = clamp(max(diskAlpha, photonRing * 0.42), 0.0, 0.98);
    emissionAlpha = max(emissionAlpha, clamp(max(max(jets.r, jets.g), jets.b), 0.0, 0.35));
    vec3 emission = toneMap(diskLight + glow * effectFade + ringColor + jets);
    float emissionBrightness = max(max(emission.r, emission.g), emission.b);
    emissionAlpha *= mix(
        1.0,
        smoothstep(0.025, 0.18, emissionBrightness),
        clamp(EMISSION_ALPHA_VISIBILITY, 0.0, 1.0)
    );
    float coverage = backgroundAlpha + emissionAlpha * (1.0 - backgroundAlpha);
    if (coverage < 0.0005) {
        discard;
    }

    vec3 premultipliedColor = backgroundColor * backgroundAlpha * (1.0 - emissionAlpha) + emission * emissionAlpha;
    premultipliedColor = mix(
        premultipliedColor,
        backgroundColor * backgroundAlpha * (1.0 - emissionAlpha) + emission,
        shadowAlpha
    );
    if (coverage < 0.0005) {
        discard;
    }

    fragColor = vec4(clamp(premultipliedColor, 0.0, 1.0), clamp(coverage, 0.0, 1.0));
}
