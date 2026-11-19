#version 150
in vec3 Position;
in vec4 Color;
in vec2 UV0;
uniform mat4 ProjMat;
uniform int Kind;
uniform mat4 FieldViewMat;
out vec4 vertexColor;
out vec2 fieldUV;
out vec3 localPosition;
flat out vec3 volumeHalfSize;
void main(){
 localPosition=Position;vertexColor=Color;fieldUV=UV0;
 volumeHalfSize=abs(Position);
 gl_Position=ProjMat*FieldViewMat*vec4(Position,1.0);
 // Far clipping the proxy would also erase visible mist in front of that plane.
 if(Kind==0)gl_Position.z=min(gl_Position.z,gl_Position.w);
}
