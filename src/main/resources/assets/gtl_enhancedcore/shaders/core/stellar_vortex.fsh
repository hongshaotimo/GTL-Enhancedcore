#version 150
in vec4 vertexColor;
in vec2 fieldUV;
in vec3 localPosition;
uniform float PhaseTime;
uniform float Visibility;
uniform int Kind;
uniform mat4 FieldViewMat;
uniform mat4 ProjMat;
uniform vec3 LocalEye;
out vec4 fragColor;
float hash(vec2 x){return fract(sin(dot(x,vec2(127.1,311.7)))*43758.5453);}
float noise(vec2 x){vec2 i=floor(x),f=fract(x);f=f*f*(3.0-2.0*f);return mix(mix(hash(i),hash(i+vec2(1,0)),f.x),mix(hash(i+vec2(0,1)),hash(i+vec2(1,1)),f.x),f.y);}
float fbm(vec2 x){float f=0.55*noise(x);x=mat2(.8,-.6,.6,.8)*x*2.13;f+=.28*noise(x);x=mat2(.8,-.6,.6,.8)*x*2.07;return f+.14*noise(x);}
void main(){
 gl_FragDepth=gl_FragCoord.z;
 vec3 color=vertexColor.rgb;float alpha=vertexColor.a;
 float cycle=mod(PhaseTime,8.0),pulse=smoothstep(1.5,3.0,cycle)*(1.0-smoothstep(5.8,8.0,cycle));
 if(Kind==0){
  vec3 eye=LocalEye;
  vec3 direction=normalize(localPosition-eye);
  // The slab clips empty screen pixels before the bounded volume integration.
  vec3 reciprocal=1.0/(direction+vec3(.000001));
  vec3 low=(-vec3(82,164,82)-eye)*reciprocal;
  vec3 high=(vec3(82,164,82)-eye)*reciprocal;
  vec3 nearAxis=min(low,high),farAxis=max(low,high);
  float entry=max(0.0,max(max(nearAxis.x,nearAxis.y),nearAxis.z));
  float exitDistance=min(min(farAxis.x,farAxis.y),farAxis.z);
  if(entry>=exitDistance)discard;
  int steps=vertexColor.a<.75?18:28;
  float stepSize=(exitDistance-entry)/float(steps);
  vec4 volume=vec4(0);
  vec3 firstPoint=vec3(0);bool hit=false;
  for(int sampleIndex=0;sampleIndex<28;sampleIndex++){
   if(sampleIndex>=steps)break;
   vec3 q=eye+direction*(entry+(float(sampleIndex)+.5)*stepSize);
   float axial=abs(q.y)/160.0;
   float turbulence=fbm(vec2(q.x+q.z*.63,q.y+q.z*.41)*.048+vec2(PhaseTime*.015,-PhaseTime*.035));
   float radius=13.0+57.0*pow(min(axial,1.0),1.45)+(turbulence-.48)*7.0;
   float radial=length(q.xz)/radius;
   float envelope=(1.0-smoothstep(.83,1.10,radial))*(1.0-smoothstep(.83,1.0,axial));
   if(envelope<.005)continue;
   if(!hit){firstPoint=q;hit=true;}
   float fine=noise(vec2(q.x-q.z*.81,q.y+q.z*.67)*.25+vec2(turbulence*2.0,PhaseTime*.045));
   float cloud=smoothstep(.28,.79,turbulence)*(.35+.65*fine);
   float curl=atan(q.z,q.x)*3.0+q.y*.09+turbulence*8.0+PhaseTime*.17;
   float filament=pow(.5+.5*sin(curl),15.0)*smoothstep(.25,.6,turbulence);
   float rim=exp(-pow((radial-.82)*8.0,2.0));
   vec3 nebula=vec3(.004,.003,.012);
   nebula+=mix(vec3(.045,.009,.14),vec3(.018,.13,.18),fine)*cloud*1.7;
   nebula+=vec3(.11,.42,.50)*filament*(.2+.8*rim)*.85;
   nebula+=vec3(.22,.06,.27)*rim*cloud*.42;
   float sampleAlpha=1.0-exp(-envelope*(.085+cloud*.12)*stepSize*.22);
   volume.rgb+=(1.0-volume.a)*nebula*sampleAlpha;
   volume.a+=(1.0-volume.a)*sampleAlpha;
   if(volume.a>.985)break;
  }
  if(volume.a<.006)discard;
  color=volume.rgb/max(volume.a,.001);
  alpha=volume.a;
  vec4 clip=ProjMat*FieldViewMat*vec4(firstPoint,1);
  gl_FragDepth=clamp(clip.z/clip.w*.5+.5,0.0,1.0);
 }else if(Kind==1){
  float r=length(fieldUV);if(r>1.0)discard;
  float soft=exp(-r*r*7.0),core=exp(-r*r*50.0);alpha*=soft+core*.7;
  color*=.85+.12*sin(PhaseTime*.8+vertexColor.r*31.0);
 }else if(Kind==2){
  float soft=exp(-fieldUV.y*fieldUV.y*5.0)*(1.0-smoothstep(.75,1.0,abs(fieldUV.y)));
  // Continuous cores and soft shoulders remain resolved at a distance.
  alpha*=soft*(.86+.14*sin(fieldUV.x*.15-PhaseTime*.8));
  color*=.85+pulse*.08;
 }else{
  float x=fieldUV.x*6.28318,t=fieldUV.y;
  float turbulence=fbm(vec2(x*1.3,t*9.0-PhaseTime*.24));
  float fil=pow(max(.5+.5*sin(x*3.0+t*13.0-PhaseTime*.7+turbulence*2.6),0.0),9.0);
  float wisps=smoothstep(.32,.8,turbulence);
  color=mix(vec3(.075,.12,.32),vec3(.16,.48,.64),fil);
  alpha=(fil*.16+wisps*.08)*pow(max(0.0,sin(t*3.14159)),1.4);
 }
 alpha*=Visibility;if(alpha<.002)discard;
 // The reference renders linear cloud colors into an sRGB display. Minecraft's
 // custom framebuffer path needs the same transfer before additive composition.
 color=max(color,vec3(0));color=mix(color*12.92,1.055*pow(color,vec3(1.0/2.4))-.055,step(vec3(.0031308),color));
 // ADDITIVE_TRANSPARENCY uses ONE/ONE: alpha must attenuate emitted RGB here.
 fragColor=vec4(Kind==0?color:color*alpha,alpha);
}
