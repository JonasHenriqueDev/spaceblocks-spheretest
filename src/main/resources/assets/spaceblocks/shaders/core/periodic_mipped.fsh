#version 150
uniform sampler2D Sampler0;
uniform vec4 ColorModulator;
in float vertexDistance;
in vec4 vertexColor;
in vec2 texCoord0;
in float sourceY;
uniform float SourceFloor;
out vec4 fragColor;
void main() {
    if (sourceY < SourceFloor - 0.001) discard;
    vec4 color = texture(Sampler0, texCoord0) * vertexColor * ColorModulator;
    if (color.a < 0.1) discard;
    fragColor = color;
}
