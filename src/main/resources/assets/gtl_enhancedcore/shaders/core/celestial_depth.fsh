#version 150
in vec4 vertexColor;
in vec2 fieldUV;
in vec3 localPosition;
uniform float PhaseTime;
uniform int Profile;
uniform int Kind;
uniform mat4 ProjMat;
uniform mat4 FieldViewMat;
uniform vec3 LocalEye;
uniform vec3 CoreCenter;
uniform float CoreRadius;
out vec4 fragColor;

float hash(vec2 p) { return fract(sin(dot(p,vec2(127.1,311.7)))*43758.5453); }
float noise(vec2 p) {
    vec2 i=floor(p),f=fract(p);f=f*f*(3.0-2.0*f);
    return mix(mix(hash(i),hash(i+vec2(1,0)),f.x),mix(hash(i+vec2(0,1)),hash(i+vec2(1,1)),f.x),f.y);
}
float fbm(vec2 p) {
    float f=.55*noise(p);p=mat2(.8,-.6,.6,.8)*p*2.13;
    f+=.28*noise(p);p=mat2(.8,-.6,.6,.8)*p*2.07;return f+.14*noise(p);
}
float celestialNoise3(vec3 p) {
    vec3 i=floor(p),f=fract(p);f=f*f*(3.0-2.0*f);
    vec2 uv=i.xy+vec2(37,17)*i.z+f.xy;
    return mix(noise(uv),noise(uv+vec2(37,17)),f.z);
}
float gasNoise(vec3 p) {
    float n=.57*celestialNoise3(p);p=p*2.03+vec3(3.7,8.1,1.9);
    return n+.29*celestialNoise3(p)+.14*celestialNoise3(p*2.07);
}
vec3 displayColor(vec3 color) {
    color=max(color,vec3(0));
    return mix(color*12.92,1.055*pow(color,vec3(1.0/2.4))-.055,step(vec3(.0031308),color));
}
void main() {
    vec3 color=vertexColor.rgb;
    float alpha=vertexColor.a;
    if(Kind==7) {
        vec3 ray=normalize(localPosition-LocalEye),axes=vec3(79,79,43);
        vec3 origin=LocalEye/axes,direction=ray/axes;
        float aa=dot(direction,direction),bb=dot(origin,direction),cc=dot(origin,origin)-1.0;
        float discriminant=bb*bb-aa*cc;if(discriminant<=0.0)discard;
        float nearT=max(0.0,(-bb-sqrt(discriminant))/aa),farT=(-bb+sqrt(discriminant))/aa;
        if(farT<=nearT)discard;
        // Keep the event-horizon silhouette open while the front volume uses its own spatial depth.
        if(dot(LocalEye,ray)<0.0&&length(cross(LocalEye,ray))<12.1)discard;
        vec4 volumeDepth=ProjMat*FieldViewMat*vec4(LocalEye+ray*(nearT+.5),1);
        gl_FragDepth=clamp(volumeDepth.z/volumeDepth.w*.5+.5,0.0,1.0);
        // Rotation and the linear ray transform are constant throughout this pixel's march.
        float co=cos(PhaseTime*.013),si=sin(PhaseTime*.013);
        mat2 rotation=mat2(co,-si,si,co);
        vec3 gasOrigin=LocalEye/axes,gasDirection=ray/axes;
        gasOrigin.xy=rotation*gasOrigin.xy;
        gasDirection.xy=rotation*gasDirection.xy;
        int samples=vertexColor.a<.75?16:28;
        float stepSize=(farT-nearT)/float(samples),transmission=1.0;
        vec3 scattered=vec3(0);
        for(int march=0;march<28;march++) {
            if(march>=samples)break;
            vec3 point=LocalEye+ray*(nearT+(float(march)+.5)*stepSize);
            vec3 p=gasOrigin+gasDirection*(nearT+(float(march)+.5)*stepSize);
            float rim=pow(max(0.0,1.0-dot(p,p)),1.15);
            float hole=smoothstep(14.0,27.0,length(point));
            if(rim*hole<=0.0)continue;
            float n=gasNoise(p*4.2+vec3(0,PhaseTime*.023,0));
            float filament=pow(max(0.0,1.0-abs(n-.53)*5.8),3.0);
            // Zero-density samples contribute neither extinction nor scattered light.
            if(filament<=0.0)continue;
            float curl=gasNoise(p*9.7+vec3(n*2.0,0,-PhaseTime*.017));
            float density=filament*(.18+.82*curl)*rim*hole;
            float extinction=1.0-exp(-density*stepSize*.095);
            vec3 tint=mix(vec3(.025,.045,.24),vec3(.31,.065,.52),smoothstep(.26,.72,n));
            tint=mix(tint,vec3(.84,.25,.055),pow(curl,3.0)*1.4);
            tint+=vec3(.22,.28,.48)*pow(max(0.0,(n-.52)*3.0),3.0);
            scattered+=transmission*extinction*tint*1.9;
            transmission*=1.0-extinction;
        }
        float opticalDepth=1.0-transmission;
        // A bounded pocket of deep space supplies contrast against a daytime sky.
        // Ordinary alpha blending applies only to this volume, never to the light passes.
        float chord=(farT-nearT)*length(direction);
        alpha=.94*smoothstep(0.0,.90,chord);
        color=vec3(.002,.003,.012)+scattered*1.15/max(.45,opticalDepth);
    } else {
        // Analytic ray/sphere surface, with true projected depth and a smoothly shaded silhouette.
        vec3 ray=normalize(localPosition-LocalEye),oc=LocalEye-CoreCenter;
        float b=dot(oc,ray),c=dot(oc,oc)-CoreRadius*CoreRadius;
        float discriminant=b*b-c;if(discriminant<0.0) discard;
        float distance=-b-sqrt(discriminant);if(distance<0.0) discard;
        vec3 hit=LocalEye+distance*ray,n=(hit-CoreCenter)/CoreRadius;
        vec4 projected=ProjMat*FieldViewMat*vec4(hit,1);
        gl_FragDepth=projected.z/projected.w*.5+.5;
        float mu=max(0.0,dot(n,-ray));
        if(Kind==6) {
            // Opaque event horizon, with analytic sphere depth rather than a black square sprite.
            color=vec3(.001,.0007,.003)+vec3(.08,.018,.13)*pow(1.0-mu,12.0);
        } else {
        float angle=PhaseTime*.06,co=cos(angle),si=sin(angle);
        n.xz=mat2(co,-si,si,co)*n.xz;
        vec3 weights=pow(abs(n),vec3(4));weights/=max(.001,weights.x+weights.y+weights.z);
        float texture=fbm(n.yz*18.0+PhaseTime*.014)*weights.x
                     +fbm(n.zx*18.0+PhaseTime*.014)*weights.y
                     +fbm(n.xy*18.0+PhaseTime*.014)*weights.z;
        float veins=pow(max(0.0,1.0-abs(texture-.51)*8.0),3.0);
        if(CoreRadius>1.0 && Profile==0) {
            color=mix(vec3(.12,.30,.82),vec3(.80,.91,1.0),pow(mu,.32));
            color*=.84+.28*texture;
            color+=vec3(.20,.24,.39)*veins*.08;
        } else {
            float light=.14+.86*max(0.0,dot(n,normalize(vec3(-.5,.7,.9))));
            color=mix(vertexColor.rgb*.09,vertexColor.rgb*.48,smoothstep(.36,.66,texture))*light;
            color+=vec3(.30,.55,1.0)*pow(1.0-mu,3.0)*.42;
        }
        }
        alpha=1.0;
    }
    if(alpha<.001) discard;
    fragColor=vec4(displayColor(color),alpha);
}
