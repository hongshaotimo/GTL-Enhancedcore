#version 150
uniform mat4 ProjMat;
uniform mat4 FieldViewMat;
uniform mat4 InverseFieldViewMat;
uniform float PhaseTime;
uniform float Pressure;
uniform float Pulse;
uniform float Visibility;
uniform int Detail;
uniform int Pass;
in vec3 viewPosition;
in vec3 fieldPosition;
out vec4 fragColor;

float hash(vec3 p) { return fract(sin(dot(p,vec3(127.1,311.7,74.7)))*43758.5453); }
float noise(vec3 p) {
    vec3 i=floor(p),f=fract(p);f=f*f*(3.0-2.0*f);
    return mix(mix(mix(hash(i),hash(i+vec3(1,0,0)),f.x),
                   mix(hash(i+vec3(0,1,0)),hash(i+vec3(1,1,0)),f.x),f.y),
               mix(mix(hash(i+vec3(0,0,1)),hash(i+vec3(1,0,1)),f.x),
                   mix(hash(i+vec3(0,1,1)),hash(i+vec3(1,1,1)),f.x),f.y),f.z);
}
float intersection(vec3 origin,vec3 ray,float radius) {
    float b=dot(origin,ray),disc=b*b-dot(origin,origin)+radius*radius;
    if (disc<0.0) return -1.0;
    float front=-b-sqrt(disc),back=-b+sqrt(disc);
    return back<0.0?-1.0:(front>=0.0?front:back);
}
void main() {
    bool ortho=abs(ProjMat[3][3])>.5;
    vec3 origin=(InverseFieldViewMat*vec4(ortho?vec3(viewPosition.xy,0):vec3(0),1)).xyz;
    vec3 ray=ortho?normalize((InverseFieldViewMat*vec4(0,0,-1,0)).xyz):normalize(fieldPosition-origin);
    float radius=11.8-.65*Pressure;
    float hit=intersection(origin,ray,Pass==0?radius:15.0);
    if (hit<0.0) discard;
    vec3 point=origin+ray*hit;
    vec3 color;
    float alpha;
    if (Pass==0) {
        vec3 n=point/radius;
        // Differential rotation is advected in 3D, avoiding a longitude seam at the poles.
        float angle=PhaseTime*(.032+.018*(1.0-n.y*n.y));
        vec3 adv=vec3(cos(angle)*n.x-sin(angle)*n.z,n.y,sin(angle)*n.x+cos(angle)*n.z);
        float broad=noise(adv*8.0),granules=noise(adv*(Detail!=0?47.0:21.0));
        float warp=noise(adv*17.0);
        float bands=sin(adv.y*110.0+warp*9.0+adv.x*11.0);
        float filaments=pow(max(0.0,1.0-abs(bands)),4.0);
        float facing=clamp(dot(n,-ray),0.0,1.0);
        float heat=.3+.35*broad+.20*granules+.15*filaments;
        vec3 orange=mix(vec3(.72,.14,.014),vec3(1.0,.58,.075),heat);
        float core=pow(facing,5.0)*(.78+.12*Pressure)+.15*filaments;
        color=mix(orange,vec3(1.0,.98,.90),clamp(core,0.0,.96));
        color*=.72+.27*facing;
        color+=vec3(.055,.035,.008)*Pulse;
        alpha=Visibility;
    } else {
        float along=max(0.0,-dot(origin,ray));
        vec3 nearest=origin+ray*along;
        float radial=length(nearest);
        if (radial<radius*.94 || radial>=15.0) discard;
        vec3 direction=normalize(nearest);
        float angle=atan(direction.z,direction.x);
        float thread=.5+.5*sin(angle*33.0+direction.y*51.0-PhaseTime*.18
                               +3.0*noise(direction*12.0));
        float rim=exp(-max(0.0,radial-radius)*1.2);
        float boundary=1.0-smoothstep(14.0,15.0,radial);
        alpha=boundary*rim*(.065+.20*pow(thread,4.0))*(1.0+.3*Pulse)*Visibility;
        if(alpha<.003)discard;
        color=mix(vec3(1.0,.27,.015),vec3(1.0,.68,.18),thread*.5);
    }
    fragColor=vec4(clamp(color,vec3(0),vec3(.985)),alpha);
    vec4 clip=ProjMat*FieldViewMat*vec4(point,1);
    gl_FragDepth=clamp(clip.z/clip.w*.5+.5,0.0,1.0);
}
