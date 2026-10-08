#version 150
uniform float PhaseTime;
uniform float CyclePhase;
uniform int Detail;
uniform int Kind;
in vec4 vertexColor;
in vec2 flowUV;
out vec4 fragColor;
void main() {
    float edge=exp(-flowUV.y*flowUV.y*6.5);
    float core=exp(-flowUV.y*flowUV.y*90.0);
    float density=.7;
    if (Kind==0) {
        density=.72+.18*sin(flowUV.x*.65-PhaseTime*.75);
    } else if (Kind==1) {
        float pitch=Detail!=0?2.8:5.6;
        float packet=fract((flowUV.x-PhaseTime*7.0)/pitch);
        density=.42+.5*pow(max(0.0,1.0-abs(packet-.5)*2.0),5.0);
    } else if (Kind==2) {
        float head=(CyclePhase-5.96)*34.0;
        float delta=flowUV.x-head;
        density=exp(-delta*delta/6.0)*smoothstep(5.9,6.05,CyclePhase);
    } else density=1.0;
    float alpha=vertexColor.a*edge*density;
    if(alpha<.003)discard;
    vec3 color=mix(vertexColor.rgb,vec3(1.0,.97,.79),core*(Kind==0?.12:.65));
    fragColor=vec4(color,alpha);
}
