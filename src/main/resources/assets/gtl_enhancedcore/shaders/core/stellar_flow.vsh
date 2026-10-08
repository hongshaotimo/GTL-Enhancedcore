#version 150
in vec3 Position;
in vec4 Color;
in vec2 UV0;
uniform mat4 ProjMat;
uniform mat4 FieldViewMat;
out vec4 vertexColor;
out vec2 flowUV;
void main() {
    vertexColor=Color;flowUV=UV0;
    gl_Position=ProjMat*FieldViewMat*vec4(Position,1);
}
