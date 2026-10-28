#version 150
in vec4 vertexColor;
in vec2 fieldUV;
uniform float PhaseTime;
uniform float Visibility;
uniform int Kind;
out vec4 fragColor;
float hash(vec2 x){return fract(sin(dot(x,vec2(127.1,311.7)))*43758.5453);}
float noise(vec2 x){vec2 i=floor(x),f=fract(x);f=f*f*(3.0-2.0*f);return mix(mix(hash(i),hash(i+vec2(1,0)),f.x),mix(hash(i+vec2(0,1)),hash(i+vec2(1,1)),f.x),f.y);}
float fbm(vec2 x){float f=0.55*noise(x);x=mat2(.8,-.6,.6,.8)*x*2.13;f+=.28*noise(x);x=mat2(.8,-.6,.6,.8)*x*2.07;return f+.14*noise(x);}
void main(){
 vec3 color=vertexColor.rgb;float alpha=vertexColor.a;
 float cycle=mod(PhaseTime,8.0),pulse=smoothstep(1.5,3.0,cycle)*(1.0-smoothstep(5.8,8.0,cycle));
 if(Kind==0){
  vec2 p=fieldUV;float r=length(p),t=(r-126.0)/88.0;if(t<-.06||t>1.14)discard;
  float phase=atan(p.y,p.x)-2.65*t;vec2 npos=p*.075+vec2((vertexColor.a-.5)*37.0,PhaseTime*.012);
  float coarse=fbm(npos),fine=fbm(npos*3.4+coarse*2.0);
  float width=.11+.13*sin(clamp(t,0.0,1.0)*3.14159),d=sin((phase+(coarse-.5)*.29)*2.0);
  float arm=exp(-d*d/(width*width));float branch=exp(-pow(sin((phase+.10+sin(t*16.0)*.035)*2.0)/.06,2.0));
  float edge=smoothstep(-.05,.1,t)*(1.0-smoothstep(.86,1.12,t));
  float dust=smoothstep(.24,.69,coarse)*(.35+.65*fine),filaments=pow(max(fine-.22,0.0),1.5)*2.0;
  float density=(arm*dust*.75+branch*filaments*.34)*edge*(.25+.75*smoothstep(.29,.47,fine));
  color=mix(vec3(.085,.32,.8),vec3(.44,.09,.76),.5+.5*sin(phase*2.0+t*7.0+coarse*2.0));
  color=mix(color,vec3(.52,.7,1.0),clamp(filaments*arm*.5,0.0,.65));alpha=density*.46;
  color*=1.8+pulse*.35;
 }else if(Kind==1){
  float r=length(fieldUV);if(r>1.0)discard;
  float soft=exp(-r*r*7.0),core=exp(-r*r*50.0);alpha*=soft+core*.7;
  color*=.85+.12*sin(PhaseTime*.8+vertexColor.r*31.0);
 }else if(Kind==2){
  float soft=pow(max(0.0,1.0-abs(fieldUV.y)),1.6);
  alpha*=soft*(.70+.30*pow(.5+.5*sin(fieldUV.x*2.0-PhaseTime*3.0),5.0));color*=1.0+pulse*.18;
 }else{
  float x=fieldUV.x*6.28318,t=fieldUV.y;
  float fil=pow(max(.5+.5*sin(x*5.0+t*17.0-PhaseTime*1.4),0.0),7.0);
  float wisps=.5+.5*sin(x*11.0-t*31.0+PhaseTime*.6);
  color=mix(vec3(.10,.37,.85),vec3(.55,.24,.88),t);
  alpha=(fil*.23+wisps*.025)*sin(t*3.14159);
 }
 alpha*=Visibility;if(alpha<.002)discard;
 // The reference renders linear cloud colors into an sRGB display. Minecraft's
 // custom framebuffer path needs the same transfer before additive composition.
 color=max(color,vec3(0));color=mix(color*12.92,1.055*pow(color,vec3(1.0/2.4))-.055,step(vec3(.0031308),color));
 fragColor=vec4(color,alpha);
}
