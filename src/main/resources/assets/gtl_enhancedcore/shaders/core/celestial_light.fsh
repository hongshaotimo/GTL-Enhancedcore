#version 150
in vec4 vertexColor;
in vec2 fieldUV;
in vec3 localPosition;
uniform float PhaseTime;
uniform int Profile;
uniform int Kind;
uniform mat4 ProjMat;
uniform mat4 FieldViewMat;
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
    } else if(Kind==8) {
        float t=fieldUV.x,angle=fieldUV.y*6.2831853;
        float edge=smoothstep(0.0,.06,t)*(1.0-smoothstep(.78,1.0,t));
        float shear=angle-log(t+.18)*1.7-PhaseTime*.18;
        float gas=fbm(vec2(t*19.0,shear*5.0));
        float fine=fbm(vec2(t*43.0+gas*1.3,shear*11.0));
        float threads=pow(max(0.0,1.0-abs(gas-.54)*5.0),3.0);
        float caustic=exp(-pow((t-.065)/.025,2.0));
        color=mix(vec3(.44,.075,.018),vec3(1.0,.58,.18),threads);
        color=mix(color,vec3(.24,.055,.58),smoothstep(.28,1.0,t)*.85);
        color+=vec3(.8,.67,.42)*caustic*.6;
        alpha=edge*(.08+threads*.40+caustic*.47)*(.38+.62*fine);
    } else if(Kind==9) {
        float r=length(fieldUV);if(r>1.0||r<.43)discard;
        float angle=atan(fieldUV.y,fieldUV.x);
        float photon=exp(-pow((r-.46)/.018,2.0));
        float lens=exp(-pow((r-.61)/.035,2.0));
        float halo=exp(-pow((r-.49)/.14,2.0));
        color=mix(vec3(1.0,.45,.10),vec3(1.0,.9,.64),photon);
        alpha=photon*.95+lens*(.17+.12*sin(angle*3.0-PhaseTime*.3))+halo*.12;
    } else if(Kind==10) {
        float t=fieldUV.x,angle=fieldUV.y*6.2831853;
        float flow=angle-PhaseTime*.021;
        float n=fbm(vec2(t*8.0,flow*17.0));
        float detail=fbm(vec2(t*22.0+n*2.0,flow*43.0));
        float filaments=pow(max(0.0,1.0-abs(n-.53)*5.3),3.0);
        float edge=sin(t*3.1415926);
        float knots=pow(.5+.5*sin(angle*12.0-PhaseTime*.9),10.0);
        color=mix(vec3(.075,.035,.30),vec3(.39,.21,.75),detail);
        color=mix(color,vec3(.78,.48,.16),knots*.72);
        alpha=edge*edge*(.045+filaments*.30)*(.45+knots*.55);
    } else if(Kind==11) {
        // The secondary, folded accretion image follows the disk's projected inclination.
        vec2 normal=(FieldViewMat*vec4(0,.9,-.436,0)).xy;
        normal=length(normal)>.01?normalize(normal):vec2(0,1);
        vec2 p=fieldUV*38.0;
        vec2 q=vec2(dot(p,vec2(normal.y,-normal.x)),dot(p,normal));
        float r=length(vec2(q.x,q.y/.83));
        float arc=exp(-pow((r-21.0)/.8,2.0));
        float veil=exp(-pow((r-21.0)/2.2,2.0));
        float angle=atan(q.y,q.x);
        float gas=fbm(vec2(r*2.0,angle*9.0-PhaseTime*.25));
        float polar=smoothstep(.04,.65,abs(q.y)/max(r,.01));
        color=mix(vec3(.52,.14,.035),vec3(1.0,.72,.32),gas);
        alpha=(arc*.56+veil*.13)*polar*(.45+.55*gas);
    }
    if(alpha<.001) discard;
    fragColor=vec4(displayColor(color),alpha);
}
