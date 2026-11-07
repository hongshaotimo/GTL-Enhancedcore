#version 150
in vec3 Position;
in vec4 Color;
in vec2 UV0;
uniform mat4 FieldViewMat;
uniform mat4 ProjMat;
out vec4 vertexColor;
out vec2 fieldUV;
out vec3 localPosition;
void main() {
    gl_Position = ProjMat * FieldViewMat * vec4(Position, 1.0);
    vertexColor = Color;
    fieldUV = UV0;
    localPosition = Position;
}
