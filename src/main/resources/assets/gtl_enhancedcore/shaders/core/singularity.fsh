#version 150

uniform mat4 ProjMat;
uniform mat4 FieldViewMat;
uniform mat4 InverseFieldViewMat;
uniform vec3 DiskNormal;
uniform vec3 DiskTangent;
uniform float PhaseTime;
uniform int OpaquePass;
in vec3 viewPosition;
in vec3 fieldPosition;
out vec4 fragColor;

const float BOUND = 8.0;

float noise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f*f*(3.0-2.0*f);
    vec4 h = fract(sin(vec4(dot(i,vec2(127.1,311.7)),
                            dot(i+vec2(1,0),vec2(127.1,311.7)),
                            dot(i+vec2(0,1),vec2(127.1,311.7)),
                            dot(i+vec2(1,1),vec2(127.1,311.7))))*43758.5453);
    return mix(mix(h.x,h.y,f.x),mix(h.z,h.w,f.x),f.y);
}

vec4 disk(vec3 p, vec3 direction) {
    float radius = length(p);
    float edge = smoothstep(2.65,3.15,radius) * (1.0-smoothstep(5.0,6.3,radius));
    if (edge < 0.001) return vec4(0.0);
    vec3 side = normalize(cross(DiskNormal,DiskTangent));
    float angle = atan(dot(p,side),dot(p,DiskTangent));
    float flow = angle - PhaseTime * 0.20 / pow(radius,1.5);
    vec2 advected = vec2(cos(flow),sin(flow))*radius;
    float eddies = noise(advected*5.5)*0.65 + noise(advected*15.0)*0.35;
    float filament = 0.5+0.5*sin(radius*81.0 + flow*13.0
                                + 2.7*sin(flow*7.0+radius*2.0) + eddies*9.0);
    float bands = 0.5+0.5*sin(radius*29.0 + sin(flow*3.0+radius)*2.0);
    float detail = 0.10+0.52*filament*filament+0.27*eddies+0.11*bands;
    float approaching = dot(normalize(cross(DiskNormal,p)), -direction);
    float doppler = clamp(1.0+0.62*approaching,0.42,1.62);
    float heat = exp(-(radius-2.8)*0.65);
    vec3 color = mix(vec3(0.72,0.19,0.045),vec3(1.0,0.85,0.57),heat);
    color = mix(color,vec3(0.82,0.92,1.0),pow(max(approaching,0.0),3.0)*heat*0.20);
    color *= (0.22+1.50*detail)*doppler;
    float opacity = edge*(0.46+0.43*detail);
    return vec4(color,opacity);
}

void main() {
    bool ortho = abs(ProjMat[3][3]) > 0.5;
    vec3 eye = ortho ? vec3(viewPosition.xy,0.0) : vec3(0.0);
    vec3 origin = (InverseFieldViewMat * vec4(eye,1.0)).xyz;
    vec3 ray = ortho ? normalize((InverseFieldViewMat * vec4(0.0,0.0,-1.0,0.0)).xyz)
                     : normalize(fieldPosition-origin);
    float b = dot(origin,ray);
    float discriminant = b*b-dot(origin,origin)+BOUND*BOUND;
    if (discriminant <= 0.0) discard;
    float entry = max(0.0,-b-sqrt(discriminant));
    if (-b+sqrt(discriminant) <= 0.0) discard;
    vec3 p = origin+ray*(entry+0.001);
    vec3 velocity = ray;
    float momentum2 = dot(cross(p,ray),cross(p,ray));
    vec3 radiance = vec3(0.0);
    float transmission = 1.0;
    bool captured = false;
    vec3 firstHit = vec3(0.0);
    bool foundHit = false;

    // Schwarzschild null-ray bending in horizon-radius units. Disk crossings
    // are solved inside each step so a thin disk cannot fall between samples.
    for (int i=0; i<144; ++i) {
        float radius = length(p);
        if (radius < 1.015) {
            captured = true;
            if (!foundHit) firstHit = p;
            break;
        }
        if (radius > BOUND+0.1) break;
        float stepSize = clamp((radius-0.8)*0.10,0.025,0.42);
        vec3 acceleration = -1.5*momentum2*p/pow(radius,5.0);
        vec3 next = p+velocity*stepSize+0.5*acceleration*stepSize*stepSize;
        float nextRadius = length(next);
        vec3 nextAcceleration = -1.5*momentum2*next/pow(max(nextRadius,0.8),5.0);
        vec3 nextVelocity = velocity+0.5*(acceleration+nextAcceleration)*stepSize;
        float h0 = dot(p,DiskNormal);
        float h1 = dot(next,DiskNormal);
        if (h0*h1 <= 0.0 && abs(h0-h1)>0.00001) {
            vec3 hit = mix(p,next,clamp(h0/(h0-h1),0.0,1.0));
            vec4 sampleColor = disk(hit,normalize(velocity));
            if (sampleColor.a > 0.005) {
                if (!foundHit) { firstHit=hit; foundHit=true; }
                radiance += transmission*sampleColor.rgb*sampleColor.a;
                transmission *= 1.0-sampleColor.a;
            }
        }
        // Sparse hot gas gives a finite thickness at edge-on viewing angles.
        float height = abs((h0+h1)*0.5);
        if (height < 0.20 && radius > 2.7 && radius < 6.3) {
            vec4 gas = disk((p+next)*0.5,normalize(velocity));
            float density = exp(-height*height/0.006)*stepSize*1.2;
            float opacity = 1.0-exp(-gas.a*density);
            if (opacity > 0.001) {
                if (!foundHit) { firstHit=p; foundHit=true; }
                radiance += transmission*gas.rgb*opacity;
                transmission *= 1.0-opacity;
            }
        }
        p = next;
        velocity = nextVelocity;
        if (transmission < 0.01) break;
    }

    float alpha = captured ? 1.0 : 1.0-transmission;
    if (alpha < 0.008 || ((OpaquePass != 0) != captured)) discard;
    // Bounded tone mapping preserves colored filaments instead of saturating white.
    vec3 color = radiance/max(alpha,0.001);
    color = vec3(1.0)-exp(-color*1.45);
    fragColor = vec4(color,alpha);
    vec4 clip = ProjMat*FieldViewMat*vec4(firstHit,1.0);
    gl_FragDepth = clamp(clip.z/clip.w*0.5+0.5,0.0,1.0);
}
