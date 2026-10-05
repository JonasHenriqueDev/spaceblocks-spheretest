#version 150

#moj_import <fog.glsl>

uniform sampler2D Sampler0;

uniform vec4 ColorModulator;
uniform float FogStart;
uniform float FogEnd;
uniform vec4 FogColor;

in float vertexDistance;
in vec4 vertexColor;
in vec2 texCoord0;

out vec4 fragColor;

uniform float DetailVisibility;
uniform float FaceSize;
uniform int FaceIndex;
uniform int CachedMode;
uniform int PortalSourceFace;
in vec2 portalTargetUV;
in vec2 portalSourceUV;
#moj_import <spaceblocks:portal_common.glsl>
void main() {
 if(CachedMode==0&&(any(lessThan(portalTargetUV,vec2(0)))||any(greaterThanEqual(portalTargetUV,vec2(FaceSize)))))discard;
 if(CachedMode==2){int face;vec2 source=portalFold(portalTargetUV,face);if(face!=PortalSourceFace||any(greaterThan(abs(source-portalSourceUV),vec2(.05))))discard;}

    float threshold=fract(sin(dot(gl_FragCoord.xy,vec2(12.9898,78.233)))*43758.5453);
    if(DetailVisibility<=threshold)discard;
    vec4 color = texture(Sampler0, texCoord0) * vertexColor * ColorModulator;
    fragColor = linear_fog(color, vertexDistance, FogStart, FogEnd, FogColor);
}
