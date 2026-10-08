#version 150
in vec3 Position;
uniform mat4 ProjMat;
uniform mat4 FieldViewMat;
out vec3 viewPosition;
out vec3 fieldPosition;
void main() {
    fieldPosition=Position;
    vec4 view=FieldViewMat*vec4(Position,1.0);
    viewPosition=view.xyz;
    gl_Position=ProjMat*view;
}
