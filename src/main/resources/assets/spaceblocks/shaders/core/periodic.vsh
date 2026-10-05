#version 150
// Jeija/Spheretest PLANET_KEEP_SCALE adaptation, LGPL-2.1-or-later.
// camera-relative cut through camera and vertex. See docs/SPHERETEST-SOURCES.md.
#moj_import <light.glsl>
in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV2;
in vec3 Normal;
uniform sampler2D Sampler2;
uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform vec3 ChunkOffset;
uniform vec3 Eye;
uniform float PlanetRadius;
out float vertexDistance;
out vec4 vertexColor;
out vec2 texCoord0;
void main() {
    vec3 planar = Position + ChunkOffset - Eye;
    float distance = length(planar.xz);
    float angle = distance / PlanetRadius;
    float radial = PlanetRadius * exp(planar.y / PlanetRadius);
    float factor = distance < 0.000001 ? exp(planar.y / PlanetRadius) : radial * sin(angle) / distance;
    vec3 pos = vec3(planar.x * factor, radial * cos(angle) - PlanetRadius, planar.z * factor);
    gl_Position = ProjMat * ModelViewMat * vec4(pos, 1.0);
    vertexDistance = length(pos);
    vertexColor = Color * minecraft_sample_lightmap(Sampler2, UV2);
    texCoord0 = UV0;
}
