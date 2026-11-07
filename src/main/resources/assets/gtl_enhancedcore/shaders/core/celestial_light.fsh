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
vec3 displayColor(vec3 color) {
    color=max(color,vec3(0));
    return mix(color*12.92,1.055*pow(color,vec3(1.0/2.4))-.055,step(vec3(.0031308),color));
}
void main() {
    vec3 color=vertexColor.rgb;
    float alpha=vertexColor.a;
    // Every branch writes depth explicitly: the solid spheres share this shader with additive fields.
    gl_FragDepth=gl_FragCoord.z;
    if(Kind==0) {
        vec2 p=fieldUV;float r=length(p),t=(r-2.6)/9.45;
        if(t<-.06||t>1.04) discard;
        float twist=Profile==0?2.8:3.9,arms=Profile==0?2.0:3.0;
        float phase=atan(p.y,p.x)-twist*t;
        vec2 npos=p*1.45+vec2((vertexColor.a-.5)*17.0,PhaseTime*.022);
        float coarse=fbm(npos),fine=fbm(npos*3.4+coarse*2.0);
        float width=.11+.13*sin(clamp(t,0.0,1.0)*3.14159);
        float d=sin((phase+(coarse-.5)*.29)*arms);
        float arm=exp(-d*d/(width*width));
        float branch=exp(-pow(sin((phase+.10+sin(t*22.0)*.035)*arms)/.065,2.0));
        float edge=smoothstep(-.04,.12,t)*(1.0-smoothstep(.88,1.035,t));
        float dust=smoothstep(.24,.69,coarse)*(.35+.65*fine);
        float filaments=pow(max(fine-.22,0.0),1.5)*2.0;
        float density=(arm*dust*.75+branch*filaments*.34)*edge*(.25+.75*smoothstep(.29,.47,fine));
        color=mix(vec3(.085,.32,.8),vec3(.44,.09,.76),.5+.5*sin(phase*arms+t*7.0+coarse*2.0));
        color=mix(color,vec3(.52,.7,1.0),clamp(filaments*arm*.5,0.0,.65));
        color*=1.65;
        alpha=density*(Profile==0?.41:.34);
    } else if(Kind==1) {
        float radius=length(fieldUV);if(radius>1.0) discard;
        alpha*=exp(-radius*radius*7.0)+.7*exp(-radius*radius*50.0);
        color*=.85+.12*sin(PhaseTime*.8+vertexColor.r*31.0);
    } else if(Kind==2) {
        float soft=pow(max(0.0,1.0-abs(fieldUV.y)),1.8);
        alpha*=soft*(.65+.35*pow(.5+.5*sin(fieldUV.x*2.0-PhaseTime*2.0),5.0));
    } else if(Kind==4) {
        float radius=length(fieldUV);if(radius>1.0) discard;
        float atmosphere=exp(-pow((radius-.49)/.15,2.0));
        float rays=pow(max(0.0,1.0-abs(fieldUV.y)*32.0),4.0)
                   +.35*pow(max(0.0,1.0-abs(fieldUV.x)*38.0),4.0);
        alpha*=atmosphere*.29+rays*exp(-radius*3.2)*.24;
    } else if(Kind==5) {
        float angle=fieldUV.x*6.2831853,t=fieldUV.y;
        float filament=pow(.5+.5*sin(angle*5.0+t*17.0-PhaseTime*.9),6.0);
        float mist=fbm(vec2(angle*2.4,t*9.0-PhaseTime*.13));
        color=mix(vec3(.10,.37,.85),vec3(.55,.24,.88),t);
        alpha=(filament*.13+mist*.045)*sin(t*3.14159);
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
        float angle=PhaseTime*.06,co=cos(angle),si=sin(angle);
        n.xz=mat2(co,-si,si,co)*n.xz;
        vec3 weights=pow(abs(n),vec3(4));weights/=max(.001,weights.x+weights.y+weights.z);
        float texture=fbm(n.yz*18.0+PhaseTime*.014)*weights.x
                     +fbm(n.zx*18.0+PhaseTime*.014)*weights.y
                     +fbm(n.xy*18.0+PhaseTime*.014)*weights.z;
        float veins=pow(max(0.0,1.0-abs(texture-.51)*8.0),3.0);
        if(CoreRadius>1.0) {
            color=mix(vec3(.12,.30,.82),vec3(.80,.91,1.0),pow(mu,.32));
            color*=.84+.28*texture;
            color+=vec3(.20,.24,.39)*veins*.08;
        } else {
            float light=.14+.86*max(0.0,dot(n,normalize(vec3(-.5,.7,.9))));
            color=mix(vertexColor.rgb*.09,vertexColor.rgb*.48,smoothstep(.36,.66,texture))*light;
            color+=vec3(.30,.55,1.0)*pow(1.0-mu,3.0)*.42;
        }
        alpha=1.0;
    }
    if(alpha<.001) discard;
    fragColor=vec4(displayColor(color),alpha);
}
